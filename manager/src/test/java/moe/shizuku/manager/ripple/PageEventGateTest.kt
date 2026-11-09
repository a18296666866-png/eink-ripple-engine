package moe.shizuku.manager.ripple

import org.junit.Assert.assertEquals
import org.junit.Test

class PageEventGateTest {
    @Test fun sameSourceRapidEventsArePreserved() {
        val gate = PageEventGate()
        assertEquals(true, gate.accept("KEY", true, 1000L))
        assertEquals(true, gate.accept("KEY", true, 1030L))
        assertEquals(true, gate.accept("KEY", true, 1050L))
    }
    @Test fun onlyMirroredSourceIsDeduplicated() {
        val gate = PageEventGate()
        assertEquals(true, gate.accept("TOUCH", false, 1000L))
        assertEquals(false, gate.accept("KEY", false, 1040L))
        assertEquals(true, gate.accept("KEY", true, 1060L))
        assertEquals(true, gate.accept("TOUCH", true, 1240L))
    }
    @Test fun resetRemovesState() {
        val gate = PageEventGate()
        assertEquals(true, gate.accept("KEY", false, 1000L))
        gate.reset()
        assertEquals(true, gate.accept("TOUCH", false, 1001L))
    }
}
