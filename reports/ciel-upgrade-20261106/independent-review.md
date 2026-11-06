# Independent source review — 6 November 2026

Reviewer: `integration_review`, operating separately from the production implementation owners. Initial scope: Ciel Android/source changes through `0fabde4d38c303ad48a158bffab4c10dcee813d1` on `codex/ciel-mobile-upgrade-20261106`, including the new Live controller and phone dispatch boundaries. Production inspection was read-only. The frozen source manifest is in `evidence/frozen-source.json`; the follow-up scope is recorded below.

Verdict before the authoritative build: no unresolved P0/P1 findings in the reviewed paths. The reported P2 lifecycle findings were also repaired. This is a source review; it does not establish that tests, a signed artifact, installation, or live Google audio passed.

## Findings verified as repaired

- Stop fences queued session creation and submissions; trusted owner input and untrusted notifications cannot share a session through a queue race.
- A delayed input transcript does not cancel the current model turn's tools or playback. Cancelled sessions cannot replay stale screenshots/actions.
- Scheduled reminders route through Live with trust and Stop checks and record their actual outcome.
- Legacy card/TTS tools that interfere with Live focus are excluded from Live advertisements.
- Home shows active meeting recording and a Stop control. A stale meeting handoff cannot release a new conversation's microphone.
- Muting during a typed answer's final audio drain releases focus and clears the speaking state.
- A disconnected controller's active screen requests stop at their next checkpoint without cancelling another controller's work.
- Direct typing, IME submission, node actions and global actions have final cancellation checkpoints.

## Follow-up production review — `dd1dd38`

Reviewed delta: `0fabde4d38c303ad48a158bffab4c10dcee813d1..dd1dd38a4510a305cbef08f458436b7c18c7ee76`. Verdict: no new P0/P1 findings or unresolved P2 regressions identified in the requested production paths. The source contracts support proceeding with the pending CI and artifact validation.

- `DeviceInfoController`: location reads handle permission revocation explicitly. Listener removal encloses the entire registration/wait sequence, including a failure after only some providers registered and an interrupted wait. Existing permission-error and location-result shapes remain intact.
- `A11yIme`: every public entry returns its existing unavailable-channel result before accessing API 33 input methods on older devices. Private helpers declare their API requirement, and final `beforeWrite` cancellation checkpoints remain in place.
- `Scheduler`: API 30 can use exact alarms without calling the API 31 permission query. Revocation between checking and scheduling falls back to an inexact alarm with the same trigger and pending intent; the log records the actual branch used.
- `ScanActivity`: the CameraX opt-in acknowledges the borrowed `ImageProxy.image` API. ML Kit processing retains the proxy until completion, and the no-image path still closes it.
- Manifest: telephony is explicitly optional, matching the existing optional call/SMS tools without adding permissions or making cellular hardware an installation prerequisite.
- `StandAside`: all blur-radius reads and writes are guarded by API 31 checks. Saving/restoring overlay flags and alpha retains the existing behavior on API 30.
- `CameraChrome`: typed `View` properties preserve press/release values and durations. `StateListAnimator` still assigns the owning view to its animator set and children.
- Shared renderer and GL backends: Android GLES 2.0 and desktop OpenGL 2.1 both expose the new separate-alpha call. Straight shader RGB is blended into their premultiplied surfaces with normal coverage `As + Ad * (1 - As)` or additive coverage `As + Ad`, preserving each pass's RGB equation. Every renderer blend selection uses the shared helper. The rim shader's nonnegative `pow` base avoids a fractional-power NaN from rounding.
- Nova: the raised, narrower antennae, positive taper exponent and complete RGB triplets fit the existing horn mesh/shader contracts. Removing `hornShade` selects its existing zero default so the explicit gold color is used. The original `BallLook.kt` declarations are unchanged.

Validation here was source inspection and `git diff --check`; no Gradle or runtime execution was performed. The lint-engine override was authored by this reviewer and is outside this independent production review. The 423 passing tests in `evidence/ci-42201498655-tests.json` belong to `0fabde4`, and the alpha diagnostic used that snapshot's compiled core with proxy/parameter overrides. Neither establishes a pass for `dd1dd38`. CI run `42205992315` and inspection of the exact resulting artifact remain pending at this review boundary.

## Explicit limits

Automatic notification triage is unavailable in Gemini Live and is identified as a Custom model feature. Manual notification handling remains connected. Google's configured key was rejected before setup completion, so successful Live audio and phone latency remain unverified. Final package review follows the central build and signing checks; this document must not be used as proof that an unsigned or different artifact was reviewed.
