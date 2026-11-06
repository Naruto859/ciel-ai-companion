package net.kuafuai.andee.net

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class CommandCancellationTest {
    @Test fun `stop rejects a command that was accepted before it reached the worker`() {
        val gate = CommandCancellation()
        val accepted = gate.generation()
        gate.cancel()
        try {
            gate.check(accepted)
            fail("a stale command would operate the phone after Stop")
        } catch (_: CancellationException) { }
        gate.check(gate.generation()) // A new user request is still allowed.
    }

    @Test fun `stop wakes a long screen wait without taking its later screenshot`() {
        val gate = CommandCancellation()
        val accepted = gate.generation()
        val entered = CountDownLatch(1)
        val captured = AtomicBoolean(false)
        val worker = Executors.newSingleThreadExecutor()
        try {
            val waiting = worker.submit<Boolean> {
                entered.countDown()
                try {
                    gate.await(accepted, 20_000)
                    captured.set(true)
                    false
                } catch (_: CancellationException) {
                    true
                }
            }
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            gate.cancel()
            assertTrue(waiting.get(1, TimeUnit.SECONDS))
            assertFalse(captured.get())
        } finally {
            worker.shutdownNow()
        }
    }

    @Test fun `a cancelled worker cannot begin a fresh phone action`() {
        val gate = CommandCancellation()
        val worker = Executors.newSingleThreadExecutor()
        try {
            assertTrue(worker.submit<Boolean> {
                Thread.currentThread().interrupt()
                try {
                    gate.check(gate.generation())
                    false
                } catch (_: CancellationException) {
                    true
                } finally {
                    Thread.interrupted()
                }
            }.get(1, TimeUnit.SECONDS))
        } finally {
            worker.shutdownNow()
        }
    }

    @Test fun `a controller replaced after the first swipe cannot perform later swipes`() {
        val gate = CommandCancellation()
        val generation = gate.generation()
        val original = Any()
        val replacement = Any()
        val connected = AtomicReference(original)
        val firstSwipe = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val swipes = AtomicInteger()
        val worker = Executors.newSingleThreadExecutor()
        try {
            val stopped = worker.submit<Boolean> {
                try {
                    CommandCancellation.withSource({ connected.get() === original }) {
                        repeat(3) {
                            gate.check(generation)
                            swipes.incrementAndGet()
                            if (it == 0) {
                                firstSwipe.countDown()
                                check(resume.await(2, TimeUnit.SECONDS))
                            }
                        }
                    }
                    false
                } catch (_: CancellationException) {
                    true
                }
            }
            assertTrue(firstSwipe.await(2, TimeUnit.SECONDS))
            connected.set(replacement)
            resume.countDown()
            assertTrue(stopped.get(2, TimeUnit.SECONDS))
            assertEquals(1, swipes.get())
            // Losing one owner must not invalidate a different owner's work.
            CommandCancellation.withSource({ connected.get() === replacement }) { gate.check(generation) }
            assertTrue(worker.submit<Boolean> { gate.check(generation); true }.get(2, TimeUnit.SECONDS))
        } finally {
            resume.countDown()
            worker.shutdownNow()
        }
    }

    @Test fun `disconnect during a screen wait releases it without taking the later screenshot`() {
        val gate = CommandCancellation()
        val generation = gate.generation()
        val connected = AtomicBoolean(true)
        val inWait = AtomicBoolean(false)
        val firstCheck = AtomicBoolean(true)
        val checked = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val captured = AtomicBoolean(false)
        val worker = Executors.newSingleThreadExecutor()
        try {
            val stopped = worker.submit<Boolean> {
                try {
                    CommandCancellation.withSource({
                        val active = connected.get()
                        if (inWait.get() && firstCheck.compareAndSet(true, false)) {
                            checked.countDown()
                            check(resume.await(2, TimeUnit.SECONDS))
                        }
                        active
                    }) {
                        inWait.set(true)
                        gate.await(generation, 20_000)
                        captured.set(true)
                    }
                    false
                } catch (_: CancellationException) {
                    true
                }
            }
            assertTrue(checked.await(2, TimeUnit.SECONDS))
            connected.set(false)
            resume.countDown()
            assertTrue(stopped.get(2, TimeUnit.SECONDS))
            assertFalse(captured.get())
        } finally {
            resume.countDown()
            worker.shutdownNow()
        }
    }
}
