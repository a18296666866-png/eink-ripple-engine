package moe.shizuku.manager

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.widget.TextView
import androidx.annotation.RequiresApi
import com.google.android.material.button.MaterialButton
import moe.shizuku.manager.adb.AdbPairingTutorialActivity
import moe.shizuku.manager.databinding.ActivitySetupBinding
import moe.shizuku.manager.home.AdbDialogFragment
import moe.shizuku.manager.ripple.RippleActivity
import moe.shizuku.manager.ripple.ITouchService
import moe.shizuku.manager.ripple.ITouchCallback
import moe.shizuku.manager.ripple.TouchService
import moe.shizuku.manager.app.AppBarActivity
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

/**
 * The first-run flow is deliberately linear: every card maps to one action
 * needed before the raw input listener can safely run.
 */
open class SetupActivity : AppBarActivity() {

    private lateinit var binding: ActivitySetupBinding
    private var bottomNavigationAdjusted = false

    private val step1Action get() = findViewById<MaterialButton>(R.id.step1Action)
    private val step2Action get() = findViewById<MaterialButton>(R.id.step2Action)
    private val step3Action get() = findViewById<MaterialButton>(R.id.step3Action)
    private val step4Action get() = findViewById<MaterialButton>(R.id.step4Action)
    private val step5Action get() = findViewById<MaterialButton>(R.id.step5Action)
    private val step6Action get() = findViewById<MaterialButton>(R.id.step6Action)
    private val step1Status get() = findViewById<TextView>(R.id.step1Status)
    private val step2Status get() = findViewById<TextView>(R.id.step2Status)
    private val step3Status get() = findViewById<TextView>(R.id.step3Status)
    private val step4Status get() = findViewById<TextView>(R.id.step4Status)
    private val step5Status get() = findViewById<TextView>(R.id.step5Status)
    private val step6Status get() = findViewById<TextView>(R.id.step6Status)
    private val setupListenerStatus get() = findViewById<TextView>(R.id.setupListenerStatus)
    private val setupTargetButton get() = findViewById<MaterialButton>(R.id.setupTargetButton)
    private val setupToggleButton get() = findViewById<MaterialButton>(R.id.setupToggleButton)

