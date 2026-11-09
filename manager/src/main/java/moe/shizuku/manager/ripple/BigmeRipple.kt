package moe.shizuku.manager.ripple

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.lang.reflect.Method
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class BigmeRipple(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val stateLock = Any()
    private var policy: Any? = null
    private var setAnimationMethod: Method? = null

    fun detect(): Boolean = synchronized(stateLock) {
        try {
            policy = null
            setAnimationMethod = null
            val managerClass = Class.forName("xrz.framework.manager.XrzEinkManager")
            val constructor = managerClass.getDeclaredConstructor(Context::class.java)
            constructor.isAccessible = true
            val manager = constructor.newInstance(context.applicationContext)

            val getPolicy = managerClass.getMethod("getDisplayPolicyManager")
            getPolicy.isAccessible = true
            policy = getPolicy.invoke(manager)

            if (policy != null) {
                setAnimationMethod = policy!!.javaClass.methods.firstOrNull { method ->
                    if (method.name != "setEinkAnimationTimeout" || method.parameterTypes.size != 2) {
                        return@firstOrNull false
                    }
                    val types = method.parameterTypes
                    val intType = types[0]
                    val longType = types[1]
                    (intType == Int::class.javaPrimitiveType || intType == Int::class.java) &&
                        (longType == Long::class.javaPrimitiveType || longType == Long::class.java)
                }
                    ?: throw NoSuchMethodException("setEinkAnimationTimeout(int,long)")
                setAnimationMethod?.isAccessible = true
                Log.i(
                    "BigmeRipple",
                    "接口就绪 policy=${policy!!.javaClass.name} method=${setAnimationMethod!!.toGenericString()}"
                )
                return true
            }
            Log.w("BigmeRipple", "接口探测失败：DisplayPolicyManager 为空")
        } catch (e: Throwable) {
            Log.w("BigmeRipple", "未检测到大我接口: ${e.message}")
        }
        return false
    }

    fun preparePage(rotation: Int, next: Boolean, isWaterfall: Boolean): Boolean {
        val mode = RippleMapping.command(
            device = RippleMapping.Device.BIGME,
            pattern = if (isWaterfall) RippleMapping.Pattern.CURTAIN else RippleMapping.Pattern.WIND,
            rotation = rotation,
            source = RippleMapping.Source.TOUCH,
            direction = if (next) RippleMapping.Direction.NEXT else RippleMapping.Direction.PREVIOUS
        )?.code ?: return false
        return prepareMode(mode, rotation, next, isWaterfall)
    }

    fun prepareMode(
        mode: Int,
        rotation: Int = 0,
        next: Boolean = false,
        isWaterfall: Boolean = false
    ): Boolean {
        synchronized(stateLock) {
            if (policy == null || setAnimationMethod == null) {
                if (!detect()) {
                    Log.w("BigmeRipple", "跳过下发：接口未就绪 rotation=$rotation next=$next vertical=$isWaterfall")
                    return false
                }
            }

            try {
                var error = invokeOnHandler {
                    setAnimationMethod?.invoke(policy, mode, 500L)
                }
                if (error != null) {
                    // The vendor policy object may become stale after a
                    // display-service restart or rotation. Re-resolve once
                    // before reporting a failed animation to the caller.
                    Log.w("BigmeRipple", "首次下发失败，重新探测接口后重试", error)
                    if (detect()) {
                        error = invokeOnHandler {
                            setAnimationMethod?.invoke(policy, mode, 500L)
                        }
                    }
                }
                if (error != null) throw error
                Log.i(
                    "BigmeRipple",
                    "下发成功 rotation=$rotation next=$next vertical=$isWaterfall mode=$mode duration=500，无自动复位"
                )
                return true
            } catch (e: Throwable) {
                Log.e(
                    "BigmeRipple",
                    "下发失败 rotation=$rotation next=$next vertical=$isWaterfall mode=$mode",
                    e
                )
                return false
            }
        }
    }

    /** Run vendor calls on a consistent looper to avoid vendor-thread races. */
    private fun invokeOnHandler(action: () -> Unit): Throwable? {
        if (Looper.myLooper() == handler.looper) {
            return try {
                action()
                null
            } catch (error: Throwable) {
                error
            }
        }

        val latch = CountDownLatch(1)
        var error: Throwable? = null
        if (!handler.post {
                try {
                    action()
                } catch (throwable: Throwable) {
                    error = throwable
                } finally {
                    latch.countDown()
                }
            }) {
            return IllegalStateException("Bigme animation handler is not accepting tasks")
        }
        if (!latch.await(800L, TimeUnit.MILLISECONDS)) {
            return TimeoutException("Bigme animation call timed out")
        }
        return error
    }
}
