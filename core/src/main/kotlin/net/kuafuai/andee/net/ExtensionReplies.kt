package net.kuafuai.andee.net

import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Retains an early reply and binds it to the exact extension connection that received the request. */
class ExtensionReplies<Connection : Any> {
    data class Reply(val result: Any?, val error: String?)
    class Request<Connection : Any> internal constructor(
        val id: String,
        internal val connection: Connection,
        internal val reply: CompletableFuture<Reply>,
    ) {
        fun await(timeoutMs: Long): Reply? = try {
            reply.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            null
        }
    }

    private val pending = ConcurrentHashMap<String, Request<Connection>>()

    fun begin(connection: Connection): Request<Connection> =
        Request(UUID.randomUUID().toString(), connection, CompletableFuture<Reply>()).also { pending[it.id] = it }

    fun complete(connection: Connection, id: String, result: Any?, error: String?): Boolean {
        val request = pending[id] ?: return false
        if (request.connection !== connection || !pending.remove(id, request)) return false
        return request.reply.complete(Reply(result, error))
    }

    fun forget(request: Request<Connection>) {
        pending.remove(request.id, request)
    }

    fun disconnect(connection: Connection, reason: String) {
        for (request in pending.values) {
            if (request.connection === connection && pending.remove(request.id, request)) {
                request.reply.complete(Reply(null, reason))
            }
        }
    }

    fun close(reason: String) {
        for (request in pending.values) {
            if (pending.remove(request.id, request)) request.reply.complete(Reply(null, reason))
        }
    }
}
