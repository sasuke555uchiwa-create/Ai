# SASUKE AI Web & Local Search Integration

A comprehensive search architecture for SASUKE AI that combines real-time grounded web search powered by a Cloudflare Worker and Tavily Search API with instant local full-text search across historical conversations and messages.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural and user experience decisions were confirmed through interactive clarification and will guide this implementation:

- **Confirmed Search Backend**: Cloudflare Worker with Tavily Search API. Keeps the Tavily API secret off client devices, provides abuse rate limiting and optional Firebase auth verification, and returns AI-optimized snippets without requiring paid Google Search Grounding quota on Gemini.
- **Confirmed Placement**: Dual search experience—both a dedicated standalone Search screen (`SearchScreen.kt`) for structured web research and a seamless toggle within the main Chat screen (`ChatScreen.kt`).
- **Confirmed Local Search**: In addition to real-time web search, full-text search over saved conversation sessions and message history in the local Room database (`AppDao.kt` / `HistoryScreen.kt`) will be delivered.
- **Fallback Strategy**: When the Search Worker is unreachable, unconfigured, or quota-exhausted, the app surfaces a clear non-blocking explanation with a 1-tap option to continue query execution via Standard Gemini AI (ungrounded), guaranteeing zero chat dead-ends.

---

## 1. Overview & Core Concept

- **What It Does**: Enhances SASUKE AI with dual-layer search capabilities:
  1. *Real-time Web Search*: Users can query live events, facts, and external data. Bounded web search snippets from Tavily are fed into Gemini 3.5 Flash or Gemini 3.1 Pro as untrusted reference material, producing grounded answers with verifiable, clickable source links.
  2. *Local Conversation Search*: Users can search past discussions, code snippets, and research notes across their conversation history with instant query debouncing and highlighted match previews.
- **Target Audience / Persona**: Users seeking up-to-date knowledge, factual verification, and effortless retrieval of prior AI conversations within a high-performance Android experience.
- **Key Value**: Eliminates HTTP 429 quota exhaustion from Gemini native Google grounding, keeps third-party API credentials secure behind a serverless proxy, and provides rapid discovery across both the live web and local device history.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Dedicated Web Search Flow (`SearchScreen.kt`)**:
   - User navigates to the "Search" tab on the bottom navigation bar.
   - User enters a research query and taps the search action button.
   - An active status badge indicates live web retrieval (`Searching web via Tavily...`).
   - If successful, the screen renders:
     - Grounded AI synthesis card with latency metrics.
     - Verified source chips displaying site titles and external link icons, opening the original URL in the browser when tapped.
   - If the worker is unconfigured or returns an error, an informative card explains the status and provides a 1-tap "Ask Standard AI" button alongside an "Open in Google Search" fallback button.

2. **Chat Toggle Search Flow (`ChatScreen.kt`)**:
   - User taps the Globe / Search icon in the chat input bar to toggle Web Search mode.
   - The UI displays an active neon badge (`🌐 Web Search Active`).
   - Sending a prompt fetches real-time Tavily search snippets via the Cloudflare Worker, prompts Gemini with strict grounding and injection-safety instructions, and streams the grounded answer.
   - Clickable source pills appear beneath the completed assistant message.
   - If search fails, an error banner appears with a direct "Ask Standard AI" button to re-run the prompt without search.

3. **Local History Search Flow (`HistoryScreen.kt`)**:
   - User navigates to the "History" tab.
   - A search bar at the top allows typing keywords (e.g., "Kotlin", "Recipe", "API").
   - The list dynamically filters matching sessions and displays message preview snippets containing the keyword.
   - Tapping any session opens the chat thread directly.

4. **Settings & Worker Setup Flow (`SettingsScreen.kt`)**:
   - Under the "Web Search Provider" section, the user sees the Cloudflare Worker URL input field and a "Test Connection" button.
   - Tapping "Test Connection" pings the `/health` endpoint and shows a confirmation toast with latency.
   - An expandable setup guide provides the 4 deployment steps (`npx wrangler secret put TAVILY_API_KEY`, `npx wrangler deploy`).

### Visual Identity & Theme

- **Aesthetic Direction**: SASUKE Cyberpunk Dark / Premium Tech aesthetic matching the existing design language.
- **Color Tokens**:
  - `SasukeNeonCyan` (`#00F2FE`): Search accents, active search badges, and verified source chips.
  - `SasukeElectricViolet` (`#7B2CBF`): Primary action buttons and search execution buttons.
  - `SasukeCobaltBlue` (`#2244FF`): Secondary actions, "Ask Standard AI" fallback buttons.
  - `SasukeRedError` (`#FF4D4D`): Search failure and quota-exhaustion alert containers.
  - Dark container surfaces (`#1A1C29` / `surfaceVariant`) for search cards and source containers.
- **Interactive Feedback**:
  - Pulsing progress indicator during live search queries.
  - Instant ripple effects on source chips and history items.
  - Non-intrusive dismissible error cards.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Cloudflare Worker Backend vs. Direct Client-Side Tavily API Calls**
  - *Chosen Approach*: Serverless Cloudflare Worker proxy (`/search-worker`).
  - *Why*: Storing the Tavily API key on Android client devices or in `BuildConfig` risks credential leakage via APK decompilation. The worker enforces per-IP rate limiting, validates query inputs, supports Firebase ID token authentication, and shields the API quota.
  - *Alternatives Considered*: Direct REST call from Android was rejected due to API key exposure and lack of abuse protection.

