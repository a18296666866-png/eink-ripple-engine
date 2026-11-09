package moe.shizuku.manager.ripple

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Test

class RippleAuditTest {
    @Test fun stableMachineReadableLine() {
        val command = RippleMapping.command(RippleMapping.Device.IREADER,
            RippleMapping.Pattern.WIND, Surface.ROTATION_90, RippleMapping.Source.TOUCH,
            RippleMapping.Direction.NEXT, 64)
        val line = RippleAudit.line(RippleMapping.Device.IREADER,
            RippleMapping.Pattern.WIND, Surface.ROTATION_90, RippleMapping.Source.TOUCH,
            "ABS_XY_SWIPE", RippleMapping.Direction.NEXT, RippleMapping.Direction.NEXT,
            false, command, "API_ACCEPTED")
        assertEquals(true,line.contains("|base=4|code=68|status=API_ACCEPTED"))
        assertEquals(true,line.contains("|rotation=90|"))
    }

    @Test fun untrustedRawCodeIsEscaped() {
        val line = RippleAudit.line(RippleMapping.Device.BIGME,
            RippleMapping.Pattern.WIND, Surface.ROTATION_0, RippleMapping.Source.KEY,
            "RAW|status=API_ACCEPTED", RippleMapping.Direction.NEXT,
            RippleMapping.Direction.PREVIOUS, true, null, "API_EXCEPTION")
        assertEquals(true,line.contains("|raw=RAW_status_API_ACCEPTED|"))
        assertEquals(1,line.split("|status=").size-1)
    }
}
