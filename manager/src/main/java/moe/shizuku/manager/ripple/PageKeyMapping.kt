package moe.shizuku.manager.ripple

/** Physical page-key meanings from the central mapping table. */
object PageKeyMapping {

    fun isPrevious(
        code: String,
        rotation: Int,
        useVolumeDefaults: Boolean = false,
        volumeKeysFollowRotation: Boolean = true
    ): Boolean {
        return keyDirection(code, rotation, useVolumeDefaults, volumeKeysFollowRotation) ==
            RippleMapping.Direction.PREVIOUS
    }

    fun isNext(
        code: String,
        rotation: Int,
        useVolumeDefaults: Boolean = false,
        volumeKeysFollowRotation: Boolean = true
    ): Boolean {
        return keyDirection(code, rotation, useVolumeDefaults, volumeKeysFollowRotation) ==
            RippleMapping.Direction.NEXT
    }

    private fun keyDirection(
        code: String,
        rotation: Int,
        useVolumeDefaults: Boolean,
        volumeKeysFollowRotation: Boolean
    ): RippleMapping.Direction? {
        val device = if (!useVolumeDefaults) {
            RippleMapping.Device.HANVON
        } else if (volumeKeysFollowRotation) {
            RippleMapping.Device.IREADER
        } else {
            RippleMapping.Device.BIGME
        }
        return RippleMapping.keyDirection(device, rotation, code)
    }

}
