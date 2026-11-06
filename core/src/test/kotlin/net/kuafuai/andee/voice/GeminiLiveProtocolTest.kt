package net.kuafuai.andee.voice

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class GeminiLiveProtocolTest {
    @Test fun `setup uses direct native audio without unsupported model settings or secrets`() {
        val cfg = GeminiLiveConfig("private-test-key")
        val raw = GeminiLiveProtocol.setup(cfg, "Be helpful", JSONArray()).toString()
        val setup = JSONObject(raw).getJSONObject("setup")
        assertEquals("models/gemini-3.8-live", setup.getString("model"))
        assertEquals("AUDIO", setup.getJSONObject("generationConfig").getJSONArray("responseModalities").getString(0))
        assertTrue(setup.has("inputAudioTranscription"))
        assertTrue(setup.has("outputAudioTranscription"))
        assertTrue(setup.has("sessionResumption"))
        assertTrue(setup.has("contextWindowCompression"))
        assertFalse(raw.contains(cfg.apiKey))
        assertFalse(cfg.toString().contains(cfg.apiKey))
        assertFalse(raw.contains("thinkingLevel"))
        assertFalse(raw.contains("enableAffectiveDialog"))
        assertFalse(raw.contains("proactivity"))
        assertEquals(350, setup.getJSONObject("realtimeInputConfig").getJSONObject("automaticActivityDetection").getInt("silenceDurationMs"))
    }

    @Test fun `schemas retain parameters but never publish internal method or localOnly flags`() {
        val t = JSONObject().put("name", "inspect_phone").put("method", "screen.ui_tree")
            .put("localOnly", true).put("description", "Inspect the phone")
            .put("parameters", JSONObject("""{"type":"object","properties":{"options":{"type":"object"}}}"""))
        val setup = GeminiLiveProtocol.setup(GeminiLiveConfig("test"), "", JSONArray().put(t)).getJSONObject("setup")
        val f = setup.getJSONArray("tools").getJSONObject(0).getJSONArray("functionDeclarations").getJSONObject(0)
        assertEquals("inspect_phone", f.getString("name"))
        assertFalse(f.has("method"))
        assertFalse(f.has("localOnly"))
        assertEquals("object", f.getJSONObject("parametersJsonSchema").getJSONObject("properties").getJSONObject("options").getString("type"))
        assertEquals("BLOCKING", f.getString("behavior"))
    }

    @Test fun `API keys are encoded replaced and redacted`() {
        val url = GoogleApiCredentials.appendKey("wss://example.test/ws?key=old&alt=sse", "new+key&x=1")
        assertTrue(url.contains("key=new%2Bkey%26x%3D1"))
        assertFalse(url.contains("key=old"))
        assertTrue(url.contains("alt=sse"))
        val safe = GoogleApiCredentials.redact("failed $url with new+key&x=1", "new+key&x=1")
        assertFalse(safe.contains("new+key"))
        assertFalse(safe.contains("new%2Bkey"))
    }

    @Test fun `PCM packets carry the expected input and output sample rates`() {
        val bytes = byteArrayOf(0, 1, 2, 3)
        val packet = GeminiLiveProtocol.audio(bytes).getJSONObject("realtimeInput").getJSONObject("audio")
        assertEquals("audio/pcm;rate=16000", packet.getString("mimeType"))
        assertArrayEquals(bytes, Base64.getDecoder().decode(packet.getString("data")))
        val content = JSONObject().put("modelTurn", JSONObject().put("parts", JSONArray()
            .put(JSONObject().put("text", "not audio"))
            .put(JSONObject().put("inlineData", JSONObject().put("mimeType", "audio/pcm;rate=24000").put("data", packet.getString("data"))))))
        assertArrayEquals(bytes, GeminiLiveProtocol.audioParts(content).single())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `wrong output sample rate is rejected instead of playing distorted speech`() {
        GeminiLiveProtocol.audioParts(JSONObject("""{"modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=48000","data":"AAE="}}]}}"""))
    }

    @Test fun `tool results keep call identity and actual errors`() {
        val p = GeminiLiveProtocol.toolResponse("call-9", "tap", JSONObject().put("error", "Accessibility is off"))
            .getJSONObject("toolResponse").getJSONArray("functionResponses").getJSONObject(0)
        assertEquals("call-9", p.getString("id"))
        assertEquals("tap", p.getString("name"))
        assertEquals("Accessibility is off", p.getJSONObject("response").getJSONObject("result").getString("error"))
    }
}
