package moe.shizuku.manager.ripple

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Executors

class TouchService : ITouchService.Stub() {
    @Volatile private var listening = false
    @Volatile private var speed = 64
    @Volatile private var hanvonSpeed = 100
    @Volatile private var isWaterfall = false
    @Volatile private var reverseAnimation = false
    @Volatile private var rotation = 0
    @Volatile private var maxX = 1080
    @Volatile private var maxY = 1920
    @Volatile private var touchAxesSwapped = false
    @Volatile private var coordinateMapper = TouchCoordinateMapper(maxX, maxY, false)
    @Volatile private var targetPackages: Set<String> = HashSet()
    @Volatile private var useVolumeKeyDefaults = false
    @Volatile private var keyMappingRotation = -1
    @Volatile private var touchSources: Set<String> = setOf("/dev/input/event2")
    @Volatile private var keySources: Set<String> = setOf("/dev/input/event6")
    
    private var currentX = -1
    private var currentY = -1
    private var startX = -1
    private var startY = -1
    private var isTouching = false 
    private var touchStartedAt = 0L
    private var gestureRotation = 0
    
    @Volatile private var currentTopPackage = ""
    @Volatile private var callback: ITouchCallback? = null

    @Volatile private var process: Process? = null
    @Volatile private var readerThread: Thread? = null
    @Volatile private var pollThread: Thread? = null
    @Volatile private var listeningGeneration = 0L
    private var serviceContext: Context? = null

