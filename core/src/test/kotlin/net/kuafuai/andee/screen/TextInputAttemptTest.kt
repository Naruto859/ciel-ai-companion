package net.kuafuai.andee.screen

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class TextInputAttemptTest {
    @Test fun `readback loss after commit must not type again through the fallback keyboard`() {
        var writes = 0
        var fallback = false
        try {
            TextInputAttempt.replace("hello", { true }, { writes++ }, { null })
                ?: run { fallback = true }
            fail("an unverified write must be reported")
        } catch (_: IllegalStateException) { }
        assertEquals(1, writes)
        assertFalse(fallback)
    }

    @Test fun `an unavailable editor before any write still permits fallback`() {
        var writes = 0
        assertNull(TextInputAttempt.replace("hello", { false }, { writes++ }, { "hello" }))
        assertEquals(0, writes)
    }

    @Test fun `a stop between clearing and committing prevents the new text`() {
        var checkpoints = 0
        var committed = false
        try {
            TextInputAttempt.replace("hello", { true }, { committed = true }, { "hello" }) {
                if (++checkpoints == 2) throw CancellationException("stopped")
            }
            fail("stopped input continued")
        } catch (_: CancellationException) { }
        assertFalse(committed)
    }

    @Test fun `an uncertain editor exception cannot silently select another input channel`() {
        var fallback = false
        try {
            TextInputAttempt.replace("hello", { true }, { throw IllegalStateException("editor died") }, { null })
                ?: run { fallback = true }
            fail("the editor exception was hidden")
        } catch (_: IllegalStateException) { }
        assertFalse(fallback)
    }
}
