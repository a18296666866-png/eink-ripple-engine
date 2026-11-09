package moe.shizuku.manager.home

import android.os.Build
import moe.shizuku.manager.management.AppsViewModel
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.UserHandleCompat
import rikka.recyclerview.IdBasedRecyclerViewAdapter
import rikka.recyclerview.IndexCreatorPool
import rikka.shizuku.Shizuku

class HomeAdapter(private val homeModel: HomeViewModel, private val appsModel: AppsViewModel) :
    IdBasedRecyclerViewAdapter(ArrayList()) {

    init {
        updateData()
        setHasStableIds(true)
    }

    companion object {

        private const val ID_STATUS = 0L
        private const val ID_APPS = 1L
        private const val ID_TERMINAL = 2L
        private const val ID_START_ROOT = 3L
        private const val ID_START_WADB = 4L
        private const val ID_START_ADB = 5L
        private const val ID_LEARN_MORE = 6L
        private const val ID_ADB_PERMISSION_LIMITED = 7L
    }

    override fun onCreateCreatorPool(): IndexCreatorPool {
        return IndexCreatorPool()
    }

    fun updateData() {
        val status = homeModel.serviceStatus.value?.data ?: return
        val isPrimaryUser = UserHandleCompat.myUserId() == 0
        // 【核心修复】直接判断底层 Binder 状态
        val isRunning = rikka.shizuku.Shizuku.pingBinder()

        clear()
        
        // 1. 状态卡片
        addItem(ServerStatusViewHolder.CREATOR, status, ID_STATUS)

        // 2. 授权列表卡片（永久显示）
        addItem(AuthListViewHolder.CREATOR, Any(), 9998L)

        // 3. 无线调试启动卡片（永久显示，不隐藏）
        if (isPrimaryUser) {
            addItem(StartWirelessAdbViewHolder.CREATOR, null, ID_START_WADB)
        }
        
        // 4. 波纹助手卡片
        addItem(RippleViewHolder.CREATOR, Any(), 999L)

        notifyDataSetChanged()
    }
}
