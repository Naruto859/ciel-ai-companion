package net.kuafuai.andee.net

import android.util.Log
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArraySet

/**
 * The body's WebSocket server. Two very different clients arrive here:
 *
 *  - **Drivers** — the brain (via `adb forward`), a debug/viewer client, top-bar
 *    tests. They send `request` frames and receive `response`s and broadcast
 *    `event`s. This is the original shape.
 *
 *  - **Call extension** — a peripheral (see whatsapp-agent's `bodyClient.ts`)
 *    that dials in, sends `{"type":"register","role":"call_extension",...}`,
 *    and hands us a batch of tool schemas it can serve. The body then reverses
 *    the arrow: request/response goes body → extension for those tool names,
 *    and the extension pushes `event` frames back up. It is not a driver, so
 *    it never sees broadcast events and does not sit in [active].
 *
 * Envelopes:
 *
 *   Driver → Server:
 *     {"type":"request","id":"...","method":"screen.tap","params":{...}}
 *
 *   Server → Driver:
 *     {"type":"response","id":"...","result":{...}}
 *     {"type":"response","id":"...","error":"..."}
 *     {"type":"event","kind":"asr.final","data":{...}}
 *
 *   Extension → Server:
 *     {"type":"register","role":"call_extension",
 *      "device_name":"...","scene":"...","tools":[{...}, ...]}
 *     {"type":"response","id":"...","result":{...}|"error":"..."}
 *     {"type":"event","kind":"call.state|call.subtitle|call.summary","data":{...}}
 *
 *   Server → Extension:
 *     {"type":"request","id":"...","method":"pickup_call","params":{...}}
 *
 * [onCommand] returns a JSON-serializable value (usually JSONObject) or throws.
 * Thrown exceptions become error responses; nothing else propagates.
 *
 * Optional developer access through loopback / adb forward. A per-install
 * token is mandatory even here: other apps on Android can reach loopback.
 * Remote companion access belongs to the authenticated peer transport.
 */
