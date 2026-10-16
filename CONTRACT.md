# App 03 "Aura" — Builder Contract (v2, binding)

Read this before writing a single line. v1's contract prevented the app-02
integration mess; v2 extends it. Every interface, model, and route below is
FINAL. Do not invent alternatives. If two builders need the same thing, it is
defined HERE, not in two places.

## 0. Identity (unchanged from v1)

- App name: **Aura**. Package: `com.sreecharan.stressrelief`.
- Repo: `rootforsree/app-03-stress-management`, branch `main`.
- Design authority: `research/design-direction.md` ("Aurora Calm"). Follow it exactly.
  NO red/orange/yellow in structural UI. NO Apple-HIG styling. Material 3 Expressive.
- **v2 product decision (Charan, 2026-10-16): the app is 100% free.**
  No paywall, no RevenueCat, no entitlement checks, no premium upsells, no locks.
  The coordinator already deleted `ui/paywall/`, `data/billing/`, the RevenueCat
  dependency, and all shared-file references (Routes, AppNavHost, MainActivity,
  StressReliefApp, SleepScreen, SleepViewModel). Builders: strip any remaining
  premium references in YOUR owned files; do not reintroduce any.

## 1. Work packages and file ownership (no overlaps; stay in your lanes)

Four builders, file-disjoint. Nobody touches another builder's files. Nobody
commits; the coordinator merges and pushes.

- **Builder 1 — Insights engine (data layer).** Owns:
  `data/health/` (all files), `data/model/` (new files only; existing models
  are frozen), `data/repository/WellnessRepository.kt` +
  `data/repository/WellnessRepositoryImpl.kt` (ADDITIVE changes only —
  existing functions keep exact signatures), `app/src/main/AndroidManifest.xml`
  (permission additions only). Also owns `data/repository/InsightCalculators.kt`
  (new file, pure Kotlin, zero Android imports).
- **Builder 2 — Home v2 (UI).** Owns: `ui/home/` (all files),
  `ui/settings/` (strip remaining premium refs only, no redesign).
  Implements the 9-section redesign from `research/home-redesign.md`, with the
  v2 changes in section 2 of this contract. May read (never write) repository
  interfaces and `data/model/`.
- **Builder 3 — Breathball features (UI + one new store).** Owns:
  `ui/breathe/` (all files), `data/model/CustomPattern.kt` (new),
  `data/datastore/CustomPatternStore.kt` (new). May read (never write) all
  repository interfaces, `HealthConnectStateProvider`, and `data/catalog/`.
- **Builder 4 — Insights screen (UI).** Owns: `ui/insights/` (all files).
  Strip the premium gate. Rebuild the screen on `InsightsData` (section 4).
  May read (never write) repository interfaces and `data/model/`.

Shared components: import from `ui/components` (existing). Nobody creates a
new components/ dir. Strings go in the existing `res/values/strings_aura_b4.xml`
(or the appropriate existing strings file); no hardcoded UI text. Dark theme on
every new screen.

## 2. v2 product changes (apply everywhere)

1. **No premium.** Insights, all sleep sounds, everything: free. Delete don't
   gate. If you find a `isPremium`, `BillingGate`, `PaywallRoute`, lock icon
   tied to premium, or upsell card in your files, remove it.
2. **Insights is free** and becomes the analytics screen (section 4).
3. **Home section 9** (was "Insights promo / premium upsell" in the redesign
   spec) is now a plain free Insights entry card — no lock, no upsell copy.
   It navigates to `InsightsRoute`.
4. **Home hero tap** goes to `InsightsRoute` (free).

## 3. Navigation (frozen; coordinator owns Routes.kt and AppNavHost.kt)

v2 routes (all `@Serializable` in `navigation/Routes.kt`):

