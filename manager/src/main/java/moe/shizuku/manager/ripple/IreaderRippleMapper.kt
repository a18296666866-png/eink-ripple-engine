package moe.shizuku.manager.ripple

import android.view.Surface

/** iReader EPDC effect base codes; touch and key share the same effect mapping. */
internal object IreaderRippleMapper : VendorRippleMapper {
    override val commandType = RippleMapping.CommandType.IREADER_EFFECT
    private val wind = arrayOf(DirectionCodes(2,1), DirectionCodes(3,4),
        DirectionCodes(2,1), DirectionCodes(3,4))
    private val curtain = arrayOf(DirectionCodes(4,3), DirectionCodes(1,2),
        DirectionCodes(3,4), DirectionCodes(2,1))

    override fun baseCode(pattern: RippleMapping.Pattern, rotation: Int,
        source: RippleMapping.Source, direction: RippleMapping.Direction): Int =
        (if (pattern == RippleMapping.Pattern.WIND) wind else curtain)
            .get(rotationIndex(rotation)).forDirection(direction)

    override fun keyDirection(rotation: Int, code: String): RippleMapping.Direction? {
        val reverse = rotation == Surface.ROTATION_180 || rotation == Surface.ROTATION_270
        return when (code) {
            "KEY_VOLUMEUP" -> if (reverse) RippleMapping.Direction.NEXT else RippleMapping.Direction.PREVIOUS
            "KEY_VOLUMEDOWN" -> if (reverse) RippleMapping.Direction.PREVIOUS else RippleMapping.Direction.NEXT
            else -> null
        }
    }
}
