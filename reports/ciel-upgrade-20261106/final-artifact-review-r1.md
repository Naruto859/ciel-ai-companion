# Independent final artifact review — 6 November 2026

Reviewer: `final_artifact_review`, independent of the implementation and signing owner. Production inspection was read-only; no build, emulator, phone action or Google request was started.

**Initial bounded verdict (before runtime):** no concrete P0/P1 blocker found in the reviewed source, signing, package, ABI or coexistence declarations. Later runtime observations are recorded below. Successful Gemini voice remains unverified.

**Artifact status:** the APK identified below is now a superseded, unpublished candidate. Its later runtime results and the replacement-source delta are recorded separately below. They do not certify the replacement APK.

## Exact review target

- Production source: `0afd4b1d82a4470d9007aea9cc3206433bcee11d` in `/root/ciel-companion-clean`.
- Harness-only follow-up reviewed: `62a8b1d` (`verify_ciel_apk.py`, its four compatibility tests and CI invocation). It does not change packaged app/core source.
- Signed APK: `/root/ciel-artifacts-20261106/final/ciel-preview-0.12.0-24-universal.apk`.
- SHA-256: `966180f919790ab16180833bd1371210070bc4616d073824619b9869f2fbf5f6`.
- Size: 16,037,798 bytes.
- Package: `ai.ciel.companion.preview`, label **Ciel Preview**, version **0.12.0 (24)**; minimum API 30, target API 34.
- Signer certificate SHA-256: `1e11584f8c081fc18f0ddaf6467a8268f59bf5a37c2b0bd55105bb7af0a17bac`.

Machine-readable observations: [final-artifact-review-r1.json](evidence/final-artifact-review-r1.json).

## Checks and observations

1. All **368 frozen source-file hashes match** the reviewed workspace. The CI unsigned Preview APK has SHA-256 `0a704700f97a357ce2178170eeb10425ee30e4ef87a99603b6304837a4dd21d1`. Every common ZIP member has identical uncompressed bytes in that APK and the signed deliverable; no member is removed, and only three signing metadata entries are added. This establishes signing preserved the CI payload; it is not a reproducible-build claim.
2. Android Build Tools 34 `apksigner verify --verbose --print-certs` verifies the exact file using APK Signature Scheme v3, and its certificate matches the expected release identity. `zipalign -c 4` passes. `aapt dump badging` reports no debuggable application flag and names `CielHomeActivity` as the launcher.
3. Both native libraries in `lib/arm64-v8a/` have 64-bit ELF headers and machine type AArch64 (`183`). The ARMv7, x86 and x86_64 library headers also match their directories. ARM64 support is real, rather than a manifest-only declaration. No required split flags or shared UID appear in the binary manifest.
4. The signed Preview's three provider authorities and signature permission are prefixed with `ai.ciel.companion.preview`. The previous Ciel x86_64 APK uses `ai.ciel.companion` for those declarations. There is **no provider-authority collision**. This supports installing alongside old Ciel; actual coexistence has not been exercised by this reviewer. A canonical package with this new signer still cannot update the old differently signed Ciel installation.
5. The binary manifest labels the application, accessibility service, body service and notification listener **Ciel Preview**. The launcher is exported; the accessibility service requires `BIND_ACCESSIBILITY_SERVICE`. The body service, internal activities, providers and alarm receiver are not exported. The library profile installer receiver is guarded by the system `DUMP` permission. Backup is disabled. No additional exported phone-control component was identified.
6. Packaged `LICENSE`, `THIRD_PARTY_NOTICES.md` and `CIEL_NOTICE.md` match their source files exactly. `assets/andee_key.txt` is empty. A scan of DEX and assets found no full Google API key format, private-key PEM, keystore, PKCS#12 or `.env` member. This is bounded pattern evidence, not proof that arbitrary secrets cannot exist.
7. The late `LineBreaker.BREAK_STRATEGY_HIGH_QUALITY` reference is an integer constant available below the minimum supported API. The label changes are resolved in the actual manifest. Neither introduces a concrete API-level crash. Home's service handoff still hides the floating window, keeps its cancellation token, and routes microphone promotion through the foreground start path; overlay display failure is handled. Those are source observations, not successful microphone evidence.
8. Current CI XML independently confirms **328 core + 95 Android tests**, zero failures/errors/skips, and **0 lint errors / 513 warnings**. The remaining exact-alarm warning corresponds to a guarded call with a revocation fallback. The lint warnings inspected did not establish a P0/P1 defect in this scope. Earlier independent production review is retained in [independent-review.md](independent-review.md).
9. The verifier's SDK-range signer parser now accepts the new AOSP v3 label format while rejecting public-key and SourceStamp digests. It requires exactly one certificate identity. The compatibility repair leaves the APK unchanged.

