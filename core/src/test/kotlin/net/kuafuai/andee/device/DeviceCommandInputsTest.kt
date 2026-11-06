package net.kuafuai.andee.device

import org.junit.Assert.*
import org.junit.Test

class DeviceCommandInputsTest {
    private fun rejected(block: () -> Unit) {
        try {
            block()
            fail("invalid phone action was accepted")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun `invalid alarm arguments never become a clock intent`() {
        DeviceCommandInputs.alarm(0, 0)
        DeviceCommandInputs.alarm(23, 59)
        for ((hour, minute) in listOf(-1 to -1, 24 to 0, 12 to 60, 12 to -1)) {
            rejected { DeviceCommandInputs.alarm(hour, minute) }
        }
    }

    @Test fun `calendar event times must describe a real forward interval`() {
        val begin = 1_800_000_000_000L
        assertEquals(begin + 3_600_000L, DeviceCommandInputs.calendarEnd(begin, null))
        assertEquals(begin + 1, DeviceCommandInputs.calendarEnd(begin, begin + 1))
        rejected { DeviceCommandInputs.calendarEnd(0, null) }
        rejected { DeviceCommandInputs.calendarEnd(begin, begin) }
        rejected { DeviceCommandInputs.calendarEnd(begin, begin - 1) }
        try {
            DeviceCommandInputs.calendarEnd(Long.MAX_VALUE, null)
            fail("overflow could create an event before its start")
        } catch (_: ArithmeticException) { }
    }

    @Test fun `empty SMS drafts cannot be submitted but Unicode multipart drafts are valid`() {
        rejected { DeviceCommandInputs.sms("", "Hello") }
        rejected { DeviceCommandInputs.sms("+123456789", "  ") }
        rejected { DeviceCommandInputs.sms("+123\n+456", "Hello") }
        DeviceCommandInputs.sms("+123456789", "नमस्ते 🌻 ".repeat(30))
    }
}
