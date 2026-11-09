package moe.shizuku.manager.ripple

/** Vendor-specific waveform mapping. Input semantics must already be normalized. */
internal interface VendorRippleMapper {
    val commandType: RippleMapping.CommandType
    fun baseCode(pattern: RippleMapping.Pattern, rotation: Int,
        source: RippleMapping.Source, direction: RippleMapping.Direction): Int
    fun keyDirection(rotation: Int, code: String): RippleMapping.Direction?
}

internal data class DirectionCodes(val previous: Int, val next: Int) {
    fun forDirection(direction: RippleMapping.Direction): Int =
        if (direction == RippleMapping.Direction.PREVIOUS) previous else next
}

internal fun rotationIndex(rotation: Int): Int = when (rotation) {
    android.view.Surface.ROTATION_90 -> 1
    android.view.Surface.ROTATION_180 -> 2
    android.view.Surface.ROTATION_270 -> 3
    else -> 0
}
