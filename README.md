# Ciel AI Companion

<p align="center">
  <img src="images/brand/illustrations/think.webp" width="140" alt="Ciel AI Mascot" />
</p>

<p align="center">
  <strong>Autonomous AI Operating Companion for Android</strong><br>
  Real-Time Voice Streaming • 97 System & Device Automation Tools • Zero-Leak Security • On-Device & Cloud Intelligence
</p>

<p align="center">
  <a href="#features">Features</a> ·
  <a href="#architecture">Architecture</a> ·
  <a href="#zero-credit-card-voice">Zero-Card Voice</a> ·
  <a href="#tools-suite">Tools Suite</a> ·
  <a href="#installation">Installation</a> ·
  <a href="#security--privacy">Security</a>
</p>

---

## Overview

**Ciel** is an autonomous operating companion designed to give Android devices true intelligent agency while keeping the user in full control. Operating as a clean, floating companion widget, Ciel perceives your screen, listens and speaks in real time, automates complex system workflows, and proactively assists across your daily mobile activities.

Unlike conventional voice assistants that merely return web snippets, Ciel interacts directly with Android's system APIs—tapping, scrolling, typing, inspecting application layouts, and orchestrating shell scripts via Termux or background daemons.

---

## Key Features

### 1. Zero-Card Real-Time Voice Streaming
* **Low Latency**: Direct bidirectional streaming over Google AI Studio Live WebSocket (`BidiGenerateContent`).
* **Sub-250ms Response**: Continuous audio stream without clumsy file uploads or 1-second ASR turnaround delays.
* **100% Free**: Operates without international credit cards or cloud billing setups.
* **Bilingual & Natural**: Native support for English, Hindi, and Hinglish dialogue with fluid inflection.

### 2. Full Device Automation (97 Built-in Tools)
* **Screen Interaction**: Native accessibility tree parser (`get_screen_element`), coordinate taps, continuous scroll/fling, dragging, and slider adjustments.
* **Text & Keyboard**: Type text into any focused input, trigger system IME actions, and clear or submit forms.
* **Application Control**: Launch, switch, inspect, or terminate applications directly.
* **Perception Engine**: Inspect live screenshots, detect anime or video playback scenes, zoom on screen details, and contextualize user activities.

### 3. Proactive Companion Mindset
* **Unsolicited Voice Prompts**: When viewing content or running complex tasks, Ciel can observe on-screen context and initiate helpful, conversational commentary.
* **Instant Silence / Interruptibility**: Say *"Shut up"* or *"Chup raho"*, and Ciel instantly yields speech and releases audio focus.
* **Background Task Notification**: Direct WebSocket push bridge from server engines (e.g., Kanban workflows or build monitors) directly triggers the companion to announce completions.

### 4. Customizable Identity & System Guard
* **User-Defined Prompting**: Built-in encrypted Notebook (`小本本`) with a dedicated **Guard** tab allowing up to 2,000 characters of custom persona rules injected at the very top of each turn.
* **Dynamic Custom Endpoint**: Supports self-hosted OpenAI/Anthropic proxies and local inference endpoints alongside Google AI Studio.

---

## Architecture

```
                       ┌─────────────────────────┐
                       │     Floating Widget     │
                       │ (EmotionBall GL Surface)│
                       └────────────┬────────────┘
                                    │
                                    ▼
                       ┌─────────────────────────┐
                       │   BodyCoreService (OS)  │
                       │ ┌─────────────────────┐ │
                       │ │  97 Device Tools    │ │
                       │ ├─────────────────────┤ │
                       │ │  A11y Node Inspector│ │
                       │ ├─────────────────────┤ │
                       │ │  Screen Perception  │ │
                       │ └─────────────────────┘ │
                       └────────────┬────────────┘
                                    │
            ┌───────────────────────┴───────────────────────┐
            ▼                                               ▼
┌─────────────────────────┐                     ┌─────────────────────────┐
│ Google AI Studio Live   │                     │ Local / Tailscale Hub   │
│ (Bidirectional ASR/TTS) │                     │ (Brain, Memory & Cron)  │
└─────────────────────────┘                     └─────────────────────────┘
```

---

## 97 Built-in Tools Summary

| Category | Representative Capabilities |
| :--- | :--- |
| **Screen Perception** | `get_screen_element`, `take_screenshot`, `wait_for_screen`, `zoom_screen_region` |
| **Touch & Gestures** | `tap_screen_element`, `tap_by_coordinates`, `swipe_by_coordinates`, `drag` |
| **Input & IME** | `type_text`, `submit_input`, `clear_input`, `press_key` |
| **System Operations** | `launch_app`, `system_action` (Home, Back, Recents), volume & brightness |
| **Information** | Battery telemetry, notification stream capture, audio focus monitoring |

---

## Security & Privacy (Zero-Leak Policy)

* **Local Preference Vault**: All credentials, endpoint tokens, and encryption keys are stored on-device using Android Keystore and AES-256 encrypted preferences.
* **Zero Hardcoded Secrets**: The codebase contains zero API keys, secrets, or tracking telemetry.
* **Self-Contained Distribution**: Builds are reproducible via standard Gradle commands with verified public artifacts.

---

## Getting Started

### Prerequisites
* Android 8.0 (API level 26) or higher.
* Android Accessibility Service enabled for screen automation.
* Display over other apps (Overlay) permission for the floating companion.

### Direct Download
Download the latest verified ARM64 build:
👉 **[Download Ciel APK (arm64-v8a)](https://cielmanu.indevs.in/dl/ciel-arm64.apk)**

### Build from Source
```bash
git clone https://github.com/Naruto859/ciel-ai-companion.git
cd ciel-ai-companion
./gradlew assembleDebug
```
The compiled APK will be located at `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`.

---

## License

Distributed under the Apache 2.0 License. See `LICENSE` for details.
