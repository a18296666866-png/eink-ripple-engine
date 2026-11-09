package moe.shizuku.manager.ripple

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HanvonSpeedModelTest {
    @Test
    fun clampsValuesToSupportedRange() {
        assertEquals(30, HanvonSpeedModel.normalize(-1))
        assertEquals(300, HanvonSpeedModel.normalize(999))
        assertEquals(150, HanvonSpeedModel.normalize(150))
    }

    @Test
    fun mapsProgressRoundTrip() {
        assertEquals(0, HanvonSpeedModel.progressFor(30))
        assertEquals(27, HanvonSpeedModel.progressFor(300))
        assertEquals(100, HanvonSpeedModel.valueForProgress(HanvonSpeedModel.progressFor(100)))
        assertEquals(200, HanvonSpeedModel.valueForProgress(HanvonSpeedModel.progressFor(200)))
    }

    @Test
    fun distinguishesPresetsFromCustomValues() {
        assertTrue(HanvonSpeedModel.isPreset(50))
        assertTrue(HanvonSpeedModel.isPreset(100))
        assertTrue(HanvonSpeedModel.isPreset(200))
        assertFalse(HanvonSpeedModel.isPreset(150))
        assertEquals(200, HanvonSpeedModel.presetValue(43))
        assertEquals(null, HanvonSpeedModel.presetValue(99))
    }
}
