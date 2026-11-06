package net.kuafuai.andee.voice

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class GeminiLiveSessionTest {
    private class Socket(val req: Request, val callback: WebSocketListener) : WebSocket {
        val sent = LinkedBlockingQueue<JSONObject>()
        var cancelled = false
        var queued = 0L
        override fun request() = req
        override fun queueSize() = queued
        override fun send(text: String): Boolean { sent.offer(JSONObject(text)); return !cancelled }
        override fun send(bytes: ByteString) = false
        override fun close(code: Int, reason: String?) = true
        override fun cancel() { cancelled = true }
        fun connect() {
            callback.onOpen(this, Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(101).message("Switching Protocols").build())
            assertTrue(sent.poll(2, TimeUnit.SECONDS)!!.has("setup"))
            receive("""{"setupComplete":{}}""")
        }
        fun receive(json: String) = callback.onMessage(this, json)
        fun nextResponse(): JSONObject = requireNotNull(sent.poll(3, TimeUnit.SECONDS)) { "No response sent" }
    }

    private val declaration = JSONArray("""[{"name":"phone_action","parameters":{"type":"object","properties":{"step":{"type":"integer"}}}}]""")

    @Test fun `one server packet delivers audio input output and completion together`() {
        lateinit var socket: Socket
        val events = ArrayList<String>()
        val session = GeminiLiveSession(GeminiLiveConfig("test"), "", declaration, { 0L }, { _, _, _, _ -> null }, 40,
            object : GeminiLiveSession.Listener {
                override fun onInputText(delta: String, finished: Boolean) { events.add("in:$delta") }
                override fun onAudio(pcm: ByteArray) { events.add("audio:${pcm.size}") }
                override fun onOutputText(delta: String) { events.add("out:$delta") }
                override fun onTurnComplete() { events.add("done") }
            }, socketFactory = { req, listener -> Socket(req, listener).also { socket = it } })
        try {
            session.start(); socket.connect()
            socket.receive("""{"serverContent":{"inputTranscription":{"text":"Hi","finished":true},"modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"AAE="}}]},"outputTranscription":{"text":"Hello"},"turnComplete":true}}""")
            assertEquals(listOf("in:Hi", "audio:2", "out:Hello", "done"), events)
            session.close()
            socket.receive("""{"serverContent":{"outputTranscription":{"text":"late"}}}""")
            assertEquals(4, events.size)
        } finally { session.close() }
    }

    @Test fun `replayed call IDs return the result without repeating a phone action`() {
        lateinit var socket: Socket
        val calls = AtomicInteger()
        val generation = AtomicLong(7)
        val session = GeminiLiveSession(GeminiLiveConfig("test"), "", declaration, generation::get,
            { _, _, _, accepted -> assertEquals(7L, accepted); calls.incrementAndGet(); JSONObject().put("performed", true) },
            40, object : GeminiLiveSession.Listener {}, socketFactory = { req, listener -> Socket(req, listener).also { socket = it } })
        try {
            session.start(); socket.connect()
            val request = """{"toolCall":{"functionCalls":[{"id":"same","name":"phone_action","args":{"step":1}}]}}"""
            socket.receive(request)
            val first = socket.nextResponse()
            socket.receive(request)
            assertEquals(first.toString(), socket.nextResponse().toString())
            assertEquals(1, calls.get())
        } finally { session.close() }
    }

    @Test fun `server cancellation prevents queued actions while preserving serial order`() {
        lateinit var socket: Socket
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val calls = java.util.concurrent.CopyOnWriteArrayList<Int>()
        val session = GeminiLiveSession(GeminiLiveConfig("test"), "", declaration, { 1L },
            { _, args, _, _ ->
                val step = args.getInt("step")
                calls.add(step)
                if (step == 1) { firstStarted.countDown(); assertTrue(releaseFirst.await(3, TimeUnit.SECONDS)) }
                JSONObject().put("performed", step)
            }, 40, object : GeminiLiveSession.Listener {}, socketFactory = { req, listener -> Socket(req, listener).also { socket = it } })
        try {
            session.start(); socket.connect()
            socket.receive("""{"toolCall":{"functionCalls":[{"id":"1","name":"phone_action","args":{"step":1}},{"id":"2","name":"phone_action","args":{"step":2}},{"id":"3","name":"phone_action","args":{"step":3}}]}}""")
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS))
            socket.receive("""{"toolCallCancellation":{"ids":["2"]}}""")
            releaseFirst.countDown()
            socket.nextResponse(); socket.nextResponse()
            assertEquals(listOf(1, 3), calls.toList())
        } finally { releaseFirst.countDown(); session.close() }
    }

    @Test fun `unknown tools return an error without dispatching`() {
        lateinit var socket: Socket
        val calls = AtomicInteger()
        val session = GeminiLiveSession(GeminiLiveConfig("test"), "", declaration, { 0L }, { _, _, _, _ -> calls.incrementAndGet() }, 40,
            object : GeminiLiveSession.Listener {}, socketFactory = { req, listener -> Socket(req, listener).also { socket = it } })
        try {
            session.start(); socket.connect()
            socket.receive("""{"toolCall":{"functionCalls":[{"id":"bad","name":"not_advertised","args":{}}]}}""")
            assertTrue(socket.nextResponse().toString().contains("not available"))
            assertEquals(0, calls.get())
        } finally { session.close() }
    }

    @Test fun `outbound backpressure stops audio without leaking the key`() {
        lateinit var socket: Socket
        var error = ""
        val session = GeminiLiveSession(GeminiLiveConfig("secret-test-key"), "", declaration, { 0L }, { _, _, _, _ -> null }, 40,
            object : GeminiLiveSession.Listener { override fun onError(message: String) { error = message } },
            socketFactory = { req, listener -> Socket(req, listener).also { socket = it } })
        try {
            session.start(); socket.connect()
            socket.queued = 4_000_001
            session.sendAudio(byteArrayOf(0, 0))
            assertTrue(socket.cancelled)
            assertTrue(error.contains("connection"))
            assertFalse(error.contains("secret-test-key"))
        } finally { session.close() }
    }

    @Test fun `a disposed session cannot open a new socket`() {
        val opened = AtomicInteger()
        val session = GeminiLiveSession(GeminiLiveConfig("test"), "", declaration, { 0L }, { _, _, _, _ -> null }, 40,
            object : GeminiLiveSession.Listener {},
            socketFactory = { req, listener -> opened.incrementAndGet(); Socket(req, listener) })
        session.close()
        session.start()
        assertEquals(0, opened.get())
        session.close()
    }

    @Test fun `a cancelled result is not delivered again on replay`() {
        lateinit var socket: Socket
        val calls = AtomicInteger()
        val session = GeminiLiveSession(GeminiLiveConfig("test"), "", declaration, { 0L },
            { _, _, _, _ -> calls.incrementAndGet(); JSONObject().put("performed", true) }, 40,
            object : GeminiLiveSession.Listener {}, socketFactory = { req, listener -> Socket(req, listener).also { socket = it } })
        try {
            session.start(); socket.connect()
            val request = """{"toolCall":{"functionCalls":[{"id":"same","name":"phone_action","args":{}}]}}"""
            socket.receive(request)
            socket.nextResponse()
            socket.receive("""{"toolCallCancellation":{"ids":["same"]}}""")
            socket.receive(request)
            assertNull(socket.sent.poll(100, TimeUnit.MILLISECONDS))
            assertEquals(1, calls.get())
        } finally { session.close() }
    }

    @Test fun `an interrupted tool cannot send its screen image and its action is not repeated`() {
        lateinit var socket: Socket
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val firstCalls = AtomicInteger()
        val session = GeminiLiveSession(GeminiLiveConfig("test"), "", declaration, { 0L },
            { _, _, id, _ ->
                if (id == "first") {
                    firstCalls.incrementAndGet()
                    firstStarted.countDown()
                    releaseFirst.await(2, TimeUnit.SECONDS)
                    JSONObject().put("performed", true).put("png_base64", "AAE=")
                } else JSONObject().put("performed", true)
            }, 40, object : GeminiLiveSession.Listener {},
            socketFactory = { req, listener -> Socket(req, listener).also { socket = it } })
        try {
            session.start(); socket.connect()
            val first = """{"toolCall":{"functionCalls":[{"id":"first","name":"phone_action","args":{}}]}}"""
            socket.receive(first)
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS))
            socket.receive("""{"serverContent":{"interrupted":true}}""")
            releaseFirst.countDown()
            socket.receive("""{"toolCall":{"functionCalls":[{"id":"second","name":"phone_action","args":{}}]}}""")
            // The serial worker's next completed action proves the first result has been cached.
            val next = socket.nextResponse()
            assertEquals("second", next.getJSONObject("toolResponse").getJSONArray("functionResponses").getJSONObject(0).getString("id"))
            socket.receive(first)
            assertTrue(socket.nextResponse().has("toolResponse"))
            assertEquals(1, firstCalls.get())
            assertTrue(socket.sent.isEmpty())
        } finally { releaseFirst.countDown(); session.close() }
    }

    @Test fun `a late input transcript cannot cancel a tool or its spoken answer`() {
        lateinit var socket: Socket
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val hostGeneration = AtomicLong()
        val turns = AtomicInteger()
        val session = GeminiLiveSession(GeminiLiveConfig("test"), "", declaration, hostGeneration::get,
            { _, _, _, accepted ->
                started.countDown()
                release.await(2, TimeUnit.SECONDS)
                assertEquals(accepted, hostGeneration.get())
                JSONObject().put("performed", true)
            }, 40, object : GeminiLiveSession.Listener {
                override fun onUserTurn(fromAudio: Boolean) { turns.incrementAndGet(); hostGeneration.incrementAndGet() }
            }, socketFactory = { req, listener -> Socket(req, listener).also { socket = it } })
        try {
            session.start(); socket.connect()
            socket.receive("""{"toolCall":{"functionCalls":[{"id":"action","name":"phone_action","args":{}}]}}""")
            assertTrue(started.await(2, TimeUnit.SECONDS))
            socket.receive("""{"serverContent":{"inputTranscription":{"text":"open settings","finished":true}}}""")
            release.countDown()
            assertTrue(socket.nextResponse().toString().contains("performed"))
            socket.receive("""{"serverContent":{"modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"AAE="}}]},"turnComplete":true}}""")
            socket.receive("""{"serverContent":{"inputTranscription":{"text":"please","finished":true}}}""")
            assertEquals(1, turns.get())
            assertEquals(1L, hostGeneration.get())
        } finally { release.countDown(); session.close() }
    }

    @Test fun `background device notes wait for setup without starting a spoken turn`() {
        lateinit var socket: Socket
        val turns = AtomicInteger()
        val session = GeminiLiveSession(GeminiLiveConfig("test"), "", declaration, { 0L }, { _, _, _, _ -> null }, 40,
            object : GeminiLiveSession.Listener {
                override fun onUserTurn(fromAudio: Boolean) { turns.incrementAndGet() }
            }, socketFactory = { req, listener -> Socket(req, listener).also { socket = it } })
        try {
            session.start()
            session.note("The user closed the displayed page.")
            assertTrue(socket.sent.isEmpty())
            socket.connect()
            val update = socket.nextResponse().getJSONObject("clientContent")
            assertFalse(update.getBoolean("turnComplete"))
            assertEquals(0, turns.get())
        } finally { session.close() }
    }
}
