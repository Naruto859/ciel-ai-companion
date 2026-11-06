package net.kuafuai.andee.voice

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class LiveRequestGateTest {
    @Test fun `Stop rejects a queued Talk and message but permits a new user request`() {
        val gate = LiveRequestGate()
        val worker = Executors.newSingleThreadExecutor()
        val audioBusy = CountDownLatch(1)
        val releaseAudio = CountDownLatch(1)
        val created = AtomicInteger()
        val sent = AtomicInteger()
        try {
            worker.submit { audioBusy.countDown(); releaseAudio.await(3, TimeUnit.SECONDS) }
            assertTrue(audioBusy.await(1, TimeUnit.SECONDS))
            val old = gate.token()
            val talk = worker.submit { gate.runIfCurrent(old) { created.incrementAndGet() } }
            val message = worker.submit { gate.runIfCurrent(old) { created.incrementAndGet(); sent.incrementAndGet() } }
            gate.cancel { }
            releaseAudio.countDown()
            talk.get(2, TimeUnit.SECONDS)
            message.get(2, TimeUnit.SECONDS)
            assertEquals(0, created.get())
            assertEquals(0, sent.get())
            val fresh = gate.token()
            worker.submit { gate.runIfCurrent(fresh) { created.incrementAndGet(); sent.incrementAndGet() } }.get(2, TimeUnit.SECONDS)
            assertEquals(1, created.get())
            assertEquals(1, sent.get())
        } finally {
            releaseAudio.countDown()
            worker.shutdownNow()
        }
    }
}
