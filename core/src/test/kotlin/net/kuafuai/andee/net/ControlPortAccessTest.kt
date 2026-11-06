package net.kuafuai.andee.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class ControlPortAccessTest {
    private val token = "test-only-control-token-0123456789abcdef"
    private val local = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))

    @Test fun `another app on loopback cannot control the phone without the token`() {
        assertFalse(ControlPortAccess.allowed(local, null, null, token))
        assertFalse(ControlPortAccess.allowed(local, null, "Bearer wrong", token))
        assertFalse(ControlPortAccess.allowed(local, null, "Bearer ${token.dropLast(1)}", token))
        assertTrue(ControlPortAccess.allowed(local, null, "Bearer $token", token))
    }

    @Test fun `browser origins and non-loopback clients are refused even with the token`() {
        for (origin in listOf("https://example.test", "null", "file://")) {
            assertFalse(ControlPortAccess.allowed(local, origin, "Bearer $token", token))
        }
        val lan = InetAddress.getByAddress(byteArrayOf(192.toByte(), 168.toByte(), 1, 8))
        assertFalse(ControlPortAccess.allowed(lan, null, "Bearer $token", token))
        assertFalse(ControlPortAccess.allowed(null, null, "Bearer $token", token))
    }

    @Test fun `an unconfigured token never opens the port`() {
        assertFalse(ControlPortAccess.allowed(local, null, "Bearer ", ""))
        assertFalse(ControlPortAccess.allowed(local, null, "Bearer short", "short"))
    }
}
