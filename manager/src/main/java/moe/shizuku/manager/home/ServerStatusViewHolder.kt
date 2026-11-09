package moe.shizuku.manager.home

import android.content.Intent
import android.graphics.Color
import android.provider.Settings
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import moe.shizuku.manager.R
import rikka.recyclerview.BaseViewHolder
import rikka.recyclerview.BaseViewHolder.Creator
import rikka.shizuku.Shizuku

class ServerStatusViewHolder(parent: ViewGroup) : BaseViewHolder<Any>(
    LayoutInflater.from(parent.context).inflate(R.layout.item_home_status, parent, false)
) {
    companion object {
        val CREATOR = Creator<Any> { inflater, parent -> ServerStatusViewHolder(parent) }
    }

    init {
        // 点击设置按钮，跳转系统“关于本机”并通知无障碍服务去连点
        itemView.findViewById<MaterialButton>(R.id.btn_settings)?.setOnClickListener {
            try {
                // 发送暗号，让无障碍准备连点
                val broadcastIntent = Intent("moe.shizuku.manager.action.CLICK_BUILD")
                broadcastIntent.setPackage(itemView.context.packageName)
                itemView.context.sendBroadcast(broadcastIntent)

                // 跳转到关于本机
                val intent = Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                itemView.context.startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                itemView.context.startActivity(intent)
            }
        }
    }

    override fun onBind() {
        val tv = itemView.findViewById<TextView>(R.id.status_text)
        tv.setTextColor(Color.BLACK)
        if (Shizuku.pingBinder()) {
            tv.text = "Shizuku 正在运行"
        } else {
            tv.text = "Shizuku 未运行"
        }
    }
}
