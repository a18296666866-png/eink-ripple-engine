package moe.shizuku.manager.home

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import moe.shizuku.manager.R
import moe.shizuku.manager.management.ApplicationManagementActivity
import rikka.recyclerview.BaseViewHolder
import rikka.recyclerview.BaseViewHolder.Creator

class AuthListViewHolder(parent: ViewGroup) : BaseViewHolder<Any>(
    LayoutInflater.from(parent.context).inflate(R.layout.item_home_auth, parent, false)
) {
    companion object {
        val CREATOR = Creator<Any> { inflater, parent -> AuthListViewHolder(parent) }
    }

    init {
        val intent = Intent(itemView.context, ApplicationManagementActivity::class.java)
        itemView.setOnClickListener { itemView.context.startActivity(intent) }
        itemView.findViewById<MaterialButton>(R.id.action_auth).setOnClickListener { 
            itemView.context.startActivity(intent) 
        }
    }

    override fun onBind() {
        itemView.findViewById<TextView>(R.id.title_auth).text = "授权列表"
        itemView.findViewById<MaterialButton>(R.id.action_auth).text = "进入"
    }
}
