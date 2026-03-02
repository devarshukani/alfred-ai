# Alfred AI

An on-device AI voice assistant for Android, powered by [Mistral Large](https://mistral.ai/) for reasoning and function calling, with on-device vector memory and a knowledge graph backed by [ObjectBox](https://objectbox.io/).

Alfred registers as the device assistant — invoke it with a long-press on Home or the assistant gesture. It runs as a transparent overlay with a wave-bar UI, no full-screen activity needed.

## Features

- **Voice in / voice out** — Android SpeechRecognizer for STT, [Sherpa-ONNX](https://github.com/k2-fsa/sherpa-onnx) (Piper VITS) for on-device TTS
- **Mistral function calling** — multi-turn tool-call loop with automatic retry and rate-limit handling
- **Semantic skill routing** — user queries are embedded on-device and matched to the most relevant skills via cosine similarity, so only a handful of tools are sent to the LLM per turn
- **On-device vector memory** — facts and preferences stored as ObjectBox entities with 384-dim embeddings (all-MiniLM-L6-v2), recalled via nearest-neighbor search
- **Knowledge graph** — entities and relationships extracted from conversation, stored as graph nodes/edges in ObjectBox, traversed for richer context
- **Rich visual cards** — the LLM can render interactive cards (carousels, charts, score cards, buttons) via a `show_card` tool
- **Runtime model download** — ONNX models (~100 MB total) are downloaded during onboarding, not bundled in the APK

### Skills

| Skill | Capabilities |
|---|---|
| Phone | Search contacts, make/dial calls |
| SMS | Send messages, open SMS app |
| Alarm / Timer | Set, dismiss, snooze alarms; countdown timers; stopwatch |
| Calculator | Math expressions, unit conversions |
| Calendar | Create events, view today/tomorrow/week schedule |
| Mail | Compose emails, share via email |
| Search | Launch apps, search contacts, device settings, web search (DuckDuckGo) |
| Weather | Current conditions + 3-day forecast (Open-Meteo, no API key) |
| Location | GPS coordinates, reverse geocoding |
| Maps | Directions, place search |
| Payments | UPI payments, launch payment apps (GPay, PhonePe, Paytm, etc.) |
| Stocks | Real-time prices + chart data (Indian NSE/BSE and US markets) |
| Memory | Persistent remember/recall/update/forget with semantic search |

## Architecture

```
User voice → SpeechRecognizer (STT)
  → AlfredBrain
    → EmbeddingModel (embed query)
    → SkillRegistry (select top-K skills by cosine similarity)
    → MemoryStore (inject relevant memories into system prompt)
    → MistralClient (chat + function calling loop)
      ↔ Skill.executeTool() (up to 5 iterations)
    → SherpaOnnxTts (speak response)
  → WaveAssistantScreen (overlay UI + rich cards)
```


## Project Structure

```
app/src/main/java/com/alfredassistant/alfred_ai/
├── OverlayAssistActivity.kt        # Entry point — transparent overlay activity
├── MainActivity.kt                 # Launcher activity (onboarding + dashboard)
├── assistant/
│   ├── AlfredBrain.kt              # Orchestrator — routes queries through skills
│   └── MistralClient.kt            # Mistral API client with tool-call loop
├── db/
│   ├── ObjectBoxStore.kt           # ObjectBox store singleton
│   ├── MemoryEntity.kt             # Vector-enabled memory entity
│   ├── KnowledgeNode.kt            # Knowledge graph node
│   ├── KnowledgeEdge.kt            # Knowledge graph edge
│   └── ToolEntity.kt               # Tool embedding entity
├── embedding/
│   └── EmbeddingModel.kt           # all-MiniLM-L6-v2 ONNX inference (384-dim)
├── models/
│   └── ModelDownloader.kt          # Runtime download of TTS + embedding models
├── skills/                         # Skill definitions (tool schemas + execution)
│   ├── Skill.kt                    # Base Skill interface + ToolDef DSL
│   ├── SkillRegistry.kt            # Embedding-based skill selection
│   ├── MemorySkill.kt, PhoneSkill.kt, WeatherSkill.kt, ...
├── tools/                          # Action implementations (Android intents, APIs)
│   ├── MemoryStore.kt, KnowledgeGraph.kt
│   ├── PhoneAction.kt, AlarmAction.kt, WeatherAction.kt, ...
├── speech/
│   └── SpeechHelper.kt             # STT + TTS orchestration
├── tts/
│   └── SherpaOnnxTts.kt            # Sherpa-ONNX TTS wrapper
└── ui/
    ├── WaveAssistantScreen.kt       # Main overlay UI (Compose)
    ├── AlfredWaveBar.kt             # Animated wave bar
    ├── RichCardBox.kt               # Rich card renderer
    ├── OnboardingScreen.kt          # Permission + model download flow
    ├── MainDashboardScreen.kt       # Launcher dashboard
    └── theme/                       # Material 3 theme
```

## Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 11+
- Android SDK 35 (compile) / min SDK 28
- A [Mistral AI](https://console.mistral.ai/) API key

## Setup

### 1. Clone the repo

```bash
git clone https://github.com/your-username/alfred-ai.git
cd alfred-ai
```

### 2. Add your API key

Create or edit `local.properties` in the project root:

```properties
MISTRAL_API_KEY=your_mistral_api_key_here
# Optional: use a Mistral Agent instead of raw model
# MISTRAL_AGENT_ID=your_agent_id
```

> `local.properties` is gitignored and never committed.

### 3. Download models (development only)

For local development and testing, run the setup script to download TTS and embedding models into `app/src/main/assets/`:

```bash
chmod +x setup-models.sh
./setup-models.sh
```

This downloads:
- **Sherpa-ONNX AAR** (~50 MB) — native TTS runtime → `app/libs/`
- **Piper TTS model** (vits-piper-en_US-ryan-medium, ~80 MB) — `app/src/main/assets/models/tts/`
- **all-MiniLM-L6-v2** (~22 MB ONNX + vocab) — `app/src/main/assets/models/embedding/`

> In production builds, the large ONNX models are **not** bundled in the APK. They are downloaded at runtime during the onboarding flow via `ModelDownloader`. Only the small files (`tokens.txt`, `espeak-ng-data/`) need to be in assets.

### 4. Build and run

Open the project in Android Studio and run on a physical device (arm64). Or from the command line:

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

> The app filters to `arm64-v8a` only. Emulators on x86_64 won't work with the native ONNX libraries.

## Signing a Release Build

Create a `keystore.properties` file in the project root:

```properties
storeFile=alfred-key.jks
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

Then build the release APK:

```bash
./gradlew assembleRelease
```

The signed APK will be at `app/build/outputs/apk/release/app-release.apk`.

## Key Dependencies

| Library | Purpose |
|---|---|
| [Sherpa-ONNX](https://github.com/k2-fsa/sherpa-onnx) | On-device TTS (Piper VITS) with bundled ONNX Runtime |
| [ONNX Runtime Android](https://onnxruntime.ai/) | Embedding model inference (all-MiniLM-L6-v2) |
| [ObjectBox](https://objectbox.io/) | On-device vector database with HNSW index |
| [OkHttp](https://square.github.io/okhttp/) | HTTP client for Mistral API |
| [Jetpack Compose](https://developer.android.com/jetpack/compose) + Material 3 | UI framework |
| [Apache Commons Compress](https://commons.apache.org/proper/commons-compress/) | tar.bz2 extraction for model downloads |
| [Tabler Icons](https://tabler.io/icons) | Icon set for Compose UI |

## Permissions

The app requests the following permissions as needed during onboarding:

| Permission | Used for |
|---|---|
| `RECORD_AUDIO` | Voice input (STT) |
| `INTERNET` | Mistral API, weather, web search, model downloads |
| `READ_CONTACTS` | Contact search for calls, SMS, payments |
| `CALL_PHONE` | Making phone calls |
| `SET_ALARM` | Setting alarms and timers |
| `READ_CALENDAR` / `WRITE_CALENDAR` | Calendar events |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Weather, maps, location queries |
| `SEND_SMS` | Sending text messages |

## License

This project is not yet licensed. All rights reserved.
