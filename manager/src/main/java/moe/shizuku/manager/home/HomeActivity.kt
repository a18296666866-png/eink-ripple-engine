package moe.shizuku.manager.home

import android.content.DialogInterface
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.text.method.LinkMovementMethod
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbPairingTutorialActivity
import moe.shizuku.manager.app.AppBarActivity
import moe.shizuku.manager.databinding.AboutDialogBinding
import moe.shizuku.manager.databinding.HomeActivityBinding
import moe.shizuku.manager.ktx.toHtml
import moe.shizuku.manager.management.appsViewModel
import moe.shizuku.manager.settings.SettingsActivity
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.utils.AppIconCache
import rikka.core.ktx.unsafeLazy
import rikka.lifecycle.Status
import rikka.lifecycle.viewModels
import rikka.recyclerview.addEdgeSpacing
import rikka.recyclerview.addItemSpacing
import rikka.recyclerview.fixEdgeEffect
import rikka.shizuku.Shizuku

abstract class HomeActivity : AppBarActivity() {

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        checkServerStatus()
        appsModel.load()
        grantSelfPermission()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        checkServerStatus()
    }

    private val homeModel by viewModels { HomeViewModel() }
    private val appsModel by appsViewModel()
    private val adapter by unsafeLazy { HomeAdapter(homeModel, appsModel) }

    
    // 当检测到 Shizuku 启动时，无感静默执行自启和保活
    private fun grantSelfPermission() {
        if (!rikka.shizuku.Shizuku.pingBinder()) return
        Thread {
            try {
                val currentPackage = packageName
                // Protected permissions may be rejected by an ADB-backed server;
                // the remaining app-ops are still useful on supported builds.
                val cmdGrant = "pm grant $currentPackage android.permission.WRITE_SECURE_SETTINGS"
                
                // 2. 加入电池优化白名单 (最核心的省电防杀机制)
                val cmdBattery = "dumpsys deviceidle whitelist +$currentPackage"
                
                // 3. 允许后台运行
                val cmdBg = "cmd appops set $currentPackage RUN_IN_BACKGROUND allow"
                
                // 4. 允许开机自启 (绕过部分系统的严格限制)
                val cmdBoot = "cmd appops set $currentPackage BOOT_COMPLETED allow"
                
                val fullCmd = "$cmdGrant; $cmdBattery; $cmdBg; $cmdBoot"
                
                val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
                val newProcessMethod = shizukuClass.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java, Array<String>::class.java, String::class.java
                )
                newProcessMethod.isAccessible = true
                val process = newProcessMethod.invoke(null, arrayOf("sh", "-c", fullCmd), null, null) as rikka.shizuku.ShizukuRemoteProcess
                process.waitFor()
                android.util.Log.i("ShizukuAutoGrant", "已无感执行授权与保活脚本")
            } catch (e: Throwable) {
                android.util.Log.e("ShizukuAutoGrant", "无感保活失败: ${e.message}")
            }
        }.start()
    }

    private fun requestRuntimePermissions() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7101)
        }
    }

    private fun isAutoPairAccessibilityEnabled(): Boolean {
        val manager = getSystemService(android.content.Context.ACCESSIBILITY_SERVICE)
            as android.view.accessibility.AccessibilityManager
        return manager.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        ).any { it.resolveInfo.serviceInfo.name == "moe.shizuku.manager.adb.AutoPairAccessibilityService" }
    }

    private fun guideAccessibilityIfNeeded() {
        if (isAutoPairAccessibilityEnabled()) return
        MaterialAlertDialogBuilder(this)
            .setTitle("开启自动配对权限")
            .setMessage("配对码配对需要读取系统配对页面中的验证码。请在无障碍设置中开启“自动配对服务”。")
            .setPositiveButton("去开启") { _, _ ->
                startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("稍后", null)
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestRuntimePermissions()

        // === 自动注入区 ===
        // 1. 检查是否刚配对成功跳回来
        if (intent.getBooleanExtra("PAIR_SUCCESS", false)) {
            android.widget.Toast.makeText(this, "✅ 配对成功！请点击主页上的「启动」按钮", android.widget.Toast.LENGTH_LONG).show()
            intent.removeExtra("PAIR_SUCCESS")
        }

        // 2. 检查无障碍权限
        guideAccessibilityIfNeeded()
        // === 自动注入结束 ===


        writeStarterFiles()

        val binding = HomeActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        homeModel.serviceStatus.observe(this) {
            if (it.status == Status.SUCCESS) {
                val status = homeModel.serviceStatus.value?.data ?: return@observe
                adapter.updateData()
                ShizukuSettings.setLastLaunchMode(if (status.uid == 0) ShizukuSettings.LaunchMethod.ROOT else ShizukuSettings.LaunchMethod.ADB)
            }
        }
        appsModel.grantedCount.observe(this) {
            if (it.status == Status.SUCCESS) {
                adapter.updateData()
            }
        }

        val recyclerView = binding.list
        recyclerView.adapter = adapter
        recyclerView.fixEdgeEffect()
        recyclerView.addItemSpacing(top = 4f, bottom = 4f, unit = TypedValue.COMPLEX_UNIT_DIP)
        recyclerView.addEdgeSpacing(top = 4f, bottom = 4f, left = 16f, right = 16f, unit = TypedValue.COMPLEX_UNIT_DIP)

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_pair -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        startActivity(Intent(this, AdbPairingTutorialActivity::class.java))
                    } else {
                        startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                    }
                    false
                }
                R.id.nav_ripple -> {
                    startActivity(Intent(this, moe.shizuku.manager.SetupActivity::class.java))
                    false
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    false
                }
                else -> false
            }
        }
        binding.bottomNavigation.selectedItemId = R.id.nav_home

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
    }

    override fun onResume() {
        super.onResume()
        checkServerStatus()
        if (Shizuku.pingBinder()) grantSelfPermission()
    }

    private fun checkServerStatus() {
        homeModel.reload()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_about -> {
                val binding = AboutDialogBinding.inflate(LayoutInflater.from(this), null, false)
                binding.sourceCode.movementMethod = LinkMovementMethod.getInstance()
                binding.sourceCode.text = getString(
                    R.string.about_view_source_code,
                    "<b><a href=\"https://github.com/RikkaApps/Shizuku\">GitHub</a></b>"
                ).toHtml()
                binding.icon.setImageBitmap(
                    AppIconCache.getOrLoadBitmap(
                        this,
                        applicationInfo,
                        Process.myUid() / 100000,
                        resources.getDimensionPixelOffset(R.dimen.default_app_icon_size)
                    )
                )
                binding.versionName.text = packageManager.getPackageInfo(packageName, 0).versionName
                MaterialAlertDialogBuilder(this)
                    .setView(binding.root)
                    .show()
                true
            }
            R.id.action_stop -> {
                if (!Shizuku.pingBinder()) {
                    return true
                }
                MaterialAlertDialogBuilder(this)
                    .setMessage(R.string.dialog_stop_message)
                    .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                        try {
                            Shizuku.exit()
                        } catch (e: Throwable) {
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun writeStarterFiles() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Starter.writeSdcardFiles(applicationContext)
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    MaterialAlertDialogBuilder(this@HomeActivity)
                        .setTitle("Cannot write files")
                        .setMessage(Log.getStackTraceString(e))
                        .setPositiveButton(android.R.string.ok, null)
                        .create()
                        .apply {
                            setOnShowListener {
                                this.findViewById<TextView>(android.R.id.message)!!.apply {
                                    typeface = Typeface.MONOSPACE
                                    setTextIsSelectable(true)
                                }
                            }
                        }
                        .show()
                }
            }
        }
    }
}
