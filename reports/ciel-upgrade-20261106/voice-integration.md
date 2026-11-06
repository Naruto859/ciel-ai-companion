# Ciel native Live voice integration

Status on 2026-11-06: implementation frozen for the parent's authoritative Android/core build. `git diff --check` passes. This report does not claim that a physical phone or a successful Gemini Live session has been tested.

## What changed

- Android now defaults to direct Gemini Live with one user-provided Google AI Studio key. The normal Live path has no CodeFlying, Cloudflare, Huoshan/Volcengine, GCP project, or separate ASR/chat/TTS dependency. Advanced Custom model and paired Hub modes remain available.
- The direct WebSocket sends 16 kHz mono PCM input and receives 24 kHz PCM output. Server VAD uses a 350 ms silence setting; Android capture sends 20 ms chunks. These are settings, not measured end-to-end latency.
- Session setup requests audio transcripts, session resumption, and context compression. Reconnect attempts and outbound queues are bounded. The standard `gemini-3.8-live` model uses blocking tools; the incompatible extended-thinking model is explicitly refused.
- Audio, transcripts, tool calls, cancellation, and completion fields are processed independently. A late input transcript does not cancel a current tool or spoken answer.
- Tool jobs capture the dispatcher's cancellation generation before entering a serial queue. Per-session completed call IDs prevent replaying an already performed action. Cancelled work cannot deliver an old screenshot. A disposed session cannot be restarted.
- Stop invalidates queued Talk/text/reminder requests before they can publish a session. It also closes Live, cancels current phone work, and suspends inbound Hub requests. Debug server requests carry the cancellation generation captured at receipt.
- One Live audio focus owner handles microphone and playback, including typed answers and scheduled responses. Playback tracks are separate per answer, interruption clears the old track, and muting a draining answer releases non-mic focus. Meeting handoff is fenced by both session epoch and dispatch generation.
- Manual notification Look, meeting/call summaries, and reminders retain their external-material trust flag through a separate Live session. Session trust selection happens on the serial audio worker, so simultaneously queued owner and outside-material submissions cannot reuse the wrong trust mode.
- Scheduled todos now route to Live after a bounded quiet wait. Stop/removed-todo/busy/error cases are recorded. A complete model response is recorded as `responded`; it does not mark the underlying task done. Missed reminders remain visible in the timeline and in the next conversation's historical context.
- Reminder bubble replies work in Live; bubbles wait while Live is active. Page-close and scene updates enter as silent `turnComplete=false` context. The scene chip and app-trigger entry also support Live.
- Foreground conversation APIs work without overlay or accessibility grants. The service exposes active meeting state so the Home screen can keep its Stop control available.
- API keys have no built-in fallback. URL query values are encoded and redacted from errors/logs; `GeminiLiveConfig.toString()` masks its key.

## Deliberate Live limits

- `ui.ask`, `ui.alert`, and legacy `tts.speak` are omitted because their independent TTS focus interrupts Live. Needed choices are asked in the model's native voice and await a user reply.
- Automatic notification rules, autonomous review/planning, and topic folding remain Custom-model features. `note.notify_rule` and `note.fold` are not advertised in Live.
- Desktop drag and the separate locator-model `screen.tap_target` tool are not advertised. Supported screen element/coordinate tools still dispatch on the phone, subject to permissions and cancellation.
- Receiving delegated peer-host work is still a LocalBrain feature. Live does not claim arbitrary desktop, root, or Android-restricted capabilities.
- Free-tier Gemini use depends on Google's current quotas and account/model availability. No zero-cost unlimited-service guarantee is made.

## Verification evidence

Twenty new core tests were added for the Live path:

| Test file | Cases | Coverage |
| --- | ---: | --- |
| `GeminiLiveProtocolTest` | 6 | Setup, declaration fidelity, key redaction, PCM rates, rejected audio format, tool identity/errors |
| `GeminiLiveSessionTest` | 10 | Combined packets, replay, cancellation, unavailable tools, backpressure, disposed lifecycle, stale screenshots, late transcripts, silent context |
| `LiveRequestGateTest` | 1 | Queued Talk/text followed by Stop cannot create/send; a new request can |
| `LiveScheduledTurnTest` | 3 | Original trust/repeat semantics, Stop during quiet wait, supported scheduling/omitted legacy tools |

The parent owns the central `:core:test`, `:app:testDebugUnitTest`, APK build, and artifact checks. Their final output is authoritative; this agent did not run a competing Gradle build.

The parent's bounded network probe reached Google's socket, then Google closed with code 1007: `API key not valid. Please pass a valid API key.` This occurred before `setupComplete`, so model availability, tool-schema acceptance, audio, and response latency remain unverified. No real phone tool was executed by that probe.

`research/live-setup-validation.json` contains 92 built-in declarations for a device with no runtime call extension or paired peers. Its provenance file records how the final availability filter was applied and explicitly requires comparison with the compiled implementation. It contains no API key.

Physical-device validation still needs microphone capture, speaker echo/interruption, background/foreground behavior, permission-denied paths, meeting Stop, and representative phone actions using a working key.

## Sources consulted

- https://ai.google.dev/gemini-api/docs/live-api/capabilities
- https://ai.google.dev/gemini-api/docs/live-api/tools
- https://ai.google.dev/gemini-api/docs/live-api/session-management
- https://ai.google.dev/gemini-api/docs/pricing
- `/root/.agents/skills/gemini-live-api-dev/SKILL.md`
- `/root/.hermes/skills/devops/android-voice-companion-workarounds/SKILL.md`

Saved research pages and the credential-free setup fixture are in `reports/ciel-upgrade-20261106/research/`.
