#!/usr/bin/env python3
"""One explicit, bounded Live API compatibility probe; never executes phone tools.

Requires the optional Python websockets package and GEMINI_API_KEY in the process
environment. This contacts Google and consumes the configured account's quota.
It measures text-to-first-audio from this host, not phone microphone latency.
"""

import argparse
import asyncio
import base64
import json
import inspect
import os
from pathlib import Path
import time
from urllib.parse import quote
import wave

import websockets


ENDPOINT = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"


async def probe(setup, key, output):
    started = time.monotonic()
    result = {
        "model": setup["setup"]["model"], "status": "started",
        "measurement_scope": "VPS text prompt to first returned audio; no phone microphone or tool execution",
        "tool_declarations": sum(len(t.get("functionDeclarations", [])) for t in setup["setup"].get("tools", [])),
        "phone_tools_executed": 0, "events": [],
    }
    pcm = bytearray()
    try:
        # The key is constructed only in memory and never written into evidence.
        options = {"open_timeout": 30, "close_timeout": 5, "max_size": 8 * 1024 * 1024}
        if "proxy" in inspect.signature(websockets.connect).parameters:
            # Match the phone's direct connection. An operator can explicitly
            # select a compatible proxy without changing the host environment.
            options["proxy"] = os.environ.get("CIEL_PROBE_PROXY") or None
        async with websockets.connect(ENDPOINT + "?key=" + quote(key, safe=""), **options) as connection:
            result["socket_open_ms"] = round((time.monotonic() - started) * 1000)
            await connection.send(json.dumps(setup))
            async with asyncio.timeout(40):
                while True:
                    event = json.loads(await connection.recv())
                    result["events"].append({"phase": "setup", "keys": list(event)})
                    if "error" in event:
                        raise RuntimeError(json.dumps(event["error"]))
                    if "setupComplete" in event:
                        break
            result["setup_complete_ms"] = round((time.monotonic() - started) * 1000)
            requested = time.monotonic()
            await connection.send(json.dumps({"clientContent": {
                "turns": [{"role": "user", "parts": [{"text": "Say hello briefly in English. Do not call any tools."}]}],
                "turnComplete": True,
            }}))
            transcripts = []
            async with asyncio.timeout(60):
                while True:
                    event = json.loads(await connection.recv())
                    if "error" in event:
                        raise RuntimeError(json.dumps(event["error"]))
                    if "toolCall" in event:
                        result["unexpected_tool_names"] = [c.get("name") for c in event["toolCall"].get("functionCalls", [])]
                        raise RuntimeError("The hello probe requested a tool; no tool was executed")
                    content = event.get("serverContent", {})
                    for part in content.get("modelTurn", {}).get("parts", []):
                        inline = part.get("inlineData", {})
                        if inline.get("mimeType", "").startswith("audio/pcm"):
                            chunk = base64.b64decode(inline["data"], validate=True)
                            if chunk and "first_audio_after_text_ms" not in result:
                                result["first_audio_after_text_ms"] = round((time.monotonic() - requested) * 1000)
                            if len(pcm) + len(chunk) > 4 * 1024 * 1024:
                                raise RuntimeError("Audio exceeded the bounded probe allowance")
                            pcm.extend(chunk)
                    transcription = content.get("outputTranscription", {}).get("text")
                    if transcription:
                        transcripts.append(transcription)
                    if content.get("turnComplete"):
                        result["turn_complete_after_text_ms"] = round((time.monotonic() - requested) * 1000)
                        break
            result["transcript"] = "".join(transcripts)
            result["status"] = "passed" if pcm else "no_audio"
            result["audio_bytes"] = len(pcm)
            if pcm:
                audio_path = output.with_suffix(".wav")
                with wave.open(str(audio_path), "wb") as audio:
                    audio.setnchannels(1)
                    audio.setsampwidth(2)
                    audio.setframerate(24000)
                    audio.writeframes(pcm)
                result["audio_artifact"] = str(audio_path)
    except Exception as error:
        result["status"] = "failed"
        result["error_type"] = type(error).__name__
        result["error"] = str(error).replace(key, "[redacted]")[:600]
    result["total_ms"] = round((time.monotonic() - started) * 1000)
    output.write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result, indent=2))
    return 0 if result["status"] == "passed" else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--setup", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    key = os.environ.get("GEMINI_API_KEY", "").strip()
    if not key:
        parser.error("Set GEMINI_API_KEY in the process environment")
    return asyncio.run(probe(json.loads(args.setup.read_text()), key, args.output))


if __name__ == "__main__":
    raise SystemExit(main())
