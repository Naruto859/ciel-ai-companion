# Ciel phone-control audit — 6 November 2026

Scope: Android phone tools, their transport and cancellation boundaries, device permissions, input reliability, and the readiness checks. Baseline: Ciel source commit `e98627d`. The baseline ARM64 APK was identified separately by the primary agent. These are source findings; they do not establish physical-phone behavior.

## Findings and implemented changes

| Finding | Evidence in the baseline | Change |
| --- | --- | --- |
| Critical: the developer socket accepted unauthenticated phone control and broadcast private events to the LAN | `BodyWsServer` defaulted to `0.0.0.0`, and `onOpen` added every connection to `active` | Service is off by default; optional server binds loopback, requires a per-install bearer token, rejects browser origins, and admits a socket before serving commands or events. Paired peers retain their separate transport. |
| Private notebook tools could be invoked by a hub that guessed their names | `forHub()` filtered advertisements, but `BodyWsClient.handleRequest` used unrestricted `methodOf()` | Added `hubMethodOf()` and enforce it for every built-in hub request. Extensions cannot reuse built-in names or internal method names to bypass the filter. |
| A fast call-extension reply could be lost, and another client could spoof it | `SynchronousQueue.offer(..., 0)` before the caller polled; response handler did not check the sending socket | `ExtensionReplies` retains an early reply, binds each request to its exact connection, and fails only that connection’s requests on replacement/disconnection. |
| Reconnects could forward old results to a new hub, or an old close callback could disturb a replacement | Workers replied through mutable `conn`; old callbacks always scheduled reconnects | Requests and replies retain their source connection. Stale connection callbacks and queued work are rejected. |
| A disconnected controller could finish the later steps of an active multi-swipe command | Socket identity was checked only before dispatch; the active command retained its valid Stop generation | Both socket workers now bind each command to its exact live controller. Every cancellation checkpoint checks that lifetime, including bounded checks during `screen.wait`; disconnecting a passive client does not cancel another owner's task. |
| Stop did not stop `screen.wait` or invalidate queued phone commands | `cancelTask()` only dimmed the UI; `screen.wait` called `Thread.sleep` | Generation-based cancellation, stop-aware waits, checkpoints before gesture/typing actions, and queue tokens. A legacy hub is suspended until the next user turn after Stop. An Android gesture already dispatched may still finish. |
| Stop could arrive during slow accessibility-node resolution, before the resolved node submitted input | `submitInput` checked only at entry, before Binder calls to resolve the target | Check again immediately before `ACTION_IME_ENTER`, direct click/long-click, and global navigation actions. |
| Concurrent debug/hub/local screen operations could undo each other’s overlay guards | Cached unbounded server executor; separate callers shared screenshot/gesture overlays | Bounded serial transport executors and a reentrant shared screen-operation lock. |
| Accessibility text input could type twice after an uncertain first write | `A11yIme.replace` returned null if readback vanished after `commitText`; null selected ADBKeyboard fallback | `TextInputAttempt` distinguishes a no-write fallback from an uncertain write. It never retries an already-submitted text/password through another keyboard. Partial-buffer replacement is refused before modifying text. |
| Android 12+ precise-location requests omitted coarse location, and approximate-only access was still reported as missing | The permission catalog issued only `ACCESS_FINE_LOCATION` and checked only that grant | Fine and coarse location are requested together; approximate location satisfies the permission flow and remains usable when the user chooses it. |
| Several device errors named permissions the request tool could not open | SMS, calendar writing, and steps returned `send_sms`, `write_calendar`, and `activity_recognition`, but the catalog used different IDs | Return the actual `sms_send`, `calendar_write`, and `steps` IDs; all these permission errors now include a machine-readable `permission_id`. |
| Mixed permission requests could claim every permission was granted when only runtime permissions were granted | Result calculation ignored missing overlay/listener access | Report actual combined grants, expose `missing_ids`, and open one pending special-access page at a time. A late dialog response is bound to its own request ID. |
| Volume, brightness and clock tools lacked their Android declarations | Manifest omitted `MODIFY_AUDIO_SETTINGS`, `WRITE_SETTINGS`, and `SET_ALARM` | Added declarations through the UI/manifest work. Brightness remains an explicit user-controlled special grant and has a request route. |
| Brightness could report a failed settings write as success | `runCatching { Settings.System.putInt(...) }.isSuccess` ignored a returned `false` | Verify permission and both Boolean results; validate the requested range before writing. |
| Location/sensor listeners could survive interruption; GPS-only registration could starve network location | `providers.any` stopped at the first successful registration; cleanup ran only after normal waits | Register enabled candidates, support coarse access, bound waits, and unregister in `finally`. |
| Invalid calendar times or a hardcoded calendar ID could target the wrong calendar | No interval validation and fallback `calendarId = 1` | Require a valid forward interval, read/write permission, and an actual visible writable calendar. |
| SMS, direct dial and alarm handoffs claimed completion Android had not confirmed | Immediate `sent:true`, `dialed`, `alarm_set` after asynchronous submission | Report submission/request accurately; split long Unicode SMS messages into Android multipart segments. Carrier delivery and a connected call are not claimed. |
| Ciel contacted an upstream service and offered upgrades for the wrong application | `SelfCheck.probeVersion` sent version/model/brand/Android/language/device ID to `https://api.heyandee.com` and linked Andee/CodeFlying updates | Removed that automatic request and its client/constants. Readiness shows the installed Ciel version/build locally. |
| Readiness treated Gemini Live like an OpenAI/legacy speech backend | Backend `/models` + bearer/chat fallback and separate legacy credentials | Direct authenticated Gemini model-metadata check with the key in a header. Success is a note that model metadata is available, not a claimed successful Live audio session. Accessibility/overlay are optional for in-app Live conversation. |