```kotlin
@Serializable object OnboardingRoute
@Serializable object HomeRoute
@Serializable object BreatheRoute
@Serializable object JournalRoute
@Serializable object SleepRoute
@Serializable data class BreathingSessionRoute(val goal: String, val patternId: String)
@Serializable object MoodCheckInRoute
@Serializable data class JournalEditorRoute(val entryId: Long = -1L)
@Serializable object ProgramsRoute
@Serializable object PatternBuilderRoute          // v2: custom pattern builder
@Serializable object InsightsRoute                 // v2: free for everyone
@Serializable object SleepSessionRoute
@Serializable object SettingsRoute
// PaywallRoute: DELETED in v2. Do not reference it.
```

`PatternBuilderRoute` lives in the Breathe tab graph. Its screen signature
(Builder 3 implements; coordinator already wired the destination):

```kotlin
@Composable
fun PatternBuilderScreen(
    onBack: () -> Unit,
    onStartSession: (goal: String, patternId: String) -> Unit,
)
```

`BreathingSessionRoute.patternId` may now be a catalog id (`"4-4-6"`, `"box"`,
…) or a custom id (`"custom-<uuid>"`). `BreathingSessionViewModel` (Builder 3)
resolves custom ids via `CustomPatternStore`; catalog ids via
`BreathingPatterns.all` as today.

## 4. Data models (frozen; Builder 1 creates, Builders 2/4 consume)

New files in `data/model/`. Pure Kotlin, no Android imports.

```kotlin
/** One night of scored sleep. All fields nullable when data is missing. */
data class SleepNightScore(
    val epochDay: Long,
    val totalSleepMin: Int?,
    val timeInBedMin: Int?,
    val sleepEfficiencyPct: Int?,   // TST / TIB * 100
    val deepPct: Float?,            // deep / TST * 100
    val remPct: Float?,
    val wasoMin: Int?,
    val awakenings: Int?,
    val latencyMin: Int?,
    val score: Int?,                // 0..100 composite, null if unscorable
    val durationScore: Int?,
    val efficiencyScore: Int?,
    val architectureScore: Int?,
    val continuityScore: Int?,
    val latencyScore: Int?,
)

/** Everything the Insights screen needs. One call, one object. */
data class InsightsData(
    val sleepNights: List<SleepNightScore>, // last 14 nights, ascending by epochDay
    val sleepDebtHours: Float?,             // null when < 5 valid nights
    val sleepNeedHours: Float,              // default 8.0
    val sri: Int?,                          // -100..100, null when < 5 day-pairs
    val socialJetlagMin: Int?,              // null when insufficient data
    val restingHrToday: Int?,
    val restingHrBaseline: Float?,          // 21-day rolling mean
    val restingHrZ: Float?,                 // today's z-score vs baseline
    val hrvRmssdToday: Float?,              // null when no writer (expected)
    val recoveryScore: Int?,                // 0..100, null while calibrating
    val recoveryBand: RecoveryBand,
    val restingHrTrendBpmPerDay: Float?,    // OLS slope, trailing 14d
    val sleepTrendMinPerDay: Float?,        // OLS slope, trailing 14d
    val insightCards: List<InsightCard>,    // plain-language, ordered by priority
    val calibrating: Boolean,               // true when < 7 days of health data
    val healthConnected: Boolean,           // false when HC unavailable/denied
)

enum class RecoveryBand { CHARGED, COASTING, DEPLETED, CALIBRATING }

data class InsightCard(
    val title: String,   // short, e.g. "Sleep debt is climbing"
    val body: String,    // one plain sentence with the number
    val kind: InsightKind,
)

enum class InsightKind { SLEEP, RECOVERY, TREND, CORRELATION, HABIT }

/** Custom breathing pattern (Builder 3). */
data class CustomPattern(
    val id: String,          // "custom-<uuid>"
    val name: String,        // user-given, max 40 chars
    val inhaleSec: Int,      // 1..30
    val holdSec: Int,        // 0..30
    val exhaleSec: Int,      // 1..30
    val holdAfterSec: Int,   // 0..30
    val rounds: Int,         // 1..30
)
```

