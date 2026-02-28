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

## Phase 2 — Function Calling & Device Actions (In Progress)
- [x] **Mistral function calling infrastructure** — tool call loop in AlfredBrain
- [x] **Phone — Search contacts** by name, returns all numbers with labels
- [x] **Phone — Make calls** directly via voice command
- [x] **Phone — Dial number** (open dialer without auto-calling)
- [x] **Phone — Multiple numbers** — AI asks user which number (Mobile/Work/etc.)
- [x] **Alarm** — Set alarms (one-time + recurring with day selection), dismiss, snooze, show all
- [x] **Timer** — Set countdown timers with duration + label, show timers
- [x] **Stopwatch** — Start stopwatch via clock app
- [ ] **Calculation** — Math expressions, unit conversions
- [ ] **Calendar** — Create events, check schedule, reminders

## Phase 3 — Communication & Search
- [ ] **Mail** — Compose, read, summarize emails
- [ ] **Search (Device)** — Find apps, contacts, files, settings
- [ ] **Search (Web)** — Web search and summarize results

## Phase 4 — Advanced Features
- [ ] **Weather** — Current conditions, forecasts (API integration)
- [ ] **Activity Launch (Payment)** — Open payment apps (GPay, etc.)
- [ ] **Notification Listening** — Read, summarize, act on notifications
- [ ] **Memory / Knowledge Graph** — On-device persistent memory, user preferences, learned context

---

## Code Structure
```
app/src/main/java/com/alfredassistant/alfred_ai/
├── OverlayAssistActivity.kt          # Single entry point
├── assistant/
│   ├── AlfredBrain.kt                # Orchestrator — routes to Mistral + function calls
│   └── MistralClient.kt             # Mistral API client (chat + function calling)
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
│   └── memory/MemoryStore.kt
├── speech/
│   └── SpeechHelper.kt               # STT + TTS
└── ui/
    ├── AlfredOrb.kt                   # Animated orb
    ├── AssistantState.kt
    ├── OverlayAssistantScreen.kt
    └── theme/
        ├── Color.kt
        ├── Theme.kt
        └── Type.kt
```
