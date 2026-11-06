package net.kuafuai.andee.voice

import java.util.concurrent.atomic.AtomicLong

/** Serializes session publication with Stop and rejects work queued before that Stop. */
class LiveRequestGate {
    private val epoch = AtomicLong()
    private val lock = Any()

    fun token(): Long = epoch.get()
    fun isCurrent(token: Long): Boolean = token == epoch.get()

    fun runIfCurrent(token: Long, block: () -> Unit): Boolean = synchronized(lock) {
        if (!isCurrent(token)) return false
        block()
        true
    }

    fun cancel(block: () -> Unit) {
        // Invalidate queued work even while waiting for an in-progress publication to finish.
        epoch.incrementAndGet()
        synchronized(lock) { block() }
    }
}
