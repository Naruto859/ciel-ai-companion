package net.kuafuai.andee.voice

/** Legacy cascade-only features must not be promised by a native Live session. */
object LiveToolAvailability {
    private val unavailable = setOf(
        "screen.drag",       // Desktop only.
        "screen.tap_target", // Needs the separate locator backend.
        "note.fold",         // Intercepted by LocalBrain, not the dispatcher.
        "note.notify_rule",  // Automatic triage currently uses the Custom model backend.
        "tts.speak", "ui.ask", "ui.alert", // Their separate TTS focus stops native Live audio.
    )
    fun supports(method: String): Boolean = method !in unavailable
}
