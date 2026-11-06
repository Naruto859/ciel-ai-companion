package net.kuafuai.andee.net

import java.net.InetAddress
import java.security.MessageDigest

/** Admission for the optional local developer port. Paired peers use their own authenticated transport. */
object ControlPortAccess {
    fun allowed(address: InetAddress?, origin: String?, authorization: String?, expectedToken: String): Boolean {
        // Loopback is not authentication: another Android app can connect to it too.
        if (address?.isLoopbackAddress != true || !origin.isNullOrEmpty() || expectedToken.length < 32) return false
        val header = authorization ?: return false
        if (!header.startsWith("Bearer ", ignoreCase = true)) return false
        return MessageDigest.isEqual(
            expectedToken.toByteArray(Charsets.UTF_8),
            header.substring(7).toByteArray(Charsets.UTF_8),
        )
    }
}