`StressScore` (existing) is unchanged in shape. v2 label bands (Builder 1
applies in `computeStressScore()`):
0–25 Low, 26–50 Moderate, 51–75 High, 76–100 Very high.
(Higher = more stressed. This fixes the "Moderate / 25" confusion.)

## 5. Repository additions (frozen signatures; Builder 1 implements)

```kotlin
// WellnessRepository additions (existing functions unchanged):
suspend fun computeInsights(): InsightsData
```

`computeInsights()` never throws: every Health Connect read is guarded, and
missing data yields nulls + `calibrating=true` / neutral defaults. It never
scores missing data as zero.

```kotlin
// HealthConnectStateProvider additions (Builder 1 implements):
/** Write permissions needed for session write-back. */
fun writePermissions(): Set<String>
/**
 * Writes a completed breathing session as an ExerciseSessionRecord
 * (EXERCISE_TYPE_BREATHING). Returns false on any failure. Never throws.
 */
suspend fun writeBreathingSession(startMs: Long, endMs: Long): Boolean
```

`suspend fun readStressSignals()` keeps its signature; Builder 1 extends its
internals to also use heart-rate series when `RestingHeartRateRecord` is absent
(derived fallback: mean of lowest 20% of overnight 00:00–06:00 samples).

`requiredPermissions()` (read set) is extended by Builder 1 to include:
- `HeartRateRecord` (samples)
- `HeartRateVariabilityRmssdRecord` (request; almost nothing writes it — never depend on it)

## 6. Health Connect permissions (Builder 1 adds to AndroidManifest.xml)

Keep the existing two. Add exactly:

```xml
<uses-permission android:name="android.permission.health.READ_HEART_RATE_VARIABILITY" />
<uses-permission android:name="android.permission.health.WRITE_EXERCISE" />
<uses-permission android:name="android.permission.health.READ_HEALTH_DATA_HISTORY" />
```

Do NOT add `READ_HEALTH_DATA_IN_BACKGROUND` (needs Play review; v2 reads on
foreground only).

## 7. Insight algorithms (frozen; Builder 1 implements in InsightCalculators.kt)

Pure functions, unit-testable, no Android imports. Formulas from
`research/health-algos-hrv.md`, `research/health-algos-sleep.md`,
`research/health-algos-repos.md`. Summary (exact bands in the research files):

1. **Sleep quality score** (per night): `30%·Duration + 25%·Efficiency +
   25%·Architecture + 15%·Continuity + 5%·Latency`, trapezoidal sub-scoring.
   Duration: 100 at 7–9h → 0 at <4h or >10h. Efficiency: 100 at ≥90% → 0 at
   <60%. Architecture: deep 13–23% and REM 20–25% of TST. Continuity: WASO
   <20min and <2 awakenings = 100 → 0 at WASO >60min or >6 awakenings. Latency:
   10–20min = 100; <5min or >30min scores low. Stage fields absent → score the
   components that exist, rescale weights, mark the rest null.
2. **Sleep debt**: 14-night window, linear-decay weights `w(i) = 1 − 0.75·i/13`,
   `debt = Σ w(i)·(need − slept_i)`, need default 8.0h, clamp [0, 10h].
   Nights with no data are skipped but consume their slot. <5 valid nights →
   `sleepDebtHours = null`.
3. **SRI** (Sleep Regularity Index): minute-level asleep/awake strips for 7+
   days from sleep sessions (gaps = awake). `SRI = −100 + 200·matches/(M·(N−1))`.
   Requires ≥5 overlapping day-pairs, else null.
4. **Social jetlag**: `|mid-sleep Mon–Fri − mid-sleep Sat–Sun|` in minutes.
5. **Resting-HR baseline**: 21-day rolling mean/std over nightly resting HR
   (record or derived fallback); admit a day only if data completeness >70%.
   `z = (today − mean)/std`. ≥1.5σ elevated, ≥3σ extreme (one-sided, high only).
