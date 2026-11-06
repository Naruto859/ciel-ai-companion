package net.kuafuai.andee.net

import android.util.Log
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Outbound side of the body's networking: dials into a remote hub (agentworld
 * or similar), sends a `register` envelope with our tool schemas, then serves
 * `request` messages coming down the pipe by handing them to
 * [CommandDispatcher] and mailing the result back.
 *
 * Reconnects with exponential backoff (500ms → 30s, capped). Runs alongside
 * [BodyWsServer] — that side keeps serving viewer/local traffic; this side is
 * how we get to a hub that lives behind NAT / on the public internet.
 *
 * Hub protocol:
 *   → {"type":"register","data":{"device_id":"...","device_name":"...","tools":[...]}}
 *   ← {"type":"request","id":"r7","method":"tap_screen","params":{...}}
 *   → {"type":"response","id":"r7","result":{...}}
 *   → {"type":"response","id":"r7","error":"..."}
 *   → {"type":"event","kind":"asr.final","device_id":"...","data":{...}}
 *   ← {"type":"progress","content":"读取界面","agent_id":"..."}
 *   ← {"type":"message","content":"已经帮你打开微信了","agent_id":"..."}
 *
 * `progress` is narration for the human — one subtitle line, never spoken.
 * `message` is the agent's finished answer, which the body speaks aloud.
 * Both are fire-and-forget; neither expects a response.
 *
 * Method names on the wire are the LLM-facing tool names (tap_screen,
 * get_screen_element, …). We translate them to the body's internal `screen.*`
 * method strings via [ToolSchemas.methodOf] before dispatching.
 */
