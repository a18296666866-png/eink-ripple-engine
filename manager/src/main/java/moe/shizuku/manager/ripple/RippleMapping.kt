package moe.shizuku.manager.ripple

import android.view.Surface

/** The only source of truth for the three vendor animation mapping tables. */
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

    /** Resolve the final vendor command. Reverse animation is applied before this lookup. */
    fun command(
        device: Device,
        pattern: Pattern,
        rotation: Int,
        source: Source,
        direction: Direction,
        speed: Int = 64
    ): Command? {
        val r = normalizeRotation(rotation)
        return when (device) {
            Device.IREADER -> Command(CommandType.IREADER_EFFECT, iReaderEffect(r, pattern, direction, speed))
            Device.HANVON -> Command(CommandType.HANVON_DIRECTION, hanvonDirection(r, pattern, source, direction))
            Device.BIGME -> Command(CommandType.BIGME_MODE, bigmeMode(r, pattern, source, direction))
            Device.UNKNOWN -> null
        }
    }

    /** Physical key semantics from the mapping document. */
    fun keyDirection(device: Device, rotation: Int, code: String): Direction? {
        val r = normalizeRotation(rotation)
        return when (device) {
            Device.HANVON -> when (r) {
                Surface.ROTATION_180 -> when (code) {
                    "0257" -> Direction.PREVIOUS
                    "0256" -> Direction.NEXT
                    else -> null
                }
                else -> when (code) {
                    "0254" -> Direction.PREVIOUS
                    "0255" -> Direction.NEXT
                    else -> null
                }
            }
            Device.IREADER -> when (r) {
                Surface.ROTATION_180, Surface.ROTATION_270 -> when (code) {
                    "KEY_VOLUMEDOWN" -> Direction.PREVIOUS
                    "KEY_VOLUMEUP" -> Direction.NEXT
                    else -> null
                }
                else -> when (code) {
                    "KEY_VOLUMEUP" -> Direction.PREVIOUS
                    "KEY_VOLUMEDOWN" -> Direction.NEXT
                    else -> null
                }
            }
            Device.BIGME -> when (code) {
                "KEY_VOLUMEUP" -> Direction.PREVIOUS
                "KEY_VOLUMEDOWN" -> Direction.NEXT
                else -> null
            }
            Device.UNKNOWN -> null
        }
    }

    private fun iReaderEffect(
        rotation: Int,
        pattern: Pattern,
        direction: Direction,
        speed: Int
    ): Int {
        val code = when (pattern) {
            Pattern.WIND -> when (rotation) {
                Surface.ROTATION_90, Surface.ROTATION_270 -> if (direction == Direction.NEXT) 4 else 3
                else -> if (direction == Direction.NEXT) 1 else 2
            }
            Pattern.CURTAIN -> when (rotation) {
                Surface.ROTATION_90 -> if (direction == Direction.NEXT) 2 else 1
                Surface.ROTATION_180 -> if (direction == Direction.NEXT) 4 else 3
                Surface.ROTATION_270 -> if (direction == Direction.NEXT) 1 else 2
                else -> if (direction == Direction.NEXT) 3 else 4
            }
        }
        return code or speed
    }

    private fun hanvonDirection(
        rotation: Int,
        pattern: Pattern,
        source: Source,
        direction: Direction
    ): Int {
        // The key table is intentionally the inverse of the touch table.
        val effectiveDirection = if (source == Source.KEY) {
            if (direction == Direction.NEXT) Direction.PREVIOUS else Direction.NEXT
        } else direction
        return when (pattern) {
            Pattern.WIND -> when (rotation) {
                Surface.ROTATION_90 -> if (effectiveDirection == Direction.NEXT) 6 else 8
                Surface.ROTATION_180 -> if (effectiveDirection == Direction.NEXT) 9 else 7
                Surface.ROTATION_270 -> if (effectiveDirection == Direction.NEXT) 8 else 6
                else -> if (effectiveDirection == Direction.NEXT) 7 else 9
            }
            Pattern.CURTAIN -> when (rotation) {
                Surface.ROTATION_90 -> if (effectiveDirection == Direction.NEXT) 7 else 9
                Surface.ROTATION_180 -> if (effectiveDirection == Direction.NEXT) 6 else 8
                Surface.ROTATION_270 -> if (effectiveDirection == Direction.NEXT) 9 else 7
                else -> if (effectiveDirection == Direction.NEXT) 8 else 6
            }
        }
    }

    private fun bigmeMode(
        rotation: Int,
        pattern: Pattern,
        source: Source,
        direction: Direction
    ): Int {
        val isKey = source == Source.KEY
        val isNext = direction == Direction.NEXT
        val effectiveNext = when (pattern) {
            Pattern.WIND -> when (rotation) {
                Surface.ROTATION_90 -> isKey == isNext
                Surface.ROTATION_180 -> !isNext
                Surface.ROTATION_270 -> isKey != isNext
                else -> isNext
            }
            Pattern.CURTAIN -> when (rotation) {
                Surface.ROTATION_90 -> isKey == isNext
                Surface.ROTATION_180 -> !isNext
                // Both sources use the same direct page semantics at 270°.
                Surface.ROTATION_270 -> isNext
                else -> isNext
            }
        }
        return when (rotation) {
            Surface.ROTATION_90 -> if (pattern == Pattern.CURTAIN) {
                if (effectiveNext) 2 else 1
            } else {
                if (effectiveNext) 4 else 3
            }
            Surface.ROTATION_180 -> if (pattern == Pattern.CURTAIN) {
                if (effectiveNext) 4 else 3
            } else {
                if (effectiveNext) 1 else 2
            }
            Surface.ROTATION_270 -> if (pattern == Pattern.CURTAIN) {
                if (effectiveNext) 1 else 2
            } else {
                if (effectiveNext) 4 else 3
            }
            else -> if (pattern == Pattern.CURTAIN) {
                if (effectiveNext) 3 else 4
            } else {
                if (effectiveNext) 2 else 1
            }
        }
    }
}
