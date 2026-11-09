package moe.shizuku.manager.ripple

import org.junit.Assert.assertEquals
import org.junit.Test

class VendorRippleMapperTest {
    @Test fun all96VendorMappings() {
        val tables = listOf(
            listOf(2,1,2,1, 3,4,3,4, 2,1,2,1, 3,4,3,4),
            listOf(4,3,4,3, 1,2,1,2, 3,4,3,4, 2,1,2,1),
            listOf(9,7,7,9, 8,6,6,8, 7,9,9,7, 6,8,8,6),
            listOf(6,8,8,6, 9,7,7,9, 8,6,6,8, 7,9,9,7),
            listOf(1,2,1,2, 4,3,3,4, 1,2,1,2, 3,4,4,3),
            listOf(4,3,4,3, 2,1,1,2, 4,3,4,3, 2,1,2,1)
        )
        val devices = RippleMapping.Device.values().take(3)
        var checked = 0
        devices.forEachIndexed { d, device ->
            RippleMapping.Pattern.values().forEachIndexed { mode, pattern ->
                val golden = tables[d*2+mode]
                (0..3).forEach { rot ->
                    RippleMapping.Source.values().forEachIndexed { s, source ->
                        RippleMapping.Direction.values().forEachIndexed { dir, direction ->
                            val cmd = RippleMapping.command(device,pattern,rot,source,direction,0)
                            assertEquals("$device/$pattern/$rot/$source/$direction",
                                golden[rot*4+s*2+dir],cmd?.code)
                            checked++
                        }
                    }
                }
            }
        }
        assertEquals(96,checked)
    }
    @Test fun invalidIreaderSpeedFallsBackToMedium() {
        val c=RippleMapping.command(RippleMapping.Device.IREADER,
            RippleMapping.Pattern.WIND,0,RippleMapping.Source.TOUCH,
            RippleMapping.Direction.NEXT,123)
        assertEquals(65,c?.code)
    }
}
