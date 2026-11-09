package moe.shizuku.manager.ripple

import android.view.Surface

/** Public mapping facade. Vendor private mapping tables must not leak into event parsing. */
object RippleMapping {
    enum class Device { IREADER, HANVON, BIGME, UNKNOWN }
    enum class Pattern { WIND, CURTAIN }
    enum class Source { TOUCH, KEY }
    enum class Direction { PREVIOUS, NEXT }
    enum class CommandType { HANVON_DIRECTION, IREADER_EFFECT, BIGME_MODE }

    data class Command(val type: CommandType, val code: Int)

    fun normalizeRotation(rotation: Int): Int = when (rotation) {
        Surface.ROTATION_90 -> Surface.ROTATION_90
        Surface.ROTATION_180 -> Surface.ROTATION_180
        Surface.ROTATION_270 -> Surface.ROTATION_270
        else -> Surface.ROTATION_0
    }

    private fun vendor(device: Device): VendorRippleMapper? = when (device) {
        Device.IREADER -> IreaderRippleMapper
        Device.HANVON -> HanvonRippleMapper
        Device.BIGME -> BigmeRippleMapper
        Device.UNKNOWN -> null
    }

    /** Reverse animation is applied exactly once before this method. */
    fun command(device: Device, pattern: Pattern, rotation: Int,
        source: Source, direction: Direction, speed: Int = 64): Command? {
        val mapper = vendor(device) ?: return null
        val base = mapper.baseCode(pattern, normalizeRotation(rotation), source, direction)
        val validSpeed = if (speed == 0 || speed == 64 || speed == 128) speed else 64
        return Command(mapper.commandType,
            if (device == Device.IREADER) base or validSpeed else base)
    }

    fun keyDirection(device: Device, rotation: Int, code: String): Direction? =
        vendor(device)?.keyDirection(normalizeRotation(rotation), code)
}
