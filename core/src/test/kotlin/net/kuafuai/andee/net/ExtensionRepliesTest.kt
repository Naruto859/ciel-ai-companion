package net.kuafuai.andee.net

import org.junit.Assert.*
import org.junit.Test

class ExtensionRepliesTest {
    @Test fun `an extension response arriving before the caller waits is retained`() {
        val replies = ExtensionReplies<Any>()
        val owner = Any()
        val request = replies.begin(owner)
        assertTrue(replies.complete(owner, request.id, "answered", null))
        assertEquals("answered", request.await(1)?.result)
    }

    @Test fun `a driver or replaced extension cannot claim another connection's request`() {
        val replies = ExtensionReplies<Any>()
        val owner = Any()
        val request = replies.begin(owner)
        assertFalse(replies.complete(Any(), request.id, "forged", null))
        assertTrue(replies.complete(owner, request.id, "real", null))
        assertEquals("real", request.await(1)?.result)
        assertFalse(replies.complete(owner, request.id, "duplicate", null))
    }

    @Test fun `disconnect wakes early waiters but does not fail the replacement connection`() {
        val replies = ExtensionReplies<Any>()
        val old = Any()
        val next = Any()
        val first = replies.begin(old)
        val second = replies.begin(next)
        replies.disconnect(old, "disconnected")
        assertEquals("disconnected", first.await(1)?.error)
        assertTrue(replies.complete(next, second.id, "new", null))
        assertEquals("new", second.await(1)?.result)
    }

    @Test fun `a timed out request cannot be completed later`() {
        val replies = ExtensionReplies<Any>()
        val owner = Any()
        val request = replies.begin(owner)
        assertNull(request.await(1))
        replies.forget(request)
        assertFalse(replies.complete(owner, request.id, "late", null))
    }
}
