package moe.shizuku.manager.ripple

import android.content.Context
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.lang.reflect.Method

class HanvonRipple {
    private val handler = Handler(Looper.getMainLooper())
    private val stateLock = Any()
    private val restoreToken = Any()
    private var restoreGeneration = 0L
    private var einkManager: Any? = null
    private var rippleMethod: Method? = null
    private var speedMethod: Method? = null

    // 供 UI 层判断的方法
    fun isHanvonDevice(context: Context): Boolean {
        return try {
            context.getSystemService("eink") != null
        } catch (e: Exception) {
            false
        }
    }

    // 供底层 Service 检测的方法
    fun detect(context: Context? = null): Boolean {
        try {
            // Hanvon N10 Pro exposes a usable framework manager through the
            // Context service registry. This is the same path used by the
            // device-side accessibility probe; there is no public
            // EinkManager.asInterface(IBinder) on this firmware.
            val contextManager = context?.getSystemService("eink")
            if (contextManager != null && bind(contextManager)) return true

            // Keep the binder fallback for older Hanvon firmware variants.
            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService = serviceManager.getMethod("getService", String::class.java)
            val binder = getService.invoke(null, "eink") as? IBinder ?: return false

            val einkManagerClass = Class.forName("android.os.EinkManager")
            val asInterface = einkManagerClass.getMethod("asInterface", IBinder::class.java)
            val manager = asInterface.invoke(null, binder) ?: return false
            return bind(manager)
        } catch (e: Exception) {
            Log.d("HanvonRipple", "未检测到汉王服务: ${e.message}")
        }
        return false
    }

    private fun bind(manager: Any): Boolean {
        val type = manager.javaClass
        val ripple = type.getMethod("setRegalRipple", String::class.java)
        val speed = type.getMethod("setRegalRippleSpeed", String::class.java)
        einkManager = manager
        rippleMethod = ripple.apply { isAccessible = true }
        speedMethod = speed.apply { isAccessible = true }
        return true
    }

    fun preparePage(rotation: Int, next: Boolean, speedStr: String, isWaterfall: Boolean): Boolean {
        val direction = RippleMapping.command(
            device = RippleMapping.Device.HANVON,
            pattern = if (isWaterfall) RippleMapping.Pattern.CURTAIN else RippleMapping.Pattern.WIND,
            rotation = rotation,
            source = RippleMapping.Source.TOUCH,
            direction = if (next) RippleMapping.Direction.NEXT else RippleMapping.Direction.PREVIOUS
        )?.code?.toString() ?: return false
        return prepareDirection(direction, speedStr, rotation, next, isWaterfall)
    }

    fun prepareDirection(
        direction: String,
        speedStr: String,
        rotation: Int = 0,
        next: Boolean = false,
        isWaterfall: Boolean = false
    ): Boolean {
        synchronized(stateLock) {
            if (einkManager == null || rippleMethod == null || speedMethod == null) return false
            try {
                // The page call is made by the serial ripple dispatcher, while
                // the delayed restore runs on the main looper. Serialize both
                // paths so an old restore can never overwrite a new direction.
                handler.removeCallbacksAndMessages(restoreToken)
                val generation = ++restoreGeneration
                speedMethod?.invoke(einkManager, speedStr)
                rippleMethod?.invoke(einkManager, direction)
                Log.d("HanvonRipple", "下发方向码=$direction，旋转=$rotation，下一页=$next，垂幕纹=$isWaterfall")
                // Hanvon requires returning to the default mode after the effect.
                handler.postAtTime(
                    { restoreIfCurrent(generation) },
                    restoreToken,
                    SystemClock.uptimeMillis() + 300L
                )
                return true
            } catch (e: Exception) {
                Log.e("HanvonRipple", "下发失败: ${e.message}")
                return false
            }
        }
    }

    fun restore() {
        synchronized(stateLock) {
            restoreGeneration++
            handler.removeCallbacksAndMessages(restoreToken)
            if (einkManager == null || rippleMethod == null) return
            try {
                rippleMethod?.invoke(einkManager, "0")
            } catch (e: Exception) {
                Log.e("HanvonRipple", "复位失败: ${e.message}")
            }
        }
    }

    private fun restoreIfCurrent(generation: Long) {
        synchronized(stateLock) {
            if (generation != restoreGeneration) return
            if (einkManager == null || rippleMethod == null) return
            try {
                rippleMethod?.invoke(einkManager, "0")
            } catch (e: Exception) {
                Log.e("HanvonRipple", "复位失败: ${e.message}")
            }
        }
    }
}
