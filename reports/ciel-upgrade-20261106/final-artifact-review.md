# Independent final artifact review — Ciel Preview r2

Reviewer: `final_artifact_review`, independent of implementation and signing. Production inspection was read-only. No Gradle build, emulator or Google request was started by this reviewer.

**Verdict:** no concrete P0/P1 blocker found in the reviewed source, exact package or exercised Android UI paths. The r2 APK's installation, launch, avatar persistence, large-font navigation, blank-key rejection and wide-viewport navigation have supporting evidence across the two runs below. This is an explicitly limited Preview: successful Gemini voice and physical-phone behavior remain unverified.

## Exact candidate

- Production source: `549ba53619e3d78ce0f3887dd291f10e5400033a`.
- Build CI: [42213575554](https://github.com/Naruto859/ciel-ai-companion/actions/runs/42213575554).
- Runtime harness reviewed through `1debaf38ee004c83a6e41f233c8a99394245fc38`; it does not change app/core source.
- APK: `/root/ciel-artifacts-20261106/final-r2/ciel-preview-0.12.0-24-r2-universal.apk`.
- SHA-256: `a2695f68f8151d4c44680e8b6d980a4ab9d96241c39ec185267c63c6d2211726`.
- Size: 16,037,798 bytes.
- Package: `ai.ciel.companion.preview`, label **Ciel Preview**, version **0.12.0 (24)**; minimum API 30, target API 34.
- Signer certificate SHA-256: `1e11584f8c081fc18f0ddaf6467a8268f59bf5a37c2b0bd55105bb7af0a17bac`.

Detailed observations: [final-artifact-review.json](evidence/final-artifact-review.json).

## Source and package observations

1. **All 368 frozen source hashes match.** The APK's `META-INF/version-control-info.textproto` records source revision `549ba53619e3d78ce0f3887dd291f10e5400033a`. No app/core source change was found between that revision and the reviewed workspace.
2. The CI unsigned Preview has SHA-256 `04d8702155f70e076f430dec4dab68a88330269325a47f5512387a976f454f47`. Every shared ZIP member has identical uncompressed bytes in the unsigned input and signed r2 file. No member was removed; only three signing metadata entries were added. Signing preserved the CI payload. This is not a reproducible-build claim.
3. Android Build Tools 34 `apksigner verify --verbose --print-certs` verifies r2 using APK Signature Scheme v3 and the expected certificate. `zipalign -c 4` passes. `aapt dump badging` reports no debuggable application flag and names `CielHomeActivity` as the launcher.
4. Both ARM64 native libraries have 64-bit ELF headers with machine type AArch64 (`183`). ARMv7, x86 and x86_64 headers also match their directories. The binary manifest contains no required split flags or shared UID.
5. The three provider authorities and the signature permission remain package-scoped to `ai.ciel.companion.preview`. The binary manifest is byte-identical to the prior candidate, whose authorities were compared against old Ciel and had no collisions. The application, accessibility service, body service and notification listener are labeled **Ciel Preview**. Internal activities, providers, the body service and alarm receiver are not exported. The accessibility service is protected by `BIND_ACCESSIBILITY_SERVICE`, and the library profile receiver by `DUMP`. Backup is disabled. Actual coexistence with the old app is not established by these declarations alone.
6. Packaged `LICENSE`, `THIRD_PARTY_NOTICES.md` and `CIEL_NOTICE.md` match source exactly. `assets/andee_key.txt` is empty. The DEX/assets scan found no full Google API key format, private-key PEM, keystore, PKCS#12 or `.env` member. This is bounded pattern evidence, not an exhaustive secret-detection guarantee.
7. The production delta from the previous candidate consists only of the English and Chinese navigation labels. `aapt dump --values resources` confirms **Avatar** and **形象** are in the actual r2 APK. DEX, native libraries and the binary manifest are unchanged from r1; only `resources.arsc`, VCS metadata and signing metadata differ. The actual r2 Home image at font scale 1.5 confirms **Avatar** stays on one line, resolving the earlier navigation wrap.
8. All **338 core class files** in this build match the actual-renderer build from CI `42205992315`, with no added or removed class. The inspected avatar renderer output remains relevant to the engine in this candidate; it does not replace Android UI or audio-driven animation evidence.
9. Current build XML independently confirms **328 core + 95 Android tests**, with zero failures, errors or skips. Lint has **0 errors / 513 warnings**. Earlier source review and resolved lifecycle/security findings are retained in [independent-review.md](independent-review.md). No new P0/P1 issue was identified in this bounded final review.

## Harness review

The `62a8b1d` signer-parser fix accepts the current AOSP SDK-range v3 signer format while excluding public-key and SourceStamp digests. Exactly one certificate identity is required.

`549ba53` records actual UI hierarchy for secure Setup pages without requesting a prohibited screenshot. Other screens still require valid PNGs. The Window Manager query/restore arguments match [Android 14's WindowManagerShellCommand](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/services/core/java/com/android/server/wm/WindowManagerShellCommand.java). `549787a` also snapshots dormant `user_rotation` and `accelerometer_rotation` preferences before mutation and includes them in cleanup restoration.

The full r2 run exposed two remaining harness assumptions: Android did not expose the inline `EditText.error` caption as a text node, and rotation commands did not produce a landscape image. `1debaf3` checks observable blank-key rejection through retained Setup/key input and persistent missing-key state after restart. It explicitly does not verify the inline error caption. It also requests a real `wm size` viewport, checks exact PNG dimensions and restores the prior override. This tests wide layout, not a rotation sensor. Targeted mode retains fresh installation, exact-byte provenance, startup and crash checks, and identifies its scope in the report.

`skip_unsigned_smoke` applies only when explicitly selected for the CI-key build; a supplied signed-APK run still follows the exact-byte verification and runtime path. A source-only run must not be presented as a runtime pass.

## Exact r2 runtime observations

Both runs used Android 14's `sdk_gphone64_x86_64` emulator. Their supplied and pulled installed APK hashes equal the r2 digest above; the local artifact was rehashed and remains unchanged. The targeted run's Build Tools 37 package report also verifies the expected certificate, alignment and package identity.

| Run | Recorded result | Evidence supplied |
| --- | --- | --- |
| [42215450664](https://github.com/Naruto859/ciel-ai-companion/actions/runs/42215450664) — full scope | **7/9 passed; overall failed** | Exact install, resumed Home/missing-key state, original avatar, Moss/Nova preview isolation and saved persistence, large-font tabs, no app crash/ANR. The inline-caption and window-manager rotation assertions failed as described above. |
| [42217850551](https://github.com/Naruto859/ciel-ai-companion/actions/runs/42217850551) — targeted `setup-layout` | **5/5 passed** | Fresh exact install and resumed Home, blank-key rejection after restart, actual 1920×1080 viewport/tab checks, no app crash/ANR. |

This is **combined evidence from two runs of the same APK**, not a claim that a single full-suite rerun passed. The targeted run did not repeat avatar-persistence or large-font checks.

The retained [full report](evidence/runtime-r2/full/smoke-report.json) and [targeted report](evidence/runtime-r2/setup-layout/smoke-report.json) were inspected, together with XML, image and log evidence. All 14 full-run and seven targeted capture records match their on-disk PNG/XML hashes. Manual inspection confirms:

- **Blank-key handling:** after Save with no key, Setup remains selected and the empty Gemini key input has focus. After a process restart, Home still displays **Add your Gemini key to begin**. No inline error-caption rendering or valid-key save is claimed from this test.
- **Avatar and larger text:** saved Moss has the expected rendered character and Current label. The r2 large-font Home image keeps all navigation labels on one line. Persistence assertions include process restarts and distinguish temporary previews from saved choices.
- **Wide layout:** the actual Home and Avatar PNGs are 1920×1080. XML places all three fixed navigation tabs inside the screen and exposes a scrollable content area. The tall hero extends below the initial viewport; this does not establish that all content is visible at once or that every wide-layout control was exercised.
- **Runtime state:** both reports end with the app's own resumed activity/PID and no attributed app crash/ANR. The targeted log contains no matching fatal/ANR entry. Cleanup records successful commands for stopping the test app and restoring display/preferences; neither run reports cleanup command errors.

## Remaining gates and limits

- The available Google key was rejected before `setupComplete`; successful Live audio, provider acceptance of the current tool declarations, real phone tools and response latency remain unverified. No key retry was made by this reviewer.
- Physical-phone audio routing, microphone permission flow, OEM accessibility/keyboard behavior, device/sensor rotation and phone actions remain untested. The review cannot certify every Android device or unrestricted phone control.
- Automatic notification triage/reviews remain documented Custom-model features. Native picked-document reading is still absent. The release must remain an explicitly limited Preview.

These runtime results apply only to the stated r2 digest. Any packaged-source change requires a new artifact and review of its delta.

## Historical candidate

The previous `966180f9…` APK is superseded and unpublished. Its [review](final-artifact-review-r1.md) and [JSON evidence](evidence/final-artifact-review-r1.json) are retained separately. Run `42211532681` proved that file installed, launched, preserved avatar choices and produced no app crash/ANR; the overall run failed on secure screenshot assumptions and rotation, and its large-font screenshot exposed the navigation wrap. Those observations are not counted as an r2 runtime pass.
