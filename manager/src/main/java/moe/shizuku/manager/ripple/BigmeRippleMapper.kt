package moe.shizuku.manager.ripple

/** Bigme display policy manager: distinct touch and hardware key tables. */
internal object BigmeRippleMapper : VendorRippleMapper {
    override val commandType = RippleMapping.CommandType.BIGME_MODE
    private val windTouch = arrayOf(DirectionCodes(1,2), DirectionCodes(4,3),
        DirectionCodes(1,2), DirectionCodes(3,4))
    private val windKey = arrayOf(DirectionCodes(1,2), DirectionCodes(3,4),
        DirectionCodes(1,2), DirectionCodes(4,3))
    private val curtainTouch = arrayOf(DirectionCodes(4,3), DirectionCodes(2,1),
        DirectionCodes(4,3), DirectionCodes(2,1))
    private val curtainKey = arrayOf(DirectionCodes(4,3), DirectionCodes(1,2),
        DirectionCodes(4,3), DirectionCodes(2,1))

    override fun baseCode(pattern: RippleMapping.Pattern, rotation: Int,
        source: RippleMapping.Source, direction: RippleMapping.Direction): Int {
        val entries = when (pattern) {
            RippleMapping.Pattern.WIND ->
                if (source == RippleMapping.Source.TOUCH) windTouch else windKey
            RippleMapping.Pattern.CURTAIN ->
                if (source == RippleMapping.Source.TOUCH) curtainTouch else curtainKey
        }
        return entries[rotationIndex(rotation)].forDirection(direction)
    }

    override fun keyDirection(rotation: Int, code: String): RippleMapping.Direction? =
        when (code) {
            "KEY_VOLUMEUP" -> RippleMapping.Direction.PREVIOUS
            "KEY_VOLUMEDOWN" -> RippleMapping.Direction.NEXT
            else -> null
        }
}
