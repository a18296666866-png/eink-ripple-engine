package moe.shizuku.manager.ripple

import android.view.Surface

/** Hanvon 'eink' service. Physical keys use an inverted waveform-code mapping. */
internal object HanvonRippleMapper : VendorRippleMapper {
    override val commandType = RippleMapping.CommandType.HANVON_DIRECTION
    private val wind = arrayOf(DirectionCodes(9,7), DirectionCodes(8,6),
        DirectionCodes(7,9), DirectionCodes(6,8))
    private val curtain = arrayOf(DirectionCodes(6,8), DirectionCodes(9,7),
        DirectionCodes(8,6), DirectionCodes(7,9))

    override fun baseCode(pattern: RippleMapping.Pattern, rotation: Int,
        source: RippleMapping.Source, direction: RippleMapping.Direction): Int {
        val actual = if (source == RippleMapping.Source.KEY) {
            if (direction == RippleMapping.Direction.PREVIOUS) RippleMapping.Direction.NEXT
            else RippleMapping.Direction.PREVIOUS
        } else direction
        return (if (pattern == RippleMapping.Pattern.WIND) wind else curtain)
            .get(rotationIndex(rotation)).forDirection(actual)
    }

    override fun keyDirection(rotation: Int, code: String): RippleMapping.Direction? =
        if (rotation == Surface.ROTATION_180) when (code) {
            "0257" -> RippleMapping.Direction.PREVIOUS
            "0256" -> RippleMapping.Direction.NEXT
            else -> null
        } else when (code) {
            "0254" -> RippleMapping.Direction.PREVIOUS
            "0255" -> RippleMapping.Direction.NEXT
            else -> null
        }
}