    // Keep vendor reflection/native calls off the getevent reader thread. A
    // slow e-ink API must not make us stop consuming input events.
    private val rippleExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ripple-dispatch")
    }
    private val whitespaceRegex = Regex("\\s+")
    
    private val pageEventGate = PageEventGate()

    private val hanvon = HanvonRipple()
    private var bigme: BigmeRipple? = null
    private var isHanvon = false
    private var isIreader = false
    private var isBigme = false
    @Volatile private var detectedDevice = RippleMapping.Device.UNKNOWN

    @Synchronized
    override fun registerCallback(cb: ITouchCallback?) { this.callback = cb }

    private fun sendLog(msg: String) {
        android.util.Log.d("TouchService", msg)
        try { callback?.onLog(msg) } catch (e: Exception) {}
    }

    @Synchronized
    override fun startListening() { 
        internalStop() 
        listening = true

        // Event numbers can change after reboot or firmware updates. These
        // are only fallbacks; capability discovery below replaces them.
        touchSources = setOf("/dev/input/event2")
        keySources = setOf("/dev/input/event6")

        serviceContext = currentApplicationContext()
        
        // 1. 探测汉王
        isHanvon = hanvon.detect(serviceContext)
        detectedDevice = if (isHanvon) RippleMapping.Device.HANVON else RippleMapping.Device.UNKNOWN
        // 2. 探测掌阅
        isIreader = if (!isHanvon) try {
            Class.forName("android.eink.EPDCDevice")
            EpdcHelper.load()
            true
        } catch (e: Throwable) {
            sendLog("掌阅接口不可用: ${e.message}")
            false
        } else false
        if (isIreader) detectedDevice = RippleMapping.Device.IREADER
        
        // 3. 探测大我 (跨进程获取 Context)
        if (!isHanvon && !isIreader) {
            try {
                val context = serviceContext
                if (context != null) {
                    bigme = BigmeRipple(context)
                    isBigme = bigme?.detect() == true
                    if (isBigme) detectedDevice = RippleMapping.Device.BIGME
                }
            } catch (e: Throwable) {
                sendLog("大我探测异常: ${e.message}")
            }
        }

        when {
            isHanvon -> sendLog("启动监听 (检测到汉王接口)")
            isIreader -> {
                sendLog("启动监听 (检测到掌阅接口)")
            }
            isBigme -> sendLog("启动监听 (检测到大我接口)")
            else -> sendLog("启动监听 (当前设备不支持波纹)")
        }
        
        val readerGeneration = listeningGeneration
        readerThread = Thread {
            try {
                discoverInputSources()
                parseMaxX()
                // N10 Pro exposes touch/pen on event2 and page keys on event6.
                // getevent accepts one optional device path, so read the merged
                // stream and filter its source path below; event4 is the
                // gsensor and must never be treated as touch input.
                process = ProcessBuilder("/system/bin/getevent", "-l")
                    .redirectErrorStream(true)
                    .start()
                val reader = BufferedReader(InputStreamReader(process!!.inputStream))
                
                while (listening) {
                    val line = reader.readLine() ?: break
                    if (!listening) break
                    
                    val parts = line.trim().split(whitespaceRegex)
                    val source = parts.firstOrNull { it.startsWith("/dev/input/event") }
                        ?.removeSuffix(":")
                    if (source == null || (source !in touchSources && source !in keySources)) continue
                    val eventIndex = parts.indexOfFirst { it == "EV_ABS" || it == "EV_KEY" }
                    if (eventIndex >= 0 && parts.size > eventIndex + 2) {
                        val type = parts[eventIndex]
                        val code = parts[eventIndex + 1]
                        val value = parts[eventIndex + 2]
                        
                        if (type == "EV_ABS" && source in touchSources) {
                            if (isXCode(code)) {
                                val rawValue = parseEventValue(value)
                                currentX = rawValue
                                if (isTouching && startX == -1) startX = currentX
                            } else if (isYCode(code)) {
                                val rawValue = parseEventValue(value)
                                currentY = rawValue
                                if (isTouching && startY == -1) startY = currentY
                            } else if (code == "ABS_MT_TRACKING_ID") {
                                if (isTrackingEnd(value)) {
                                    if (isTouching) {
                                        isTouching = false
                                        finishTouch()
                                    }
                                } else if (!isTouching) {
                                    // Protocol-B devices repeat the current
                                    // tracking id (often 0) on every frame.
                                    // Only the first id starts a gesture;
                                    // resetting startX/startY for each frame
                                    // collapses a swipe into a zero-distance
                                    // tap and makes the page animation appear
                                    // to be missing.
                                    isTouching = true
                                    currentX = -1
                                    currentY = -1
                                    startX = -1
                                    startY = -1
                                    gestureRotation = currentRotation()
                                    touchStartedAt = System.currentTimeMillis()
                                }
                            }
                        } else if (type == "EV_KEY" && (source in touchSources || source in keySources)) {
                            if (code == "BTN_TOUCH") {
                                if (isUpValue(value) && isTouching) {
                                    isTouching = false
                                    finishTouch()
                                } else if (isDownValue(value)) {
                                    isTouching = true
                                    currentX = -1
                                    currentY = -1
                                    startX = -1
                                    startY = -1
                                    gestureRotation = currentRotation()
                                    touchStartedAt = System.currentTimeMillis()
                                }
                            }
                            // Hanvon's N10 Pro pen_touch driver emits 02ee
                            // instead of BTN_TOUCH for a screen tap and does
                            // not include coordinates in that event stream.
                            if (code == "02ee") {
                                if (isDownValue(value)) {
                                    isTouching = true
                                    currentX = -1
                                    currentY = -1
                                    startX = -1
                                    startY = -1
                                    gestureRotation = currentRotation()
                                    touchStartedAt = System.currentTimeMillis()
                                } else if (isUpValue(value) && isTouching) {
                                    isTouching = false
                                    finishTouch()
                                }
                            }
                            if (isDownValue(value)) {
                                val keyRotation = currentRotation()
                                refreshKeyMapping(keyRotation)
                                if (PageKeyMapping.isPrevious(
                                        code,
                                        keyRotation,
                                        useVolumeKeyDefaults,
                                        volumeKeysFollowRotation = !isBigme
                                    )
                                ) {
                                    triggerKey(false, code, keyRotation)
                                } else if (PageKeyMapping.isNext(
                                        code,
                                        keyRotation,
                                        useVolumeKeyDefaults,
                                        volumeKeysFollowRotation = !isBigme
                                    )
                                ) {
                                    triggerKey(true, code, keyRotation)
                                } else if (source in keySources) {
                                    sendLog("按键采样：$code DOWN（设备能力中未配置翻页含义）")
                                }
                            }
                        }
                    }
                }
                if (listening && readerGeneration == listeningGeneration) {
                    listening = false
                    sendLog("输入事件监听已退出，请检查输入设备权限")
                }
            } catch (e: Exception) {
                if (listening && readerGeneration == listeningGeneration) {
                    listening = false
                    sendLog("输入事件监听异常: ${e.message}")
                }
            } finally {
                if (readerGeneration == listeningGeneration) {
                    val abandoned = process
                    process = null
                    destroyProcess(abandoned)
                }
            }
        }.also { it.start() }
        
        pollThread = Thread { pollTopApp() }.also { it.start() }
    }
    
    @Synchronized
    override fun stopListening() { 
        internalStop()
        sendLog("已彻底阻断监听")
    }

    /** Reserved Shizuku user-service destroy transaction. */
    override fun destroy() {
        internalStop()
        System.exit(0)
    }
    
    private fun internalStop() {
        val oldProcess = process
        val oldReaderThread = readerThread
        val oldPollThread = pollThread

        listeningGeneration++
        hanvon.restore()
        listening = false
        isTouching = false
        process = null
        readerThread = null
        pollThread = null

        try { oldProcess?.inputStream?.close() } catch (e: Exception) {}
        destroyProcess(oldProcess)
        oldReaderThread?.interrupt()
        oldPollThread?.interrupt()
        try { if (oldReaderThread !== Thread.currentThread()) oldReaderThread?.join(500) } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        try { if (oldPollThread !== Thread.currentThread()) oldPollThread?.join(500) } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        currentX = -1
        currentY = -1
        startX = -1
        startY = -1
        touchStartedAt = 0L
        gestureRotation = 0
        currentTopPackage = ""
        pageEventGate.reset()
        isHanvon = false
        isIreader = false
        isBigme = false
        detectedDevice = RippleMapping.Device.UNKNOWN
        bigme = null
    }

    private fun destroyProcess(target: Process?) {
        if (target == null) return
        try {
            // Give getevent a chance to exit cleanly first. On older Android
            // versions, destroying only the Java Process wrapper can leave
            // the native reader orphaned under init.
            target.destroy()
            var waitedMs = 0
            while (waitedMs < 200) {
                try {
                    target.exitValue()
                    return
                } catch (_: IllegalThreadStateException) {
                    try { Thread.sleep(20) } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                    waitedMs += 20
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                target.destroyForcibly()
            }
        } catch (_: Throwable) {
            try { target.destroy() } catch (_: Throwable) {}
        }
    }

    private fun currentApplicationContext(): Context? {
        return try {
            Class.forName("android.app.ActivityThread")
                .getMethod("currentApplication")
                .invoke(null) as? Context
        } catch (_: Throwable) {
            null
        }
    }

    /** Read the rotation previously updated by the low-priority poller. */
    private fun currentRotation(): Int = rotation

    /**
     * Some Hanvon firmware keeps Display.getRotation() at 0 while the input
     * dispatcher already applies the physical display rotation. The input
     * viewport is the authoritative value for both touch and gpio keys.
     */
    private fun readInputViewportRotation(): Int? {
        return try {
            val probe = ProcessBuilder("dumpsys", "input")
                .redirectErrorStream(true)
                .start()
            val output = BufferedReader(InputStreamReader(probe.inputStream)).use { it.readText() }
            destroyProcess(probe)
            Regex("Viewport INTERNAL:.*?orientation=(\\d+)", setOf(RegexOption.DOT_MATCHES_ALL))
                .find(output)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?.takeIf { it in 0..3 }
        } catch (_: Throwable) {
            null
        }
    }

    private fun parseEventValue(value: String): Int {
        return value.removePrefix("0x").toLongOrNull(16)?.toInt() ?: -1
    }

    private fun isXCode(code: String): Boolean {
        // N10 Pro's pen/touch node reports ABS_X, while some firmware builds
        // expose the same stream as ABS_MT_POSITION_X.
        return code == "ABS_X" || code == "ABS_MT_POSITION_X"
    }

    private fun isYCode(code: String): Boolean {
        return code == "ABS_Y" || code == "ABS_MT_POSITION_Y"
    }

    private fun refreshKeyMapping(currentRotation: Int) {
        if (currentRotation == keyMappingRotation) return
        keyMappingRotation = currentRotation
        rereadKeyCapabilities()
        sendLog(
            "按键映射已刷新：旋转=$currentRotation，" +
                if (useVolumeKeyDefaults && isBigme) "大我音量键（固定：音量+上一页/音量-下一页）"
                else if (useVolumeKeyDefaults) "音量键（音量+上一页/音量-下一页，180°/270°反向）"
                else "设备原始翻页键（按设备能力自动匹配）"
        )
    }

    /** Re-read the actual key capabilities after a physical rotation change. */
    private fun rereadKeyCapabilities() {
        try {
            val probe = ProcessBuilder("/system/bin/getevent", "-lp")
                .redirectErrorStream(true)
                .start()
            val text = BufferedReader(InputStreamReader(probe.inputStream)).use { it.readText() }
            destroyProcess(probe)
            val hasVolumeKeys = text.contains("KEY_VOLUMEUP") && text.contains("KEY_VOLUMEDOWN")
            val hasHanvonKeys = text.contains("0254") && text.contains("0255") &&
                text.contains("0256") && text.contains("0257")
            useVolumeKeyDefaults = when {
                hasHanvonKeys -> false
                hasVolumeKeys -> true
                else -> isIreader || isBigme
            }
        } catch (_: Throwable) {
            useVolumeKeyDefaults = isIreader || isBigme
        }
    }

    private fun isTrackingEnd(value: String): Boolean {
        return value.equals("ffffffff", ignoreCase = true) || value == "-1"
    }

    private fun isDownValue(value: String): Boolean {
        return value.equals("DOWN", ignoreCase = true) || value == "1" || value == "00000001"
    }

    private fun isUpValue(value: String): Boolean {
        return value.equals("UP", ignoreCase = true) || value == "0" || value == "00000000"
    }

    private fun parseMaxX() {
        try {
            val source = touchSources.firstOrNull() ?: "/dev/input/event2"
            val probe = ProcessBuilder("/system/bin/getevent", "-lp", source)
                .redirectErrorStream(true)
                .start()
            val output = BufferedReader(InputStreamReader(probe.inputStream)).use { it.readText() }
            probe.destroy()
            val match = Regex("""(?:ABS_X|ABS_MT_POSITION_X)\s+.*?max\s+(\d+)""")
                .find(output)
            val yMatch = Regex("(?:ABS_Y|ABS_MT_POSITION_Y)\\s+.*?max\\s+(\\d+)")
                .find(output)
            val rawXMax = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1080
            val rawYMax = yMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1920
            val (screenWidth, screenHeight) = displaySize()
            val displayRotation = currentRotation()
            val nativeScreenWidth = if (displayRotation == android.view.Surface.ROTATION_90 ||
                displayRotation == android.view.Surface.ROTATION_270) screenHeight else screenWidth
            val nativeScreenHeight = if (displayRotation == android.view.Surface.ROTATION_90 ||
                displayRotation == android.view.Surface.ROTATION_270) screenWidth else screenHeight

            // Compare the raw axis ranges with the panel's native orientation,
            // not the already-rotated logical display dimensions.
            val normalError =
                kotlin.math.abs(rawXMax - nativeScreenWidth).toFloat() / nativeScreenWidth +
                    kotlin.math.abs(rawYMax - nativeScreenHeight).toFloat() / nativeScreenHeight
            val swappedError =
                kotlin.math.abs(rawXMax - nativeScreenHeight).toFloat() / nativeScreenHeight +
                    kotlin.math.abs(rawYMax - nativeScreenWidth).toFloat() / nativeScreenWidth
            touchAxesSwapped = swappedError < normalError
            maxX = if (touchAxesSwapped) rawYMax else rawXMax
            maxY = if (touchAxesSwapped) rawXMax else rawYMax
            coordinateMapper = TouchCoordinateMapper(rawXMax, rawYMax, touchAxesSwapped)
            sendLog("触摸坐标：原始=${rawXMax}×${rawYMax}，屏幕=${screenWidth}×${screenHeight}，旋转=$displayRotation，${if (touchAxesSwapped) "已交换横纵轴" else "轴向正常"}")
        } catch (e: Exception) {
            maxX = 1080
            maxY = 1920
            touchAxesSwapped = false
            coordinateMapper = TouchCoordinateMapper(maxX, maxY, false)
        }
    }

    private fun displaySize(): Pair<Int, Int> {
        val context = serviceContext ?: currentApplicationContext()
        val metrics = DisplayMetrics()
        try {
            val displayManager = context?.getSystemService(DisplayManager::class.java)
            val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
            if (display != null) {
                display.getRealMetrics(metrics)
                if (metrics.widthPixels > 0 && metrics.heightPixels > 0) {
                    return metrics.widthPixels to metrics.heightPixels
                }
            }
        } catch (_: Throwable) {
        }
        val fallback = context?.resources?.displayMetrics
        return (fallback?.widthPixels?.takeIf { it > 0 } ?: 1080) to
            (fallback?.heightPixels?.takeIf { it > 0 } ?: 1920)
    }

    /**
     * Input event numbers are not stable between vendors or firmware builds.
     * Prefer capability-based discovery and retain the N10 Pro defaults when
     * the procfs view is unavailable.
     */
    private fun discoverInputSources() {
        try {
            // Shizuku's user service can execute getevent, but some vendor
            // builds deny access to /proc/bus/input/devices. getevent -lp is
            // therefore the primary discovery source.
            val probe = ProcessBuilder("/system/bin/getevent", "-lp")
                .redirectErrorStream(true)
                .start()
            val text = BufferedReader(InputStreamReader(probe.inputStream)).use { it.readText() }
            probe.destroy()
            if (!applyInputDescription(text)) throw IllegalStateException("getevent returned no input devices")
        } catch (_: Throwable) {
            try {
                // Keep procfs as a fallback for older firmware where the
                // getevent capability output is incomplete.
                val text = java.io.File("/proc/bus/input/devices").readText()
                if (!applyInputDescription(text)) throw IllegalStateException("procfs returned no input devices")
            } catch (_: Throwable) {
                sendLog("输入设备自动发现失败，使用默认节点：触摸=event2，按键=event6")
            }
        }
    }

    private fun applyInputDescription(text: String): Boolean {
        try {
            val discoveredTouch = mutableSetOf<String>()
            val touchScores = mutableMapOf<String, Int>()
            val discoveredKeys = mutableSetOf<String>()
            var detectedVolumeKeys = false
            var detectedHanvonPageKeys = false
            val blocks = text.split(Regex("(?=add device \\d+:)|(?=I: Bus=)"))
            for (block in blocks) {
                val source = Regex("/dev/input/event\\d+").find(block)?.value
                    ?: Regex("Handlers=.*?\\b(event\\d+)\\b")
                        .find(block)?.groupValues?.getOrNull(1)
                        ?.let { "/dev/input/$it" }
                    ?: continue
                // sc7a20 also exposes ABS_X/ABS_Y, but it is not a touch
                // panel. Require touch-specific capabilities to exclude it.
                val hasTouchAxes =
                    (block.contains("ABS_MT_POSITION_X") && block.contains("ABS_MT_POSITION_Y")) ||
                    (block.contains("ABS_X") && block.contains("ABS_Y"))
                val blockName = InputDeviceNameParser.name(block)
                val isTouch = hasTouchAxes &&
                    (block.contains("BTN_TOUCH") || block.contains("02ee") ||
                        blockName.contains("pen_touch", ignoreCase = true)) &&
                    !blockName.contains("Free3-R", ignoreCase = true)
                val isKey = block.contains("KEY_VOLUMEUP") || block.contains("KEY_VOLUMEDOWN") ||
                    block.contains("KEY_PAGEUP") || block.contains("KEY_PAGEDOWN") ||
                    block.contains("0254") || block.contains("0255") ||
                    blockName.contains("gpio-keys", ignoreCase = true) ||
                    blockName.contains("page", ignoreCase = true)
                if (isKey && block.contains("KEY_VOLUMEUP") && block.contains("KEY_VOLUMEDOWN")) {
                    detectedVolumeKeys = true
                }
                if (isKey && block.contains("0254") && block.contains("0255") &&
                    block.contains("0256") && block.contains("0257")) {
                    detectedHanvonPageKeys = true
                }
                if (isTouch) {
                    discoveredTouch += source
                    // Composite HID/remote nodes such as Free3-R may expose
                    // BTN_TOUCH and ABS_X/ABS_Y while also carrying keys.
                    // Prefer the real multitouch panel and select only one
                    // touch source; mixing global gesture state from several
                    // devices makes taps and swipes nondeterministic.
                    var score = 0
                    if (block.contains("ABS_MT_POSITION_X") &&
                        block.contains("ABS_MT_POSITION_Y")) score += 100
                    if (blockName.contains("fts", ignoreCase = true) ||
                        blockName.contains("touch", ignoreCase = true) ||
                        blockName.contains("pen", ignoreCase = true)) score += 20
                    if (blockName.contains("Free3-R", ignoreCase = true)) score -= 100
                    touchScores[source] = maxOf(touchScores[source] ?: Int.MIN_VALUE, score)
                }
                if (isKey) discoveredKeys += source
            }
            val selectedTouch = touchScores.maxByOrNull { it.value }?.key
            if (selectedTouch != null) {
                touchSources = setOf(selectedTouch)
            } else if (discoveredTouch.isNotEmpty()) {
                touchSources = setOf(discoveredTouch.first())
            }
            if (discoveredKeys.isNotEmpty()) keySources = discoveredKeys
            useVolumeKeyDefaults = when {
                detectedHanvonPageKeys -> false
                detectedVolumeKeys -> true
                else -> isIreader || isBigme
            }
            sendLog("输入设备：触摸=${touchSources.joinToString()}（单一真实触摸节点），按键=${keySources.joinToString()}")
            refreshKeyMapping(currentRotation())
            return discoveredTouch.isNotEmpty() || discoveredKeys.isNotEmpty()
        } catch (e: Throwable) {
            return false
        }
    }
    
    /** iReader/EPDC speed flags: 128 slow, 64 medium, 0 fast. */
    override fun setSpeed(speed: Int) {
        this.speed = if (speed in listOf(0, 64, 128)) speed else 64
    }

    /** Hanvon firmware speed mode/custom value: normally 50/100/200. */
    override fun setHanvonSpeed(speed: Int) {
        this.hanvonSpeed = speed.coerceIn(30, 300)
    }
    override fun setWaterfallMode(isWaterfall: Boolean) { this.isWaterfall = isWaterfall }
    override fun setReverseAnimation(reverse: Boolean) { this.reverseAnimation = reverse }
    override fun setRotation(rot: Int) { this.rotation = rot }
    override fun setTargetPackages(pkgNames: Array<out String>?) { this.targetPackages = pkgNames?.toHashSet() ?: HashSet() }
    override fun isListening(): Boolean = listening

    private fun finishTouch() {
        if (!listening) return
        val duration = if (touchStartedAt > 0L) {
            (System.currentTimeMillis() - touchStartedAt)
                .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        } else 0
        triggerTouch(duration)
        touchStartedAt = 0L
    }

    /** Default reading-app touch profile: edge zones page, center opens settings. */
    private data class TouchProfile(
        val leftPageEnd: Float = 0.35f,
        val rightPageStart: Float = 0.65f,
        val tapSlop: Int = 80,
        val swipeThreshold: Int = 80,
        val horizontalDominance: Float = 1.2f,
        val maxTapDurationMs: Int = 900
    )

    private fun touchProfileFor(packageName: String): TouchProfile {
        // Keep package selection isolated so reader-specific rules can be
        // added later without changing the raw input parser.
        return when {
            packageName.contains("ireader", ignoreCase = true) -> TouchProfile()
            packageName.contains("hanvon", ignoreCase = true) -> TouchProfile()
            packageName.contains("bigme", ignoreCase = true) -> TouchProfile()
            else -> TouchProfile()
        }
    }

    private fun triggerTouch(durationMs: Int) {
        if (!listening) return
        // A coordinate-less touch cannot tell whether it landed in a page
        // zone or the reader's center/settings zone, so ignore it safely.
        if (currentX < 0 || currentY < 0) {
            sendLog("触摸已忽略：无坐标事件（02ee）")
            return
        }

        val endPoint = coordinateMapper.toLogical(currentX, currentY, gestureRotation)
        val startPoint = if (startX >= 0 && startY >= 0) {
            coordinateMapper.toLogical(startX, startY, gestureRotation)
        } else {
            endPoint
        }
        if (currentRotation() != gestureRotation) {
            sendLog("触摸已忽略：手势期间屏幕发生旋转")
            return
        }

        val deltaX = if (startX >= 0) endPoint.x - startPoint.x else 0
        val deltaY = if (startY >= 0) endPoint.y - startPoint.y else 0
        val profile = touchProfileFor(currentTopPackage)
        val isSwipe = kotlin.math.abs(deltaX) >= profile.swipeThreshold &&
            kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY) * profile.horizontalDominance

        val isNext = if (isSwipe) {
            deltaX < 0
        } else if (kotlin.math.abs(deltaX) <= profile.tapSlop &&
            kotlin.math.abs(deltaY) <= profile.tapSlop &&
            durationMs <= profile.maxTapDurationMs
        ) {
            val progress = endPoint.x.toFloat() /
                coordinateMapper.logicalWidth(gestureRotation).coerceAtLeast(1).toFloat()
            when {
                progress <= profile.leftPageEnd -> false
                progress >= profile.rightPageStart -> true
                else -> {
                    sendLog("触摸已忽略：中间设置区 (${(progress * 100).toInt()}%)")
                    return
                }
            }
        } else {
            sendLog("触摸已忽略：非翻页手势")
            return
        }
        if (!acceptPageTrigger("TOUCH", isNext)) return
        dispatchRipple(
            next = isNext,
            source = "TOUCH",
            rawCode = if (isSwipe) "ABS_XY_SWIPE" else "ABS_XY_TAP",
            eventRotation = gestureRotation,
            eventStartX = startPoint.x,
            eventStartY = startPoint.y,
            eventEndX = endPoint.x,
            eventEndY = endPoint.y,
            durationMs = durationMs
        )
    }

    private fun triggerKey(isNext: Boolean, rawCode: String, eventRotation: Int) {
        if (!listening) return
        if (!acceptPageTrigger("KEY", isNext)) return
        dispatchRipple(
            next = isNext,
            source = "KEY",
            rawCode = rawCode,
            eventRotation = eventRotation,
            eventStartX = -1,
            eventStartY = -1,
            eventEndX = -1,
            eventEndY = -1,
            durationMs = 0
        )
    }

    /**
     * A few firmware builds expose one physical page action through both the
     * touch and key nodes. Suppress only the same-direction event arriving
     * immediately from another source; genuine fast reverse/next actions are
     * kept intact.
     */
    private fun acceptPageTrigger(source: String, next: Boolean): Boolean {
        val accepted = pageEventGate.accept(source, next,
            android.os.SystemClock.elapsedRealtime())
        if (!accepted) sendLog("已忽略跨源重复翻页事件：$source")
        return accepted
    }

    private fun dispatchRipple(
        next: Boolean,
        source: String,
        rawCode: String,
        eventRotation: Int,
        eventStartX: Int,
        eventStartY: Int,
        eventEndX: Int,
        eventEndY: Int,
        durationMs: Int
    ) {
        if (!listening) return
        val generation = listeningGeneration
        val nextDirection = if (next) "NEXT" else "PREVIOUS"
        rippleExecutor.execute {
            if (!listening || generation != listeningGeneration) return@execute

            var rippleSent = false
            val packages = targetPackages
            // Foreground package is maintained by the separate polling thread.
            if (packages.isEmpty() || packages.contains(currentTopPackage)) {
                // Bind the animation to the same rotation snapshot used to
                // classify this event. Reading rotation again here allows a
                // queued event to be rendered with the next orientation and
                // produces alternating directions during rotation changes.
                val effectRotation = eventRotation
                val waterfallSnapshot = isWaterfall
                val reverseSnapshot = reverseAnimation
                val hanvonSpeedSnapshot = hanvonSpeed
                val iReaderSpeedSnapshot = speed
                val pattern = if (waterfallSnapshot) {
                    RippleMapping.Pattern.CURTAIN
                } else {
                    RippleMapping.Pattern.WIND
                }
                val sourceType = if (source == "KEY") {
                    RippleMapping.Source.KEY
                } else {
                    RippleMapping.Source.TOUCH
                }
                val logicalDirection = if (next) {
                    RippleMapping.Direction.NEXT
                } else {
                    RippleMapping.Direction.PREVIOUS
                }
                val finalDirection = if (reverseSnapshot) {
                    if (logicalDirection == RippleMapping.Direction.NEXT) {
                        RippleMapping.Direction.PREVIOUS
                    } else {
                        RippleMapping.Direction.NEXT
                    }
                } else {
                    logicalDirection
                }
                val command = RippleMapping.command(
                    device = detectedDevice,
                    pattern = pattern,
                    rotation = effectRotation,
                    source = sourceType,
                    direction = finalDirection,
                    speed = iReaderSpeedSnapshot
                )
                val animationRotation = effectRotation
                try {
                    android.util.Log.d(
                        "TouchService",
                        "翻页事件 device=$detectedDevice source=$source raw=$rawCode logical=${logicalDirection.name} final=${finalDirection.name} reverse=$reverseSnapshot eventRotation=$eventRotation animationRotation=$animationRotation"
                    )
                    rippleSent = when (detectedDevice) {
                        RippleMapping.Device.HANVON -> command?.takeIf {
                            it.type == RippleMapping.CommandType.HANVON_DIRECTION
                        }?.let {
                            hanvon.prepareDirection(
                                it.code.toString(), hanvonSpeedSnapshot.toString(),
                                animationRotation,
                                finalDirection == RippleMapping.Direction.NEXT, waterfallSnapshot
                            )
                        } == true
                        RippleMapping.Device.IREADER -> command?.takeIf {
                            it.type == RippleMapping.CommandType.IREADER_EFFECT
                        }?.let { EpdcHelper.nativeSendPageH(it.code) } == true
                        RippleMapping.Device.BIGME -> command?.takeIf {
                            it.type == RippleMapping.CommandType.BIGME_MODE
                        }?.let {
                            bigme?.prepareMode(it.code, animationRotation,
                                finalDirection == RippleMapping.Direction.NEXT, waterfallSnapshot)
                        } == true
                        RippleMapping.Device.UNKNOWN -> false
                    }
                    android.util.Log.i("RippleAudit", RippleAudit.line(
                        detectedDevice, pattern, animationRotation, sourceType,
                        rawCode, logicalDirection, finalDirection, reverseSnapshot,
                        command, if (rippleSent) "API_ACCEPTED" else "API_REJECTED"
                    ))
                    val directionCode = when (command?.type) {
                        RippleMapping.CommandType.HANVON_DIRECTION -> "方向码=${command.code}"
                        RippleMapping.CommandType.IREADER_EFFECT -> "效果码=${command.code}"
                        RippleMapping.CommandType.BIGME_MODE -> "模式码=${command.code}"
                        null -> "接口未匹配"
                    }
                    val rotationText = "事件旋转=$eventRotation，接口旋转=$animationRotation"
                    sendLog("${backendName()} ${if (next) "下一页" else "上一页"}，$rotationText，${if (waterfallSnapshot) "垂幕纹" else "拂风纹"}，$directionCode，${if (rippleSent) "已下发" else "未下发"}")
                } catch (e: Throwable) {
                    android.util.Log.e("RippleAudit", RippleAudit.line(
                        detectedDevice, pattern, animationRotation, sourceType,
                        rawCode, logicalDirection, finalDirection, reverseSnapshot,
                        command, "API_EXCEPTION"
                    ), e)
                    sendLog("下发异常: ${e.message}")
                }
            } else {
                sendLog("未下发: 当前应用不在目标列表 current=$currentTopPackage target=${packages.joinToString()}")
            }

            try {
                callback?.onPageEvent(
                    source,
                    nextDirection,
                    rawCode,
                    eventStartX,
                    eventStartY,
                    eventEndX,
                    eventEndY,
                    durationMs,
                    rippleSent
                )
            } catch (_: Exception) {}
        }
    }

    private fun backendName(): String = when (detectedDevice) {
        RippleMapping.Device.HANVON -> "汉王"
        RippleMapping.Device.IREADER -> "掌阅"
        RippleMapping.Device.BIGME -> "大我"
        RippleMapping.Device.UNKNOWN -> "未知设备"
    }

    private fun pollTopApp() {
        while (listening && !Thread.currentThread().isInterrupted) {
            val rot = readInputViewportRotation() ?: runCatching {
                val ctx = serviceContext ?: currentApplicationContext()
                ctx?.getSystemService(DisplayManager::class.java)
                    ?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation
            }.getOrNull()
            if (rot != null && rot != rotation) {
                rotation = rot
                refreshKeyMapping(rot)
            }
            if (targetPackages.isNotEmpty()) refreshTopApp()
            try { Thread.sleep(750) } catch (_: InterruptedException) { break }
        }
    }

    private fun refreshTopApp() {
        try {
            fun dump(vararg args: String): String = runCatching {
                val process = ProcessBuilder("dumpsys", *args)
                    .redirectErrorStream(true)
                    .start()
                BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
                    .also { process.destroy() }
            }.getOrDefault("")
            fun findFocused(output: String) = output.lineSequence()
                .filter { it.contains("mCurrentFocus") || it.contains("mFocusedApp") || it.contains("topResumedActivity") }
                .flatMap { Regex("""([a-zA-Z0-9._]+)/[a-zA-Z0-9._$]+""").findAll(it).asSequence() }
                .firstOrNull()
            val windowMatch = findFocused(dump("window", "windows"))
            val match = windowMatch ?: findFocused(dump("activity", "activities"))
            // An unknown foreground package is safer than retaining a stale
            // package and accidentally animating the wrong application.
            currentTopPackage = match?.groupValues?.get(1).orEmpty()
        } catch (_: Throwable) {
            currentTopPackage = ""
        }
    }
}
