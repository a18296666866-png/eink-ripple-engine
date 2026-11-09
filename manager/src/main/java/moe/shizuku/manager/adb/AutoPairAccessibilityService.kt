package moe.shizuku.manager.adb

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class AutoPairAccessibilityService : AccessibilityService() {

    private var lastPairCode: String? = null
    private var lastPort: Int = -1
    private var lastSentTime = 0L

    // 自动连点版本号相关
    private var isClickingBuild = false
    private var clickCount = 0
    private val handler = Handler(Looper.getMainLooper())

    private val clickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "moe.shizuku.manager.action.CLICK_BUILD") {
                isClickingBuild = true
                clickCount = 0
                Log.d("AutoPair", "收到指令，准备自动连点版本号")
                Toast.makeText(context, "正在尝试自动点击版本号...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val filter = IntentFilter("moe.shizuku.manager.action.CLICK_BUILD")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(clickReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(clickReceiver, filter)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val rootNode = rootInActiveWindow ?: return

        // Read pairing text only during user-initiated pairing inside Android Settings.
        val activePackage = event.packageName?.toString().orEmpty()
        val armedUntil = getSharedPreferences("auto_pair_guard", MODE_PRIVATE)
            .getLong("armed_until_elapsed", 0L)
        if (activePackage == "com.android.settings" &&
            android.os.SystemClock.elapsedRealtime() < armedUntil) {
            handlePairingCode(rootNode)
        }

        // 2. 处理自动点击版本号
        if (isClickingBuild) {
            findAndClickBuildNumber(rootNode)
        }
    }

    private fun handlePairingCode(rootNode: AccessibilityNodeInfo) {
        val allTexts = mutableListOf<String>()
        collectTexts(rootNode, allTexts)

        var foundCode: String? = null
        var foundPort = -1

        for (text in allTexts) {
            if (text.contains("配对码") || text.contains("pairing code") || text.matches(Regex("^\\d{6}$"))) {
                val codeMatch = Regex("\\b\\d{6}\\b").find(text)
                if (codeMatch != null) foundCode = codeMatch.value
            }
            if (text.contains(":") && !text.contains("http")) {
                val portMatch = Regex(":(\\d{4,5})\\b").find(text)
                if (portMatch != null) {
                    foundPort = portMatch.groupValues[1].toIntOrNull() ?: -1
                }
            }
        }

        if (foundCode != null && foundPort != -1) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastSentTime > 3000 && (foundCode != lastPairCode || foundPort != lastPort)) {
                lastPairCode = foundCode
                lastPort = foundPort
                lastSentTime = currentTime
                
                Log.i("AutoPair", "接收到无线调试配对信息，已隐藏敏感验证码")
                Toast.makeText(this, "配对信息已提交，请查看真实配对结果", Toast.LENGTH_LONG).show()
                
                // 发送配对指令给底层
                getSharedPreferences("auto_pair_guard", MODE_PRIVATE).edit()
                    .remove("armed_until_elapsed").apply()
                sendPairIntent(foundCode, foundPort)

            }
        }
    }

    private fun findAndClickBuildNumber(node: AccessibilityNodeInfo?) {
        if (node == null || clickCount >= 7) {
            if (clickCount >= 7) {
                isClickingBuild = false
                Toast.makeText(this, "✅ 连点完成，快去检查开发者选项！", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (node.text != null && node.text.contains("版本号") && !node.text.contains("Android")) {
            val clickableNode = findClickableParent(node)
            if (clickableNode != null) {
                clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                clickCount++
                Log.d("AutoPair", "点击版本号第 $clickCount 次")
                
                handler.postDelayed({
                    if (isClickingBuild) findAndClickBuildNumber(rootInActiveWindow)
                }, 150)
                return
            }
        }

        for (i in 0 until node.childCount) {
            findAndClickBuildNumber(node.getChild(i))
        }
    }

    private fun findClickableParent(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent
        }
        return null
    }

    private fun collectTexts(node: AccessibilityNodeInfo?, list: MutableList<String>) {
        if (node == null) return
        if (node.text != null) list.add(node.text.toString())
        for (i in 0 until node.childCount) collectTexts(node.getChild(i), list)
    }

    private fun sendPairIntent(code: String, port: Int) {
        val intent = Intent()
        intent.setClassName(packageName, "moe.shizuku.manager.adb.AdbPairingService")
        intent.action = "moe.shizuku.manager.action.DIRECT_PAIR"
        intent.putExtra("pair_code", code)
        intent.putExtra("pairing_port", port)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(intent)
            else startService(intent)
        } catch (e: Throwable) {
            Log.w("AutoPair", "启动后台配对受系统限制，请回到应用手动重试", e)
        }
    }

    override fun onInterrupt() {
        Log.w("AutoPair", "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(clickReceiver) } catch (e: Exception) {}
        handler.removeCallbacksAndMessages(null)
    }
}
