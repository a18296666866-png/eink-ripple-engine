package moe.shizuku.manager.ripple

import android.view.Surface

/** Converts fixed-panel getevent coordinates into current logical display coordinates. */
class TouchCoordinateMapper(
    rawXMax: Int,
    rawYMax: Int,
    private val rawAxesSwapped: Boolean
) {
    private val nativeWidth = (if (rawAxesSwapped) rawYMax else rawXMax).coerceAtLeast(1)
    private val nativeHeight = (if (rawAxesSwapped) rawXMax else rawYMax).coerceAtLeast(1)

    fun logicalWidth(rotation: Int): Int {
        return if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            nativeHeight
        } else {
            nativeWidth
        }
    }

    fun toLogical(rawX: Int, rawY: Int, rotation: Int): Point {
        val nativeX = if (rawAxesSwapped) rawY else rawX
        val nativeY = if (rawAxesSwapped) rawX else rawY
        return when (rotation) {
            Surface.ROTATION_90 -> Point(nativeHeight - nativeY, nativeX)
            Surface.ROTATION_180 -> Point(nativeWidth - nativeX, nativeHeight - nativeY)
            Surface.ROTATION_270 -> Point(nativeY, nativeWidth - nativeX)
            else -> Point(nativeX, nativeY)
        }
    }

    data class Point(val x: Int, val y: Int)
}