    private lateinit var prefs: android.content.SharedPreferences
    private var targetPackages: MutableSet<String> = HashSet()
    @Volatile private var touchService: ITouchService? = null
    private var isBinding = false
    private var isProcessing = false
    private var isListening = false
    private val listenerCallback = object : ITouchCallback.Stub() {
        override fun onPageEvent(source: String, direction: String, rawCode: String,
            startX: Int, startY: Int, endX: Int, endY: Int,
            durationMs: Int, rippleSent: Boolean) {
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    val action = if (source == "KEY") "按键" else "触控"
                    val page = if (direction == "NEXT") "下一页" else "上一页"
                    val result = if (rippleSent) "接口已下发" else "接口未下发"
                    binding.setupLastEvent.text = "最近事件：$action · $page · $rawCode · $result"
                }
            }
        }
        override fun onLog(message: String) {
            if (message.contains("接口旋转=")) runOnUiThread {
                if (!isFinishing && !isDestroyed) binding.setupLastCommand.text = "最近接口：$message"
            }
            if (message.contains("输入事件监听已退出") || message.contains("输入事件监听异常")) runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    isListening = false
                    setupToggleButton.text = "开启监听"
                    setupListenerStatus.text = "状态：输入监听已中断"
                }
            }
        }
    }
    private val configListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in setOf("is_waterfall", "reverse_animation", "ireader_speed", "speed", "hanvon_speed") &&
            touchService != null && !serviceExecutor.isShutdown) {
            serviceExecutor.execute {
                try { applySettingsToService() }
                catch (_: Throwable) {
                    runOnUiThread { if (!isFinishing && !isDestroyed)
                        setupListenerStatus.text = "状态：动画配置同步失败" }
                }
            }
        }
    }
    private val handler = Handler(Looper.getMainLooper())
    private val serviceExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "setup-ripple-service-calls")
    }
    private val timeoutRunnable = Runnable {
        if (touchService == null) {
            isBinding = false
            setupListenerStatus.text = "状态：服务连接超时"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("ripple_prefs", MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(configListener)
        targetPackages = prefs.getStringSet("target_pkgs", HashSet())?.toMutableSet() ?: HashSet()
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = "初始化"

        listOf(step1Action, step2Action, step3Action, step4Action, step5Action, step6Action).forEach {
            it.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
            it.strokeColor = ColorStateList.valueOf(Color.BLACK)
            it.strokeWidth = 1
            it.setTextColor(Color.BLACK)
        }

        listOf(setupTargetButton, setupToggleButton).forEach {
            it.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
            it.strokeColor = ColorStateList.valueOf(Color.BLACK)
            it.strokeWidth = 1
            it.setTextColor(Color.BLACK)
        }

        step1Action.setOnClickListener { openDeveloperOptions() }
        step2Action.setOnClickListener { openAccessibilitySettings() }
        step3Action.setOnClickListener { openWirelessSettings() }
        step4Action.setOnClickListener { openPairing() }
        step5Action.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startWirelessService()
            } else {
                openWirelessSettings()
            }
        }
        step6Action.setOnClickListener {
            startActivity(Intent(this, RippleActivity::class.java).putExtra("tab", "animation"))
        }
        setupTargetButton.setOnClickListener { if (!isProcessing) showAppListDialog() }
        setupToggleButton.setOnClickListener { toggleListening() }
        findViewById<TextView>(R.id.navInit).apply {
            text = "首页"
            setOnClickListener { }
        }
        findViewById<TextView>(R.id.navAnimation).apply {
            text = "翻页动画"
            setOnClickListener {
                startActivity(Intent(this@SetupActivity, RippleActivity::class.java).putExtra("tab", "animation"))
            }
        }
        fitBottomNavigation(binding.setupBottomNav)
        binding.refreshButton.setOnClickListener { refreshState() }
        binding.saveInitializationButton.setOnClickListener {
            getSharedPreferences("ripple_prefs", MODE_PRIVATE)
                .edit().putBoolean("initialization_saved", true).apply()
            android.widget.Toast.makeText(this, "初始化设置已保存", android.widget.Toast.LENGTH_SHORT).show()
            refreshState()
        }
        updateTargetButtonText()
        refreshState()
        checkRippleService()
    }

    override fun onResume() {
        super.onResume()
        refreshState()
        checkRippleService()
        if (touchService != null && !serviceExecutor.isShutdown) {
            serviceExecutor.execute {
                try { applySettingsToService() }
                catch (_: Throwable) {}
            }
        }
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(configListener)
        handler.removeCallbacks(timeoutRunnable)
        try {
            Shizuku.unbindUserService(userServiceArgs(), connection, false)
        } catch (_: Throwable) {
        }
        serviceExecutor.shutdown()
        super.onDestroy()
    }

    private fun userServiceArgs(): Shizuku.UserServiceArgs {
        return Shizuku.UserServiceArgs(
            ComponentName(packageName, TouchService::class.java.name)
        ).tag("ripple-service-v2")
            .processNameSuffix("ripple")
            .daemon(true)
            .version(4)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            handler.removeCallbacks(timeoutRunnable)
            isBinding = false
            touchService = ITouchService.Stub.asInterface(service)
            setupListenerStatus.text = "状态：服务已连接"
            serviceExecutor.execute {
                try {
                    touchService?.registerCallback(listenerCallback)
                    applySettingsToService()
                    syncListeningState()
                } catch (_: Throwable) {
                    runOnUiThread { setupListenerStatus.text = "状态：服务连接异常" }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBinding = false
            touchService = null
            isListening = false
            runOnUiThread {
                setupListenerStatus.text = "状态：服务断开"
                setupToggleButton.text = "开启监听"
            }
        }
    }

    private fun runServiceCommand(command: (ITouchService) -> Unit) {
        val service = touchService ?: return
        serviceExecutor.execute {
            try {
                command(service)
            } catch (_: Throwable) {
                runOnUiThread { setupListenerStatus.text = "状态：操作失败" }
            }
        }
    }

    private fun checkRippleService() {
        if (touchService != null || isBinding) return
        if (!Shizuku.pingBinder()) {
            setupListenerStatus.text = "状态：Shizuku 未连接"
            return
        }
        isBinding = true
        setupListenerStatus.text = "状态：正在连接服务..."
        handler.removeCallbacks(timeoutRunnable)
        handler.postDelayed(timeoutRunnable, 5000)
        try {
            Shizuku.bindUserService(userServiceArgs(), connection)
        } catch (_: Throwable) {
            isBinding = false
            setupListenerStatus.text = "状态：服务绑定失败"
        }
    }

    private fun applySettingsToService() {
        val speed = prefs.getInt("ireader_speed", prefs.getInt("speed", 64))
        val hanvonSpeed = prefs.getInt("hanvon_speed", 100).coerceIn(30, 300)
        val waterfall = prefs.getBoolean("is_waterfall", false)
        val reverseAnimation = prefs.getBoolean("reverse_animation", false)
        val rotation = (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        val packages = targetPackages.toTypedArray()
        val service = touchService ?: return
        service.setSpeed(speed)
        service.setHanvonSpeed(hanvonSpeed)
        service.setWaterfallMode(waterfall)
        service.setReverseAnimation(reverseAnimation)
        service.setRotation(rotation)
        service.setTargetPackages(packages)
    }

    private fun syncListeningState() {
        val service = touchService ?: return
        val running = service.isListening
        runOnUiThread {
            isListening = running
            setupToggleButton.text = if (running) "停止监听" else "开启监听"
            setupListenerStatus.text = if (running) "状态：正在监听" else "状态：服务已连接"
        }
    }

    private fun toggleListening() {
        val service = touchService
        if (isProcessing || service == null) {
            if (service == null) setupListenerStatus.text = "状态：服务未连接"
            return
        }
        isProcessing = true
        if (isListening) {
            isListening = false
            setupToggleButton.text = "开启监听"
            setupListenerStatus.text = "状态：正在停止..."
            serviceExecutor.execute {
                try { service.stopListening() } catch (_: Throwable) {}
                runOnUiThread {
                    isProcessing = false
                    setupListenerStatus.text = "状态：已停止监听"
                }
            }
        } else {
            isListening = true
            setupToggleButton.text = "停止监听"
            setupListenerStatus.text = "状态：正在监听"
            serviceExecutor.execute {
                try {
                    service.startListening()
                    val active = service.isListening
                    runOnUiThread {
                        isProcessing = false
                        isListening = active
                        setupToggleButton.text = if (active) "停止监听" else "开启监听"
                        setupListenerStatus.text = if (active) "状态：正在监听" else "状态：监听启动失败"
                    }
                } catch (_: Throwable) {
                    runOnUiThread {
                        isListening = false
                        isProcessing = false
                        setupToggleButton.text = "开启监听"
                        setupListenerStatus.text = "状态：启动监听失败"
                    }
                }
            }
        }
    }

    private fun updateTargetButtonText() {
        setupTargetButton.text = if (targetPackages.isEmpty()) "所有应用" else "已选 ${targetPackages.size} 个应用"
    }

    private fun showAppListDialog() {
        isProcessing = true
        val pm = packageManager
        val userApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { app ->
                if (app.packageName == packageName) false else {
                    val label = app.loadLabel(pm).toString()
                    pm.getLaunchIntentForPackage(app.packageName) != null ||
                        listOf("reader", "ireader", "hanvon", "bigme", "阅读", "读书").any {
                            label.contains(it, ignoreCase = true) ||
                                app.packageName.contains(it, ignoreCase = true)
                        }
                }
            }
            .sortedBy { it.loadLabel(pm).toString() }
        val names = userApps.map { it.loadLabel(pm).toString() }.toTypedArray()
        val packages = userApps.map { it.packageName }.toTypedArray()
        val draftPackages = targetPackages.toMutableSet()
        val checked = userApps.map { draftPackages.contains(it.packageName) }.toBooleanArray()
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_NoActionBar)
            .setTitle("选择需要监听的软件 (可多选)")
            .setMultiChoiceItems(names, checked) { _, which, selected ->
                if (selected) draftPackages.add(packages[which]) else draftPackages.remove(packages[which])
            }
            .setPositiveButton("确定") { _, _ ->
                isProcessing = false
                targetPackages = draftPackages
                prefs.edit().putStringSet("target_pkgs", targetPackages).apply()
                updateTargetButtonText()
                runServiceCommand { it.setTargetPackages(targetPackages.toTypedArray()) }
            }
            .setNegativeButton("取消") { _, _ -> isProcessing = false }
            .setNeutralButton("清空选择") { _, _ ->
                isProcessing = false
                targetPackages.clear()
                prefs.edit().putStringSet("target_pkgs", targetPackages).apply()
                updateTargetButtonText()
                runServiceCommand { it.setTargetPackages(arrayOf()) }
            }
            .setOnCancelListener { isProcessing = false }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.BLACK)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.BLACK)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(Color.BLACK)
        }
        dialog.show()
    }

    private fun openDeveloperOptions() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openWirelessSettings() {
        val intent = Intent("android.settings.ADB_WIRELESS_SETTINGS")
        try {
            startActivity(intent)
        } catch (_: Throwable) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        }
    }

    private fun openPairing() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getSharedPreferences("auto_pair_guard", MODE_PRIVATE).edit()
                .putLong("armed_until_elapsed",
                    android.os.SystemClock.elapsedRealtime() + 5 * 60_000L)
                .apply()
            startActivity(Intent(this, AdbPairingTutorialActivity::class.java))
        } else {
            openWirelessSettings()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun startWirelessService() {
        AdbDialogFragment().show(supportFragmentManager)
    }

    private fun fitBottomNavigation(view: View) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || bottomNavigationAdjusted) return
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

    private fun isDeveloperOptionsEnabled(): Boolean {
        return try {
            Settings.Global.getInt(
                contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
            ) == 1
        } catch (_: Throwable) {
            false
        }
    }

    private fun isWirelessDebuggingEnabled(): Boolean {
        return try {
            Settings.Global.getInt(contentResolver, "adb_wifi_enabled", 0) == 1 || Shizuku.pingBinder()
        } catch (_: Throwable) {
            Shizuku.pingBinder()
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val manager = getSystemService(ACCESSIBILITY_SERVICE)
            as android.view.accessibility.AccessibilityManager
        return manager.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        ).any {
            it.resolveInfo.serviceInfo.name ==
                "moe.shizuku.manager.adb.AutoPairAccessibilityService"
        }
    }

    private fun updateStep(
        status: android.widget.TextView,
        button: com.google.android.material.button.MaterialButton,
        completed: Boolean,
        completedText: String = "已完成"
    ) {
        status.text = if (completed) "✓ $completedText" else "未完成"
        status.setTextColor(if (completed) 0xFF176B32.toInt() else 0xFF333333.toInt())
        button.text = if (completed) "查看 / 重做" else button.tag as? String ?: button.text
        button.alpha = if (completed) 0.82f else 1f
    }

    private fun refreshState() {
        val developer = isDeveloperOptionsEnabled()
        val accessibility = isAccessibilityEnabled()
        val wireless = isWirelessDebuggingEnabled()
        val paired = ShizukuSettings.getPreferences()
            .getBoolean("pairing_completed", false)
        val serviceRunning = Shizuku.pingBinder()
        val rippleConfigured = getSharedPreferences("ripple_prefs", MODE_PRIVATE)
            .getBoolean("animation_configured", false)

        step1Action.tag = "去打开"
        step2Action.tag = "去授权"
        step3Action.tag = "去打开"
        step4Action.tag = "开始配对"
        step5Action.tag = "启动服务"
        step6Action.tag = "设置波纹"

        updateStep(step1Status, step1Action, developer, "开发者选项已打开")
        updateStep(step2Status, step2Action, accessibility, "无障碍已授权")
        updateStep(step3Status, step3Action, wireless, "无线调试已打开")
        updateStep(step4Status, step4Action, paired, "配对码配对已完成")
        updateStep(step5Status, step5Action, serviceRunning, "Shizuku 服务已连接")
        updateStep(step6Status, step6Action, rippleConfigured, "监听配置已保存")

        val completed = listOf(developer, accessibility, wireless, paired, serviceRunning, rippleConfigured).count { it }
        binding.setupProgress.progress = completed
        binding.setupProgressText.text = "初始化 $completed / 6"
        binding.setupRemaining.text = if (completed == 6) "初始化完成" else "还差 ${6 - completed} 步"
        binding.setupChecklist.text = buildString {
            append(if (developer) "✓ " else "○ ").append("开发者选项").append(if (developer) "  已完成" else "  待完成").append('\n')
            append(if (accessibility) "✓ " else "○ ").append("无障碍权限").append(if (accessibility) "  已完成" else "  待完成").append('\n')
            append(if (wireless) "✓ " else "○ ").append("无线调试").append(if (wireless) "  已完成" else "  待完成").append('\n')
            append(if (paired) "✓ " else "○ ").append("配对码配对").append(if (paired) "  已完成" else "  待完成").append('\n')
            append(if (serviceRunning) "✓ " else "○ ").append("无线调试服务").append(if (serviceRunning) "  已连接" else "  待完成").append('\n')
            append(if (rippleConfigured) "✓ " else "○ ").append("动画设置").append(if (rippleConfigured) "  已保存" else "  待完成")
        }

        listOf(binding.step1Card, binding.step2Card, binding.step3Card, binding.step4Card, binding.step5Card, binding.step6Card)
            .forEach { it.visibility = View.GONE }
        when {
            !developer -> binding.step1Card.visibility = View.VISIBLE
            !accessibility -> binding.step2Card.visibility = View.VISIBLE
            !wireless -> binding.step3Card.visibility = View.VISIBLE
            !paired -> binding.step4Card.visibility = View.VISIBLE
            !serviceRunning -> binding.step5Card.visibility = View.VISIBLE
            else -> binding.step6Card.visibility = View.VISIBLE
        }
        binding.refreshButton.visibility = if (completed == 6) View.VISIBLE else View.VISIBLE
    }
}
