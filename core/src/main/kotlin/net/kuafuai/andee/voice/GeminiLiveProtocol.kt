package net.kuafuai.andee.voice

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

data class GeminiLiveConfig(
    val apiKey: String,
    val model: String = DEFAULT_MODEL,
    val voice: String = "Erinome",
) {
    fun endpointUrl(): String = GoogleApiCredentials.appendKey(ENDPOINT, apiKey)

    override fun toString(): String = "GeminiLiveConfig(apiKey=[redacted], model=$model, voice=$voice)"

    companion object {
        // Official Gemini Live capabilities/pricing docs, checked 2026-11-06.
        const val DEFAULT_MODEL = "gemini-3.8-live"
        const val ENDPOINT = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
    }
}

/** Wire-format helpers, shared with JVM tests. No target action or audio device is touched here. */
object GeminiLiveProtocol {
    const val INPUT_SAMPLE_RATE = 16_000
    const val OUTPUT_SAMPLE_RATE = 24_000
    const val SILENCE_MS = 350

    fun setup(config: GeminiLiveConfig, instruction: String, tools: JSONArray, resumeHandle: String? = null): JSONObject {
        val model = config.model.trim().removePrefix("models/").ifBlank { GeminiLiveConfig.DEFAULT_MODEL }
        val resume = JSONObject()
        if (!resumeHandle.isNullOrBlank()) resume.put("handle", resumeHandle)
        val setup = JSONObject()
            .put("model", "models/$model")
            .put("generationConfig", JSONObject()
                .put("responseModalities", JSONArray().put("AUDIO"))
                .put("speechConfig", JSONObject().put("voiceConfig", JSONObject()
                    .put("prebuiltVoiceConfig", JSONObject().put("voiceName", config.voice)))))
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", instruction))))
            .put("inputAudioTranscription", JSONObject())
            .put("outputAudioTranscription", JSONObject())
            .put("sessionResumption", resume)
            .put("contextWindowCompression", JSONObject().put("slidingWindow", JSONObject()))
            .put("realtimeInputConfig", JSONObject()
                .put("automaticActivityDetection", JSONObject()
                    .put("disabled", false)
                    .put("startOfSpeechSensitivity", "START_SENSITIVITY_HIGH")
                    .put("endOfSpeechSensitivity", "END_SENSITIVITY_HIGH")
                    .put("prefixPaddingMs", 80)
                    .put("silenceDurationMs", SILENCE_MS)))
        if (tools.length() > 0) {
            val functions = declarations(tools)
            if (model == "gemini-3.8-live") {
                for (i in 0 until functions.length()) functions.getJSONObject(i).put("behavior", "BLOCKING")
            }
            setup.put("tools", JSONArray().put(JSONObject().put("functionDeclarations", functions)))
        }
        // 3.8 Live does not accept thinkingLevel or enableAffectiveDialog. Do not copy settings from TTS/Vertex.
        return JSONObject().put("setup", setup)
    }

    /** Raw ToolSchemas and OpenAI-shaped extensions both resolve to the same declaration. */
    fun declarations(tools: JSONArray): JSONArray {
        val out = JSONArray()
        val seen = HashSet<String>()
        for (i in 0 until tools.length()) {
            val item = tools.optJSONObject(i) ?: continue
            val tool = item.optJSONObject("function") ?: item
            val name = tool.optString("name")
            if (!name.matches(Regex("[A-Za-z_][A-Za-z0-9_.:-]{0,63}")) || !seen.add(name)) continue
            val declaration = JSONObject().put("name", name).put("description", tool.optString("description"))
            tool.optJSONObject("parameters")?.let { schema ->
                // JSON Schema preserves free-form objects used by note/stage tools. The narrower
                // protobuf Schema cannot represent all of those without silently changing a tool.
                declaration.put("parametersJsonSchema", JSONObject(schema.toString()))
            }
            out.put(declaration)
        }
        return out
    }

    fun audio(pcm: ByteArray): JSONObject {
        require(pcm.size % 2 == 0) { "PCM input must contain complete 16-bit samples" }
        return JSONObject().put("realtimeInput", JSONObject().put("audio", JSONObject()
            .put("mimeType", "audio/pcm;rate=$INPUT_SAMPLE_RATE")
            .put("data", Base64.getEncoder().encodeToString(pcm))))
    }

    fun text(text: String, turnComplete: Boolean = true): JSONObject = JSONObject().put("clientContent", JSONObject()
        .put("turns", JSONArray().put(JSONObject().put("role", "user")
            .put("parts", JSONArray().put(JSONObject().put("text", text)))))
        .put("turnComplete", turnComplete))

    fun image(data: String, mime: String = "image/png"): JSONObject =
        JSONObject().put("realtimeInput", JSONObject().put("video", JSONObject().put("mimeType", mime).put("data", data)))

    fun toolResponse(id: String, name: String, result: Any?): JSONObject =
        JSONObject().put("toolResponse", JSONObject().put("functionResponses", JSONArray().put(JSONObject()
            .put("id", id).put("name", name)
            .put("response", JSONObject().put("result", result ?: JSONObject.NULL)))))

    fun audioParts(content: JSONObject): List<ByteArray> {
        val parts = content.optJSONObject("modelTurn")?.optJSONArray("parts") ?: return emptyList()
        val result = ArrayList<ByteArray>()
        for (i in 0 until parts.length()) {
            val inline = parts.optJSONObject(i)?.optJSONObject("inlineData") ?: continue
            val mime = inline.optString("mimeType")
            if (!mime.startsWith("audio/pcm")) continue
            val rate = Regex("rate=(\\d+)").find(mime)?.groupValues?.get(1)?.toIntOrNull()
            require(rate == null || rate == OUTPUT_SAMPLE_RATE) { "Unsupported Live audio sample rate: $rate" }
            val pcm = Base64.getDecoder().decode(inline.getString("data"))
            require(pcm.size % 2 == 0) { "Live audio contained an incomplete PCM sample" }
            if (pcm.isNotEmpty()) result.add(pcm)
        }
        return result
    }
}
