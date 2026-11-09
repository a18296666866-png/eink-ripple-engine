package moe.shizuku.manager.ripple

/** Reads device names from getevent -lp or /proc/bus/input/devices. */
object InputDeviceNameParser {
    private val getevent = Regex("""(?m)^\s*name:\s*"([^"]+)"""")
    private val procfs = Regex("""(?m)^N:\s*Name="([^"]+)"""")
    fun name(text: String): String =
        getevent.find(text)?.groupValues?.get(1)
            ?: procfs.find(text)?.groupValues?.get(1).orEmpty()
}
