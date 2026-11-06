package net.kuafuai.andee.net

import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit

/** A stop invalidates work already accepted, including work still waiting in a transport queue. */
class CommandCancellation {
    private val monitor = Object()
    @Volatile private var generation = 0L

    fun generation(): Long = generation

    fun cancel() = synchronized(monitor) {
        generation++
        monitor.notifyAll()
    }

    fun check(expectedGeneration: Long) {
        checkSource()
        if (expectedGeneration != generation || Thread.currentThread().isInterrupted) {
            throw CancellationException("The user stopped this task. No further phone action was performed.")
        }
    }

    fun await(expectedGeneration: Long, millis: Long) {
        require(millis >= 0) { "wait duration must not be negative" }
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis)
        synchronized(monitor) {
            while (true) {
                check(expectedGeneration)
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0) return
                // A transport can disappear without a user Stop. Check its
                // lifetime while waiting too, without cancelling other owners.
                TimeUnit.NANOSECONDS.timedWait(monitor, minOf(remaining, SOURCE_POLL_NS))
            }
        }
    }

    companion object {
        private val activeSource = ThreadLocal<() -> Boolean>()
        private val SOURCE_POLL_NS = TimeUnit.MILLISECONDS.toNanos(100)

        /** Bind synchronous work and every later checkpoint to its exact controller connection. */
        fun <T> withSource(isActive: () -> Boolean, block: () -> T): T {
            val previous = activeSource.get()
            activeSource.set { (previous?.invoke() != false) && isActive() }
            try {
                checkSource()
                return block()
            } finally {
                if (previous == null) activeSource.remove() else activeSource.set(previous)
            }
        }

        private fun checkSource() {
            if (activeSource.get()?.invoke() == false) {
                throw CancellationException("The controller disconnected or stopped this task. No further phone action was performed.")
            }
        }
    }
}
