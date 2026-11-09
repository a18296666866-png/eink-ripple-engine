package moe.shizuku.manager.home

import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import moe.shizuku.manager.R
import moe.shizuku.manager.adb.AdbPairingTutorialActivity
import moe.shizuku.manager.starter.StarterActivity
import rikka.recyclerview.BaseViewHolder
import rikka.recyclerview.BaseViewHolder.Creator

class StartWirelessAdbViewHolder(parent: ViewGroup) : BaseViewHolder<Any>(
    LayoutInflater.from(parent.context).inflate(R.layout.home_start_wireless_adb, parent, false)
) {
    companion object {
        val CREATOR = Creator<Any> { inflater, parent -> StartWirelessAdbViewHolder(parent) }
    }

    init {
        val context = itemView.context
        itemView.findViewById<MaterialButton>(R.id.action_wireless_adb_pair)?.setOnClickListener {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                context.startActivity(Intent(context, AdbPairingTutorialActivity::class.java))
            } else {
                try {
                    context.startActivity(Intent("android.settings.ADB_WIRELESS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
        
        // “启动”按钮：自动扫描端口并走无线调试
        itemView.findViewById<MaterialButton>(R.id.action_wireless_adb_start)?.setOnClickListener {
            Toast.makeText(context, "正在扫描无线调试端口...", Toast.LENGTH_SHORT).show()
            startAdbWithScan(context)
        }
    }

    private fun startAdbWithScan(context: Context) {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        try {
            nsdManager.discoverServices("_adb-tls-connect._tcp", NsdManager.PROTOCOL_DNS_SD, object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String?) {}
                override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                    Toast.makeText(context, "扫描失败，请确保开启无线调试", Toast.LENGTH_SHORT).show()
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
                            
                            // 强制走无线调试，绝不试 Root
                            val intent = Intent(context, StarterActivity::class.java)
                            intent.putExtra(StarterActivity.EXTRA_IS_ROOT, false)
                            intent.putExtra(StarterActivity.EXTRA_HOST, "127.0.0.1")
                            intent.putExtra(StarterActivity.EXTRA_PORT, port)
                            context.startActivity(intent)
                        }
                    })
                }
            })
        } catch (e: Exception) {
            Toast.makeText(context, "发生错误: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBind() {}
}