6. **Recovery score**: per-driver z-scores vs baseline, weighted sum, logistic
   squash `100/(1+e^(−1.6(z+0.2)))`. Weights: RHR 0.30 / sleep debt 0.20 /
   HRV-RMSSD 0.40 (if present) / breathing minutes 0.10. Missing inputs are
   DROPPED and remaining weights rescaled (never scored as zero). Bands:
   ≥67 CHARGED, 40–66 COASTING, <40 DEPLETED, <7 days data CALIBRATING.
7. **Trend slopes**: OLS slope on trailing 14 nightly values (resting HR
   bpm/day, sleep min/day). ~15 lines; write directly.
8. **Correlation cards**: Pearson r over trailing 30 days for pairs
   (sleep duration → next-day stress score; breathing minutes → stress score;
   resting HR → next-day mood). Emit an `InsightCard` when |r| ≥ 0.4 and n ≥ 10,
   with the plain-language sentence and the number.
9. **Sleep→stress / habit cards**: rule-based, e.g. "RHR 8% above your baseline
   3 nights running — consider an early wind-down." Priority order: RECOVERY
   anomalies first, then SLEEP, then CORRELATION, then TREND, then HABIT.

Constraint (from research): Health Connect exposes NO raw RR intervals, so
RMSSD/SDNN/pNN50/LF-HF/Baevsky are NOT computable — do not attempt. Read
`HeartRateVariabilityRmssdRecord` when present; otherwise skip HRV gracefully.

## 8. CustomPatternStore (frozen; Builder 3 creates in data/datastore/)

```kotlin
class CustomPatternStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val patterns: Flow<List<CustomPattern>>   // JSON in DataStore prefs, key "custom_patterns"
    val breathingGoalMin: Flow<Int>           // daily goal minutes, default 10
    suspend fun savePattern(pattern: CustomPattern)
    suspend fun deletePattern(id: String)
    suspend fun setBreathingGoalMin(minutes: Int)  // 5..120, coerce
}
```

Use the existing `data/local/JsonCodec.kt` for serialization. No Room changes,
no DB version bump. No new Hilt module needed (constructor injection).

## 9. Home v2 sections (Builder 2; from research/home-redesign.md, v2 deltas applied)

Top to bottom: (1) greeting + smart nudge (plain text, tap → BreatheRoute);
(2) redesigned stress hero — 168dp ring, `displayLarge` score, 7-day mood
sparkline bars, week delta line, ONE fully readable top factor, tap →
InsightsRoute; (3) Continue card — THE only gradient-filled card on the
screen, first-match logic (missing check-in → MoodCheckInRoute; active program
→ ProgramsRoute; no breathing today → BreatheRoute; after 18:00 no sleep
session → SleepSessionRoute); (4) Health Connect slim banner (~72dp, keeps
existing connect/install flows, hidden when Granted/Unavailable); (5) Your
week — merged streak + week card (bloom, 7 mood bars, weekly calm minutes,
journal count, condensed today checklist), tap → InsightsRoute; (6) quick
actions 2×2 with per-tile dawn-spectrum accents (Check in → MoodCheckInRoute,
Breathe → BreatheRoute, Journal → JournalEditorRoute(), Wind down →
SleepRoute); (7) Today's prompt — rotating from `JournalPrompts` catalog
(`epochDay % 20`), tap → JournalEditorRoute(); (8) Wind down tonight —
`SleepRepository.observeRecent(1)` last-session summary + Start →
SleepSessionRoute, collapsed slim row before 18:00, expanded after;
(9) Insights entry card — FREE, no lock, no upsell copy → InsightsRoute.

Visual rules: exactly ONE gradient-filled card per screen (the Continue card).
Hero 28dp, cards 22dp. HomeViewModel injects `BreathingRepository` and
`SleepRepository` (both exist). Keep the nested-Scaffold inset fix:
inner Scaffolds use `contentWindowInsets = WindowInsets(0, 0, 0, 0)`.

## 10. Breathball features (Builder 3; from research/breathball.md)

In `ui/breathe/` (+ the two new data files from section 8):

