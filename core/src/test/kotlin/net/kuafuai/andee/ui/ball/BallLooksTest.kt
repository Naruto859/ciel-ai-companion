package net.kuafuai.andee.ui.ball

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BallLooksTest {
    @After fun restoreOriginal() {
        BallLooks.requestByName("andee")
        BallLooks.latch()
    }

    @Test fun `a removed saved companion resets both renderer and persona to original`() {
        BallLooks.requestByName("nova")
        BallLooks.latch()
        for (removed in listOf("cream", "bajie", "rabbit", "bear", "imp", "unknown")) {
            BallLooks.requestByName("moss")
            BallLooks.latch()
            BallLooks.requestByName(removed)
            BallLooks.latch()
            assertSame(ANDEE, BallLooks.active)
            assertSame(ANDEE, BallLooks.byName(removed))
        }
    }

    @Test fun `null saved selection restores original even after a preview was selected`() {
        BallLooks.requestByName("moss")
        BallLooks.requestByName(null)
        assertSame(ANDEE, BallLooks.requestedLook)
    }

    @Test fun `two independent animation states retain their own geometry across global swaps`() {
        val original = EmotionState { ANDEE }
        val preview = EmotionState { MOSS }
        for (look in BallLooks.ALL) {
            BallLooks.requestByName(look.name)
            BallLooks.latch()
            original.tick(0.016f, 0.2f, Mood.CALM, false, false)
            preview.tick(0.016f, 0.2f, Mood.CALM, false, false)
            assertEquals(ANDEE.eyeX * 2f, original.eyeRX - original.eyeLX, 0.00001f)
            assertEquals(MOSS.eyeX * 2f, preview.eyeRX - preview.eyeLX, 0.00001f)
        }
    }

    @Test fun `each new companion completes every supported emotion without invalid geometry`() {
        for (look in listOf(MOSS, NOVA)) {
            assertTrue(look.gestures)
            val state = EmotionState { look }
            var time = 0f
            for (mood in Mood.entries) {
                repeat(30) {
                    time += 0.016f
                    state.tick(0.016f, time, mood, mood == Mood.LISTENING, mood == Mood.SEARCHING)
                    assertTrue("${look.name}: $mood", listOf(state.eyeLX, state.eyeLY, state.eyeRX,
                        state.eyeRY, state.mouthY, state.scaleX, state.scaleY, state.hornSway, state.hornLean).all { it.isFinite() })
                }
            }
        }
    }
}
