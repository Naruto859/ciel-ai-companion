package net.kuafuai.andee.voice

import net.kuafuai.andee.config.Todo
import org.junit.Assert.*
import org.junit.Test

class LiveScheduledTurnTest {
    private fun todo(untrusted: Boolean, mode: String) = Todo(
        id = "reminder-7", what = "Check the delivery", mode = mode, at = 1L,
        cronExpr = "", nextRunAt = 0L, lastRunAt = 2L, lastStatus = "fired",
        lastError = "", status = "active", createdAt = 0L, untrusted = untrusted,
    )

    @Test fun `Live alarm handoff retains outside material trust and repeating semantics`() {
        val request = LiveScheduledTurn.from(todo(untrusted = true, mode = "cron"), 9L)
        assertTrue(request.untrusted)
        assertEquals(9L, request.stopToken)
        assertTrue(request.text.contains("notification somebody else sent"))
        assertTrue(request.text.contains("do NOT close it"))
        val ownerRequest = LiveScheduledTurn.from(todo(untrusted = false, mode = "once"), 10L)
        assertFalse(ownerRequest.untrusted)
        assertTrue(ownerRequest.text.contains("when it is done"))
    }

    @Test fun `an alarm waiting for quiet cannot start after Stop`() {
        val gate = LiveRequestGate()
        val alarm = LiveScheduledTurn.from(todo(untrusted = true, mode = "once"), gate.token())
        gate.cancel { }
        var dispatched = false
        assertFalse(gate.runIfCurrent(alarm.stopToken) { dispatched = true })
        assertFalse(dispatched)
        val next = LiveScheduledTurn.from(todo(untrusted = true, mode = "once"), gate.token())
        assertTrue(gate.runIfCurrent(next.stopToken) { dispatched = true; assertTrue(next.untrusted) })
        assertTrue(dispatched)
    }

    @Test fun `Live exposes working scheduling but excludes legacy focus and background model tools`() {
        assertTrue(LiveToolAvailability.supports("note.todo_add"))
        assertTrue(LiveToolAvailability.supports("device.open_app"))
        assertFalse(LiveToolAvailability.supports("ui.ask"))
        assertFalse(LiveToolAvailability.supports("ui.alert"))
        assertFalse(LiveToolAvailability.supports("tts.speak"))
        assertFalse(LiveToolAvailability.supports("note.notify_rule"))
        assertFalse(LiveToolAvailability.supports("note.fold"))
    }
}
