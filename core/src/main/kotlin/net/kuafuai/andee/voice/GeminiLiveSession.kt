package net.kuafuai.andee.voice

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * One persistent Gemini Live conversation. Audio and function calls share this
 * connection; no ASR -> chat -> TTS cascade or hosted proxy is involved.
 *
 * Socket callbacks never execute a phone action. A single worker serializes
 * tools, and every job captures the host's cancellation generation before it is
 * queued. Replayed function-call IDs return the saved result, never repeat an action.
 */
class GeminiLiveSession(
    private val config: GeminiLiveConfig,
    private val instruction: String,
    private val tools: JSONArray,
    private val toolGeneration: () -> Long,
    private val callTool: (name: String, args: JSONObject, id: String, generation: Long) -> Any?,
    private val maxSteps: Int,
    private val listener: Listener,
    private val socketFactory: ((Request, WebSocketListener) -> WebSocket)? = null,
) : AutoCloseable {
    interface Listener {
        fun onConnecting() {}
        fun onReady() {}
        fun onAudio(pcm: ByteArray) {}
        fun onInputText(delta: String, finished: Boolean) {}
        fun onOutputText(delta: String) {}
        fun onUserTurn(fromAudio: Boolean) {}
        fun onToolStart(name: String) {}
        fun onToolCancellation() {}
        fun onInterrupted() {}
        fun onTurnComplete() {}
        fun onError(message: String) {}
        fun onClosed() {}
    }

    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS).writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
    private val timer = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "GeminiLive-Timer").apply { isDaemon = true } }
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "GeminiLive-Tools").apply { isDaemon = true } }
    private val connection = AtomicLong()
    private val workEpoch = AtomicLong()
    private val steps = AtomicInteger()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val cancelled = ConcurrentHashMap.newKeySet<String>()
    private val deliveries = ConcurrentHashMap<String, Long>()
    private val deliveryEpochs = ConcurrentHashMap<String, Long>()
    private val completed = ConcurrentHashMap<String, JSONObject>()
    private val advertised = GeminiLiveProtocol.declarations(tools).let { a ->
        (0 until a.length()).map { a.getJSONObject(it).getString("name") }.toSet()
    }
    @Volatile private var socket: WebSocket? = null
    @Volatile private var active = false
    @Volatile private var disposed = false
    @Volatile private var ready = false
    @Volatile private var resumption: String? = null
    @Volatile private var heardInput = false
    private var handshakeTimeout: ScheduledFuture<*>? = null
    private var reconnectTask: ScheduledFuture<*>? = null
    private var attempts = 0
    private var connectedAt = 0L
    private var lastImageAt = 0L
    private var lastTool = ""
    private var repeatedTools = 0
    private var repetitionEpoch = -1L
    private data class TextTurn(val text: String, val images: List<String>)
    private val pendingText = java.util.concurrent.ConcurrentLinkedQueue<TextTurn>()
    private val pendingContext = java.util.concurrent.ConcurrentLinkedQueue<String>()

    @Synchronized
    fun start() {
        if (active || disposed) return
        active = true
        if (config.apiKey.isBlank()) {
            fail("Add your Gemini API key in Ciel settings, then tap Talk.")
            return
        }
        if (config.model.trim().removePrefix("models/").endsWith("-extended-thinking")) {
            fail("Use ${GeminiLiveConfig.DEFAULT_MODEL} for this Live voice mode. Extended-thinking session control is not supported yet.")
            return
        }
        openConnection()
    }

    fun isReady(): Boolean = active && ready

    fun sendAudio(pcm: ByteArray) {
        if (pcm.isEmpty() || !isReady()) return
        send(GeminiLiveProtocol.audio(pcm))
    }

    fun sendText(text: String, images: List<String> = emptyList()) {
        if ((text.isBlank() && images.isEmpty()) || !active) return
        if (images.size > 6 || images.any { it.length > 4_000_000 }) {
            fail("Choose up to six smaller photos for this conversation.")
            return
        }
        // A deliberately submitted new user turn owns the next tools.
        steps.set(0)
        workEpoch.incrementAndGet()
        heardInput = true
        listener.onUserTurn(fromAudio = false)
        val turn = TextTurn(text, images.toList())
        if (ready) sendTextTurn(turn)
        else if (pendingText.size < 4) pendingText.add(turn)
        else fail("Ciel is still connecting. Wait for the connection before sending another message.")
    }

    fun endAudio() {
        if (isReady()) send(JSONObject().put("realtimeInput", JSONObject().put("audioStreamEnd", true)))
    }

    /** Device context updates do not open a new user turn or ask the model to speak. */
    fun note(text: String) {
        if (!active || text.isBlank()) return
        if (ready) send(GeminiLiveProtocol.text(text.take(16_000), turnComplete = false))
        else {
            if (pendingContext.size >= 24) pendingContext.poll()
            pendingContext.add(text.take(16_000))
        }
    }

    @Synchronized
    private fun openConnection() {
        if (!active) return
        ready = false
        val generation = connection.incrementAndGet()
        listener.onConnecting()
        val callback = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!current(generation)) { webSocket.cancel(); return }
                webSocket.send(GeminiLiveProtocol.setup(config, instruction, tools, resumption).toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) = receive(generation, text)
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = receive(generation, bytes.utf8())

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!current(generation)) return
                if (code == 1007 || code == 1008) {
                    fail("Gemini rejected the session. Check your API key and Live model. " + safe(reason))
                } else reconnect(generation)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!current(generation)) return
                when (response?.code) {
                    400, 401, 403, 404 -> fail("Gemini connection was rejected (HTTP ${response.code}). Check your API key and Live model.")
                    429 -> fail("Your Gemini quota is currently exhausted. Wait and try again, or check your Google AI Studio limits.")
                    else -> reconnect(generation)
                }
            }
        }
        try {
            val request = Request.Builder().url(config.endpointUrl()).build()
            socket = socketFactory?.invoke(request, callback) ?: http.newWebSocket(request, callback)
            handshakeTimeout?.cancel(false)
            handshakeTimeout = timer.schedule({
                if (current(generation) && !ready) reconnect(generation)
            }, 20, TimeUnit.SECONDS)
        } catch (e: Exception) {
            fail("Could not open Gemini Live. " + safe(e.message.orEmpty()))
        }
    }

    private fun receive(generation: Long, raw: String) {
        if (!current(generation)) return
        try {
            val frame = JSONObject(raw)
            frame.optJSONObject("error")?.let {
                val message = if (it.optInt("code") == 429) "Gemini quota reached. Try again later."
                else "Gemini: " + safe(it.optString("message", "The service rejected this request."))
                fail(message)
                return
            }
            if (frame.has("setupComplete")) {
                handshakeTimeout?.cancel(false)
                ready = true
                connectedAt = System.nanoTime()
                while (true) send(GeminiLiveProtocol.text(pendingContext.poll() ?: break, turnComplete = false))
                listener.onReady()
                while (true) sendTextTurn(pendingText.poll() ?: break)
            }
            frame.optJSONObject("sessionResumptionUpdate")?.let { update ->
                if (update.optBoolean("resumable") && update.optString("newHandle").isNotBlank()) {
                    resumption = update.getString("newHandle")
                }
            }
            frame.optJSONObject("toolCallCancellation")?.optJSONArray("ids")?.let { ids ->
                for (i in 0 until ids.length()) cancelled.add(ids.getString(i))
                if (ids.length() > 0) listener.onToolCancellation()
            }
            frame.optJSONObject("serverContent")?.let { content ->
                // These fields can coexist in ONE event. An else-if here loses speech or transcripts.
                if (content.optBoolean("interrupted")) {
                    workEpoch.incrementAndGet()
                    listener.onInterrupted()
                    heardInput = false
                }
                val audio = if (content.optBoolean("interrupted")) emptyList() else GeminiLiveProtocol.audioParts(content)
                if (audio.isNotEmpty()) beginAudioResponse()
                content.optJSONObject("inputTranscription")?.let { transcript ->
                    val text = transcript.optString("text")
                    if (text.isNotEmpty()) {
                        // Transcripts are independent of model output and may arrive AFTER tools
                        // or speech. They are display data, not a cancellation/turn-start signal.
                        listener.onInputText(text, transcript.optBoolean("finished"))
                    }
                }
                if (!content.optBoolean("interrupted")) {
                    for (pcm in audio) listener.onAudio(pcm)
                    content.optJSONObject("outputTranscription")?.optString("text")?.takeIf { it.isNotEmpty() }
                        ?.let { listener.onOutputText(it) }
                }
                if (content.optBoolean("turnComplete")) {
                    heardInput = false
                    listener.onTurnComplete()
                }
            }
            frame.optJSONObject("toolCall")?.optJSONArray("functionCalls")?.let { calls ->
                if ((0 until calls.length()).any { !completed.containsKey(calls.getJSONObject(it).optString("id")) }) beginAudioResponse()
                for (i in 0 until calls.length()) enqueueTool(generation, calls.getJSONObject(i))
            }
            if (frame.has("goAway")) reconnect(generation)
        } catch (e: Exception) {
            fail("Could not process the Gemini response. " + safe(e.message.orEmpty()))
        }
    }

    private fun beginAudioResponse() {
        if (heardInput) return
        heardInput = true
        steps.set(0)
        workEpoch.incrementAndGet()
        listener.onUserTurn(fromAudio = true)
    }

    private fun enqueueTool(generation: Long, call: JSONObject) {
        val id = call.optString("id")
        val name = call.optString("name")
        if (id.isBlank()) { fail("Gemini returned a tool call without an ID; no phone action was performed."); return }
        if (id in cancelled) return
        deliveries[id] = generation
        deliveryEpochs[id] = workEpoch.get()
        completed[id]?.let { if (canDeliver(id)) send(it); return }
        if (name !in advertised) {
            send(GeminiLiveProtocol.toolResponse(id, name, JSONObject().put("error", "This tool is not available on this device.")))
            return
        }
        if (!inFlight.add(id)) return
        val epoch = workEpoch.get()
        val hostGeneration = toolGeneration()
        worker.execute {
            try {
                if (!current(generation) || epoch != workEpoch.get() || id in cancelled) {
                    // A replay may already be waiting on a new connection. Return a definite
                    // cancellation result rather than dropping it or executing the stale action.
                    val skipped = GeminiLiveProtocol.toolResponse(id, name,
                        JSONObject().put("error", "The request was cancelled before it started. No action was performed."))
                    completed[id] = skipped
                    if (canDeliver(id)) send(skipped)
                    return@execute
                }
                if (steps.incrementAndGet() > maxSteps.coerceIn(1, 200)) {
                    fail("Ciel stopped after $maxSteps actions. Check the screen, then ask to continue.")
                    return@execute
                }
                val args = call.optJSONObject("args") ?: JSONObject()
                val signature = name + args.toString()
                if (repetitionEpoch != epoch) {
                    repetitionEpoch = epoch
                    lastTool = ""
                    repeatedTools = 0
                }
                repeatedTools = if (signature == lastTool) repeatedTools + 1 else 1
                lastTool = signature
                if (repeatedTools > 3) {
                    fail("Ciel stopped a repeated action. Check the screen before trying again.")
                    return@execute
                }
                listener.onToolStart(name)
                val result = try {
                    callTool(name, args, id, hostGeneration)
                } catch (e: Exception) {
                    JSONObject().put("error", safe(e.message ?: e.javaClass.simpleName))
                }
                val prepared = try {
                    prepareToolResponse(id, name, result)
                } catch (e: Exception) {
                    PreparedToolResponse(GeminiLiveProtocol.toolResponse(id, name,
                        JSONObject().put("error", "The tool finished, but its result could not be read. Do not repeat the action without checking the phone.")), null)
                }
                // Completed IDs live for this session, including resumptions; evicting them could repeat
                // irreversible work. End a very long session instead of forgetting executed actions.
                if (completed.size >= 1000) { fail("Start a new conversation to continue using phone actions."); return@execute }
                // Record the outcome BEFORE image throttling or a reconnect can interrupt delivery.
                completed[id] = prepared.payload
                prepared.image?.let { sendImage(it, "image/png") { canDeliver(id) } }
                if (canDeliver(id)) send(prepared.payload)
            } finally {
                inFlight.remove(id)
            }
        }
    }

    private fun sendTextTurn(turn: TextTurn) {
        if (turn.images.isEmpty()) {
            send(GeminiLiveProtocol.text(turn.text))
            return
        }
        val epoch = workEpoch.get()
        worker.execute {
            for (data in turn.images) {
                if (!sendImage(data, "image/jpeg") { epoch == workEpoch.get() }) return@execute
            }
            if (isReady() && epoch == workEpoch.get()) send(GeminiLiveProtocol.text(turn.text.ifBlank { "Please look at these photos." }))
        }
    }

    private data class PreparedToolResponse(val payload: JSONObject, val image: String?)

    private fun prepareToolResponse(id: String, name: String, result: Any?): PreparedToolResponse {
        var image: String? = null
        fun clean(value: Any?, depth: Int): Any? {
            if (depth > 24) return "[nested result omitted]"
            val wrapped = when (value) {
                is Map<*, *> -> JSONObject(value)
                is Collection<*> -> JSONArray(value)
                else -> value
            }
            return when (wrapped) {
                is JSONObject -> JSONObject().also { out ->
                    for (key in wrapped.keys()) {
                        val v = wrapped.opt(key)
                        if (key == "png_base64" && v is String) {
                            image = v
                            out.put(key, "[screen image omitted from JSON]")
                        } else out.put(key, clean(v, depth + 1))
                    }
                }
                is JSONArray -> JSONArray().also { out ->
                    for (i in 0 until wrapped.length()) out.put(clean(wrapped.opt(i), depth + 1))
                }
                else -> wrapped
            }
        }
        var cleaned = clean(result, 0)
        val frame = image?.takeIf { it.length <= 4_000_000 }
        if (image != null && frame == null) cleaned = JSONObject().put("result", cleaned).put("image_unavailable", true)
        val serialized = cleaned?.toString().orEmpty()
        if (serialized.length > 160_000) cleaned = JSONObject()
            .put("truncated", true).put("preview", serialized.take(160_000))
        return PreparedToolResponse(GeminiLiveProtocol.toolResponse(id, name, cleaned), frame)
    }

    /** At most one video frame per second, and never from work the user has already stopped. */
    private fun sendImage(data: String, mime: String, stillCurrent: () -> Boolean): Boolean {
        if (!isReady() || !stillCurrent()) return false
        val pause = 1000 - (System.currentTimeMillis() - lastImageAt)
        if (pause > 0) {
            try { Thread.sleep(pause) } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        if (!isReady() || !stillCurrent()) return false
        if (!send(GeminiLiveProtocol.image(data, mime))) return false
        lastImageAt = System.currentTimeMillis()
        return true
    }

    private fun canDeliver(id: String): Boolean = active && ready &&
        deliveries[id] == connection.get() && deliveryEpochs[id] == workEpoch.get() && id !in cancelled

    @Synchronized
    private fun reconnect(generation: Long) {
        if (!current(generation)) return
        if (ready && System.nanoTime() - connectedAt > TimeUnit.SECONDS.toNanos(60)) attempts = 0
        if (++attempts > 3) { fail("Gemini Live disconnected. Check your connection, then tap Talk to reconnect."); return }
        ready = false
        workEpoch.incrementAndGet()
        connection.incrementAndGet() // Reject callbacks from the socket being replaced.
        handshakeTimeout?.cancel(false)
        socket?.cancel()
        socket = null
        listener.onInterrupted()
        listener.onConnecting()
        reconnectTask?.cancel(false)
        reconnectTask = timer.schedule({ openConnection() }, 500L shl (attempts - 1), TimeUnit.MILLISECONDS)
    }

    private fun send(payload: JSONObject): Boolean {
        if (!active) return false
        val ws = socket ?: return false
        if (ws.queueSize() > 4_000_000) {
            fail("The connection cannot keep up with live audio. Check your network, then reconnect.")
            return false
        }
        if (!ws.send(payload.toString())) {
            reconnect(connection.get())
            return false
        }
        return true
    }

    private fun current(generation: Long): Boolean = active && connection.get() == generation
    private fun safe(message: String): String = GoogleApiCredentials.redact(message, config.apiKey).take(500)

    private fun fail(message: String) {
        if (!active) return
        listener.onError(safe(message))
        close()
    }

    @Synchronized
    override fun close() {
        if (disposed) return
        disposed = true
        active = false
        ready = false
        connection.incrementAndGet()
        workEpoch.incrementAndGet()
        handshakeTimeout?.cancel(false)
        reconnectTask?.cancel(false)
        socket?.cancel()
        socket = null
        pendingText.clear()
        pendingContext.clear()
        worker.shutdownNow()
        timer.shutdownNow()
        http.dispatcher.executorService.shutdown()
        http.connectionPool.evictAll()
        listener.onClosed()
    }
}
