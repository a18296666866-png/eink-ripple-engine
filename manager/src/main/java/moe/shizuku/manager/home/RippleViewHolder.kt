package moe.shizuku.manager.home

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import moe.shizuku.manager.R
import moe.shizuku.manager.ripple.HanvonRipple
import moe.shizuku.manager.ripple.RippleActivity
import moe.shizuku.manager.SetupActivity
import rikka.recyclerview.BaseViewHolder
import rikka.recyclerview.BaseViewHolder.Creator

class RippleViewHolder(parent: ViewGroup) : BaseViewHolder<Any>(
    LayoutInflater.from(parent.context).inflate(R.layout.item_home_ripple, parent, false)
) {
    companion object {
        val CREATOR = Creator<Any> { inflater, parent -> RippleViewHolder(parent) }
    }

    init {
        val intent = Intent(itemView.context, SetupActivity::class.java)
        itemView.setOnClickListener { itemView.context.startActivity(intent) }
        itemView.findViewById<MaterialButton>(R.id.action_ripple).setOnClickListener { 
            itemView.context.startActivity(intent) 
        }
    }

    override fun onBind() {
        val isHanvon = HanvonRipple().isHanvonDevice(itemView.context)
        val isIreader = try {
            Class.forName("android.eink.EPDCDevice")
            true
        } catch (e: Throwable) {
            false
        }
        val isBigme = try {
            Class.forName("xrz.framework.manager.XrzEinkManager")
            true
        } catch (e: Throwable) {
            false
        }

        val titleText: String
        val summaryText: String
        val buttonText: String

        if (isHanvon) {
            titleText = "汉王波纹助手"
            summaryText = "提供汉王系统级别的翻页波纹特效，支持自定义应用与速度。"
            buttonText = "进入"
            itemView.isEnabled = true
            itemView.findViewById<MaterialButton>(R.id.action_ripple).isEnabled = true
        } else if (isIreader) {
            titleText = "掌阅波纹助手"
            summaryText = "提供掌阅底层驱动的翻页波纹特效，支持自定义应用与速度。"
            buttonText = "进入"
            itemView.isEnabled = true
            itemView.findViewById<MaterialButton>(R.id.action_ripple).isEnabled = true
        } else if (isBigme) {
            titleText = "大我波纹助手"
            summaryText = "提供大我系统级别的翻页波纹特效，固定 500ms 动画。"
            buttonText = "进入"
            itemView.isEnabled = true
            itemView.findViewById<MaterialButton>(R.id.action_ripple).isEnabled = true
        } else {
            titleText = "不支持波纹"
            summaryText = "当前设备底层未发现汉王、掌阅或大我的波纹控制接口。"
            buttonText = "不支持"
            itemView.isEnabled = true
            itemView.findViewById<MaterialButton>(R.id.action_ripple).isEnabled = true
        }
        
        itemView.findViewById<TextView>(R.id.title_ripple).text = titleText
        itemView.findViewById<TextView>(R.id.summary_ripple).text = summaryText
        itemView.findViewById<MaterialButton>(R.id.action_ripple).text = buttonText
    }
}
