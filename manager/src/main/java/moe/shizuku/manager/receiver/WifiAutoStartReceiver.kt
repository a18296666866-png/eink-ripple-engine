package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import moe.shizuku.manager.starter.StarterActivity
import rikka.shizuku.Shizuku

class WifiAutoStartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 只处理网络变化或Wifi变化
        if (intent.action != ConnectivityManager.CONNECTIVITY_ACTION && intent.action != android.net.wifi.WifiManager.WIFI_STATE_CHANGED_ACTION) return

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = cm.activeNetworkInfo

        // 如果已经连上 Wi-Fi，且 Shizuku 没在运行，则尝试启动
        if (networkInfo != null && networkInfo.type == ConnectivityManager.TYPE_WIFI && networkInfo.isConnected) {
            
            // 防止频繁触发，加个延迟
            Handler(Looper.getMainLooper()).postDelayed({
                if (!Shizuku.pingBinder()) {
                    Log.i("WifiAutoStart", "检测到 Wi-Fi 连接，准备自动开启无线调试...")
                    
                    // 1. 尝试通过 Shizuku 权限强制开启系统无线调试（前提是 App 已经有 WRITE_SECURE_SETTINGS 权限）
                    try {
                        Settings.Global.putInt(context.contentResolver, "adb_wifi_enabled", 1)
                        Log.i("WifiAutoStart", "已尝试开启系统无线调试开关")
                    } catch (e: Exception) {
                        Log.e("WifiAutoStart", "开启无线调试失败（缺 WRITE_SECURE_SETTINGS 权限）: ${e.message}")
                    }

                    // 2. 扫描连接端口并拉起 StarterActivity
                    scanPortAndStart(context)
                }
            }, 2000)
        }
    }

    private fun scanPortAndStart(context: Context) {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        
        try {
            nsdManager.discoverServices("_adb-tls-connect._tcp", NsdManager.PROTOCOL_DNS_SD, object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String?) {}
                override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                    Log.e("WifiAutoStart", "扫描失败: $errorCode")
                }
                override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
                override fun onDiscoveryStopped(serviceType: String?) {}
                override fun onServiceLost(serviceInfo: NsdServiceInfo?) {}

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    nsdManager.stopServiceDiscovery(this)
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {}
                        override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                            val port = resolvedInfo.port
                            Log.i("WifiAutoStart", "找到端口: $port，准备拉起服务连接")
                            
                            // 拉起 StarterActivity 进行静默/自动连接
                            val startIntent = Intent(context, StarterActivity::class.java)
                            startIntent.putExtra(StarterActivity.EXTRA_IS_ROOT, false)
                            startIntent.putExtra(StarterActivity.EXTRA_HOST, "127.0.0.1")
                            startIntent.putExtra(StarterActivity.EXTRA_PORT, port)
                            startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(startIntent)
                        }
                    })
                }
            })
        } catch (e: Exception) {
            Log.e("WifiAutoStart", "mDNS 异常: ${e.message}")
        }
    }
}