class BodyWsClient(
    private val url: String,
    private val deviceId: String,
    private val deviceName: String,
    private val dispatcher: CommandDispatcher,
    /**
     * Extension-provided tools are merged into the `register` payload alongside
     * [ToolSchemas.all]. When the registry changes, the service tears this
     * client down and starts a fresh one so the hub sees an up-to-date list —
     * the hub has no separate "tools changed" frame, `register` is the only
     * lever we have on its idea of what this device can do.
     */
    private val registry: CallExtensionRegistry,
    private val onProgress: (String) -> Unit = {},
    private val onFinal: (String) -> Unit = {},
) {

    private val running = AtomicBoolean(false)
    @Volatile private var acceptingRequests = true
    private val scheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "BodyWsClient-scheduler").apply { isDaemon = true }
        }

    // Requests can block (dispatchGesture waits ~seconds), so hand them off
    // to a worker so we don't stall the recv thread and miss pings/pongs.
    private val worker = java.util.concurrent.ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS, java.util.concurrent.ArrayBlockingQueue<Runnable>(64),
        { r -> Thread(r, "BodyWsClient-worker").apply { isDaemon = true } },
    )

    @Volatile
    private var conn: Inner? = null

    @Volatile
    private var backoffMs: Long = INITIAL_BACKOFF_MS

    fun start() {
        if (!running.compareAndSet(false, true)) return
        val host = runCatching { URI(url).host }.getOrNull().orEmpty()
        Log.i(TAG, "starting hub client → $host  (device=$deviceId '$deviceName')")
        scheduleConnect(0)
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        val c = conn
        conn = null
        if (current === this) current = null
        runCatching { c?.close() }
        scheduler.shutdownNow()
        worker.shutdownNow()
    }

    /** Stop is enforced here even if the remote hub ignores its cancellation event. */
    fun cancelRequests() {
        acceptingRequests = false
    }

    // ------------------------------------------------------------------
    // internal
    // ------------------------------------------------------------------

    private fun scheduleConnect(delayMs: Long) {
        if (!running.get()) return
        try {
            scheduler.schedule({ if (running.get()) connectOnce() }, delayMs, TimeUnit.MILLISECONDS)
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            // stop() can shut down the scheduler between the check and schedule.
        }
    }

    private fun connectOnce() {
        try {
            val c = Inner(URI(url))
            conn = c
            c.connect()
        } catch (t: Throwable) {
            Log.e(TAG, "connect failed: ${t.javaClass.simpleName}")
            onDisconnected()
        }
    }

    private fun onConnected(source: Inner) {
        backoffMs = INITIAL_BACKOFF_MS  // reset backoff on success
        // forHub(), NOT all(): the five `note.*` notebook tools are localOnly.
        // The notebook holds what this person likes, what they promised, and
        // what they are like — it is the one part of this app that is nobody
        // else's business, so it is never advertised to the hub. Swapping this
        // back to all() would silently ship it on the next reconnect, with no
        // visible symptom other than the cloud brain gaining five tools.
        val builtin = ToolSchemas.forHub()
        val extra = registry.tools()
        // Built-ins first, extension tools appended. Registration rejects
        // reserved names before an extension can reach this merge.
        val tools = org.json.JSONArray()
        for (i in 0 until builtin.length()) tools.put(builtin.getJSONObject(i))
        for (i in 0 until extra.length()) tools.put(extra.getJSONObject(i))
        val register = JSONObject()
            .put("type", "register")
            .put(
                "data", JSONObject()
                    .put("device_id", deviceId)
                    .put("device_name", deviceName)
                    .put("tools", tools)
            )
        runCatching { source.send(register.toString()) }
        Log.i(
            TAG,
            "registered as $deviceId with ${tools.length()} tools " +
                "(${builtin.length()} built-in + ${extra.length()} from extension)"
        )
    }

    private fun onDisconnected() {
        if (!running.get()) return
        val next = backoffMs
        backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
        Log.i(TAG, "reconnect in ${next}ms")
        scheduleConnect(next)
    }

    private fun handleRequest(source: Inner, req: JSONObject) {
        val id = req.optString("id", "")
        if (!acceptingRequests) {
            respondError(source, id, "The user stopped the task. Wait for their next message.")
            return
        }
        val toolName = req.optString("method", "")
        val params = req.optJSONObject("params")
        // Two-name translation for built-ins (`tap_screen` → `screen.tap`).
        // Extension tools don't play this game — the schema they registered
        // carries one name, and that same name goes straight to the dispatcher,
        // which will hand it back to the extension over the reverse channel.
        val builtin = ToolSchemas.methodOf(toolName)
        val method = if (builtin != null) ToolSchemas.hubMethodOf(toolName)
            else if (registry.hasMethod(toolName)) toolName else null
        if (method == null) {
            respondError(source, id, "tool is unavailable to the remote hub: $toolName")
            return
        }
        // A request on *this* socket is the brain driving, which is what the
        // edge glow announces. Marked here rather than in the dispatcher
        // because only the transport can tell the brain apart from the top-bar
        // buttons and the debug client on 9008, which share the same dispatch
        // path and are nobody's task. Usually redundant — `progress` normally
        // arrives first — but a model that opens with a tool call still counts.
        val generation = dispatcher.cancellationGeneration()
        try {
            worker.execute {
                if (!running.get() || !acceptingRequests || conn !== source || !source.isOpen) return@execute
                val response = try {
                    val result = CommandCancellation.withSource({
                        running.get() && acceptingRequests && conn === source && source.isOpen
                    }) {
                        dispatcher.checkGeneration(generation)
                        dispatcher.beginTask()
                        dispatcher.dispatch(method, params, expectedGeneration = generation)
                    }
                    JSONObject()
                        .put("type", "response")
                        .put("id", id)
                        .put("result", result ?: JSONObject.NULL)
                } catch (t: Throwable) {
                    Log.w(TAG, "tool '$toolName' failed: ${t.message}")
                    JSONObject()
                        .put("type", "response")
                        .put("id", id)
                        .put("error", t.message ?: t.javaClass.simpleName)
                }
                if (conn === source && source.isOpen) runCatching { source.send(response.toString()) }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            respondError(source, id, "The phone command queue is full or closed; retry after the current action.")
        }
    }

    /**
     * Push an unsolicited event up to the hub (ASR transcripts, etc). Dropped
     * silently when the socket is down — events are not queued, the brain only
     * ever sees what happened while it was listening.
     */
    fun sendEvent(kind: String, data: JSONObject) {
        if (kind == "task.stop") acceptingRequests = false
        else if (kind == "asr.final") acceptingRequests = true
        val c = conn ?: return
        if (!c.isOpen) return
        val envelope = JSONObject()
            .put("type", "event")
            .put("kind", kind)
            .put("device_id", deviceId)
            .put("data", data)
        runCatching { c.send(envelope.toString()) }
    }

    private fun respondError(source: Inner, id: String, msg: String) {
        val response = JSONObject()
            .put("type", "response")
            .put("id", id)
            .put("error", msg)
        if (conn === source && source.isOpen) runCatching { source.send(response.toString()) }
    }

    /**
     * WebSocketClient callbacks. Small inner class so we can null out [conn]
     * cleanly across reconnects.
     */
    private inner class Inner(uri: URI) : WebSocketClient(uri) {

        override fun onOpen(handshake: ServerHandshake?) {
            if (!running.get() || conn !== this) {
                close()
                return
            }
            Log.i(TAG, "hub connected to ${uri.host}")
            current = this@BodyWsClient
            onConnected(this)
        }

        override fun onMessage(message: String) {
            if (!running.get() || conn !== this) return
            val msg = runCatching { JSONObject(message) }.getOrNull() ?: return
            when (msg.optString("type")) {
                "request" -> handleRequest(this, msg)
                // Narration and final answers arrive on the recv thread; both
                // handlers only touch @Volatile UI state or post to the main
                // Handler, so there's nothing to hand off to the worker here.
                "progress" -> if (acceptingRequests) msg.optString("content").takeIf { it.isNotBlank() }?.let(onProgress)
                "message" -> if (acceptingRequests) msg.optString("content").takeIf { it.isNotBlank() }?.let(onFinal)
                else -> Log.d(TAG, "ignoring type=${msg.optString("type")}")
            }
        }

        override fun onClose(code: Int, reason: String?, remote: Boolean) {
            Log.i(TAG, "onClose code=$code reason=$reason remote=$remote")
            if (conn !== this) return
            conn = null
            if (current === this@BodyWsClient) current = null
            onDisconnected()
        }

        override fun onError(ex: Exception?) {
            Log.w(TAG, "onError ${ex?.message}")
        }
    }

    companion object {
        private const val TAG = "BodyWsClient"
        private const val INITIAL_BACKOFF_MS = 500L
        private const val MAX_BACKOFF_MS = 30_000L

        /**
         * The connected client, for services outside BodyCoreService's
         * wiring (NotificationRelayService pushing `notification` events).
         * Null while the hub link is down — pushers drop silently by design:
         * buffering-and-replaying after reconnect would deliver stale
         * "new message" interrupts.
         */
        @Volatile
        var current: BodyWsClient? = null
            private set
    }
}