## Pending gates and limits

- The initial candidate's partial runtime result is recorded below. The replacement APK still needs exact signed-byte package and Android runtime review. Do not transfer the earlier digest's observations to it.
- The configured Google key was rejected before `setupComplete`. Successful Live audio, tool-schema acceptance by Google, phone tool execution and response latency remain unverified. No repeat request was made by this reviewer.
- Physical-device audio routing, permission interactions, OEM accessibility/keyboard behavior and phone actions remain untested. This review cannot certify every Android device or unrestricted phone control.
- Automatic notification triage/reviews remain documented Custom-model features, and native picked-document reading is still absent. The target is an explicitly limited Preview, not a completed feature certification.

The integration owner must retain the same APK digest when attaching later runtime results. Any packaged-source change requires a new artifact and review of its changed paths.

## Superseded candidate runtime — run `42211532681`

Reviewed `/root/ciel-artifacts-20261106/ci-42211532681/smoke-evidence/smoke-report.json` and the actual large-font Home image. The report binds both the supplied and pulled installed APK to `966180f919790ab16180833bd1371210070bc4616d073824619b9869f2fbf5f6`. On the Android 14 x86_64 emulator, installation, Home launch/resumed PID, the original avatar, Moss/Nova preview isolation and saved persistence passed. No app crash or ANR was found; cleanup reported no command errors.

The overall run **failed**, with three bounded causes: secure Setup returned no screenshot, its large-font capture did the same, and the legacy rotation setting did not produce a landscape image. The missing-key validation assertion was not reached after the first capture failure, so its behavior remains unproven by this run. Visual review also confirms the P2 navigation defect: **Companion** wraps as `Companio` / `n` at font scale 1.5.

## Replacement delta review — `549ba53`

No P0/P1 regression or further definite harness failure was identified in this delta. Only two packaged strings change: English **Companion → Avatar** and Chinese **伙伴 → 形象**. Navigation destinations and callbacks are unchanged. Shorter text addresses the observed wrap, with final visual confirmation still required.

The secure-capture branch writes the actual UI hierarchy and explicitly marks the screenshot as omitted. Its returned bounds are not used as screenshot proof. Other screens still require valid PNGs. The landscape check now sets Window Manager rotation and waits for an actual image with landscape dimensions; it does not relabel a portrait image.

The rotation query and restore syntax matches [Android 14's WindowManagerShellCommand](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/services/core/java/com/android/server/wm/WindowManagerShellCommand.java): `free` / `lock N` and `default` / `disabled` / `enabled`. The follow-up harness-only commit `549787a` is also reviewed: dormant `settings system user_rotation` and `accelerometer_rotation` values are now saved before Window Manager mutations and included in the existing cleanup restoration loop. No additional concrete failure was identified.

`skip_unsigned_smoke` only skips the CI-key run when explicitly selected; it does not skip a supplied signed-APK run. Both build and signed-APK workflows continue to require explicit gate results before being described as successful. No Gradle, emulator or Google call was launched by this reviewer.
