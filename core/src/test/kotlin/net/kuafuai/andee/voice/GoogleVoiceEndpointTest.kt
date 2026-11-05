package net.kuafuai.andee.voice

import net.kuafuai.andee.asr.GoogleAsr
import net.kuafuai.andee.config.VoiceSettings
import net.kuafuai.andee.tts.GoogleTts
import net.kuafuai.andee.ui.ball.ANDEE
import net.kuafuai.andee.ui.ball.BallLooks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleVoiceEndpointTest {

    private fun sampleSettings(
        apiKey: String = "test-gemini-key",
        baseUrl: String = "",
        project: String = "",
        location: String = "",
        asrModel: String = "",
        ttsModel: String = "",
        asrEndpoint: String = "",
    ): VoiceSettings = VoiceSettings(
        apiKey = "",
        asrEndpoint = asrEndpoint,
        asrResourceId = "",
        asrUid = "uid-123",
        ttsEndpoint = "",
        ttsResourceId = "",
        ttsSpeaker = "",
        ttsSampleRate = 24000,
        voiceProvider = VoiceSettings.VOICE_GOOGLE,
        googleApiKey = apiKey,
        googleBaseUrl = baseUrl,
        googleProject = project,
        googleLocation = location,
        googleAsrModel = asrModel,
        googleTtsModel = ttsModel,
        googleLanguage = "",
    )

    private class NoopAsrListener : net.kuafuai.andee.asr.AsrEngine.Listener {
        override fun onReady() {}
        override fun onPartial(text: String) {}
        override fun onFinal(text: String) {}
        override fun onError(msg: String) {}
        override fun onClosed() {}
    }

    private class NoopTtsListener : net.kuafuai.andee.tts.TtsEngine.Listener {
        override fun onOpen(sampleRate: Int) {}
        override fun onAudio(pcm: ByteArray) {}
        override fun onSentenceStart(text: String) {}
        override fun onSentenceEnd(text: String) {}
        override fun onDone() {}
        override fun onError(msg: String) {}
        override fun onClose(reason: net.kuafuai.andee.tts.TtsEngine.CloseReason) {}
    }

    @Test
    fun defaultAsrUsesGoogleAiStudioFreeEndpoint() {
        val asr = GoogleAsr(sampleSettings(), NoopAsrListener())
        val url = asr.liveEndpointUrl()
        assertTrue("Live URL should point to Google AI Studio GenerativeService",
            url.contains("generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"))
        assertTrue("Live URL should contain API key", url.contains("key=test-gemini-key"))
        assertEquals("models/gemini-2.5-flash-native-audio-latest", asr.modelPath())
    }

    @Test
    fun asrCustomWebSocketOverridesDefault() {
        val asr = GoogleAsr(
            sampleSettings(baseUrl = "wss://my-proxy.com/ws/live"),
            NoopAsrListener()
        )
        val url = asr.liveEndpointUrl()
        assertEquals("wss://my-proxy.com/ws/live?key=test-gemini-key", url)
    }

    @Test
    fun asrVertexFallbackWhenProjectNumberConfigured() {
        val asr = GoogleAsr(
            sampleSettings(
                baseUrl = "https://aiplatform.googleapis.com",
                project = "1234567890",
                location = "asia-northeast1",
                asrModel = "gemini-live-2.5-flash",
            ),
            NoopAsrListener()
        )
        val url = asr.liveEndpointUrl()
        assertTrue(url.contains("aiplatform.googleapis.com/ws/google.cloud.aiplatform.v1beta1.LlmBidiService/BidiGenerateContent"))
        assertEquals(
            "projects/1234567890/locations/asia-northeast1/publishers/google/models/gemini-live-2.5-flash",
            asr.modelPath()
        )
    }

    @Test
    fun defaultTtsUsesGoogleAiStudioEndpoint() {
        val tts = GoogleTts(sampleSettings(), NoopTtsListener())
        val url = tts.ttsEndpointUrl()
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash-tts:streamGenerateContent?alt=sse&key=test-gemini-key",
            url
        )
    }

    @Test
    fun voiceMappingResolvesErinomeAndAchernar() {
        assertEquals("Erinome", GoogleTts.voiceFor("zh_female_vv_uranus_bigtts"))
        assertEquals("Achernar", GoogleTts.voiceFor("zh_female_roumeinvyou_uranus_bigtts"))
        assertEquals("Erinome", GoogleTts.voiceFor("Erinome"))
        assertEquals("Achernar", GoogleTts.voiceFor("Achernar"))
        assertEquals("Kore", GoogleTts.voiceFor("Kore"))
        assertEquals("Erinome", GoogleTts.voiceFor(""))
        assertEquals("Erinome", GoogleTts.voiceFor("unknown_speaker"))
    }

    @Test
    fun andeeBallLookMatchesOriginalAesthetic() {
        val look = ANDEE
        assertEquals("andee", look.name)
        // Original velvety soft blue base: 0.250f, 0.460f, 0.870f
        assertEquals(0.250f, look.bodyBase[0], 0.01f)
        assertEquals(0.460f, look.bodyBase[1], 0.01f)
        assertEquals(0.870f, look.bodyBase[2], 0.01f)

        // Gestures and animation flags preserved
        assertTrue(look.gestures)
        assertFalse(look.rimDark)
        assertEquals(BallLooks.ALL.first().name, "andee")
    }
}