- **Decision 2: Untrusted Context Grounding vs. Native Model Tools**
  - *Chosen Approach*: Retrieve top 5 bounded snippets from Tavily and supply them via system instructions with prompt-injection defenses; do NOT pass the native `googleSearch` tool in the Gemini request.
  - *Why*: Gemini's native `googleSearch` tool requires an active Google Cloud billing account and frequently triggers HTTP 429 `RESOURCE_EXHAUSTED` errors on standard API keys. Supplying external snippets directly to the LLM operates on standard Gemini generation quota and guarantees accurate, non-fabricated citations using only original Tavily URLs.

- **Decision 3: Room Database Full-Text Search Queries**
  - *Chosen Approach*: Add `LIKE` filtering DAO queries (`searchSessions` and `searchMessages`) in `AppDao.kt` for local history discovery.
  - *Why*: Instant, offline-capable, zero external dependency, and fully synchronized with the existing Firestore chat repository.

---

## 4. Technical Architecture & Data Strategy

### Architecture & Component Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                          SASUKE AI Android App                         │
│                                                                        │
│   ┌────────────────────┐   ┌───────────────────┐   ┌────────────────┐  │
│   │   ChatScreen       │   │   SearchScreen    │   │ HistoryScreen  │  │
│   │  (Search Toggle)   │   │  (Dedicated Tab)  │   │ (Local Search) │  │
│   └─────────┬──────────┘   └─────────┬─────────┘   └───────┬────────┘  │
│             │                        │                     │           │
│             ▼                        ▼                     ▼           │
│   ┌────────────────────────────────────────────┐   ┌────────────────┐  │
│   │               ChatViewModel                │   │     AppDao     │  │
│   └─────────┬────────────────────────┬─────────┘   │ (Room SQLite)  │  │
│             │                        │             └────────────────┘  │
│             ▼                        ▼                                 │
│   ┌──────────────────┐     ┌──────────────────┐                        │
│   │ TavilySearch-    │     │  GeminiApiClient │                        │
│   │ WorkerClient     │     │ (Grounded Prompt)│                        │
│   └─────────┬────────┘     └──────────────────┘                        │
└─────────────┼──────────────────────────────────────────────────────────┘
              │ HTTPS POST /search
              ▼
┌────────────────────────────────────────────────────────────────────────┐
│                   Cloudflare Worker Backend (/search)                   │
│                                                                        │
│   - Rate Limiting & Query Validation                                  │
│   - Firebase ID Token Verification (Optional Header)                   │
│   - Tavily API Key from Cloudflare Secrets (TAVILY_API_KEY)            │
│   - Quota exhaustion (429) & upstream error normalization              │
└─────────────┬──────────────────────────────────────────────────────────┘
              │ HTTPS POST /search (Authorization: Bearer)
              ▼
┌────────────────────────────────────────────────────────────────────────┐
│                           Tavily Search API                            │
│                        (https://api.tavily.com)                        │
└────────────────────────────────────────────────────────────────────────┘
```

### Component & State Mapping

| Component | State / Events | Handlers & Outcomes |
| :--- | :--- | :--- |
| `ChatScreen` | `isSearchGroundingEnabled`, `activeTool` | Toggles search mode; displays active badge and source chips under assistant messages. |
| `SearchScreen` | `queryText`, `isSearching`, `sourcesList`, `errorMessage` | Executes standalone web query; renders synthesis card, latency, source chips, or fallback action buttons. |
| `HistoryScreen` | `searchQuery`, `filteredSessions` | Debounced search field filters sessions and previews matching message text in Room. |
| `SettingsScreen` | `searchWorkerUrlInput`, `testStatus` | Saves worker URL to `UserPreferences`; runs `/health` test check. |
| `TavilySearchWorkerClient` | `search(query, idToken)`, `healthCheck()` | Calls Cloudflare Worker; maps JSON to typed `TavilySearchResponse.Success` or `.Error`. |
| `GeminiApiClient` | `streamGenerateContent(...)` / `generateContent(...)` | Executes Gemini request with injection-safe grounding system instructions and `enableSearchGrounding = false`. |

### Execution Phases & Deliverables

1. **Verify Cloudflare Worker Backend (`/search-worker`)**: Ensure endpoint `/search` and `/health` handle errors, rate limits, and quota exhaustion with unit tests passing via `node --test`.
2. **Update Room Persistence (`AppDao.kt`)**: Add search queries for sessions and messages (`searchSessions`, `searchMessages`).
3. **Refine History Screen (`HistoryScreen.kt`)**: Add the search input header, dynamic filtering, and highlighted snippet matching.
4. **Refine Chat & Search UI (`ChatScreen.kt`, `SearchScreen.kt`, `ChatViewModel.kt`)**: Connect Tavily search streaming, verified source chips, and 1-tap ungrounded fallback.
5. **Verification & Tests**: Execute unit tests for worker and Android client; confirm build compilation via `compile_applet`.
