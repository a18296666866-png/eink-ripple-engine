package moe.shizuku.manager.ripple
object EpdcHelper {
    @Volatile private var loaded = false
    fun load() { if (!loaded) { System.loadLibrary("ripple"); loaded = true } }
    external fun nativeSendPageH(effect: Int): Boolean
}
