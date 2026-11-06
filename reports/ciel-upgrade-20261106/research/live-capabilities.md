Skip to main content

info

Welcome to the new home for Gemini API documentation in Google AI Studio.

Dismiss

menu\_open [arrow\_back](https://aistudio.google.com/prompts/new_chat)

[Get API key](https://aistudio.google.com/api-keys)

# Live API capabilities guide

content\_copy  check Copy page

lightbulb

The Live API is in preview.

This is a comprehensive guide that covers capabilities and configurationsavailable with the Live API.See [Get started with Live API](https://aistudio.google.com/docs/live-api) page for anoverview and sample code for common use cases.

## Before you begin   link

- **Familiarize yourself with core concepts:** If you haven't already done so,read the [Get started with Live API](https://aistudio.google.com/docs/live-api) page first.This will introduce you to the fundamental principles of the Live API, how itworks, and the different [implementation approaches](https://aistudio.google.com/docs/live-api#implementation-approach).
- **Try the Live API in AI Studio:** You may find it useful to try theMultimodal Live API in [Google AI Studio](https://aistudio.google.com/app/live-api) before youstart building. To use the Live API in Google AI Studio, select **Stream**.

## Model comparison   link

The following table summarizes the key differences between [Gemini 3.8 Live](https://aistudio.google.com/docs/models/gemini-3.8-live), [Gemini 3.8 Live Extended Thinking](https://aistudio.google.com/docs/models/gemini-3.8-live-extended-thinking), [Gemini 3.1 Flash Live Preview](https://aistudio.google.com/docs/models/gemini-3.1-flash-live-preview), and [Gemini 2.5 Flash Live Preview](https://aistudio.google.com/docs/models/gemini-2.5-flash-native-audio-preview-12-2025):

| Feature | Gemini 3.8 Live | Gemini 3.8 Live Extended Thinking | Gemini 3.1 Flash Live Preview | Gemini 2.5 Flash Live Preview |
| --- | --- | --- | --- | --- |
| **[Thinking](https://aistudio.google.com/docs/live-api/thinking)** | Supported (interleaved reasoning). `thinkingLevel` is not supported; omit from setup. | Supported (configurable background reasoning: `low`, `medium`, `high`; `minimal` is not supported). Supports thought summaries (`includeThoughts`). | Uses `thinkingLevel` (`minimal`, `low`, `medium`, `high`). Defaults to `minimal` to optimize for lowest latency. | Uses `thinkingBudget` to set thinking token budget. |
| **[Receiving response](https://aistudio.google.com/api-reference/live#BidiGenerateContentServerContent)** | A single server event can contain multiple content parts simultaneously (for example, `inlineData` and transcript). Ensure your code processes all parts in each event to avoid missing content. | A single server event can contain multiple content parts simultaneously. When asynchronous reasoning is active, `turnComplete: true` does not indicate an idle session; check `interaction_status` (`IN_PROGRESS` versus `IDLE`). | A single server event can contain multiple content parts simultaneously (for example, `inlineData` and transcript). Ensure your code processes all parts in each event to avoid missing content. | Each server event contains only one content part. Parts are delivered in separate events. |
| **[Client content](https://aistudio.google.com/docs/live-api/capabilities#incremental-updates)** | `send_client_content` is supported throughout the entire session lifecycle with explicit roles (`user` or `model`). `turn_complete=true` unconditionally interrupts generation. | `send_client_content` is supported throughout the entire session lifecycle with explicit roles (`user` or `model`). `turn_complete=true` unconditionally interrupts generation. | `send_client_content` is supported throughout the entire session lifecycle with explicit roles (`user` or `model`). `turn_complete=true` unconditionally interrupts generation. | `send_client_content` is supported throughout the conversation for sending incremental content updates and establishing context. |
| **[Turn coverage](https://aistudio.google.com/api-reference/live#RealtimeInputConfig.TurnCoverage)** | Defaults to `TURN_INCLUDES_AUDIO_ACTIVITY_AND_ALL_VIDEO`. The model's turn includes detected audio activity and all video frames. | Defaults to `TURN_INCLUDES_AUDIO_ACTIVITY_AND_ALL_VIDEO`. The model's turn includes detected audio activity and all video frames. | Defaults to `TURN_INCLUDES_AUDIO_ACTIVITY_AND_ALL_VIDEO`. The model's turn includes detected audio activity and all video frames. | Defaults to `TURN_INCLUDES_ONLY_ACTIVITY`. The model's turn includes only the detected activity. |
| **[Custom VAD](https://aistudio.google.com/docs/live-api/capabilities#disable-automatic-vad)** (`activity_start`/`activity_end`) | Supported. Disable automatic VAD and send `activityStart` and `activityEnd` messages manually to control turn boundaries. | Supported. Disable automatic VAD and send `activityStart` and `activityEnd` messages manually to control turn boundaries. | Supported. Disable automatic VAD and send `activityStart` and `activityEnd` messages manually to control turn boundaries. | Supported. Disable automatic VAD and send `activityStart` and `activityEnd` messages manually to control turn boundaries. |
| **[Automatic VAD configuration](https://aistudio.google.com/docs/live-api/capabilities#configure-automatic-vad)** | Supported. Configure parameters such as `start_of_speech_sensitivity`, `end_of_speech_sensitivity`, `prefix_padding_ms`, and `silence_duration_ms`. | Supported. Configure parameters such as `start_of_speech_sensitivity`, `end_of_speech_sensitivity`, `prefix_padding_ms`, and `silence_duration_ms`. | Supported. Configure parameters such as `start_of_speech_sensitivity`, `end_of_speech_sensitivity`, `prefix_padding_ms`, and `silence_duration_ms`. | Supported. Configure parameters such as `start_of_speech_sensitivity`, `end_of_speech_sensitivity`, `prefix_padding_ms`, and `silence_duration_ms`. |
| **[Asynchronous function calling](https://aistudio.google.com/docs/live-api/tools#async-function-calling)** (`behavior: NON_BLOCKING`) | Supported (default). Set `behavior: NON_BLOCKING` or use backward-compatible blocking mode (`behavior: BLOCKING`). Function scheduling (`SILENT`, `WHEN_IDLE`, `INTERRUPTED`) is supported. | Supported (Async only). Only `NON_BLOCKING` execution is supported. Blocking mode and function scheduling are not supported. | Not supported. Function calling is synchronous only. The model waits for the tool response before responding. | Supported (both synchronous and asynchronous). |
| **[Proactive audio](https://aistudio.google.com/docs/live-api/capabilities#proactive-audio)** | Permanently enabled (`proactive_audio: false` returns an error). | Permanently enabled (`proactive_audio: false` returns an error). | Not supported | Supported (configurable). Set `proactive_audio` to `true` in `proactivity` config. |
| **[Affective dialogue](https://aistudio.google.com/docs/live-api/capabilities#affective-dialog)** | Not supported (removed; omit `enable_affective_dialog`). | Not supported (removed; omit `enable_affective_dialog`). | Not supported | Supported. Set `enable_affective_dialog` to `true` in session config. |

To migrate from Gemini 3.1 Flash Live Preview to Gemini 3.8 Live, see the [migration guide](https://aistudio.google.com/docs/models/gemini-3.8-live#migrating). To learn more aboutThinking, see [Thinking in the Live API](https://aistudio.google.com/docs/live-api/thinking) and [Upgrading to Gemini 3.8 Live Extended Thinking](https://aistudio.google.com/docs/models/gemini-3.8-live-extended-thinking#upgrading).

## Establishing a connection   link

The following example shows how to create a connection with an API key:

content\_copy

Python

JavaScript

Python

```
import asyncio
from google import genai

client = genai.Client()

model = "gemini-3.8-live"
config = {"response_modalities": ["AUDIO"]}

async def main():
    async with client.aio.live.connect(model=model, config=config) as session:
        print("Session started")
        # Send content...

if __name__ == "__main__":
    asyncio.run(main())
```

## Interaction modalities   link

The following sections provide examples and supporting context for the differentinput and output modalities available in Live API.

### Sending audio   link

Audio needs to be sent as raw PCM data (raw 16-bit PCM audio, 16kHz, little-endian).

content\_copy

Python

JavaScript

Python

```
# Assuming 'chunk' is your raw PCM audio bytes
await session.send_realtime_input(
    audio=types.Blob(
        data=chunk,
        mime_type="audio/pcm;rate=16000"
    )
)
```

### Audio formats   link

Audio data in the Multimodal Live API is always raw, little-endian,16-bit PCM. Audio output always uses a sample rate of 24kHz. Input audiois natively 16kHz, but the Multimodal Live API will resample if neededso any sample rate can be sent. To convey the sample rate of input audio, setthe MIME type of each audio-containing [Blob](https://ai.google.dev/api/caching#Blob) to a valuelike `audio/pcm;rate=16000`.

### Receiving audio   link

The model's audio responses are received as chunks of data.

content\_copy

Python

JavaScript

Python

```
async for response in session.receive():
    if response.server_content and response.server_content.model_turn:
        for part in response.server_content.model_turn.parts:
            if part.inline_data:
                audio_data = part.inline_data.data
                # Process or play the audio data
```

### Sending text   link

Text can be sent using `send_realtime_input` (Python) or `sendRealtimeInput` (JavaScript).

content\_copy

Python

JavaScript

Python

```
await session.send_realtime_input(text="Hello, how are you?")
```

### Sending video   link

Video frames are sent as individual images (e.g., JPEG or PNG) at a specific frame rate (max 1 frame per second).

content\_copy

Python

JavaScript

Python

```
# Assuming 'frame' is your JPEG-encoded image bytes
await session.send_realtime_input(
    video=types.Blob(
        data=frame,
        mime_type="image/jpeg"
    )
)
```

#### Incremental content updates   link

Use incremental updates to send text input, establish session context, orrestore session context. For short contexts you can send turn-by-turninteractions to represent the exact sequence of events:

lightbulb

For Gemini 3.8 and 3.1 models, if you want to seedinitial conversation history using `send_client_content` before starting arealtime stream, set [`initial_history_in_client_content`](https://ai.google.dev/api/live#HistoryConfig) to `true` in your session config's `history_config`. You can also use`send_client_content` mid-session to append structured text turns.

content\_copy

Python

JavaScript

Python

```
turns = [\
    {"role": "user", "parts": [{"text": "What is the capital of France?"}]},\
    {"role": "model", "parts": [{"text": "Paris"}]},\
]

await session.send_client_content(turns=turns, turn_complete=False)

turns = [{"role": "user", "parts": [{"text": "What is the capital of Germany?"}]}]

await session.send_client_content(turns=turns, turn_complete=True)
```

For longer contexts it's recommended to provide a single message summary to freeup the context window for subsequent interactions. See [Session Resumption](https://aistudio.google.com/docs/live-api/session-management#session-resumption) foranother method for loading session context.

### Audio transcriptions   link

In addition to the model response, you can also receive transcriptions ofboth the audio output and the audio input.

To enable transcription of the model's audio output, send`output_audio_transcription` in the setup config. The transcription language isinferred from the model's response.

content\_copy

Python

JavaScript

Python

```
import asyncio
from google import genai
from google.genai import types

client = genai.Client()
model = "gemini-3.8-live"

config = {
    "response_modalities": ["AUDIO"],
    "output_audio_transcription": {}
}

async def main():
    async with client.aio.live.connect(model=model, config=config) as session:
        message = "Hello? Gemini are you there?"

        await session.send_client_content(
            turns={"role": "user", "parts": [{"text": message}]}, turn_complete=True
        )

        async for response in session.receive():
            if response.server_content.model_turn:
                print("Model turn:", response.server_content.model_turn)
            if response.server_content.output_transcription:
                print("Transcript:", response.server_content.output_transcription.text)

if __name__ == "__main__":
    asyncio.run(main())
```

To enable transcription of the model's audio input, send`input_audio_transcription` in setup config.

content\_copy

Python

JavaScript

Python

```
import asyncio
from pathlib import Path
from google import genai
from google.genai import types

client = genai.Client()
model = "gemini-3.8-live"

config = {
    "response_modalities": ["AUDIO"],
    "input_audio_transcription": {},
}

async def main():
    async with client.aio.live.connect(model=model, config=config) as session:
        audio_data = Path("16000.pcm").read_bytes()

        await session.send_realtime_input(
            audio=types.Blob(data=audio_data, mime_type='audio/pcm;rate=16000')
        )

        async for msg in session.receive():
            if msg.server_content.input_transcription:
                print('Transcript:', msg.server_content.input_transcription.text)

if __name__ == "__main__":
    asyncio.run(main())
```

To improve how input transcription recognizes specific terms, such as propernouns, brand names, or technical terms, add a `custom_vocabulary` list to`input_audio_transcription`. You can provide up to 1,000 terms, but best resultsare typically achieved with up to 100 terms. For more details, see [Custom vocabulary biasing](https://aistudio.google.com/docs/live-api/live-transcribe#custom-vocabulary-biasing).

content\_copy

Python

JavaScript

Python

```
config = {
    "response_modalities": ["AUDIO"],
    "input_audio_transcription": {
        "custom_vocabulary": ["Gemini", "Kubernetes", "BigQuery"],
    },
}
```

### Change voice and language   link

[Native audio output](https://aistudio.google.com/docs/live-api/capabilities#native-audio-output) models support any of the voicesavailable for our [Text-to-Speech (TTS)](https://aistudio.google.com/docs/speech-generation#voices) models. You can listen to all the voices in [AI Studio](https://aistudio.google.com/app/live-api).

To specify a voice, set the voice name within the `speechConfig` object as partof the session configuration:

content\_copy

Python

JavaScript

Python

```
config = {
    "response_modalities": ["AUDIO"],
    "speech_config": {
        "voice_config": {"prebuilt_voice_config": {"voice_name": "Kore"}}
    },
}
```

## Native audio capabilities   link

Our latest models feature [native audio output](https://aistudio.google.com/docs/models/gemini-3.8-live),which provides natural, realistic-sounding speech and improved multilingualperformance.

### Thinking   link

Gemini 3.8 Live Extended Thinking and Gemini 3.1 models use `thinkingLevel` tocontrol thinking depth. For `gemini-3.8-live`, `thinkingLevel` is not supportedand must be omitted in setup. Gemini 3.8 Live Extended Thinking supports `low`,`medium`, and `high` (`minimal` is not supported). Gemini 3.1 models support`minimal`, `low`, `medium`, and `high`. For complete details and code examples,see [Thinking in the Live API](https://aistudio.google.com/docs/live-api/thinking).

content\_copy

Python

JavaScript

Python

```
model = "gemini-3.8-live-extended-thinking"

config = types.LiveConnectConfig(
    response_modalities=["AUDIO"],
    thinking_config=types.ThinkingConfig(
        thinking_level="MEDIUM",
    )
)

async with client.aio.live.connect(model=model, config=config) as session:
    # Send audio input and receive audio
```

Additionally, you can enable thought summaries by setting `includeThoughts` to`true` in your configuration. See [Receive thought summaries](https://aistudio.google.com/docs/thinking#summaries) for moreinfo:

content\_copy

Python

JavaScript

Python

```
model = "gemini-3.8-live-extended-thinking"

config = types.LiveConnectConfig(
    response_modalities=["AUDIO"],
    thinking_config=types.ThinkingConfig(
        thinking_level="HIGH",
        include_thoughts=True
    )
)
```

### Affective dialog   link

This feature lets Gemini adapt its response style to the input expression andtone.

lightbulb

Supported on Gemini 2.5 Flash Live only. Removed in Gemini 3.8 Live and Gemini3.8 Live Extended Thinking (omit `enable_affective_dialog`), and not supportedon Gemini 3.1 Flash Live Preview.

To use affective dialog, set the api version to `v1beta` and set`enable_affective_dialog` to `true` in the setup message:

content\_copy

Python

JavaScript

Python

```
client = genai.Client(http_options={"api_version": "v1beta"})

config = types.LiveConnectConfig(
    response_modalities=["AUDIO"],
    enable_affective_dialog=True
)
```

### Proactive audio   link

When this feature is enabled, Gemini can proactively decide not to respondif the content is not relevant.

lightbulb

Permanently enabled on Gemini 3.8 Live and Gemini 3.8 Live Extended Thinking(setting `proactive_audio: false` returns an error). Configurable on Gemini 2.5Flash Live, and not supported on Gemini 3.1 Flash Live Preview.

To use it, set the api version to `v1beta` and configure the `proactivity`field in the setup message and set `proactive_audio` to `true`:

content\_copy

Python

JavaScript

Python

```
client = genai.Client(http_options={"api_version": "v1beta"})

config = types.LiveConnectConfig(
    response_modalities=["AUDIO"],
    proactivity={'proactive_audio': True}
)
```

## Live translation   link

The Live API supports real-time, low-latency translation of spoken conversations. This capability lets you build real-time voice-to-voice translation applications.

For more information and examples, see the [Live Translation guide](https://aistudio.google.com/docs/live-api/live-translate).

## Voice Activity Detection (VAD)   link

Voice Activity Detection (VAD) allows the model to recognize when a person isspeaking. This is essential for creating natural conversations, as it allows auser to interrupt the model at any time.

When VAD detects an interruption, the ongoing generation is canceled anddiscarded. Only the information already sent to the client is retained in thesession history. The server then sends a [`BidiGenerateContentServerContent`](https://ai.google.dev/api/live#bidigeneratecontentservercontent) message to report the interruption.

The Gemini server then discards any pending function calls and sends a`BidiGenerateContentServerContent` message with the IDs of the canceled calls.

content\_copy

Python

JavaScript

Python

```
async for response in session.receive():
    if response.server_content.interrupted is True:
        # The generation was interrupted

        # If realtime playback is implemented in your application,
        # you should stop playing audio and clear queued playback here.
```

### Automatic VAD   link

By default, the model automatically performs VAD ona continuous audio input stream. VAD can be configured with the [`realtimeInputConfig.automaticActivityDetection`](https://ai.google.dev/api/live#RealtimeInputConfig.AutomaticActivityDetection) field of the [setup configuration](https://ai.google.dev/api/live#BidiGenerateContentSetup).

When the audio stream is paused for more than a second (for example,because the user switched off the microphone), an [`audioStreamEnd`](https://ai.google.dev/api/live#BidiGenerateContentRealtimeInput.FIELDS.bool.BidiGenerateContentRealtimeInput.audio_stream_end) event should be sent to flush any cached audio. The client can resume sendingaudio data at any time.

content\_copy

Python

JavaScript

Python

```
# example audio file to try:
# URL = "https://storage.googleapis.com/generativeai-downloads/data/hello_are_you_there.pcm"
# !wget -q $URL -O sample.pcm
import asyncio
from pathlib import Path
from google import genai
from google.genai import types

client = genai.Client()
model = "gemini-3.8-live"

config = {"response_modalities": ["AUDIO"]}

async def main():
    async with client.aio.live.connect(model=model, config=config) as session:
        audio_bytes = Path("sample.pcm").read_bytes()

        await session.send_realtime_input(
            audio=types.Blob(data=audio_bytes, mime_type="audio/pcm;rate=16000")
        )

        # if stream gets paused, send:
        # await session.send_realtime_input(audio_stream_end=True)

        async for response in session.receive():
            if response.text is not None:
                print(response.text)

if __name__ == "__main__":
    asyncio.run(main())
```

### Automatic VAD configuration   link

For more control over the VAD activity, you can configure the followingparameters. See [API reference](https://ai.google.dev/api/live#automaticactivitydetection) for moreinfo.

content\_copy

Python

JavaScript

Python

```
from google.genai import types

config = {
    "response_modalities": ["AUDIO"],
    "realtime_input_config": {
        "automatic_activity_detection": {
            "disabled": False, # default
            "start_of_speech_sensitivity": types.StartSensitivity.START_SENSITIVITY_LOW,
            "end_of_speech_sensitivity": types.EndSensitivity.END_SENSITIVITY_LOW,
            "prefix_padding_ms": 20,
            "silence_duration_ms": 100,
        }
    }
}
```

### Hybrid VAD   link

Hybrid VAD combines the benefits of automatic VAD (robust speech start detection) and manual VAD (low-latency response finalization).

In this configuration:

1. **Automatic VAD remains enabled** on the server. The server automaticallydetects the start of user speech, using prefix padding to avoid clippingthe beginning of utterances.
2. The client uses a **client-side VAD** to detect when the user stopsspeaking.
3. When the client-side VAD detects the end of speech, it sends an [`audio_stream_end`](https://ai.google.dev/api/live#BidiGenerateContentRealtimeInput.FIELDS.bool.BidiGenerateContentRealtimeInput.audio_stream_end) signal to the server.
4. The server treats the `audio_stream_end` signal as an immediatefinalization prompt, bypassing the default server-side silence detectiondelay and returning the transcript and model response with minimal latency.
5. If the client-side VAD fails to trigger, the server-side VAD acts as afallback to detect the end of speech.

Note that if the client-side VAD threshold is set too aggressively, it may causespeech cutoffs. However, this approach prevents front-truncation issues whichmight occur with manual VAD.

content\_copy

Python

JavaScript

Python

```
# Set up with automatic VAD enabled (default)
config = {
    "response_modalities": ["AUDIO"],
}

async with client.aio.live.connect(model=model, config=config) as session:
    # Send audio data normally
    await session.send_realtime_input(
        audio=types.Blob(data=audio_bytes, mime_type="audio/pcm;rate=16000")
    )

    # When client-side VAD detects the end of speech, send:
    await session.send_realtime_input(audio_stream_end=True)
```

### Disable automatic VAD   link

Alternatively, the automatic VAD can be disabled by setting`realtimeInputConfig.automaticActivityDetection.disabled` to `true` in the setupmessage. In this configuration the client is responsible for detecting userspeech and sending [`activityStart`](https://ai.google.dev/api/live#BidiGenerateContentRealtimeInput.FIELDS.BidiGenerateContentRealtimeInput.ActivityStart.BidiGenerateContentRealtimeInput.activity_start) and [`activityEnd`](https://ai.google.dev/api/live#BidiGenerateContentRealtimeInput.FIELDS.BidiGenerateContentRealtimeInput.ActivityEnd.BidiGenerateContentRealtimeInput.activity_end) messages at the appropriate times. An `audioStreamEnd` isn't sent inthis configuration. Instead, any interruption of the stream is marked byan `activityEnd` message.

content\_copy

Python

JavaScript

Python

```
config = {
    "response_modalities": ["AUDIO"],
    "realtime_input_config": {"automatic_activity_detection": {"disabled": True}},
}

async with client.aio.live.connect(model=model, config=config) as session:
    # ...
    await session.send_realtime_input(activity_start=types.ActivityStart())
    await session.send_realtime_input(
        audio=types.Blob(data=audio_bytes, mime_type="audio/pcm;rate=16000")
    )
    await session.send_realtime_input(activity_end=types.ActivityEnd())
    # ...
```

### Understanding VAD parameters and their impact on quality   link

When using automatic VAD, two key parameters control how audio issegmented into speech turns before being sent to the model:

- **`prefixPaddingMs`**: The amount of audio to include _before_ speechis detected. This "look-back" ensures the model captures the fullonset of speech, including the first syllable which may start beforethe VAD triggers. A value of `0` may cause the beginning of words tobe clipped.
- **`silenceDurationMs`**: How long the server waits through silencebefore ending a speech turn. This determines how tolerant the systemis of natural mid-sentence pauses (e.g., thinking, breathing, orclause boundaries).

#### Impact of `silenceDurationMs` on audio quality

The `silenceDurationMs` value directly affects the size and completenessof audio chunks the model receives for processing:

- **Recommended (500ms–800ms):** Provides a good balance—the modelreceives complete, contextually rich audio chunks while keeping latencyreasonable. The server's internal default is approximately 800ms.
- **Too low (e.g., 100ms–200ms):** The system ends speech turns duringnatural pauses, splitting a single utterance into multiple small audiofragments. The model receives these fragments individually, losingcross-fragment context and resulting in lower transcription and responsequality.
- **Too high (e.g., 2000ms+):** The system waits a long time after theuser stops speaking, increasing perceived latency before the modelresponds.

#### Best practices for manual (client-side) VAD

When you disable automatic VAD and manage `activityStart`/`activityEnd`signals from your own client-side voice detection, be aware that theserver's built-in audio buffering mechanisms are bypassed. This means:

1. **No pre-speech buffer:** The server no longer prepends audio beforethe detected speech start. Your client should include sufficient audiocontext before sending `activityStart`.
2. **No silence tolerance:** The server acts immediately on your`activityEnd` signal with no additional wait. If your client-side VADuses an aggressive end-of-speech threshold (e.g., 200ms of silence),speech may be cut off mid-sentence during natural pauses.

To preserve audio quality with manual VAD, use an end-of-speech silencethreshold of at least **500ms** in your client's voice activity detector.Thresholds below this value often cause fragmented audio that degradestranscription and model response quality.

## Token count   link

You can find the total number of consumed tokens in the [usageMetadata](https://ai.google.dev/api/live#usagemetadata) field of the returned server message.

content\_copy

Python

JavaScript

Python

```
async for message in session.receive():
    # The server will periodically send messages that include UsageMetadata.
    if message.usage_metadata:
        usage = message.usage_metadata
        print(
            f"Used {usage.total_token_count} tokens in total. Response token breakdown:"
        )
        for detail in usage.response_tokens_details:
            match detail:
                case types.ModalityTokenCount(modality=modality, token_count=count):
                    print(f"{modality}: {count}")
```

## Media resolution   link

You can specify the media resolution for the input media by setting the`mediaResolution` field as part of the session configuration:

content\_copy

Python

JavaScript

Python

```
from google.genai import types

config = {
    "response_modalities": ["AUDIO"],
    "media_resolution": types.MediaResolution.MEDIA_RESOLUTION_LOW,
}
```

You can configure `mediaResolution` for multimodal sessions that includeaudio, video, or image inputs. While `mediaResolution` adjusts the tokenallocation per frame for visual inputs, audio streams are tokenized at afixed rate per second across all resolution settings. For more information,see the [Media resolution](https://aistudio.google.com/docs/media-resolution) guide.

## Limitations   link

Consider the following limitations of the Multimodal Live APIwhen you plan your project.

### Response modalities   link

The native audio models only support \`AUDIO response modality. If you need themodel response as text, use the [output audio transcription](https://aistudio.google.com/docs/live-api/capabilities#audio-transcription) feature.

### Client authentication   link

The Multimodal Live API only provides server-to-server authentication bydefault. If you're implementing your Multimodal Live API application using a [client-to-server approach](https://aistudio.google.com/docs/live-api#implementation-approach), you need touse [ephemeral tokens](https://aistudio.google.com/docs/live-api/ephemeral-tokens) to mitigate securityrisks.

### Session duration   link

Audio-only sessions are limited to 15 minutes, and audio plus video sessions arelimited to 2 minutes. However, you can configure different [session management techniques](https://aistudio.google.com/docs/live-api/session-management) for unlimitedextensions on session duration.

### Context window   link

A session has a context window limit of:

- 128k tokens for [native audio output](https://aistudio.google.com/docs/live-api/capabilities#native-audio-output) models
- 32k tokens for other Multimodal Live API models

## Supported languages   link

Multimodal Live API supports the following 99 languages.

lightbulb

[Native audio output](https://aistudio.google.com/docs/live-api/capabilities#native-audio-output) models can switch betweenlanguages naturally during conversation. You can also restrict the languagesit speaks in by specifying it in the system instructions.

| Language | BCP-47 Code | Language | BCP-47 Code |
| --- | --- | --- | --- |
| Afrikaans | `af` | Latvian | `lv` |
| Akan | `ak` | Lithuanian | `lt` |
| Albanian | `sq` | Macedonian | `mk` |
| Amharic | `am` | Malay | `ms` |
| Arabic | `ar` | Malayalam | `ml` |
| Armenian | `hy` | Maltese | `mt` |
| Assamese | `as` | Maori | `mi` |
| Azerbaijani | `az` | Marathi | `mr` |
| Basque | `eu` | Mongolian | `mn` |
| Belarusian | `be` | Nepali | `ne` |
| Bengali | `bn` | Norwegian | `no`, `nb` |
| Bosnian | `bs` | Odia | `or` |
| Bulgarian | `bg` | Oromo | `om` |
| Burmese | `my` | Pashto | `ps` |
| Catalan | `ca` | Persian | `fa` |
| Cebuano | `ceb` | Polish | `pl` |
| Chinese (Simplified) | `zh-Hans` | Portuguese (Brazil) | `pt-BR` |
| Chinese (Traditional) | `zh-Hant` | Portuguese (Portugal) | `pt-PT` |
| Croatian | `hr` | Punjabi | `pa` |
| Czech | `cs` | Quechua | `qu` |
| Danish | `da` | Romanian | `ro` |
| Dutch | `nl` | Romansh | `rm` |
| English | `en` | Russian | `ru` |
| Estonian | `et` | Serbian | `sr` |
| Faroese | `fo` | Sindhi | `sd` |
| Filipino | `fil` | Sinhala | `si` |
| Finnish | `fi` | Slovak | `sk` |
| French | `fr` | Slovenian | `sl` |
| Galician | `gl` | Somali | `so` |
| Georgian | `ka` | Southern Sotho | `st` |
| German | `de` | Spanish | `es` |
| Greek | `el` | Swahili | `sw` |
| Gujarati | `gu` | Swedish | `sv` |
| Hausa | `ha` | Tajik | `tg` |
| Hebrew | `he` | Tamil | `ta` |
| Hindi | `hi` | Telugu | `te` |
| Hungarian | `hu` | Thai | `th` |
| Icelandic | `is` | Tswana | `tn` |
| Indonesian | `id` | Turkish | `tr` |
| Irish | `ga` | Turkmen | `tk` |
| Italian | `it` | Ukrainian | `uk` |
| Japanese | `ja` | Urdu | `ur` |
| Kannada | `kn` | Uzbek | `uz` |
| Kazakh | `kk` | Vietnamese | `vi` |
| Khmer | `km` | Welsh | `cy` |
| Kinyarwanda | `rw` | Western Frisian | `fy` |
| Korean | `ko` | Wolof | `wo` |
| Kurdish | `ku` | Yoruba | `yo` |
| Kyrgyz | `ky` | Zulu | `zu` |
| Lao | `lo` |  |  |

## What's next   link

- Read the [Thinking in the Live API](https://aistudio.google.com/docs/live-api/thinking), [Tool Use](https://aistudio.google.com/docs/live-api/tools), and [Session Management](https://aistudio.google.com/docs/live-api/session-management) guides for essentialinformation on using the Live API effectively.
- Try the Multimodal Live API in [Google AI Studio](https://aistudio.google.com/app/live).
- For more info about the Live API models, see [Gemini 3.8 Live](https://aistudio.google.com/docs/models/gemini-3.8-live) and [Gemini 3.8 Live Extended Thinking](https://aistudio.google.com/docs/models/gemini-3.8-live-extended-thinking) on the Models page.
- Try more examples in the [Live API cookbook](https://colab.research.google.com/github/google-gemini/cookbook/blob/main/quickstarts/Get_started_LiveAPI.ipynb),the [Live API Tools cookbook](https://colab.research.google.com/github/google-gemini/cookbook/blob/main/quickstarts/Get_started_LiveAPI_tools.ipynb),and the [Live API Get Started script](https://github.com/google-gemini/cookbook/blob/main/quickstarts/Get_started_LiveAPI.py).

On this page  expand\_less

- [Model comparison](https://aistudio.google.com/docs/live-api/capabilities#model-comparison)

- [Establishing a connection](https://aistudio.google.com/docs/live-api/capabilities#establish-connection)

- [Interaction modalities](https://aistudio.google.com/docs/live-api/capabilities#modalities) chevron\_right

- [Native audio capabilities](https://aistudio.google.com/docs/live-api/capabilities#native-audio-output) chevron\_right

- [Live translation](https://aistudio.google.com/docs/live-api/capabilities#live-translate)

- [Voice Activity Detection (VAD)](https://aistudio.google.com/docs/live-api/capabilities#interruptions)

- [Token count](https://aistudio.google.com/docs/live-api/capabilities#token-count)

- [Media resolution](https://aistudio.google.com/docs/live-api/capabilities#media-resolution)

- [Limitations](https://aistudio.google.com/docs/live-api/capabilities#limitations) chevron\_right

- [Supported languages](https://aistudio.google.com/docs/live-api/capabilities#supported-languages)

- [What's next](https://aistudio.google.com/docs/live-api/capabilities#whats-next)


Press / to filter

Enable applet notifications

Disable applet notifications

Enable autocomplete suggestions while typing

Disable autocomplete suggestions

Submit: Ctrl + Enter
Newline: Enter

Submit: Enter
Newline: Shift + Enter

Status

Cookbook

Copy page as markdown

Copy to clipboard