package moe.shizuku.manager.ripple

import org.junit.Assert.assertEquals
import org.junit.Test

class InputDeviceNameParserTest {
    @Test fun readsAndroidInputNameFormats() {
        assertEquals("fts_ts",
            InputDeviceNameParser.name("add device 1: /dev/input/event2\n name: \"fts_ts\"\n"))
        assertEquals("pen_touch",
            InputDeviceNameParser.name("I: Bus=0019\nN: Name=\"pen_touch\"\nH: Handlers=event2"))
        assertEquals("",
            InputDeviceNameParser.name("add device 3: /dev/input/event8\n  events:\n"))
    }
}
