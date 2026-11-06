package net.kuafuai.andee.voice

import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.URLEncoder

/** Keep API keys out of logs and encode query values instead of interpolating them. */
object GoogleApiCredentials {
    fun appendKey(rawUrl: String, apiKey: String): String {
        if (apiKey.isBlank()) return rawUrl
        val websocket = rawUrl.startsWith("ws://") || rawUrl.startsWith("wss://")
        val httpUrl = rawUrl.replaceFirst(Regex("^ws:"), "http:")
            .replaceFirst(Regex("^wss:"), "https:")
            .toHttpUrl().newBuilder().setQueryParameter("key", apiKey).build().toString()
        return if (websocket) httpUrl.replaceFirst(Regex("^http:"), "ws:")
            .replaceFirst(Regex("^https:"), "wss:") else httpUrl
    }

    fun redact(message: String, apiKey: String = ""): String {
        var result = message.replace(Regex("(?i)([?&](?:key|api_key|access_token)=)[^\\s&#\"']*"), "$1[redacted]")
        if (apiKey.isNotBlank()) {
            result = result.replace(apiKey, "[redacted]")
                .replace(URLEncoder.encode(apiKey, "UTF-8"), "[redacted]")
        }
        return result
    }
}