## Verification

The phone scope adds 30 regression test methods:

- `ControlPortAccessTest` (3): unauthenticated loopback, wrong/truncated token, browser origins, LAN, absent configuration.
- `ExtensionRepliesTest` (4): reply before wait, wrong socket, replacement isolation, late reply.
- `CommandCancellationTest` (5): queued stale work, wake a long wait, interrupted worker, disconnect after the first swipe with replacement isolation, disconnect while waiting without a later screenshot.
- `HubToolAccessTest` (2): all private tool names rejected by hub lookup, extension-name impersonation.
- `DeviceCommandInputsTest` (3): alarm bounds, calendar interval/overflow, Unicode SMS drafts.
- `TextInputAttemptTest` (4): no duplicate fallback after readback loss/commit exception, safe no-write fallback, Stop before commit.
- `PermissionOutcomeTest` (7): mixed grants, failed handoff, fine/coarse pairing, approximate-only access, request-ID isolation, early result, brightness route.
- `SelfCheckMergeTest` (2 added): offline/missing-key facts replace stale probe success; local Ciel version replaces a stale upstream warning.

Final application source `549ba53` passed **423 unit tests: 328 core and 95 app**, including the phone regressions above, in CI run `42213575554`. Release lint passed with zero errors and 513 warnings; canonical and Preview release assembly also passed. [Recorded counts](ciel-upgrade-20261106/evidence/ci-42213575554-source.json) distinguish this final build from earlier failed lint attempts. The primary agent owns signing and exact-APK emulator checks. No physical-device, SMS, call or calendar action was performed by this audit agent.

## Release lint follow-up

- Location now uses explicit fine/coarse permission checks and handles `SecurityException` separately, returning a permission result when access changes. Listener cleanup covers partial registration as well as interrupted waits.
- API 33 accessibility input entry points now guard their Android version explicitly, and their private API 33 helpers declare `@RequiresApi`. The earlier connection guard already provided a runtime fallback; the new boundaries make that contract visible to lint.
- Exact-alarm access checks no longer call the API 31 method on Android 11. A grant revoked between checking and scheduling falls back to an inexact reminder, and the log reports the actual path used.
- The scanner explicitly opts into CameraX's `ExperimentalGetImage`; ML Kit borrows the image while `ImageProxy` retains ownership and is closed on completion.
- The manifest declares cellular hardware optional, matching the app's optional call/SMS tools and allowing tablets and ChromeOS devices without a modem.

## Capability review and remaining runtime work

- Audited 96 literal built-in schema methods against dispatcher/host handlers. `screen.drag` is intentionally desktop-only and excluded by `AndroidBrainHost`. Several `note.*` methods are handled by `LifeTools` or `NotifyRuleTool`. `note.fold` is intercepted by `LocalBrain` and is excluded from Live.
- The optional `tap_target` locator uses a separate vision/chat request; a native-audio model is not a valid substitute. Live excludes it and uses screen elements plus screenshot/zoom coordinates. The final compiled Live setup exposes 90 built-in declarations.
- A native user-picked document-reading tool is still absent in this baseline. Upstream issue [37](https://github.com/kuafuai/andee/issues/37) tracks it; open [PR 38](https://github.com/kuafuai/andee/pull/38) contains a proposal. That PR also independently confirms the developer-port issue; it was not imported wholesale.
- `VoiceConfig.current()` is used by local UI only in the audited Android/peer/transport sources. No dispatcher config-read endpoint was found. Provider credentials are not included in hub tool registration or peer metadata in those sources.
- Physical-phone checks remain essential for OEM accessibility/keyboard behavior, actual gestures and Stop timing, SMS/call/clock/calendar providers, approximate location, and protected screens. The source changes do not bypass lock screens or Android permissions.
