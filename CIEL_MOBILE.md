# Ciel mobile

Ciel is a personal Android companion based on [Andee](https://github.com/kuafuai/andee). This fork adds a native home screen, direct Gemini Live conversation, new procedural companions, and repairs to Android control and cancellation.

## Start talking

1. Open **Ciel → Setup**.
2. Create your own Gemini API key in [Google AI Studio](https://aistudio.google.com/apikey), paste it into **Gemini API key**, and tap **Save and use Gemini Live**. Keep the default model and voice unless you want to change them.
3. On **Home**, tap **Talk to Ciel** and allow microphone access.
4. Tap **Stop** to finish the conversation or cancel the current task. Meeting recordings also show a visible Stop control.

The microphone stays active while a Live conversation is open. You can also type a message. Overlay and Accessibility permissions are not required just to talk inside the app.

Gemini Live connects directly to Google. The default Android voice setup does not require CodeFlying or Huoshan/Volcengine. No provider key is included in the APK. Google's free availability, quotas, supported regions and model access depend on your account; this app cannot make API usage unlimited or guarantee a response time.

## Add phone controls

Open **Setup → Phone controls → Review permissions**. Android presents the actual permission or special-access screen.

| Access | What it enables |
| --- | --- |
| Accessibility | Read supported screen elements, tap, scroll, type, and use Android navigation |
| Display over other apps | The floating companion and advanced overlay panels |
| Notification access | Read notifications you authorize Ciel to handle |
| Location | Location queries; approximate access is accepted |
| Calendar, contacts, camera and other runtime permissions | The corresponding requested tools |
| Modify system settings | Brightness changes |

For a sideloaded app, some Android versions first require **App info → menu → Allow restricted settings** before you can enable Accessibility or notification access. Android remains in control of these permissions. Protected screens, device locks and OEM restrictions can limit what the assistant can do.

Stop invalidates queued work, interrupts long waits and blocks subsequent action checkpoints. A gesture already handed to Android may finish. Calls, SMS and alarm tools report when a request was submitted; that alone does not prove a connected call, carrier delivery or a created alarm.

## Choose a companion

Open **Companion**. The first, original Ciel design is preserved. **Moss** and **Nova** replace the old alternative choices.

Selecting a character previews it. Tap **Use Moss**, **Use Nova** or **Use Ciel** to save your choice. Expression buttons and **Play all expressions** demonstrate the existing 19 moods and gestures without opening the microphone. The characters also share the engine's idle motion, body gestures and speaking animation.

## Installation and updates

Android 11 or later is required. ARM64 is included in the release and universal packages.

The previously distributed Ciel 0.11.0 APK used a different debug signing key. A newly signed canonical package cannot replace that installation through an ordinary update. **Ciel Preview** uses `ai.ciel.companion.preview`, so it can be installed alongside old Ciel without deleting its data. Settings and history are separate between the two applications.

Future updates to a package must keep its signing identity. Release signing material is stored outside this repository and is never included in an APK or CI artifact. An unsigned CI build is an intermediate for signing, not an installable download.

## Current boundaries

- Gemini Live handles conversation and supported phone/notebook tools. Automatic notification rules, nightly reviews and background planning currently require the **Custom model** route; the settings explain this distinction.
- Native user-picked document reading is still a missing capability in this pass.
- The configured VPS Gemini key was rejected before Live setup completed. Successful live audio, microphone quality and phone response latency are therefore not established by the current evidence.
- Emulator screenshots and automated tests do not establish behavior on every phone. OEM keyboard/accessibility behavior, protected screens, audio routing and background restrictions still need checks on the intended device.

The [working evidence ledger](reports/ciel-upgrade-20261106/README.md), [voice audit](reports/ciel-upgrade-20261106/voice-integration.md), [phone-control audit](reports/phone-control-audit-20261106.md) and [independent source review](reports/ciel-upgrade-20261106/independent-review.md) distinguish implemented changes from completed and blocked verification.

## Validate a build

Use JDK 17 and an Android SDK with platform/build tools 34:

```sh
./gradlew --no-daemon --max-workers=2 -PuniversalApk=true -PdefaultLang=en \
  :core:test :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

Add `-PdbgSuffix=.preview` when assembling the separate Preview package. Sign the resulting unsigned APK with the protected release identity, then check the exact signed file:

```sh
python3 tools/verify_ciel_apk.py /path/to/Ciel-preview.apk --package ai.ciel.companion.preview
python3 tools/smoke_ciel_android.py --serial emulator-5554 \
  --apk /path/to/Ciel-preview.apk --package ai.ciel.companion.preview --output /path/to/new-evidence
```

The smoke harness requires a disposable emulator with no existing installation of that package. It never clears an existing app's data or contacts Gemini. Its screenshots require visual review, and its result must not be presented as voice or physical-phone validation.
