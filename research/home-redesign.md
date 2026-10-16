# Aura Home Redesign Spec

Date: 2026-10-16. Status: design spec only, no code changed.
Author: design subagent. Implements against CONTRACT.md v1 (no new tables,
no new routes, no new backends).

## What is wrong with the current home

From Charan's screenshot and the code (`ui/home/HomeScreen.kt`):

1. No focal point. Every section is a same-shaped 22dp card with the same
   padding rhythm, so the eye has nowhere to land. The stress hero should
   dominate; instead it is the same visual weight as everything else.
2. The hero is weak. The ring is small next to a big empty card, the label
   ("Moderate / 25 out of 100") confuses, and factor lines truncate into
   uselessness ("Health Connect unavailable; mood...").
3. The Connect Health card is enormous for a conditional banner and pushes
   real content down.
4. The page answers no question. A home screen must answer "how am I doing"
   and "what do I do now". Today it shows stats with no next action.
5. Sparse sections. Streak card, 4 tiles, a 3-row timeline, a promo. It reads
   as empty because nothing on it is dense or alive: no trends, no program
   state, no prompt, no evening plan, even though the app already owns all
   of that data.

## Design rules for the redesign

- Aurora Calm holds: dawn gradient `#2DD4BF -> #22D3EE -> #A78BFA -> #F9A8D4`,
  Nunito Black headlines, dark `#0E1516`, M3 Expressive shapes, no red/orange
  structural color.
- Exactly ONE gradient-filled card per screen (the Continue card). Everything
  else sits on `surfaceContainerHigh`. Scarcity makes the action obvious.
- One hero, one action, then supporting sections. Strict visual hierarchy:
  hero (28dp, biggest type) > action card (gradient) > section cards (22dp).
- Every section must be tappable and land on an existing route. No dead cards.
- Data discipline: only Room tables, DataStore, Health Connect, and the
  static catalogs from CONTRACT.md section 1. ViewModel changes needed are
  flagged per section (inject `BreathingRepository`, `SleepRepository`;
  both already exist).

## New home structure (top to bottom)

### 1. Greeting + smart nudge (no card, plain text under the top bar)
Purpose: make the page feel alive and personal on every open.
Shows: time-aware greeting ("Good evening"), today's date, and one nudge
line derived from the stress score label and today's check-in state, e.g.
"Score is up from yesterday. A 4-minute reset can help."
Data: `StressScore.label`, `observeTodayCheckIn()`, both already in the
ViewModel.
Tap: nudge line -> `BreatheRoute`.
Note: keep it to two lines max, `titleMedium`, no card chrome.

### 2. Stress hero (redesigned, the focal point)
Purpose: "how am I doing" at a glance, with trend, not just a number.
Shows: larger ring (168dp), score in `displayLarge` Black, full label,
7-day mood sparkline (7 small bars from `observeMoodHistory(7)`, colored by
score 1..5 on the dawn scale), delta line ("down 8 vs last week" computed
from the same history), and the top factor as ONE fully readable line
(no truncation of the lead factor).
Data: `StressScore`, `observeMoodHistory(7)`. Already available.
Tap: whole card -> `InsightsRoute` (score trends live there; premium-gated,
which is the correct upsell).
Fix vs current: ring and number get bigger, factors stop truncating, the
sparkline adds the missing "alive" feel.

### 3. Continue card (the action; the only gradient-filled card)
Purpose: answer "what do I do now" with the single best next step.
Shows, first match wins:
  a. Check-in missing today -> "2-minute check-in" + today's date.
     Tap -> `MoodCheckInRoute`.
  b. Active program (from `observePrograms()`, `currentDay < 7`) ->
     "{Program title} - Day {n} of 7, {minutes} min".
     Tap -> `ProgramsRoute`.
  c. No breathing session today (from `observeRecentSessions(10)`,
     filter `startMs` to today) -> "Today's reset - pick your goal".
     Tap -> `BreatheRoute`.
  d. After 18:00 local and no sleep session today ->
     "Wind down tonight". Tap -> `SleepSessionRoute`.
ViewModel change: inject `BreathingRepository` and `SleepRepository`.
Visual: full dawn-gradient background, dark ink text, white pill CTA.
This card replaces the "empty" feeling by itself.

