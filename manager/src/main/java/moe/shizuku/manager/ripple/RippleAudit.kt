package moe.shizuku.manager.ripple

/** Machine-readable API invocation audit, never proof of a physically visible waveform. */
object RippleAudit {
    private val unsafe = Regex("[^A-Za-z0-9_.-]")
    fun line(device: RippleMapping.Device, pattern: RippleMapping.Pattern, rotation: Int,
        source: RippleMapping.Source, rawCode: String,
        logical: RippleMapping.Direction, effective: RippleMapping.Direction,
        reverse: Boolean, command: RippleMapping.Command?, status: String): String {
        val degrees = when (RippleMapping.normalizeRotation(rotation)) {
            android.view.Surface.ROTATION_90 -> 90
            android.view.Surface.ROTATION_180 -> 180
            android.view.Surface.ROTATION_270 -> 270
            else -> 0
        }
        val code = command?.code ?: -1
        val base = if (device == RippleMapping.Device.IREADER && code >= 0) code and 0x07 else code
        return listOf("RIPPLE_AUDIT_V1", "device=${device.name}",
            "pattern=${pattern.name}", "rotation=$degrees", "source=${source.name}",
            "raw=${rawCode.replace(unsafe, "_")}", "logical=${logical.name}",
            "effective=${effective.name}", "reverse=$reverse", "base=$base",
            "code=$code", "status=${status.replace(unsafe, "_")}")
            .joinToString("|")
    }
}
