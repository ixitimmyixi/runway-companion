# Runway Companion

A Minecraft AI voice companion, with:
- **Brain:** an external LLM (OpenAI-compatible `/chat/completions`).
- **Voice:** **Runway** Text-to-Speech (`eleven_multilingual_v2`, `POST /v1/text_to_speech`).

Say `Runway <message>` in chat (the wake word is configurable). The mod intercepts it, asks the LLM for a reply
(with live world state as context), speaks it through Runway TTS, and echoes the
text back in chat.

## Why the LLM is external

The Runway public API has Text-to-Speech but **no standalone text/LLM endpoint** —
its only conversational model is the real-time `gwm1_avatars` (video+audio). So
the voice comes from Runway and the reply text comes from an LLM provider you
choose. `LlmClient` speaks the standard OpenAI chat format, so you can point it
at OpenAI, Anthropic-compatible gateways, Groq, or an internal endpoint by
editing `llmBaseUrl` / `llmModel` / `llmApiKey` in the config.

## Architecture (pure Java — no sidecar, no browser, no WebRTC)

```
player chat  ──►  ChatHandler  ──►  LlmClient (HTTPS, external LLM)  ──► reply text
                                       │
                                       ▼
                               RunwayTts (HTTPS, /v1/text_to_speech, poll task)
                                       │
                                       ▼
                               AudioPlayer (javax.sound) ──► speakers
```

## Config

On first launch the mod writes `.minecraft/config/companion.properties`. Fill in:

```
wakeWord=Runway
personality=You are a concise, in-character companion...
llmBaseUrl=https://api.openai.com/v1
llmModel=gpt-4o-mini
llmApiKey=            # your LLM key
runwayBaseUrl=https://api.dev.runwayml.com
runwayApiKey=         # your Runway key
ttsModel=eleven_multilingual_v2
ttsVoice=             # your voice id
```

Keys live only in this local file — never in the jar, and please never paste them
into a chat. (For a *published* mod you'd proxy calls through a server so keys
aren't shipped to players; for personal use, local config is standard.)

## Compiling (do this on your own machine — needs internet)

Requires **JDK 17** (Temurin/Adoptium is fine). Two routes:

### Route A — Forge MDK (most reliable)
1. Download the **Forge 1.20.1 MDK** from https://files.minecraftforge.net (pick
   1.20.1, "Mdk"). Unzip it — it already contains `gradlew`, the Gradle wrapper,
   and a working `build.gradle`.
2. Copy this project's `src/` folder into the MDK, replacing its `src/`.
3. Open the MDK's `build.gradle` and confirm `mappings ... version: '1.20.1'` and
   the `net.minecraftforge:forge:1.20.1-47.3.0` dependency (they'll already be
   there). No extra dependencies are needed — this mod is pure Java.
4. Build:
   ```
   ./gradlew build        # Windows: gradlew.bat build
   ```
5. Your jar is in `build/libs/runway-companion-0.1.0.jar`. Drop it in `.minecraft/mods`
   (with Forge 1.20.1 installed). Launch, then edit the generated config.

### Route B — use the files in this repo directly
1. This repo has `build.gradle`, `settings.gradle`, `gradle.properties`, and `src/`.
2. Generate the Gradle wrapper once (needs Gradle installed, or reuse an MDK's):
   ```
   gradle wrapper --gradle-version 8.8
   ./gradlew build
   ```
3. Same output path as above.

First build downloads Minecraft + Forge from Forge/Mojang Maven and takes a few
minutes. Later builds are fast.

## Known things to finish / verify

- **TTS field names.** `RunwayTts.buildBody()` sends `text` and `voice`. Confirm
  the exact field names for `/v1/text_to_speech` in the API reference
  (https://docs.dev.runwayml.com/api) and adjust if they differ.
- **Audio format.** `javax.sound` decodes WAV/AU/AIFF natively. If Runway returns
  MP3, add an MP3 SPI decoder (`mp3spi` + `jlayer`) to `dependencies` and the
  existing `AudioPlayer` handles it unchanged.
- **Voice input (optional).** This version takes typed input. For spoken input
  you'd add speech-to-text (Runway has `speech_to_speech`/dubbing but not plain
  STT; use an external STT) — a natural next step.

## Originality / disclosure

This mod uses only original assets and text. It is not affiliated with, and does
not reuse the name, characters, or assets of, any other mod. If you publish it,
keep your companion's identity (name, look, voice, personality) your own. Also
state in your mod description that chat text is sent to external AI services and
that audio is generated remotely, since players expect that disclosure.

## In-game config screen

You can set everything without touching the file:
- **Mods** button (title screen or pause menu) → **runway-companion** → **Config**, or
- press **K** in-game.

Fill in the fields and click **Save** — it writes to `companion.properties` and
applies immediately. (Rebind or disable the K key under Options → Controls →
Companion if it clashes.)

## Volume and voice picker

The config screen now has a **Volume** slider (0–100, applied to the companion's
speech) and a **Fetch voices from API** button that lists your Runway custom
voices; picking one fills in the voice id. Preset/built-in voice ids can still be
typed into the "TTS voice id" field directly. (`voices.list` returns your org's
*custom* voices only.)

## Voice input (push-to-talk)

Hold the push-to-talk key (default **V**, rebindable in **Options → Controls →
Companion**) and speak; release to send. Audio is captured from your chosen mic,
transcribed by a speech-to-text service, and the transcript runs through the
normal LLM→TTS flow. Your words are echoed in chat as a check.

Runway has no plain STT, so transcription uses an external OpenAI-compatible
endpoint. Defaults target **Groq's free Whisper** (`https://api.groq.com/openai/v1`,
`whisper-large-v3-turbo`) — sign up at console.groq.com for a free key
(no card; ~2,000 requests/day). You can point STT at OpenAI or any compatible
endpoint instead. Configure mic device and STT on **page 2** of the config screen
(the "Voice input ▶" button), or in `companion.properties`.

### One provider for everything: Groq (default)
The defaults use **Groq** for both the LLM and STT, so a single free Groq key
(console.groq.com, no card) powers the whole mod. Put that key in **either** the
LLM API key or STT API key field — each falls back to the other.

- LLM base URL: `https://api.groq.com/openai/v1`
- LLM model: `openai/gpt-oss-20b` (Groq rotates models; if you get a
  model-not-found error, check console.groq.com/docs/models for the current name)
- STT base URL: `https://api.groq.com/openai/v1`, model `whisper-large-v3-turbo`

### Prefer Claude for the LLM instead?
Set LLM base URL to `https://api.anthropic.com/v1`, model to `claude-sonnet-4-6`,
and an Anthropic key from platform.claude.com (uses Anthropic's OpenAI-compat
layer). You can still keep Groq for STT.
