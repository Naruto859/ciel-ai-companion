# Ciel mobile upgrade — working evidence ledger

Requested on 2026-11-06. Implementation and independent source review are complete for this pass. Build, package and runtime gates remain in progress; this is not a release certification.

## Target and user intent

- Source: `/root/ciel-companion-clean`, fork of <https://github.com/kuafuai/andee>.
- Starting source commit: `e98627d`; working branch: `codex/ciel-mobile-upgrade-20261106`.
- Upstream inspected: `5e63543` (2026-11-05).
- Existing distributed APK: <https://cielmanu.indevs.in/dl/ciel-arm64.apk>.
- Existing APK SHA-256: `9167cff45b0c3cfc27fc73e0e61d92f5fc8bd519418d2af1cc7811d1aa454a7c`.
- Package `ai.ciel.companion`, version `0.11.0` (23), arm64, Android 11 minimum; distributed artifact is debuggable.
- Improve the actual mobile application: reliable authorized phone tools, direct Gemini Live conversation, understandable setup and home UI, retain the first/default avatar and replace the other selectable designs.
- User mentioned “Cloudflare” and a provider starting with “V”. The source has CodeFlying and Huoshan/Volcengine defaults; direct Gemini should not require them. Cloudflare also fronts the existing download domain; this is a separate infrastructure concern.
- Elysia exists at `/root/work/elysia`. Whether this is the user's “Aliza” reference is awaiting optional clarification.
- Phone model/Android version requested; no physical ADB device was connected at initial inspection.

## Scope and ownership

| Workstream | Owner | Current scope |
| --- | --- | --- |
| Voice | `voice_gemini` | Gemini Live protocol/audio, config, BodyCoreService wiring, focused tests |
| UI and avatars | `ui_avatars` | Native home, setup, avatar studio, shared ball renderer, UI/resources |
| Phone controls | `phone_controls` | Tool dispatch, cancellation, device/screen behavior, command transport admission |
| Integration and release | root | Source/APK comparison, skills/research, build/test setup, package verification, runtime exercise and documentation |

Parallel scopes follow the REA skill's broad-investigation workflow. Existing changes in `/root/workspace-andee` are outside the target and are preserved.

## Findings under active repair

| Finding | Evidence | Status |
| --- | --- | --- |
| Default provider cannot work without unavailable CodeFlying/Volcengine credentials | VoiceConfig defaults and provider routing | Voice workstream implementing direct Gemini setup |
| Existing Gemini flow is sequential ASR, model, TTS rather than a live duplex session | Existing GoogleAsr, LlmClient, GoogleTts paths | Live protocol/audio integration in progress |
| Local command server exposes device operations without client authentication | BodyWsServer; upstream PR 38 also identifies this | Disabled-by-default, per-install authenticated transport in progress |
| Stop does not invalidate queued actions and long waits | Dispatcher/wait paths | Cancellation guards and regression tests in progress |
| Remote transport can invoke tools intended only for local use | Advertised schemas vs dispatch enforcement | Dispatcher admission fix in progress |
| Extension response can come from the wrong connection or be dropped before a waiter starts | Extension-response handling | Ownership and bounded buffering fix in progress |
| No durable launcher home; key controls depend on undiscoverable overlay gestures | Launcher manifest and SelfCheckActivity | New native home in progress |
| Tiny text, low contrast selected states, stale avatar selection and shared preview state | Existing theme/expression/GL preview code | UI workstream fixes in progress |
| Published artifact is a debug APK | `aapt dump badging` | Prepare a verified signed release; update compatibility must be checked |

## Research and verification

- Hermes `find-skills` and Firecrawl were found and read; Firecrawl access works.
- REA `reverse-engineer-anything` was read; installed CLI is 6.7.0. Existing-APK evidence is being collected under `evidence/`.
- Current official Gemini documentation was researched through Firecrawl. Do not substitute remembered model names for these current sources.
- Tests/build use JDK 17, SDK `/opt/android-sdk`, one Gradle worker and bounded heap on this shared VPS.
- Existing tests are the baseline; meaningful new tests target protocol, cancellation, security admission and user-visible state transitions.
- No claim of physical-device installation, microphone quality, real-world response latency, unrestricted phone control, unlimited free API usage, or exhaustive absence of bugs is made from static analysis.
- New avatars are not considered delivered until their actual renderer output has been inspected.

## Completion checklist

- [x] Complete the scoped concrete fixes and cross-workstream integration; independent source review found no remaining P0/P1 findings in the reviewed paths.
- [ ] Verify source tests and release build/lint.
- [ ] Inspect actual new avatar and home-screen output.
- [ ] Exercise startup, setup, stop, permissions and tools on a suitable isolated Android runtime.
- [ ] Verify Gemini Live against protocol tests and, if an authorized configured key is available, a bounded live API probe.
- [ ] Verify final APK identity, ABIs, signature, secret exclusion and installation compatibility.
- [ ] Independent review of current diff and final artifact.
- [ ] Document exact remaining limitations and provide source, screenshots and installable artifact as supported by evidence.


## Current validation status

- The first local Gradle attempt failed after overlapping source edits produced a mismatched main/test snapshot; no test-suite pass was recorded from that attempt.
- All app/core owners subsequently froze their changes. The second authoritative command runs `:core:test :app:testDebugUnitTest :app:lintRelease :app:assembleRelease -PuniversalApk=true -PdefaultLang=en` with JDK 17 and bounded memory.
- The API 35 software-only emulator could not reach an online ADB state on this VPS. Its owned process was stopped to release memory; no unrelated services were stopped. A lean API 31 emulator and a hardware-accelerated CI emulator path are being prepared.
- Google rejected the configured key before `setupComplete`. The Hermes and Elysia key values were identical; no repeated quota-consuming attempt with the same key was made. No valid latency measurement exists.
- The default assistant identity is now Ciel while explicit owner-defined names/personas are preserved. Stable private signing material remains outside the source tree. The existing public debug APK has a different signer, so a separately identified Preview package is planned to avoid deleting its data.
- A scan of the then-current 98 changed/new files found no known credential values or private key material. The final APK still requires a separate packaged-content scan.
