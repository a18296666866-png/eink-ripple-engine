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

        // 1. 处理配对码抓取逻辑
        handlePairingCode(rootNode)

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
                
                Log.i("AutoPair", "截获配对码: $foundCode, 端口: $foundPort")
                Toast.makeText(this, "✅ 已截获配对码: $foundCode，正在自动配对...", Toast.LENGTH_LONG).show()
                
                // 发送配对指令给底层
                sendPairIntent(foundCode, foundPort)

                // 【核心修复】延迟 1.5 秒后自动跳回 App
                handler.postDelayed({
                    try {
                        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                        launchIntent?.putExtra("PAIR_SUCCESS", true)
                        launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        startActivity(launchIntent)
                    } catch (e: Exception) {
                        Log.e("AutoPair", "无法跳转回App", e)
                    }
                }, 1500)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
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