### 4. Health Connect slim banner (conditional, demoted)
Purpose: keep the opt-in without letting it dominate the page.
Shows: single-row banner (48dp icon + one line + text button), only when
`HealthConnectUiState` is `Available` ("Connect to sharpen your score")
or `NeedsInstall` ("Get Health Connect"). Hidden when `Granted` or
`Unavailable`.
Data/tap: existing `onConnectHealth` / `onInstallHealthConnect` flows.
Fix vs current: the current full card (icon + title + paragraph + big
button) is ~230dp tall for a conditional prompt. The banner is ~72dp.

### 5. Your week (replaces streak card AND today timeline)
Purpose: one dense "my progress" card instead of two thin ones.
Shows, in one 22dp card:
  - Left: compact bloom visual (keep, it is the streak identity) + streak
    label (`HomeStats.streakLabel`, already exists).
  - Right: 7 mood bars (Mon-Sun, height/color by score, hollow when missing,
    from `weekDots`/`observeMoodHistory(7)`), calm minutes this week
    (from `observeRecentSessions`, sum `durationSec`), journal count this
    week (from `observeJournalEntries`).
  - A thin divider, then the 3-row today checklist (check-in / calm minutes
    / journal) condensed to one row each, smaller type.
Data: all already observed except weekly calm minutes and journal count,
which derive from flows the repos already expose.
Tap: card -> `InsightsRoute`.
Fix vs current: streak card and timeline merge, so two sparse sections
become one dense one; mood dots grow into informative bars.

### 6. Quick actions (kept, restyled)
Purpose: the four front doors, unchanged in function.
Shows: Check in, Breathe, Journal, Wind down. Keep the 2x2 grid but give
each tile its own dawn-spectrum accent (teal, cyan, violet, pink icon
chips) instead of the identical gradient chip four times; that repetition
is part of why the page looks flat.
Tap: `MoodCheckInRoute`, `BreatheRoute`, `JournalEditorRoute()`,
`SleepRoute` (unchanged).

### 7. Today's prompt (new, cheap, adds editorial content)
Purpose: give the journal a daily reason to exist on the home page.
Shows: "Today's prompt" label + one rotating prompt from the
`JournalPrompts` static catalog (index = `epochDay % 20`, deterministic,
no DB needed) + "Write" text button.
Tap: whole card -> `JournalEditorRoute()` (new entry; the editor can
preselect the prompt - if the editor does not support it yet, pass nothing
and keep the prompt visible as a suggestion).
Data: static catalog only. Zero new queries.

### 8. Wind down tonight (new, surfaces the sleep feature)
Purpose: the sleep tab is currently invisible from home; fix that.
Shows: moon icon, "Wind down tonight", last session ("Last night: 32 min
with Rain + Ocean" from `SleepRepository.observeRecent(1)`), and a
"Start" pill button. Before 18:00 show it collapsed to a slim row;
after 18:00 expand it (bigger card, it is the timely action).
Tap: -> `SleepSessionRoute`.
ViewModel change: inject `SleepRepository` (shared with section 3).
Optional later: append the reminder time from
`SettingsRepository.quietHours` when reminders are on.

### 9. Insights promo (kept, bottom)
Purpose: premium upsell, unchanged.
Shows/tap: existing `InsightsPromoCard` -> `InsightsRoute`.

## Sections removed or merged

- Standalone streak card -> merged into "Your week" (5).
- Standalone today timeline -> merged into "Your week" (5).
- Connect Health full card -> slim banner (4).

## Net effect on scroll

Was: hero, giant health card, streak, 4 tiles, timeline, promo (6 blocks,
~2 screens, feels empty). Now: greeting, hero, continue, banner
(conditional), week, quick actions, prompt, wind-down, promo (9 blocks,
~2.5 screens, every block dense or actionable).

## Out of scope (noted, not specced)

- The "Moderate / 25" label mapping in `computeStressScore()` looks wrong
  (25/100 reading as Moderate). That is data-layer tuning, separate task.
- Widget already shows score + streak; no home changes needed for it.
- RevenueCat/paywall untouched.
