package moe.shizuku.manager.ripple

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Test

class TouchCoordinateMapperTest {
    private val mapper = TouchCoordinateMapper(1000, 2000, false)

    @Test
    fun convertsAllDisplayRotations() {
        assertEquals(TouchCoordinateMapper.Point(100, 200), mapper.toLogical(100, 200, Surface.ROTATION_0))
        assertEquals(TouchCoordinateMapper.Point(1800, 100), mapper.toLogical(100, 200, Surface.ROTATION_90))
        assertEquals(TouchCoordinateMapper.Point(900, 1800), mapper.toLogical(100, 200, Surface.ROTATION_180))
        assertEquals(TouchCoordinateMapper.Point(200, 900), mapper.toLogical(100, 200, Surface.ROTATION_270))
    }

    @Test
    fun handlesSwappedRawAxes() {
        val swapped = TouchCoordinateMapper(2000, 1000, true)
        assertEquals(TouchCoordinateMapper.Point(100, 200), swapped.toLogical(200, 100, Surface.ROTATION_0))
        assertEquals(2000, swapped.logicalWidth(Surface.ROTATION_90))
    }
}
