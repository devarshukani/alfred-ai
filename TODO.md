# Alfred AI — Feature Roadmap

## Brain: Mistral Large 3 (`mistral-large-latest`)
- 675B total params, 41B active, 256k context window
- Supports function calling for action execution

---

## Phase 1 — AI Chat ✅
- [x] Voice input (Speech-to-Text)
- [x] Voice output (Text-to-Speech)
- [x] Transparent overlay UI with animated orb
- [x] Register as device assistant
- [x] Zero-activity app — overlay only, no full-screen UI
- [x] Animated orb with distinct states (idle/listening/processing/speaking)
- [x] Dark gold theme (Batman's Alfred aesthetic)
- [x] Integrate Mistral Large 3 API for intelligent responses
- [x] Conversation memory (multi-turn within session, last 20 exchanges)
- [x] System prompt: Alfred butler persona

## Phase 2 — Function Calling & Device Actions ✅
- [x] **Mistral function calling infrastructure** — tool call loop in AlfredBrain
- [x] **Phone — Search contacts** by name, returns all numbers with labels
- [x] **Phone — Make calls** directly via voice command
- [x] **Phone — Dial number** (open dialer without auto-calling)
- [x] **Phone — Multiple numbers** — AI asks user which number (Mobile/Work/etc.)
- [x] **Alarm** — Set alarms (one-time + recurring with day selection), dismiss, snooze, show all
- [x] **Timer** — Set countdown timers with duration + label, show timers
- [x] **Stopwatch** — Start stopwatch via clock app
- [x] **Calculation** — Math expressions (+,-,*,/,^,%), unit conversions (length, weight, temp, volume, speed, area)
- [x] **Calendar** — Create events (with title, time, location, description, all-day), check today/tomorrow/week schedule, open calendar

## Phase 3 — Communication & Search ✅
- [x] **Mail** — Compose emails (to, subject, body, cc, bcc), open mail app, share via email
- [x] **Search (Device)** — Find and launch apps, search contacts, open specific system settings (wifi, bluetooth, display, sound, battery, storage, location, etc.)
- [x] **Search (Web)** — Web search via DuckDuckGo (no API key needed), summarize results via voice, open browser search, open URLs

## Phase 4 — Advanced Features ✅
- [x] **Weather** — Current conditions + 3-day forecast via Open-Meteo API (free, no key needed), any city worldwide
- [x] **Activity Launch (Payment)** — Launch GPay, PhonePe, Paytm, PayPal, Samsung Pay, CRED, BHIM, etc. + UPI direct payments + list installed payment apps
- [x] **Notification Listening** — Read recent notifications, filter by app, clear history. Uses NotificationListenerService with permission flow.
- [x] **Memory / Knowledge Graph** — On-device persistent memory via SharedPreferences. Remember facts, recall them, set preferences. Memory is injected into system prompt so Alfred always knows what it remembers.

---

## Phase 5 — UI Overhaul & Polish

### Improve UI
- [x] **Remove Orb UI** — Deleted AlfredOrb.kt and InterfaceMode enum, wave bar is now the only interface
- [ ] **Spring physics** — Add spring-based animations (Compose `spring()` specs) to wave bar and UI transitions for more organic, bouncy feel
- [ ] **Drop Batman aesthetic** — Restyle the interactive interface away from dark/gold butler theme to a modern, clean, friendly design (new color palette, softer tones)
- [ ] **Dynamic form creation** — AI can generate interactive UI elements on the fly (input fields, toggles, sliders, date pickers) based on context instead of hardcoded confirmation boxes
- [ ] **Fully dynamic interactive interface** — All user-facing UI (confirmations, options, forms) should be driven by AI tool calls, not static layouts

### Onboarding
- [ ] **Animated onboarding flow** — Instead of requesting all permissions upfront, show a multi-step onboarding with cool animations introducing each feature (phone, calendar, mic, notifications, etc.)
- [ ] **Per-feature permission buttons** — Each onboarding step explains the feature with animation and has a "Grant Access" button for that specific permission

### Fixes
- [ ] **Fix markdown in TTS** — Strip any remaining markdown symbols (**, ##, `, -, *) from AI responses before passing to TTS so they don't get spoken aloud
- [ ] **Debug logging** — Add tagged logs for user speech input and Alfred's spoken responses (e.g. `Log.d("AlfredSTT", ...)` and `Log.d("AlfredTTS", ...)`) for easier debugging

---

## Phase 6 — Vector Memory, Knowledge Graph & Smart Tool Routing 🚧

### ObjectBox Vector Database
- [x] **Add ObjectBox dependency** — ObjectBox 4.0+ with HNSW vector index support for on-device vector search
- [x] **Entity models** — MemoryEntity (facts/preferences with embeddings), KnowledgeNode (knowledge graph nodes), KnowledgeEdge (relationships), ToolEntity (tool definitions with embeddings)
- [x] **ObjectBox initialization** — MyObjectBox store setup in Application/Activity

### On-Device Embedding
- [x] **Embedding model** — Use all-MiniLM-L6-v2 ONNX model (~22MB) for 384-dimensional text embeddings, runs on-device via ONNX Runtime (already bundled with Sherpa)
- [x] **EmbeddingModel class** — Tokenizer + ONNX inference pipeline, mean pooling + L2 normalization, produces FloatArray(384)
- [x] **Lazy initialization** — Model loads on first use, cached for subsequent calls

### Vector-Powered Memory (replaces SharedPreferences MemoryStore)
- [x] **Semantic memory storage** — Facts and preferences stored as ObjectBox entities with vector embeddings
- [x] **Semantic recall** — When recalling, embed the query and do nearest-neighbor search instead of exact key match
- [x] **Memory context injection** — Top-K relevant memories injected into system prompt based on user query similarity
- [x] **Migration** — Import existing SharedPreferences data into ObjectBox on first run

### On-Device Knowledge Graph
- [x] **KnowledgeNode entity** — Nodes with label, type (person/place/thing/concept/event), metadata, and embedding vector
- [x] **KnowledgeEdge entity** — Directed edges with relationship type (e.g. "lives_in", "likes", "works_at"), source/target node IDs
- [x] **Auto-extraction** — When user tells Alfred facts, extract entities and relationships into the graph
- [x] **Graph-aware recall** — When querying memory, also traverse related nodes for richer context
- [x] **Graph query tools** — LLM tools to query the knowledge graph (find related entities, get relationships)

### Smart Tool Routing (Embedding-Based Tool Selection)
- [x] **Tool embedding storage** — Each tool's name + description embedded and stored in ObjectBox at startup
- [x] **Query-based tool filtering** — Before sending to LLM, embed user prompt and find top-K most relevant tools via vector search
- [x] **Dynamic tool list** — Only send relevant tools (e.g. 5-8 instead of 30+) to Mistral, reducing token usage and improving accuracy
- [x] **Always-include tools** — Some tools (present_options, memory tools) always included regardless of similarity score

---

## Code Structure
```
app/src/main/java/com/alfredassistant/alfred_ai/
├── OverlayAssistActivity.kt          # Single entry point
├── assistant/
│   ├── AlfredBrain.kt                # Orchestrator — routes to Mistral + function calls
│   └── MistralClient.kt             # Mistral API client (chat + function calling)
├── db/
│   ├── ObjectBoxStore.kt            # ObjectBox initialization + store singleton
│   ├── MemoryEntity.kt              # Vector-enabled memory entity
│   ├── KnowledgeNode.kt             # Knowledge graph node entity
│   └── KnowledgeEdge.kt             # Knowledge graph edge entity
├── embedding/
│   └── EmbeddingModel.kt            # On-device ONNX embedding (all-MiniLM-L6-v2)
├── features/
│   ├── phone/PhoneAction.kt
│   ├── alarm/AlarmAction.kt
│   ├── timer/TimerAction.kt
│   ├── calculator/CalculatorAction.kt
│   ├── calendar/CalendarAction.kt
│   ├── mail/MailAction.kt
│   ├── search/SearchAction.kt
│   ├── weather/WeatherAction.kt
│   ├── payments/PaymentAction.kt
│   ├── notifications/NotificationAction.kt
│   └── memory/
│       ├── MemoryStore.kt            # Rewritten — ObjectBox + vector search
│       ├── KnowledgeGraph.kt         # On-device knowledge graph operations
│       └── ToolRegistry.kt           # Embedding-based tool selection
├── speech/
│   └── SpeechHelper.kt               # STT + TTS
└── ui/
    ├── AlfredWaveBar.kt               # Wave animation bar
    ├── AssistantState.kt
    ├── ConfirmationBox.kt
    ├── OverlayAssistantScreen.kt
    ├── WaveAssistantScreen.kt
    └── theme/
        ├── Color.kt
        ├── Theme.kt
        └── Type.kt
```