class BodyWsServer(
    port: Int,
    bindHost: String = "127.0.0.1",
    private val registry: CallExtensionRegistry,
    /** Extension-pushed event; the service turns these into hub broadcasts + UI updates. */
    private val onExtensionEvent: (kind: String, data: JSONObject) -> Unit = { _, _ -> },
    private val onCommand: (method: String, params: JSONObject?, expectedGeneration: Long) -> Any?,
    /** Capture at receipt so Stop invalidates requests still waiting in the serial queue. */
    private val cancellationGeneration: () -> Long,
    /**
     * `debug.say {"text"}` — hand a sentence to the brain as if the user typed
     * it, so a task can be reproduced from an authenticated developer client
     * through loopback / `adb forward`.
     */
    private val onSay: (text: String) -> Unit = {},

    /**
     * `debug.sweep` — run the quiet-hour review now instead of waiting out the
     * silence timer. Loopback only, like [onSay].
     *
     * Goes through `BodyCoreService.onSweepTimer`, so every gate the timer
     * applies still applies (silence, the daily budget, a running turn) and what
     * gets exercised is the real path. `onSweepForced` is the same thing with
     * the silence requirement lifted, for driving a test with the conversation
     * still warm.
     */
    private val onSweep: () -> Unit = {},

    /** `debug.sweep {"skip":true}` — the review with the silence gate bypassed. */
    private val onSweepForced: () -> Unit = {},
    private val authToken: String = "",
) : WebSocketServer(InetSocketAddress(bindHost, port)) {

    /** Driver connections only — the ones broadcasts go to. Extensions never join. */
    private val active = CopyOnWriteArraySet<WebSocket>()
    private val admitted = CopyOnWriteArraySet<WebSocket>()

    /**
     * In-flight reverse-requests we sent to the extension, keyed by envelope
     * id and the exact connection. A fast response is retained until its
     * caller awaits it; a late or foreign response cannot complete a request.
     */
    private val pending = ExtensionReplies<WebSocket>()

    init {
        isReuseAddr = true
    }

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake?) {
        if (!ControlPortAccess.allowed(
                conn.remoteSocketAddress?.address,
                handshake?.getFieldValue("Origin"),
                handshake?.getFieldValue("Authorization"),
                authToken,
            )) {
            conn.close(1008, "Local control requires an authorized developer connection")
            return
        }
        admitted.add(conn)
        // Provisionally treat every new connection as a driver. If a register
        // frame arrives naming role=call_extension, we lift it back out.
        active.add(conn)
        Log.d(TAG, "onOpen ${conn.remoteSocketAddress} drivers=${active.size}")
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String?, remote: Boolean) {
        admitted.remove(conn)
        active.remove(conn)
        if (registry.unregister(conn)) {
            Log.i(TAG, "call extension disconnected: ${conn.remoteSocketAddress} code=$code reason=$reason")
            // Nothing to talk to for any request we sent out — wake them all
            // with an error so brain gets a proper failure instead of the hub
            // request timing out at 30s.
            pending.disconnect(conn, "call extension disconnected")
        }
        Log.d(TAG, "onClose ${conn.remoteSocketAddress} code=$code reason=$reason remote=$remote drivers=${active.size}")
    }

    override fun onMessage(conn: WebSocket, message: String) {
        if (conn !in admitted) return
        if (message.length > MAX_MESSAGE_CHARS) {
            conn.close(1009, "Control message is too large")
            return
        }
        val msg = runCatching { JSONObject(message) }.getOrNull()
        if (msg == null) {
            Log.w(TAG, "non-JSON control message ignored")
            return
        }
        when (msg.optString("type")) {
            "request" -> handleDriverRequest(conn, msg)
            "register" -> handleRegister(conn, msg)
            "response" -> handleExtensionResponse(conn, msg)
            "event" -> handleExtensionEvent(conn, msg)
            else -> Log.d(TAG, "ignoring type=${msg.optString("type")}")
        }
    }

    private fun handleDriverRequest(conn: WebSocket, req: JSONObject) {
        if (conn !in active) return
        val id = req.optString("id", "")
        val method = req.optString("method", "")
        val params = req.optJSONObject("params")
        val generation = cancellationGeneration()
        Log.d(TAG, "request ${conn.remoteSocketAddress} $method")
        // Dispatch OFF the WS thread: blocking tools (ui.ask waits for a
        // human, scan waits for a code, look waits for a frame) would
        // otherwise stall ping/pong handling and the client drops us.
        try {
            dispatchPool.execute {
                if (conn !in active || !conn.isOpen) return@execute
                val response = try {
                    check(cancellationGeneration() == generation) { "The user stopped this task." }
                    val result = if (method == "debug.say") {
                        if (conn.remoteSocketAddress?.address?.isLoopbackAddress != true) {
                            throw IllegalStateException("debug.say is loopback only — use adb forward")
                        }
                        val text = params?.optString("text").orEmpty().trim()
                        require(text.isNotEmpty()) { "debug.say needs {\"text\": …}" }
                        onSay(text)
                        JSONObject().put("submitted", text)
                    } else if (method == "debug.sweep") {
                        // The quiet-hour review, on demand. Same reason as
                        // [onSay] and the same loopback rule: the review fires off
                        // an AlarmManager timer twenty minutes after the user stops
                        // talking, which makes "did that path work" a twenty-minute
                        // question every time. This is the same call the alarm
                        // makes, so what it exercises is the real thing rather than
                        // a rehearsal — the gates inside `onSweepTimer` still apply,
                        // including the silence check, and `skip` is there for the
                        // case where the caller is deliberately testing the path
                        // with the conversation still warm.
                        if (conn.remoteSocketAddress?.address?.isLoopbackAddress != true) {
                            throw IllegalStateException("debug.sweep is loopback only — use adb forward")
                        }
                        if (params?.optBoolean("skip", false) == true) {
                            onSweepForced()
                            JSONObject().put("started", true).put("forced", true)
                        } else {
                            onSweep()
                            JSONObject().put("started", true).put("forced", false)
                        }
                    } else if (method == "debug.worklog") {
                        // A scripted turn through the step stream ([PhoneWorkLog]), so the work card and the docked
                        // caption can be looked at without a model or a key. Loopback only, like the two above. Ends
                        // with the folded 「做了 N 步」 row, as a real turn would.
                        if (conn.remoteSocketAddress?.address?.isLoopbackAddress != true) {
                            throw IllegalStateException("debug.worklog is loopback only — use adb forward")
                        }
                        val seconds = params?.optInt("seconds", 20) ?: 20
                        Thread { net.kuafuai.andee.ui.PhoneWorkLog.rehearse(seconds) }.apply { isDaemon = true }.start()
                        JSONObject().put("started", true).put("seconds", seconds)
                    } else {
                        CommandCancellation.withSource({ conn in active && conn.isOpen }) {
                            onCommand(method, params, generation)
                        }
                    }
                    JSONObject()
                        .put("type", "response")
                        .put("id", id)
                        .put("result", result ?: JSONObject.NULL)
                } catch (t: Throwable) {
                    Log.w(TAG, "command '$method' failed: ${t.message}")
                    JSONObject()
                        .put("type", "response")
                        .put("id", id)
                        .put("error", t.message ?: t.javaClass.simpleName)
                }
                runCatching { conn.send(response.toString()) }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            runCatching { conn.send(JSONObject().put("type", "response").put("id", id)
                .put("error", "The local control queue is full or closed; retry after the current action.").toString()) }
        }
    }

    private fun handleRegister(conn: WebSocket, msg: JSONObject) {
        val role = msg.optString("role")
        if (role != "call_extension") {
            Log.w(TAG, "register with unknown role='$role', ignoring")
            return
        }
        val data = msg.optJSONObject("data") ?: msg  // tolerate either shape
        val tools = data.optJSONArray("tools") ?: JSONArray()
        val deviceName = data.optString("device_name").ifEmpty { null }
        val scene = data.optString("scene").ifEmpty { null }
        try {
            ToolSchemas.requireExtensionTools(tools)
        } catch (_: IllegalArgumentException) {
            conn.close(1008, "Invalid or reserved extension tool names")
            return
        }
        // A registered extension is a service peer, not a broadcast recipient.
        active.remove(conn)
        val previous = registry.register(conn, tools, deviceName, scene)
        if (previous != null && previous !== conn) {
            // One slot; a second extension replaces the first. Fail its
            // outstanding requests before closing, or their callers hang.
            pending.disconnect(previous, "call extension replaced")
            runCatching { previous.close(1000, "replaced by newer registration") }
        }
        Log.i(
            TAG,
            "call extension registered: ${conn.remoteSocketAddress} " +
                    "name='${deviceName ?: "-"}' tools=${tools.length()}"
        )
    }

    private fun handleExtensionResponse(conn: WebSocket, msg: JSONObject) {
        if (registry.connection() !== conn) return
        val id = msg.optString("id")
        if (id.isEmpty()) return
        val err = msg.optString("error").ifEmpty { null }
        val result = if (msg.has("result")) msg.opt("result") else null
        pending.complete(conn, id, result, err)
    }

    private fun handleExtensionEvent(conn: WebSocket, msg: JSONObject) {
        // Only accept events from the currently-registered extension. A stray
        // driver that pushes `event` frames has no channel to publish on.
        if (registry.connection() !== conn) {
            Log.d(TAG, "event from non-extension ${conn.remoteSocketAddress}, ignoring")
            return
        }
        val kind = msg.optString("kind")
        if (kind.isEmpty()) return
        val data = msg.optJSONObject("data") ?: JSONObject()
        runCatching { onExtensionEvent(kind, data) }
            .onFailure { Log.w(TAG, "onExtensionEvent threw for kind=$kind", it) }
    }

    // Phone gestures must stay serial; an unbounded cached pool also lets a
    // stalled client create arbitrarily many workers and overlapping overlays.
    private val dispatchPool = java.util.concurrent.ThreadPoolExecutor(
        1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
        java.util.concurrent.ArrayBlockingQueue<Runnable>(64),
        { r -> Thread(r, "BodyWsServer-dispatch").apply { isDaemon = true } },
    )

    override fun stop(timeout: Int) {
        admitted.clear()
        active.clear()
        dispatchPool.shutdownNow()
        pending.close("local control server stopped")
        super.stop(timeout)
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        Log.e(TAG, "onError ${conn?.remoteSocketAddress}", ex)
        if (conn != null) {
            admitted.remove(conn)
            active.remove(conn)
            if (registry.unregister(conn)) pending.disconnect(conn, "call extension error")
        }
    }

    override fun onStart() {
        Log.i(TAG, "listening on ${address}")
    }

    /** Broadcast an event to all driver clients. Extensions never receive it. */
    fun broadcastEvent(kind: String, data: JSONObject) {
        if (active.isEmpty()) return
        val envelope = JSONObject()
            .put("type", "event")
            .put("kind", kind)
            .put("data", data)
        val json = envelope.toString()
        for (c in active) runCatching { c.send(json) }
    }

    fun clientCount(): Int = active.size

    /**
     * Send [method] to the registered call extension and block for its response.
     *
     * Throws [IllegalStateException] when no extension is connected — the caller
     * (dispatcher) turns that into a proper `error` response to the brain, same
     * as any other tool failure. Also throws on timeout: `body_hub` gives a
     * request 30s, so [timeoutMs] defaults well under that.
     *
     * Runs on whatever thread the caller is on (usually a dispatch worker).
     */
    fun forwardToExtension(
        method: String,
        params: JSONObject?,
        timeoutMs: Long = DEFAULT_EXTENSION_TIMEOUT_MS,
    ): Any? {
        val conn = registry.connection()
            ?: throw IllegalStateException(
                "call extension is not connected — is the call box on and reachable?"
            )
        val slot = pending.begin(conn)
        val envelope = JSONObject()
            .put("type", "request")
            .put("id", slot.id)
            .put("method", method)
        if (params != null) envelope.put("params", params)
        try {
            if (!conn.isOpen || registry.connection() !== conn) {
                throw IllegalStateException("call extension socket is closed")
            }
            conn.send(envelope.toString())
        } catch (t: Throwable) {
            pending.forget(slot)
            throw t
        }
        val out = try {
            slot.await(timeoutMs)
        } finally {
            pending.forget(slot)
        }
            ?: throw IllegalStateException(
                "call extension did not respond within ${timeoutMs}ms for $method"
            )
        if (out.error != null) throw IllegalStateException(out.error)
        return out.result
    }

    companion object {
        private const val TAG = "BodyWs"
        private const val MAX_MESSAGE_CHARS = 1_048_576

        /**
         * Ceiling for a body→extension request. Under the hub's 30s so the
         * failure surfaces here with a real message ("did not respond") rather
         * than as an anonymous hub timeout.
         */
        const val DEFAULT_EXTENSION_TIMEOUT_MS = 25_000L
    }
}
