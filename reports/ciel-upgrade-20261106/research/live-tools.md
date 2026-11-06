Skip to main content

info

Welcome to the new home for Gemini API documentation in Google AI Studio.

Dismiss

menu\_open [arrow\_back](https://aistudio.google.com/prompts/new_chat)

[Get API key](https://aistudio.google.com/api-keys)

# Tool use with Live API

content\_copy  check Copy page

Tool use allows Live API to go beyond just conversation by enabling it toperform actions in the real-world and pull in external context while maintaininga real time connection. You can define tools such as [Function calling](https://aistudio.google.com/docs/function-calling) and [Google Search](https://aistudio.google.com/docs/google-search) with the Live API.

## Overview of supported tools   link

| Tool | Gemini 3.8 Live | Gemini 3.8 Live Extended Thinking | Gemini 3.1 Flash Live Preview | Gemini 2.5 Flash Live Preview |
| --- | --- | --- | --- | --- |
| **Search** | Supported | Supported | Supported | Supported |
| **Function calling** | Supported (synchronous and [asynchronous](https://aistudio.google.com/docs/live-api/tools#async-function-calling)) | Supported ( [asynchronous only](https://aistudio.google.com/docs/live-api/tools#async-function-calling)) | Supported (synchronous only) | Supported (synchronous and [asynchronous](https://aistudio.google.com/docs/live-api/tools#async-function-calling)) |
| **Google Maps** | Not supported | Not supported | Not supported | Not supported |
| **Code execution** | Not supported | Not supported | Not supported | Not supported |
| **URL context** | Not supported | Not supported | Not supported | Not supported |

## Function calling   link

Live API supports function calling, just like regular content generationrequests. Function calling lets the Live API interact with external data andprograms, greatly increasing what your applications can accomplish.

You can define function declarations as part of the session configuration.After receiving tool calls, the client should respond with a list of`FunctionResponse` objects using the `session.send_tool_response` method.

See the [Function calling tutorial](https://aistudio.google.com/docs/function-calling) to learnmore.

lightbulb

Unlike the `generateContent` API, the Live APIdoesn't support automatic tool response handling. You must handle tool responsesmanually in your client code.

content\_copy

Python

JavaScript

Python

```
import asyncio
import wave
from google import genai
from google.genai import types

client = genai.Client()

model = "gemini-3.8-live"

# Simple function definitions
turn_on_the_lights = {"name": "turn_on_the_lights"}
turn_off_the_lights = {"name": "turn_off_the_lights"}

tools = [{"function_declarations": [turn_on_the_lights, turn_off_the_lights]}]
config = {"response_modalities": ["AUDIO"], "tools": tools}

async def main():
    async with client.aio.live.connect(model=model, config=config) as session:
        prompt = "Turn on the lights please"
        await session.send_client_content(turns={"parts": [{"text": prompt}]})

        wf = wave.open("audio.wav", "wb")
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(24000) # Output is 24kHz

        async for response in session.receive():
            if response.data is not None:
                wf.writeframes(response.data)
            elif response.tool_call:
                print("The tool was called")
                function_responses = []
                for fc in response.tool_call.function_calls:
                    function_response = types.FunctionResponse(
                        id=fc.id,
                        name=fc.name,
                        response={ "result": "ok" } # simple, hard-coded function response
                    )
                    function_responses.append(function_response)

                await session.send_tool_response(function_responses=function_responses)

        wf.close()

if __name__ == "__main__":
    asyncio.run(main())
```

From a single prompt, the model can generate multiple function calls and thecode necessary to chain their outputs. This code executes in a sandboxenvironment, generating subsequent [BidiGenerateContentToolCall](https://ai.google.dev/api/live#bidigeneratecontenttoolcall) messages.

## Asynchronous function calling   link

Function calling executes sequentially by default, meaning execution pausesuntil the results of each function call are available. This ensures sequentialprocessing, which means you won't be able to continue interacting with the modelwhile the functions are being run.

lightbulb

Asynchronous function calling is supported on`gemini-3.8-live` and required on `gemini-3.8-live-extended-thinking`. It is notsupported on `gemini-3.1-flash-live-preview`; for that model, execution pausesuntil you send the tool response.

If you don't want to block the conversation, you can tell the model to run thefunctions asynchronously. To do so, you first need to add a `behavior` to thefunction definitions:

content\_copy

Python

JavaScript

Python

```
# Non-blocking function definitions
turn_on_the_lights = {"name": "turn_on_the_lights", "behavior": "NON_BLOCKING"} # turn_on_the_lights will run asynchronously
turn_off_the_lights = {"name": "turn_off_the_lights"} # turn_off_the_lights will still pause all interactions with the model
```

`NON-BLOCKING` ensures the function runs asynchronously while you cancontinue interacting with the model.

Then you need to tell the model how to behave when it receives the`FunctionResponse` using the `scheduling` parameter. It can either:

- Interrupt what it's doing and tell you about the response it got right away(`scheduling="INTERRUPT"`),

- Wait until it's finished with what it's currently doing(`scheduling="WHEN_IDLE"`),

- Or do nothing and use that knowledge later on in the discussion(`scheduling="SILENT"`)


content\_copy

Python

JavaScript

Python

```
# for a non-blocking function definition, apply scheduling in the function response:
  function_response = types.FunctionResponse(
      id=fc.id,
      name=fc.name,
      response={
          "result": "ok",
          "scheduling": "INTERRUPT" # Can also be WHEN_IDLE or SILENT
      }
  )
```

## Grounding with Google Search   link

You can enable Grounding with Google Search as part of the sessionconfiguration. This increases the Live API's accuracy and preventshallucinations. See the [Grounding tutorial](https://aistudio.google.com/docs/google-search) tolearn more.

content\_copy

Python

JavaScript

Python

```
import asyncio
import wave
from google import genai
from google.genai import types

client = genai.Client()

model = "gemini-3.8-live"

tools = [{'google_search': {}}]
config = {"response_modalities": ["AUDIO"], "tools": tools}

async def main():
    async with client.aio.live.connect(model=model, config=config) as session:
        prompt = "When did the last Brazil vs. Argentina soccer match happen?"
        await session.send_client_content(turns={"parts": [{"text": prompt}]})

        wf = wave.open("audio.wav", "wb")
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(24000) # Output is 24kHz

        async for chunk in session.receive():
            if chunk.server_content:
                if chunk.data is not None:
                    wf.writeframes(chunk.data)

                # The model might generate and execute Python code to use Search
                model_turn = chunk.server_content.model_turn
                if model_turn:
                    for part in model_turn.parts:
                        if part.executable_code is not None:
                            print(part.executable_code.code)

                        if part.code_execution_result is not None:
                            print(part.code_execution_result.output)

        wf.close()

if __name__ == "__main__":
    asyncio.run(main())
```

## Combining multiple tools   link

You can combine multiple tools within the Live API,increasing your application's capabilities even more:

content\_copy

Python

JavaScript

Python

```
prompt = """
Hey, I need you to do two things for me.

1. Use Google Search to look up information about the largest earthquake in California the week of Dec 5 2024?
2. Then turn on the lights

Thanks!
"""

tools = [\
    {"google_search": {}},\
    {"function_declarations": [turn_on_the_lights, turn_off_the_lights]},\
]

config = {"response_modalities": ["AUDIO"], "tools": tools}

# ... remaining model call
```

## What's next   link

- Check out more examples of using tools with the Live API in the [Tool use cookbook](https://colab.research.google.com/github/google-gemini/cookbook/blob/main/quickstarts/Get_started_LiveAPI_tools.ipynb).

- Get the full story on features and configurations from the [Live API Capabilities guide](https://aistudio.google.com/docs/live-api/capabilities).


On this page  expand\_less

- [Overview of supported tools](https://aistudio.google.com/docs/live-api/tools#tools-overview)

- [Function calling](https://aistudio.google.com/docs/live-api/tools#function-calling)

- [Asynchronous function calling](https://aistudio.google.com/docs/live-api/tools#async-function-calling)

- [Grounding with Google Search](https://aistudio.google.com/docs/live-api/tools#google-search)

- [Combining multiple tools](https://aistudio.google.com/docs/live-api/tools#combine-tools)

- [What's next](https://aistudio.google.com/docs/live-api/tools#whats-next)


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