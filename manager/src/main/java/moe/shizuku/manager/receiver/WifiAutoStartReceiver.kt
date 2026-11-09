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
                    
                    // System-protected setting writes require an explicit permission grant.
                    val enabled = try {
                        Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled", 0) == 1
                    } catch (_: Throwable) { false }
                    if (!enabled && context.checkSelfPermission(
                            android.Manifest.permission.WRITE_SECURE_SETTINGS
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        try { Settings.Global.putInt(context.contentResolver, "adb_wifi_enabled", 1) }
                        catch (error: Throwable) { Log.w("WifiAutoStart", "无法自动开启无线调试", error) }
                    }
                    val ready = try {
                        Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled", 0) == 1
                    } catch (_: Throwable) { false }
                    if (ready) scanPortAndStart(context)
                    else Log.w("WifiAutoStart", "需要在设置中手动启用无线调试")
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
                            try { context.startActivity(startIntent) }
                            catch (error: Throwable) {
                                Log.w("WifiAutoStart", "系统限制后台启动页面，请手动进入应用", error)
                            }
                        }
                    })
                }
            })
        } catch (e: Exception) {
            Log.e("WifiAutoStart", "mDNS 异常: ${e.message}")
        }
    }
}
