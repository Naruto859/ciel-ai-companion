Skip to main content

info

Welcome to the new home for Gemini API documentation in Google AI Studio.

Dismiss

menu\_open [arrow\_back](https://aistudio.google.com/prompts/new_chat)

[Get API key](https://aistudio.google.com/api-keys)

# Session management with Live API

content\_copy  check Copy page

In the Live API, a session refers to a persistentconnection where input and output are streamed continuously over the sameconnection (read more about [how it works](https://aistudio.google.com/docs/live-api)).This unique session design enables low latency and supports unique features, butcan also introduce challenges, like session time limits, and early termination.This guide covers strategies for overcoming the session management challengesthat can arise when using the Live API.

## Session lifetime   link

Without compression, audio-only sessions are limited to 15 minutes,and audio-video sessions are limited to 2 minutes. Exceeding these limitswill terminate the session (and therefore, the connection), but you can use [context window compression](https://aistudio.google.com/docs/live-api/session-management#context-window-compression) to extend sessions toan unlimited amount of time.

The lifetime of a connection is limited as well, to around 10 minutes. When theconnection terminates, the session terminates as well. In this case, you canconfigure a single session to stay active over multiple connections using [session resumption](https://aistudio.google.com/docs/live-api/session-management#session-resumption).You'll also receive a [GoAway message](https://aistudio.google.com/docs/live-api/session-management#goaway-message) before theconnection ends, allowing you to take further actions.

## Context window compression   link

To enable longer sessions, and avoid abrupt connection termination, you canenable context window compression by setting the [contextWindowCompression](https://ai.google.dev/api/live#BidiGenerateContentSetup.FIELDS.ContextWindowCompressionConfig.BidiGenerateContentSetup.context_window_compression) field as part of the session configuration.

In the [ContextWindowCompressionConfig](https://ai.google.dev/api/live#contextwindowcompressionconfig), you can configure a [sliding-window mechanism](https://ai.google.dev/api/live#ContextWindowCompressionConfig.FIELDS.ContextWindowCompressionConfig.SlidingWindow.ContextWindowCompressionConfig.sliding_window) and the [number of tokens](https://ai.google.dev/api/live#ContextWindowCompressionConfig.FIELDS.int64.ContextWindowCompressionConfig.trigger_tokens) that triggers compression.

content\_copy

Python

JavaScript

Python

```
from google.genai import types

config = types.LiveConnectConfig(
    response_modalities=["AUDIO"],
    context_window_compression=(
        # Configures compression with default parameters.
        types.ContextWindowCompressionConfig(
            sliding_window=types.SlidingWindow(),
        )
    ),
)
```

## Session resumption   link

To prevent session termination when the server periodically resets the WebSocketconnection, configure the [sessionResumption](https://ai.google.dev/api/live#BidiGenerateContentSetup.FIELDS.SessionResumptionConfig.BidiGenerateContentSetup.session_resumption) field within the [setup configuration](https://ai.google.dev/api/live#BidiGenerateContentSetup).

Passing this configuration causes theserver to send [SessionResumptionUpdate](https://ai.google.dev/api/live#SessionResumptionUpdate) messages, which can be used to resume the session by passing the last resumptiontoken as the [`SessionResumptionConfig.handle`](https://ai.google.dev/api/live#SessionResumptionConfig.FIELDS.string.SessionResumptionConfig.handle) of the subsequent connection.

Resumption tokens are valid for 2 hr after the last sessions termination.

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

async def main():
    print(f"Connecting to the service with handle {previous_session_handle}...")
    async with client.aio.live.connect(
        model=model,
        config=types.LiveConnectConfig(
            response_modalities=["AUDIO"],
            session_resumption=types.SessionResumptionConfig(
                # The handle of the session to resume is passed here,
                # or else None to start a new session.
                handle=previous_session_handle
            ),
        ),
    ) as session:
        while True:
            await session.send_client_content(
                turns=types.Content(
                    role="user", parts=[types.Part(text="Hello world!")]
                )
            )
            async for message in session.receive():
                # Periodically, the server will send update messages that may
                # contain a handle for the current state of the session.
                if message.session_resumption_update:
                    update = message.session_resumption_update
                    if update.resumable and update.new_handle:
                        # The handle should be retained and linked to the session.
                        return update.new_handle

                # For the purposes of this example, placeholder input is continually fed
                # to the model. In non-sample code, the model inputs would come from
                # the user.
                if message.server_content and message.server_content.turn_complete:
                    break

if __name__ == "__main__":
    asyncio.run(main())
```

## Receiving a message before the session disconnects   link

The server sends a [GoAway](https://ai.google.dev/api/live#GoAway) message that signals that the currentconnection will soon be terminated. This message includes the [timeLeft](https://ai.google.dev/api/live#GoAway.FIELDS.google.protobuf.Duration.GoAway.time_left),indicating the remaining time and lets you take further action before theconnection will be terminated as ABORTED.

content\_copy

Python

JavaScript

Python

```
async for response in session.receive():
    if response.go_away is not None:
        # The connection will soon be terminated
        print(response.go_away.time_left)
```

## Receiving a message when the generation is complete   link

The server sends a [generationComplete](https://ai.google.dev/api/live#BidiGenerateContentServerContent.FIELDS.bool.BidiGenerateContentServerContent.generation_complete) message that signals that the model finished generating the response.

content\_copy

Python

JavaScript

Python

```
async for response in session.receive():
    if response.server_content.generation_complete is True:
        # The generation is complete
```

## What's next   link

Explore more ways to work with the Live API in the full [Capabilities](https://aistudio.google.com/docs/live-api) guide,the [Tool use](https://aistudio.google.com/docs/live-api/tools) page, or the [Live API cookbook](https://colab.research.google.com/github/google-gemini/cookbook/blob/main/quickstarts/Get_started_LiveAPI.ipynb).

On this page  expand\_less

- [Session lifetime](https://aistudio.google.com/docs/live-api/session-management#maximum-session-duration)

- [Context window compression](https://aistudio.google.com/docs/live-api/session-management#context-window-compression)

- [Session resumption](https://aistudio.google.com/docs/live-api/session-management#session-resumption)

- [Receiving a message before the session disconnects](https://aistudio.google.com/docs/live-api/session-management#goaway-message)

- [Receiving a message when the generation is complete](https://aistudio.google.com/docs/live-api/session-management#generation-complete-message)

- [What's next](https://aistudio.google.com/docs/live-api/session-management#whats-next)


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