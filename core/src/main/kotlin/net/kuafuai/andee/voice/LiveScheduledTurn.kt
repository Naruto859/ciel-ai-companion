package net.kuafuai.andee.voice

import net.kuafuai.andee.brain.WakeText
import net.kuafuai.andee.config.Todo

/** The original alarm's trust and Stop token travel together through every queue. */
data class LiveScheduledTurn(val text: String, val untrusted: Boolean, val stopToken: Long) {
    companion object {
        fun from(todo: Todo, stopToken: Long) = LiveScheduledTurn(
            WakeText.due(todo.id, todo.what, todo.untrusted, repeats = todo.mode == "cron"),
            todo.untrusted,
            stopToken,
        )
    }
}

data class LiveScheduledOutcome(val status: Status, val reason: String? = null) {
    enum class Status { RESPONDED, CANCELLED, FAILED, BUSY }
}
