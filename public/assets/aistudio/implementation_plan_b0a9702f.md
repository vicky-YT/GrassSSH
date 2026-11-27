# Clean AI Quota & Error Notice in GrassSSH AI System

Replace the oversized, raw Gemini API quota error walls across **Ask AI**, **Pro Build**, and **Fix Console** with a clean, compact notice displaying **"Daily limit reached. Please try again tomorrow."** and concise, user-friendly error formatting for other failures.

## User Review & Critical Decisions

> [!IMPORTANT] Confirming your Phase 1 answer: when the daily or rate quota is exceeded, every AI mode will display the exact short message **"Daily limit reached. Please try again tomorrow."** inside a compact, dismissible notice bar.

- **Confirmed Limit Copy**: Display `"Daily limit reached. Please try again tomorrow."` whenever primary or fallback Gemini requests fail due to daily quota or rate-limit exhaustion.
- **Recommended Scope for Other Errors**: Shorten other verbose backend/network errors (strip raw documentation URLs, API metric names, and multi-line stack details) into clean 1–2 line messages while keeping real diagnostic visibility (e.g., network offline or timeout).
- **Recommended Banner Design**: Compact single-row/two-line tonal warning/error banner with a subtle icon and a dismiss (`×`) action so it never dominates the screen or hides the prompt box.

---

## 1. Overview & Core Concept

- **What It Does**: Intercepts raw Gemini API exceptions in the GrassSSH AI repository layer before they reach UI state, mapping quota/rate-limit errors cleanly to `"Daily limit reached. Please try again tomorrow."` and trimming noisy backend URLs/metric strings from all other errors.
- **Target Audience & Context**: Mobile SSH users running GrassSSH on phone screens where a 12-line raw API error card pushes controls off-screen and looks broken.
- **Key Value**: Keeps the AI workspace clean, readable, and calm even when the free-tier daily quota is hit.

---

## 2. User Experience & Visual Design

### Key User Flows

1. User triggers an action in **Ask AI**, **Pro Build**, or **Fix Console** after the daily free-tier quota is exhausted.
2. Primary (`gemini-3.5-flash-lite`) and fallback (`gemini-3.8-flash`) calls run; if quota/429 exhaustion occurs, the repository maps the failure to `"Daily limit reached. Please try again tomorrow."`.
3. Instead of a tall red text block filled with URLs and metric IDs, a compact, rounded notice card appears with a clear icon, the single-sentence message, and a 48dp dismiss button (`×`) so the user can clear it immediately.

### Visual Identity & Styling

- **Color & Surface**: Keep the existing GrassSSH dark terminal Material 3 palette (`CoralError` for hard failures, `AmberWarning` / soft tonal container for daily quota notices) so limit states feel informative rather than catastrophic.
- **Compact Layout & Hierarchy**: Maximum 2 lines of clean body text (`bodySmall` / `bodyMedium`), 12dp–14dp horizontal padding, and a dedicated 48dp touch-target close button on the right.
- **Consistency**: Shared compact error/limit banner component reused identically across **Ask AI**, **Pro Build**, and **Fix Console**.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Centralize Error Mapping in the AI Repository**
  - *Chosen Approach*: Normalize quota and API exceptions in a dedicated error-mapper inside the AI repository before throwing to ViewModel UI states, while logging the full raw exception via `Log.e` for developer debugging.
  - *Why*: Ensures all three modes (chat streaming, build plan generation, and console error fixing) consistently surface `"Daily limit reached. Please try again tomorrow."` without duplicating string checks in Compose views.
  - *Alternatives Considered*: Parsing error text inside each UI composable, which leads to drift between Ask AI, Pro Build, and Fix Console.

- **Decision 2: Keep Quota Errors Distinct from Transient High-Demand Retries**
  - *Chosen Approach*: Only treat transient 503/unavailable spikes as high-demand fallback triggers; when daily free-tier quota (`limit: ...`, `quota exceeded`, `generate_content_free_tier_requests`) is exceeded on both or either model, immediately surface the clean `"Daily limit reached. Please try again tomorrow."` message instead of displaying raw retry-timer URLs.
  - *Why*: Avoids leaking raw `https://aistudio.google.com/...` URLs and microsecond countdowns (`16h43m13.913449759s`) into mobile UI cards.

- **Decision 3: Dismissible Notice Banner**
  - *Chosen Approach*: Add an explicit clear/dismiss button directly on the error/limit notice card across all three AI tabs.
  - *Why*: Users on small phone screens should be able to dismiss a limit notice in one tap without clearing their entire chat history or prompt text.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

```
┌──────────────────────────────┐
│   Ask AI / Pro Build / Fix   │
│   Shared Compact Notice UI   │◄─── Dismiss action clears UiState.Error
└──────────────▲───────────────┘
               │ UiState.Error(cleanMessage)
┌──────────────┴───────────────┐
│        MainViewModel         │
│  Collects Flow / Suspend     │
└──────────────▲───────────────┘
               │ Maps raw exceptions before surfacing
┌──────────────┴───────────────┐
│     GrassSshAiRepository     │
│  1. Detect Quota / Rate 429  │──► "Daily limit reached. Please try again tomorrow."
│  2. Strip URLs & Metrics     │──► Concise 1-line summary + Log.e(full raw)
└──────────────────────────────┘
```

- **State & Error Handling**:
  - `AiSystemUiState.Error`, `BuildPlanUiState.Error`, and `ConsoleFixUiState.Error` continue using clean sealed UI state transitions, with a `clearError` / existing `clearBuildPlan` / `clearConsoleFix` / chat-error reset action wired to the banner's dismiss button.
  - `formatFailureMessage(...)` inspects `error.localizedMessage` and `error.cause` for quota markers (`quota`, `rate limit`, `rate-limits`, `free_tier`, `429`, `resource exhausted`) and returns `"Daily limit reached. Please try again tomorrow."` directly without prefixes like `"Build plan failed:"`.
  - For non-quota errors, URLs (`https://...`), internal metric blocks (`* Quota exceeded...`), and redundant prefixes are stripped and clamped to a clean single-sentence explanation, while full diagnostic text remains in Android Logcat.
- **Verification Plan**:
  - Add pure JVM unit tests covering quota-exception mapping (including the exact `"You exceeded your current quota... free_tier_requests..."` payload from the screenshot) and non-quota URL stripping.
  - Verify Android compilation and unit tests pass cleanly.