1. **Pattern builder screen** (`PatternBuilderScreen`, route wired): sliders/
   steppers for inhale 1–30s, hold 0–30s, exhale 1–30s, hold-after 0–30s,
   rounds 1–30, name field (max 40). Save → `CustomPatternStore.savePattern`.
   Entry point: "Create your own" card at the end of the pattern list in
   `BreatheScreen` → `PatternBuilderRoute`. Starting a session from a custom
   pattern navigates `BreathingSessionRoute(goal, "custom-<uuid>")`.
2. **Eyes-closed mode**: toggle on `BreathingSessionScreen`. When on: orb
   fades to near-black (alpha ~0.05), phase changes signaled by haptic +
   soft tone (reuse existing haptic cues; tone via a simple AudioTrack beep
   or the existing audio infra — Builder 3's choice, keep it small). Screen
   stays on; no wake-lock tricks.
3. **Pace slider**: on `BreathingSessionScreen`, 0.5x–2x tempo control
   (Slider, 0.1 steps, default 1.0x). Multiplies all phase durations in the
   session ticker live. Label shows e.g. "1.2x".
4. **Breathing diary**: in `BreatheScreen`, 7-day bar chart of minutes per day
   (from `BreathingRepository.observeRecentSessions`) against the daily goal
   line (`CustomPatternStore.breathingGoalMin`), plus a small goal editor
   (stepper 5–120). Bars on the dawn scale; today highlighted.
5. **Health Connect write-back**: on session finish
   (`BreathingSessionViewModel.onSessionFinished`), call
   `healthConnect.writeBreathingSession(startMs, endMs)` (fire-and-forget on
   IO dispatcher; false = silent skip). Inject `HealthConnectStateProvider`
   (already a Hilt binding).

`BreathingSessionViewModel` resolves `patternId`: `"custom-*"` → from
`CustomPatternStore.patterns` (first emission); else `BreathingPatterns.all`.
Unknown id → fall back to `"4-4-6"`, never crash.

## 11. Insights screen v2 (Builder 4)

Rebuild `ui/insights/` on `InsightsData` from `WellnessRepository.computeInsights()`:
- Recovery score hero (0–100, band label + color, "calibrating" state when
  `<7 days` data).
- Sleep section: last-14-night sleep score bars, sleep debt dial/number,
  SRI + social jetlag rows (hide when null).
- Heart section: resting HR today vs 21-day baseline, z-badge when ≥1.5σ,
  14-day trend slope line ("climbing 0.8 bpm/day").
- Insight cards list (from `insightCards`, ordered).
- "Connect Health" slim banner when `!healthConnected` (reuse the home
  banner pattern; do not duplicate its logic — extract if trivial, else copy
  the 30-line version).
- Charts: Compose Canvas only, no chart library. Keep them simple bars/lines
  on the dawn scale.
- Strip the premium gate entirely. The screen is free.

## 12. Cross-cutting rules (non-negotiable)

- No `!!` in prod, no GlobalScope, no hardcoded dispatchers (use
  `@IoDispatcher`), sealed UiState per screen, `collectAsStateWithLifecycle`,
  Hilt everywhere. Skill `android-app-architect` applies.
- Health Connect: NEVER crash when unavailable/denied. Every read guarded,
  degrade to nulls. Never score missing data as zero.
- Tests: every new ViewModel and every pure calculator gets a unit test.
  `InsightCalculators` must have tests with fixed inputs (sleep score bands,
  debt math, SRI on a synthetic week, z-score thresholds).
- Strings in XML, no hardcoded UI text. Dark theme everywhere.
- Do not touch `apks/` (gitignored). Do not commit.
- Builders write code only; the coordinator reviews, merges, pushes.

## 13. Definition of done (per builder, verified by coordinator before merge)

1. Code matches every interface/model/route named in this contract verbatim.
2. No duplicate entities, no orphaned code, no dead routes, no premium remnants.
3. Unit tests written and passing (run via CI; do not run local Gradle).
4. No new warnings that break `-Werror` (the Build APK workflow treats
   warnings as errors).
5. The coordinator runs a final conformance review across all four packages
   before the single push.
