package moe.shizuku.manager.ripple

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Test

class RippleMappingTest {
    private val touch = RippleMapping.Source.TOUCH
    private val key = RippleMapping.Source.KEY
    private val next = RippleMapping.Direction.NEXT
    private val previous = RippleMapping.Direction.PREVIOUS

    private fun assertCodes(
        device: RippleMapping.Device,
        pattern: RippleMapping.Pattern,
        rotation: Int,
        touchPrevious: Int,
        touchNext: Int,
        keyPrevious: Int = touchPrevious,
        keyNext: Int = touchNext
    ) {
        assertEquals(touchPrevious, RippleMapping.command(device, pattern, rotation, touch, previous, speed = 0)?.code)
        assertEquals(touchNext, RippleMapping.command(device, pattern, rotation, touch, next, speed = 0)?.code)
        assertEquals(keyPrevious, RippleMapping.command(device, pattern, rotation, key, previous, speed = 0)?.code)
        assertEquals(keyNext, RippleMapping.command(device, pattern, rotation, key, next, speed = 0)?.code)
    }

    @Test
    fun bigmeWindAndCurtainMatchTheMappingDocument() {
        assertCodes(RippleMapping.Device.BIGME, RippleMapping.Pattern.WIND, Surface.ROTATION_0, 1, 2)
        assertCodes(RippleMapping.Device.BIGME, RippleMapping.Pattern.WIND, Surface.ROTATION_90, 4, 3, 3, 4)
        assertCodes(RippleMapping.Device.BIGME, RippleMapping.Pattern.WIND, Surface.ROTATION_180, 1, 2)
        assertCodes(RippleMapping.Device.BIGME, RippleMapping.Pattern.WIND, Surface.ROTATION_270, 3, 4, 4, 3)
        assertCodes(RippleMapping.Device.BIGME, RippleMapping.Pattern.CURTAIN, Surface.ROTATION_0, 4, 3)
        assertCodes(RippleMapping.Device.BIGME, RippleMapping.Pattern.CURTAIN, Surface.ROTATION_90, 2, 1, 1, 2)
        assertCodes(RippleMapping.Device.BIGME, RippleMapping.Pattern.CURTAIN, Surface.ROTATION_180, 4, 3)
        assertCodes(RippleMapping.Device.BIGME, RippleMapping.Pattern.CURTAIN, Surface.ROTATION_270, 2, 1, 2, 1)
    }

    @Test
    fun hanvonKeysAreTheInverseOfTouchDirectionCodes() {
        assertCodes(RippleMapping.Device.HANVON, RippleMapping.Pattern.WIND, Surface.ROTATION_0, 9, 7, 7, 9)
        assertCodes(RippleMapping.Device.HANVON, RippleMapping.Pattern.WIND, Surface.ROTATION_90, 8, 6, 6, 8)
        assertCodes(RippleMapping.Device.HANVON, RippleMapping.Pattern.WIND, Surface.ROTATION_180, 7, 9, 9, 7)
        assertCodes(RippleMapping.Device.HANVON, RippleMapping.Pattern.WIND, Surface.ROTATION_270, 6, 8, 8, 6)
        assertCodes(RippleMapping.Device.HANVON, RippleMapping.Pattern.CURTAIN, Surface.ROTATION_0, 6, 8, 8, 6)
        assertCodes(RippleMapping.Device.HANVON, RippleMapping.Pattern.CURTAIN, Surface.ROTATION_90, 9, 7, 7, 9)
        assertCodes(RippleMapping.Device.HANVON, RippleMapping.Pattern.CURTAIN, Surface.ROTATION_180, 8, 6, 6, 8)
        assertCodes(RippleMapping.Device.HANVON, RippleMapping.Pattern.CURTAIN, Surface.ROTATION_270, 7, 9, 9, 7)
    }

    @Test
    fun keySemanticsRemainStableForBigmeAndRotateForIreader() {
        for (rotation in listOf(
            Surface.ROTATION_0,
            Surface.ROTATION_90,
            Surface.ROTATION_180,
            Surface.ROTATION_270
        )) {
            assertEquals(RippleMapping.Direction.PREVIOUS, RippleMapping.keyDirection(RippleMapping.Device.BIGME, rotation, "KEY_VOLUMEUP"))
            assertEquals(RippleMapping.Direction.NEXT, RippleMapping.keyDirection(RippleMapping.Device.BIGME, rotation, "KEY_VOLUMEDOWN"))
        }
        assertEquals(RippleMapping.Direction.PREVIOUS, RippleMapping.keyDirection(RippleMapping.Device.IREADER, Surface.ROTATION_0, "KEY_VOLUMEUP"))
        assertEquals(RippleMapping.Direction.PREVIOUS, RippleMapping.keyDirection(RippleMapping.Device.IREADER, Surface.ROTATION_180, "KEY_VOLUMEDOWN"))
    }
}
