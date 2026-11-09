package moe.shizuku.manager.ripple

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import moe.shizuku.manager.app.AppBarActivity
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

open class RippleActivity : AppBarActivity() {
    private enum class RippleBackend(val title: String) {
        HANVON("汉王"), IREADER("掌阅"), BIGME("大我"), UNKNOWN("未检测到")
    }

    private lateinit var backend: RippleBackend
    private var touchService: ITouchService? = null
    private var isProcessing = false
    private var isBinding = false
    private var isRunningState = false 
    
    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var toggleBtn: TextView
    private lateinit var targetBtn: TextView
    
    private var targetPackages: MutableSet<String> = HashSet()
    private lateinit var prefs: android.content.SharedPreferences
    private var selectedTab = "home"
    private var bottomNavigationAdjusted = false
    private var speedUiUpdating = false
    private val serviceExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ripple-service-calls")
    }
    private var pendingHanvonSpeed: Int? = null
    private var logUpdateScheduled = false
    private var pendingLogMessage: String? = null

    private val handler = Handler(Looper.getMainLooper())
    private val logUpdateRunnable = Runnable {
        val message = synchronized(this@RippleActivity) {
            logUpdateScheduled = false
            pendingLogMessage.also { pendingLogMessage = null }
        }
        if (message != null && ::logText.isInitialized && !isFinishing && !isDestroyed) {
            logText.text = "【服务日志】\n$message"
        }
    }
    private val hanvonSpeedApplyRunnable = Runnable {
        val value = pendingHanvonSpeed ?: return@Runnable
        pendingHanvonSpeed = null
        runServiceCommand("应用汉王自定义速度") { it.setHanvonSpeed(value) }
    }
    private val timeoutRunnable = Runnable {
        if (touchService == null) {
            isBinding = false
            statusText.text = "绑定超时！\n请检查 Shizuku 后台权限"
            isProcessing = false
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun userServiceArgs(): Shizuku.UserServiceArgs {
        return Shizuku.UserServiceArgs(
            ComponentName(packageName, TouchService::class.java.name)
        ).tag("ripple-service-v2")
            .processNameSuffix("ripple")
            // Keep one stable, idle-capable service across the two UI tabs.
            // The activity explicitly stops input listening before detaching;
            // keeping the user service record avoids creating a second
            // getevent reader during rapid tab switches.
            .daemon(true)
            .version(4)
    }

    private fun runServiceCommand(operation: String, command: (ITouchService) -> Unit) {
        val service = touchService ?: return
        serviceExecutor.execute {
            try {
                command(service)
            } catch (error: Throwable) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed && ::statusText.isInitialized) {
                        statusText.text = "状态：${operation}失败"
                        if (::logText.isInitialized) {
                            logText.text = "【服务异常】\n${error.message ?: error.javaClass.simpleName}"
                        }
                    }
                }
            }
        }
    }

    private fun scheduleHanvonSpeed(value: Int) {
        pendingHanvonSpeed = value
        handler.removeCallbacks(hanvonSpeedApplyRunnable)
        handler.postDelayed(hanvonSpeedApplyRunnable, 120L)
    }

    private val touchCallback = object : ITouchCallback.Stub() {
        override fun onPageEvent(
            source: String,
            direction: String,
            rawCode: String,
            startX: Int,
            startY: Int,
            endX: Int,
            endY: Int,
            durationMs: Int,
            rippleSent: Boolean
        ) {
            if (!isRunningState) return
            runOnUiThread {
                if (!isRunningState) return@runOnUiThread
                val resultStr = if (rippleSent) "✅ 波纹已下发" else "❌ 未下发"
                val sourceText = if (source == "KEY") "按键" else "触摸"
                val directionText = if (direction == "NEXT") "下一页" else "上一页"
                if (source == "KEY") {
                    logText.text = "【$sourceText：$directionText】\n编码: $rawCode\n状态: $resultStr"
                } else {
                    logText.text = "【$sourceText：$directionText】\n" +
                        "起点: ($startX, $startY) 终点: ($endX, $endY)\n" +
                        "耗时: ${durationMs}ms，事件: $rawCode\n状态: $resultStr"
                }
            }
        }
        override fun onLog(message: String) {
            synchronized(this@RippleActivity) {
                pendingLogMessage = message
                if (logUpdateScheduled) return
                logUpdateScheduled = true
            }
            handler.postDelayed(logUpdateRunnable, 80L)
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) { 
            handler.removeCallbacks(timeoutRunnable)
            isBinding = false
            isProcessing = false
            try {
                val serviceProxy = ITouchService.Stub.asInterface(service)
                touchService = serviceProxy
                serviceExecutor.execute {
                    try {
                        serviceProxy?.registerCallback(touchCallback)
                    } catch (error: Throwable) {
                        runOnUiThread { statusText.text = "状态：回调注册失败" }
                    }
                }
                // 【核心修复】连上服务后，强制把本地的速度和模式推给底层，覆盖底层的默认值
                applySettingsToService()
                
                // Binding the UI must never start the input reader. The
                // listener is an explicit user action on the home page.
                isRunningState = false
                toggleBtn.text = "开始监听"
                statusText.text = "状态：等待开启"
                prefs.edit().putBoolean("was_listening", false).apply()
                syncServiceState()
            } catch (e: Throwable) { 
                runOnUiThread { statusText.text = "连接异常：${e.message}" } 
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) { 
            isBinding = false
            touchService = null
            isProcessing = false
            isRunningState = false
            runOnUiThread { 
                statusText.text = "状态：服务断开"
                toggleBtn.text = "开启监听"
            }
        }
    }

    private fun syncServiceState() {
        val service = touchService ?: return
        serviceExecutor.execute {
            try {
                val isRunning = service.isListening
                runOnUiThread {
                    isRunningState = isRunning
                    if (isRunning) {
                        toggleBtn.text = "停止监听"
                        statusText.text = "状态：正在监听"
                    } else {
                        toggleBtn.text = "开启监听"
                        statusText.text = "状态：等待开启"
                    }
                    prefs.edit().putBoolean("was_listening", isRunning).apply()
                }
            } catch (error: Throwable) {
                runOnUiThread { statusText.text = "状态：读取服务状态失败" }
            }
        }
    }

    private fun createBlackBorder(): GradientDrawable {
        return GradientDrawable().apply { setColor(Color.WHITE); setStroke(2, Color.BLACK); cornerRadius = 8f }
    }

    private fun sectionTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 20, 0, 10)
        }
    }

    private fun detectBackend(): RippleBackend {
        if (HanvonRipple().isHanvonDevice(this)) return RippleBackend.HANVON
        try {
            Class.forName("android.eink.EPDCDevice")
            return RippleBackend.IREADER
        } catch (_: Throwable) {
        }
        try {
            Class.forName("xrz.framework.manager.XrzEinkManager")
            return RippleBackend.BIGME
        } catch (_: Throwable) {
        }
        return RippleBackend.UNKNOWN
    }

    private fun initializationSummary(): String {
        fun mark(done: Boolean) = if (done) "✓" else "○"
        val developer = try {
            android.provider.Settings.Global.getInt(
                contentResolver,
                android.provider.Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
            ) == 1
        } catch (_: Throwable) { false }
        val accessibility = try {
            (getSystemService(ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager)
                .getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { it.resolveInfo.serviceInfo.name == "moe.shizuku.manager.adb.AutoPairAccessibilityService" }
        } catch (_: Throwable) { false }
        val wireless = try {
            android.provider.Settings.Global.getInt(contentResolver, "adb_wifi_enabled", 0) == 1 || Shizuku.pingBinder()
        } catch (_: Throwable) { Shizuku.pingBinder() }
        val paired = moe.shizuku.manager.ShizukuSettings.getPreferences().getBoolean("pairing_completed", false)
        val configured = prefs.getBoolean("animation_configured", false)
        return buildString {
            append(mark(developer)).append(" 开发者选项    ")
                .append(mark(accessibility)).append(" 无障碍权限\n")
            append(mark(wireless)).append(" 无线调试      ")
                .append(mark(paired)).append(" 配对码配对\n")
            append(mark(Shizuku.pingBinder())).append(" 服务连接      ")
                .append(mark(configured)).append(" 监听配置")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        prefs = getSharedPreferences("ripple_prefs", MODE_PRIVATE)
        targetPackages = prefs.getStringSet("target_pkgs", HashSet())?.toMutableSet() ?: HashSet()
        backend = detectBackend()
        selectedTab = intent.getStringExtra("tab") ?: "animation"
        if (selectedTab == "home") {
            startActivity(Intent(this, moe.shizuku.manager.SetupActivity::class.java))
            finish()
            return
        }
        val isHome = selectedTab == "home"
        val isAnimation = selectedTab == "animation"

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(30), dp(48), dp(30))
            setBackgroundColor(Color.WHITE)
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.WHITE)
            addView(content)
        }

        content.addView(TextView(this).apply {
            text = when {
                isHome -> "墨水屏翻页助手"
                else -> "翻页动画设置"
            }
            textSize = 26f
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 4)
        })
        content.addView(TextView(this).apply {
            text = when {
                isHome -> "初始化设置与监听开关"
                else -> "拂风纹、垂幕纹与翻页速度"
            }
            textSize = 15f
            setTextColor(Color.DKGRAY)
            setPadding(0, 0, 0, 20)
        })

        val deviceSummary = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            background = createBlackBorder()
        }
        deviceSummary.addView(TextView(this).apply {
            text = "设备接口"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        deviceSummary.addView(TextView(this).apply {
            text = backend.title
            textSize = 16f
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
        })
        content.addView(deviceSummary, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) })

        statusText = TextView(this).apply {
            text = "状态：正在检查 Shizuku..."
            textSize = if (isHome) 19f else 14f
            setTextColor(Color.BLACK)
            setPadding(0, 0, 0, dp(12))
        }
        content.addView(statusText)

        logText = TextView(this).apply {
            text = "最近事件：暂无"
            textSize = 15f
            setTextColor(Color.BLACK)
            setBackground(createBlackBorder())
            setPadding(dp(18), dp(16), dp(18), dp(16))
            setLineSpacing(dp(4).toFloat(), 1f)
            visibility = View.GONE
        }
        content.addView(logText, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        fun radio(text: String, id: Int, checked: Boolean): RadioButton {
            return RadioButton(this).apply {
                this.text = text
                this.id = id
                isChecked = checked
                setTextColor(Color.BLACK)
                textSize = 15f
            }
        }

        if (isHome) {
            content.addView(sectionTitle("初始化状态"))
            content.addView(TextView(this).apply {
                text = initializationSummary()
                textSize = 15f
                setTextColor(Color.BLACK)
                setLineSpacing(dp(5).toFloat(), 1f)
                setBackground(createBlackBorder())
                setPadding(dp(18), dp(16), dp(18), dp(16))
            })
            val setupButton = TextView(this).apply {
                text = "打开初始化设置"
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(Color.BLACK)
                setBackground(createBlackBorder())
                setPadding(dp(12), dp(14), dp(12), dp(14))
                setOnClickListener {
                    startActivity(Intent(this@RippleActivity, moe.shizuku.manager.SetupActivity::class.java))
                }
            }
            content.addView(setupButton, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        }

        if (isAnimation) {
            content.addView(sectionTitle("动画效果"))
            val waterfall = prefs.getBoolean("is_waterfall", false)
            val styleGroup = RadioGroup(this).apply {
                orientation = RadioGroup.HORIZONTAL
                addView(radio("拂风纹", 20, !waterfall))
                addView(radio("垂幕纹", 21, waterfall))
                setOnCheckedChangeListener { _, id ->
                    val selectedWaterfall = id == 21
                    prefs.edit().putBoolean("is_waterfall", selectedWaterfall).apply()
                    runServiceCommand("应用动画效果") { it.setWaterfallMode(selectedWaterfall) }
                }
            }
            content.addView(styleGroup)
            val reverseAnimation = CheckBox(this).apply {
                text = "反向动画"
                textSize = 15f
                setTextColor(Color.BLACK)
                isChecked = prefs.getBoolean("reverse_animation", false)
                setOnCheckedChangeListener { _, checked ->
                    prefs.edit().putBoolean("reverse_animation", checked).apply()
                    runServiceCommand("应用反向动画") { it.setReverseAnimation(checked) }
                }
            }
            content.addView(reverseAnimation)
            content.addView(sectionTitle("翻页速度"))
            when (backend) {
                RippleBackend.IREADER -> {
                    val speedGroup = RadioGroup(this).apply {
                        orientation = RadioGroup.HORIZONTAL
                        val saved = prefs.getInt("ireader_speed", prefs.getInt("speed", 64))
                        addView(radio("慢", 31, saved == 128))
                        addView(radio("中", 32, saved == 64))
                        addView(radio("快", 33, saved == 0))
                        setOnCheckedChangeListener { _, id ->
                            val value = when (id) { 31 -> 128; 33 -> 0; else -> 64 }
                            prefs.edit().putInt("speed", value).putInt("ireader_speed", value).apply()
                            runServiceCommand("应用掌阅速度") { it.setSpeed(value) }
                        }
                    }
                    content.addView(speedGroup)
                }
                RippleBackend.HANVON -> {
                    val saved = HanvonSpeedModel.normalize(prefs.getInt("hanvon_speed", 100))
                    val customMode = prefs.getBoolean("hanvon_custom", !HanvonSpeedModel.isPreset(saved)) || !HanvonSpeedModel.isPreset(saved)
                    val customCheck = CheckBox(this).apply {
                        text = "自定义速度（${HanvonSpeedModel.MIN}–${HanvonSpeedModel.MAX}）"
                        textSize = 15f
                        setTextColor(Color.BLACK)
                        isChecked = customMode
                    }
                    val customValue = TextView(this).apply {
                        text = "当前值：$saved"
                        textSize = 14f
                        setTextColor(Color.DKGRAY)
                        setPadding(0, dp(4), 0, 0)
                    }
                    val customSeek = SeekBar(this).apply {
                        max = (HanvonSpeedModel.MAX - HanvonSpeedModel.MIN) / HanvonSpeedModel.STEP
                        progress = HanvonSpeedModel.progressFor(saved)
                        isEnabled = customCheck.isChecked
                    }
                    fun applyPreset(value: Int) {
                        handler.removeCallbacks(hanvonSpeedApplyRunnable)
                        pendingHanvonSpeed = null
                        speedUiUpdating = true
                        customCheck.isChecked = false
                        customValue.text = "当前值：$value"
                        customSeek.progress = HanvonSpeedModel.progressFor(value)
                        speedUiUpdating = false
                        prefs.edit().putInt("hanvon_speed", value).putBoolean("hanvon_custom", false).apply()
                        runServiceCommand("应用汉王速度") { it.setHanvonSpeed(value) }
                    }
                    val presets = RadioGroup(this).apply {
                        orientation = RadioGroup.HORIZONTAL
                        addView(radio("慢 ${HanvonSpeedModel.SLOW}", 41, !customMode && saved == HanvonSpeedModel.SLOW))
                        addView(radio("中 ${HanvonSpeedModel.MEDIUM}", 42, !customMode && saved == HanvonSpeedModel.MEDIUM))
                        addView(radio("快 ${HanvonSpeedModel.FAST}", 43, !customMode && saved == HanvonSpeedModel.FAST))
                        setOnCheckedChangeListener { _, id -> HanvonSpeedModel.presetValue(id)?.let(::applyPreset) }
                    }
                    content.addView(presets)
                    customCheck.setOnCheckedChangeListener { _, checked ->
                        customSeek.isEnabled = checked
                        if (!speedUiUpdating) prefs.edit().putBoolean("hanvon_custom", checked).apply()
                    }
                    customSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                            val value = HanvonSpeedModel.valueForProgress(progress)
                            customValue.text = "当前值：$value"
                            if (fromUser) {
                                if (!customCheck.isChecked) {
                                    speedUiUpdating = true
                                    customCheck.isChecked = true
                                    speedUiUpdating = false
                                }
                                prefs.edit().putInt("hanvon_speed", value).putBoolean("hanvon_custom", true).apply()
                                scheduleHanvonSpeed(value)
                            }
                        }
                        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                    })
                    content.addView(customCheck)
                    content.addView(customValue)
                    content.addView(customSeek)
                }
                RippleBackend.BIGME -> content.addView(TextView(this).apply {
                    text = "大我接口固定动画保持时间：500ms\n此设备不提供速度调节。"
                    textSize = 15f
                    setTextColor(Color.DKGRAY)
                })
                RippleBackend.UNKNOWN -> content.addView(TextView(this).apply {
                    text = "未检测到可用设备接口。"
                    textSize = 15f
                    setTextColor(Color.DKGRAY)
                })
            }
            content.addView(TextView(this).apply {
                text = "动画设置会随屏幕旋转自动选择对应接口方向。"
                textSize = 14f
                setTextColor(Color.DKGRAY)
                setPadding(0, dp(10), 0, 0)
            })
        }

        content.addView(sectionTitle("监听开关"), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = if (isHome) dp(24) else dp(18)
        })
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(12))
        }
        targetBtn = TextView(this).apply {
            text = "所有应用"
            textSize = 15f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            background = createBlackBorder()
            setPadding(dp(8), dp(14), dp(8), dp(14))
            setOnClickListener { if (!isProcessing) showAppListDialog() }
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        toggleBtn = TextView(this).apply {
            text = "开始监听"
            textSize = 16f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            background = createBlackBorder()
            setPadding(dp(8), dp(14), dp(8), dp(14))
            setOnClickListener { toggleListening() }
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        btnRow.addView(targetBtn)
        btnRow.addView(View(this), LinearLayout.LayoutParams(dp(12), 1))
        btnRow.addView(toggleBtn)
        btnRow.visibility = if (isHome) View.VISIBLE else View.GONE
        content.addView(btnRow)

        val saveButton = TextView(this).apply {
            text = "保存当前设置"
            textSize = 16f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setBackground(createBlackBorder())
            setPadding(dp(12), dp(14), dp(12), dp(14))
            setOnClickListener {
                applySettingsToService()
                prefs.edit().putBoolean("animation_configured", true).apply()
                Toast.makeText(this@RippleActivity, "设置已保存", Toast.LENGTH_SHORT).show()
            }
        }
        if (isAnimation) content.addView(saveButton, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })

        val bottomNav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(42), dp(6), dp(42), 0)
        }
        fun navItem(label: String, tab: String): TextView = TextView(this).apply {
            text = label
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(if (selectedTab == tab) Color.BLACK else Color.GRAY)
            if (selectedTab == tab) setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(10), 0, dp(10))
            layoutParams = LinearLayout.LayoutParams(0, dp(58), 1f)
            setOnClickListener {
                if (tab == selectedTab) return@setOnClickListener
                val target = if (tab == "home") {
                    Intent(this@RippleActivity, moe.shizuku.manager.SetupActivity::class.java)
                } else {
                    Intent(this@RippleActivity, RippleActivity::class.java)
                }
                target.putExtra("tab", tab)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(target)
            }
        }
        bottomNav.addView(navItem("首页", "home"))
        bottomNav.addView(navItem("翻页动画", "animation"))
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(View(this).apply { setBackgroundColor(Color.rgb(189, 189, 189)) }, LinearLayout.LayoutParams(-1, dp(2)))
        root.addView(bottomNav, LinearLayout.LayoutParams(-1, dp(72)))

        setContentView(root)
        fitBottomNavigation(bottomNav)
        updateTargetBtnText()
        // SetupActivity is the single owner of the Shizuku user service.
        // This page only edits persisted animation settings.
        statusText.text = "设置已保存，返回首页后手动开启监听"
    }

    private fun fitBottomNavigation(view: View) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M || bottomNavigationAdjusted) return
        view.post {
            val bottomInset = window.decorView.rootWindowInsets?.systemWindowInsetBottom ?: 0
            if (bottomInset <= 0) return@post
            val params = view.layoutParams
            val baseHeight = params.height.takeIf { it > 0 } ?: view.height
            params.height = baseHeight + bottomInset
            view.layoutParams = params
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bottomInset)
            bottomNavigationAdjusted = true
        }
    }
    
    override fun onResume() { 
        super.onResume()
        if (touchService != null) syncServiceState()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent == null) return
        setIntent(intent)
        val requestedTab = intent.getStringExtra("tab") ?: "home"
        val newTab = if (requestedTab == "monitor") "animation" else requestedTab
        if (newTab != selectedTab) recreate()
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeoutRunnable)
        handler.removeCallbacks(logUpdateRunnable)
        handler.removeCallbacks(hanvonSpeedApplyRunnable)
        isRunningState = false
        // This activity never owns the service connection.
        serviceExecutor.shutdown()
        super.onDestroy()
    }
    
    private fun checkShizuku() { 
        if (touchService != null || isBinding) return
        try {
            if (!Shizuku.pingBinder()) { 
                statusText.text = "状态：Shizuku 未运行"
                isProcessing = false; return 
            }
            bindService()
        } catch (e: Throwable) { statusText.text = "Shizuku 异常:\n${e.message}"; isProcessing = false }
    }

    private fun bindService() { 
        if (isBinding || touchService != null) return
        isBinding = true
        try {
            statusText.text = "状态：正在绑定服务..."
            val args = userServiceArgs()
            handler.removeCallbacks(timeoutRunnable); handler.postDelayed(timeoutRunnable, 5000)
            Shizuku.bindUserService(args, connection) 
        } catch (e: Throwable) {
            isBinding = false
            statusText.text = "绑定失败: ${e.message}"
            isProcessing = false
        }
    }

    private fun applySettingsToService() { 
        val iReaderSpeed = prefs.getInt("ireader_speed", prefs.getInt("speed", 64))
        val hanvonSpeed = HanvonSpeedModel.normalize(prefs.getInt("hanvon_speed", 100))
        val waterfall = prefs.getBoolean("is_waterfall", false)
        val rotation = (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        val packages = targetPackages.toTypedArray()
        runServiceCommand("应用设置") { service ->
            service.setSpeed(iReaderSpeed)
            service.setHanvonSpeed(hanvonSpeed)
            service.setWaterfallMode(waterfall)
        service.setReverseAnimation(prefs.getBoolean("reverse_animation", false))
            service.setRotation(rotation)
            service.setTargetPackages(packages)
        }
    }
    
    private fun toggleListening() {
        if (isProcessing || touchService == null) { 
            if (touchService == null) Toast.makeText(this, "服务未连接，请稍后", Toast.LENGTH_SHORT).show()
            return 
        }
        isProcessing = true
        if (isRunningState) { 
            isRunningState = false; toggleBtn.text = "开启监听"; statusText.text = "状态：已停止"; prefs.edit().putBoolean("was_listening", false).apply()
            val service = touchService
            serviceExecutor.execute {
                try { service?.stopListening() } catch (error: Throwable) {
                    runOnUiThread { statusText.text = "状态：停止监听失败" }
                } finally {
                    runOnUiThread { isProcessing = false }
                }
            }
        } else {
            isRunningState = true; toggleBtn.text = "停止监听"; statusText.text = "状态：正在监听"; prefs.edit().putBoolean("was_listening", true).apply()
            val service = touchService
            serviceExecutor.execute {
                try { service?.startListening() } catch (error: Throwable) {
                    runOnUiThread {
                        isRunningState = false
                        prefs.edit().putBoolean("was_listening", false).apply()
                        statusText.text = "状态：启动监听失败"
                        toggleBtn.text = "开启监听"
                    }
                } finally {
                    runOnUiThread { isProcessing = false }
                }
            }
        }
    }

    private fun updateTargetBtnText() { if (targetPackages.isEmpty()) targetBtn.text = "所有应用" else targetBtn.text = "已选 ${targetPackages.size} 个应用" }

    private fun showAppListDialog() {
        isProcessing = true
        val pm = packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val userApps = packages.filter { it.packageName != packageName && (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }.sortedBy { it.loadLabel(pm).toString() }

        val names = userApps.map { it.loadLabel(pm).toString() }.toTypedArray()
        val pkgs = userApps.map { it.packageName }.toTypedArray()
        val checkedItems = userApps.map { targetPackages.contains(it.packageName) }.toBooleanArray()

        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_NoActionBar)
            .setTitle("选择需要监听的软件 (可多选)")
            .setMultiChoiceItems(names, checkedItems) { _, which, isChecked -> if (isChecked) targetPackages.add(pkgs[which]) else targetPackages.remove(pkgs[which]) }
            .setPositiveButton("确定") { _, _ ->
                isProcessing = false; prefs.edit().putStringSet("target_pkgs", targetPackages).apply(); updateTargetBtnText()
                val packages = targetPackages.toTypedArray()
                runServiceCommand("应用监听范围") { it.setTargetPackages(packages) }
            }
            .setNegativeButton("取消") { _, _ -> isProcessing = false }
            .setNeutralButton("清空选择") { _, _ ->
                isProcessing = false
                targetPackages.clear(); prefs.edit().putStringSet("target_pkgs", targetPackages).apply(); updateTargetBtnText()
                runServiceCommand("清空监听范围") { it.setTargetPackages(arrayOf()) }
            }
            .setOnCancelListener { isProcessing = false }
            .create()

        // 强制把弹窗上的三个按钮文字刷成黑色
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.BLACK)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.BLACK)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(Color.BLACK)
        }
        dialog.show()
    }
}
