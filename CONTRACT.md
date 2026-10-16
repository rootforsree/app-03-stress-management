# App 03 "Aura" — Builder Contract (v1, binding)

Read this before writing a single line. It exists because app-02's parallel builders
created duplicate entities, orphaned code, and broken navigation. Every interface,
table, and route below is FINAL. Do not invent alternatives. If two builders need
the same thing, it is defined HERE, not in two places.

## 0. Identity

- App name: **Aura** (`@string/app_name` = "Aura")
- Package: `com.sreecharan.stressrelief` (already renamed in build files + manifest)
- Manifest application class: `.StressReliefApp` (Builder 1 creates it)
- Repo: `rootforsree/app-03-stress-management`, branch `main`
- Design authority: `research/design-direction.md` ("Aurora Calm"). Follow it exactly.
  NO red/orange/yellow in structural UI. NO Apple-HIG styling. Material 3 Expressive.

## 1. Database (Room v1, single `AuraDatabase`)

All entities live in `data/local/`. One database, version 1, exportSchema = true.

```kotlin
// mood_checkins
MoodCheckInEntity(id: Long auto, timestampMs: Long, moodScore: Int /*1..5*/,
                 activitiesJson: String /*JSON list of tags*/, note: String = "")

// breathing_sessions
BreathingSessionEntity(id: Long auto, startMs: Long, durationSec: Int,
                       goal: String /* CALM | SLEEP | FOCUS | SOS */,
                       patternId: String /* e.g. "4-4-6", "4-7-8", "box" */,
                       completed: Boolean)

// journal_entries
JournalEntryEntity(id: Long auto, timestampMs: Long, prompt: String, text: String)

// program_progress  (one row per program)
ProgramProgressEntity(programId: String /*pk*/, currentDay: Int,
                      completedDaysJson: String /*JSON list of Int*/, startedMs: Long)

// daily_activity (one row per day; powers streaks)
DailyActivityEntity(dateEpochDay: Long /*pk*/, checkInDone: Boolean,
                    breathingMinutes: Int, journalDone: Boolean)

// sleep_sessions
SleepSessionEntity(id: Long auto, startMs: Long, durationMin: Int,
                   soundsJson: String /*JSON list of sound ids*/)
```

DAOs (all in `data/local/`, all expose cold Flows, no suspend returns of lists
except one-shots):
- `MoodCheckInDao`: `observeRecent(limit)`, `observeForDay(dayStartMs, dayEndMs)`, `insert()`
- `BreathingSessionDao`: `observeRecent(limit)`, `observeTotalMinutes()`, `insert()`
- `JournalDao`: `observeAll()`, `observeById(id)`, `insert()`, `update()`, `deleteById()`
- `ProgramProgressDao`: `observeAll()`, `observeById(programId)`, `upsert()`
- `DailyActivityDao`: `observeDay(epochDay)`, `observeLastNDays(n)`, `upsert()`
- `SleepSessionDao`: `observeRecent(limit)`, `insert()`

Domain models (in `data/model/`, 1:1 with entities, no Android imports):
`MoodCheckIn, BreathingSession, JournalEntry, ProgramProgress, DailyActivity,
SleepSession, StressScore(score 0..100, label, factors: List<String>)`

Static catalogs (in `data/catalog/`, NOT in DB):
- `BreathingPatterns.kt`: patterns per goal. Each: id, name, inhaleSec, holdSec,
  exhaleSec, holdAfterSec, rounds, description.
  - CALM: "4-4-6" (4/0/6/0 x6), "Coherent" (5/0/5/0 x8)
  - SLEEP: "4-7-8" (4/7/8/0 x4), "Deep rest" (4/0/8/0 x6)
  - FOCUS: "Box" (4/4/4/4 x6), "Energize" (6/0/2/0 x8)
  - SOS: "SOS 60" (4/0/6/0 x5, single 60s round)
- `Programs.kt`: 3 programs, 7 days each: "7-Day Calm Reset", "Sleep Better Week",
  "Focus Builder". Each day: programId, day, patternId, minutes, title.
- `JournalPrompts.kt`: 20 rotating guided prompts.
- `SleepSounds.kt`: sound ids with display names. Sounds are GENERATED tones
  (no binary assets): rain (filtered noise), ocean (slow LFO noise), white noise,
  brown noise, gentle chime pads. Builder 5 synthesizes with AudioTrack.

## 2. Repository interfaces (in `data/repository/`, implemented by Builder 2)

```kotlin
interface WellnessRepository {
    fun observeMoodHistory(days: Int): Flow<List<MoodCheckIn>>
    fun observeTodayCheckIn(): Flow<MoodCheckIn?>
    suspend fun saveMoodCheckIn(score: Int, activities: List<String>, note: String)
    fun observeJournalEntries(): Flow<List<JournalEntry>>
    suspend fun saveJournalEntry(prompt: String, text: String): Long
    suspend fun updateJournalEntry(id: Long, text: String)
    suspend fun deleteJournalEntry(id: Long)
    fun observeStreakDays(): Flow<Int>          // consecutive days w/ any activity
    fun observeTotalCalmMinutes(): Flow<Int>
    suspend fun computeStressScore(): StressScore // check-ins + Health Connect
    suspend fun markBreathingDone(minutes: Int)  // updates DailyActivity
    suspend fun markJournalDone()
}

interface BreathingRepository {
    fun observeRecentSessions(limit: Int): Flow<List<BreathingSession>>
    suspend fun recordSession(goal: String, patternId: String,
                              durationSec: Int, completed: Boolean)
    fun observePrograms(): Flow<List<ProgramProgress>>  // joined w/ catalog
    suspend fun completeProgramDay(programId: String, day: Int)
}

interface SleepRepository {
    suspend fun recordSleepSession(durationMin: Int, sounds: List<String>)
    fun observeRecent(limit: Int): Flow<List<SleepSession>>
}

interface SettingsRepository {  // DataStore Preferences, file "aura_prefs"
    val themeMode: Flow<ThemeMode>        // SYSTEM | LIGHT | DARK  (data/model)
    val dynamicColor: Flow<Boolean>
    val remindersEnabled: Flow<Boolean>
    val quietHours: Flow<QuietHours>      // data class(startHour, startMin, endHour, endMin)
    val onboardingCompleted: Flow<Boolean>
    suspend fun setThemeMode(m: ThemeMode)
    suspend fun setDynamicColor(b: Boolean)
    suspend fun setRemindersEnabled(b: Boolean)
    suspend fun setQuietHours(q: QuietHours)
    suspend fun setOnboardingCompleted()
}
```

Health Connect: `data/health/HealthConnectClient.kt` (Builder 2). Exposes
`suspend fun readStressSignals(): StressSignals(avgRestingHr: Int?, sleepHours: Float?)`.
`computeStressScore()` uses it; on any failure it degrades to check-in-only data.
NEVER crash when Health Connect is unavailable.

## 3. Navigation routes (`navigation/Routes.kt`, owned by Builder 1, verbatim)

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
@Serializable object InsightsRoute        // premium-gated
@Serializable object SleepSessionRoute    // active wind-down (sounds+timer)
@Serializable object PaywallRoute
@Serializable object SettingsRoute
```

Tabs (M3 Expressive NavigationBar, 4 items): Home, Breathe, Journal, Sleep.
Mood check-in opens as a bottom sheet from Home (and from the widget deep link).
Settings opens from the Home top bar. Paywall opens from premium-gated
features (Insights, premium sleep packs) and Settings.

Onboarding: max 3 pages, skippable, ends at HomeRoute. NO hard paywall at
launch (freemium model: generous free tier). Paywall appears only at
premium features.

Deep links (widget + notifications): `aura://mood` -> MoodCheckInRoute,
`aura://breathe` -> BreatheRoute. Builder 1 adds intent filters; Builder 5
fires them.

## 4. Theme (`ui/theme/`, owned by Builder 1, per design-direction.md)

- `AuroraTheme.kt`: `AuraLightColors` / `AuraDarkColors` from the doc's hex
  tables (verbatim), `AuraTheme(darkTheme, dynamicColor)` composable.
- `AuroraBrushes.kt`: `dawn` (#2DD4BF→#22D3EE→#A78BFA→#F9A8D4),
  `nightAurora` (#0D9488→#0891B2→#7C6CF0→#E0558A), `calmWash(alpha 0.12-0.18)`.
- `AuroraType.kt`: Nunito via Google Fonts downloadable; ExtraBold/Black
  headlines, Nunito Sans body. displayLarge 57sp for breathing timer.
- `AuroraShapes.kt`: hero 28dp, card 22dp, button pill, chip 12dp.
- `MotionTokens.kt`: `GentleSpring = spring(250f, 0.85f)`,
  `CelebrationSpring = spring(300f, 0.6f)`, stagger 30-50ms constants.
- Settings toggle "Match my wallpaper" switches dynamicColor; default OFF.

## 5. File ownership (no overlaps; stay in your lanes)

- Builder 1 (shell): `MainActivity.kt`, `StressReliefApp.kt`, `navigation/`,
  `ui/theme/`, `ui/onboarding/`, `ui/settings/`, `ui/components/` (shared only:
  AuroraCard, AuroraButton, SectionHeader, EmptyState, LoadingSkeleton),
  `res/values/strings.xml`, `res/values/themes.xml`, `AndroidManifest.xml` edits.
- Builder 2 (data): `data/` (local, model, repository impl, catalog, health,
  datastore), `di/` (DatabaseModule, RepositoryModule, DataStoreModule,
  HealthModule). KEEP existing `di/DispatchersModule.kt` untouched.
- Builder 3 (breathe): `ui/breathe/` only.
- Builder 4 (home/mood/journal): `ui/home/`, `ui/mood/`, `ui/journal/`,
  `ui/insights/` only.
- Builder 5 (sleep/reminders/widget/paywall): `ui/sleep/`, `ui/paywall/`,
  `data/reminders/`, `data/billing/`, `widget/`, notification code.

Shared components used by all: import from `ui.components` (Builder 1).
Nobody else creates a components/ dir.

## 6. Cross-builder wiring (exact)

- Onboarding completion -> `SettingsRepository.setOnboardingCompleted()`;
  NavHost starts at OnboardingRoute until true, then HomeRoute.
- Breathing session finish -> `BreathingRepository.recordSession()` +
  `WellnessRepository.markBreathingDone(minutes)`.
- Mood check-in save -> `WellnessRepository.saveMoodCheckIn()` +
  `DailyActivity` checkInDone=true (inside the repo impl).
- Journal save -> `WellnessRepository.saveJournalEntry()` + markJournalDone().
- Sleep session finish -> `SleepRepository.recordSleepSession()`.
- Reminders (Builder 5): exact AlarmManager, types: morning check-in,
  evening wind-down, streak-nudge. Quiet hours from SettingsRepository.
  Reschedule on BOOT_COMPLETED. Permissions: POST_NOTIFICATIONS,
  SCHEDULE_EXACT_ALARM, RECEIVE_BOOT_COMPLETED.
- Widget (Builder 5, Glance): two actions -> deep links aura://mood,
  aura://breathe. Shows today's stress score + streak.
- Paywall (Builder 5, RevenueCat): entitlement `premium`. Free tier =
  everything except: InsightsRoute, premium sleep packs. Products configured
  later in Play Console; code must degrade gracefully with no API key
  (free tier fully works).

## 7. Quality bars (non-negotiable)

- Skill `android-app-architect` sections 0-5, 7-13 apply. Section 6/14's
  Apple-HIG aesthetic is REPLACED by Aurora Calm (this app is Android-native).
- No `!!` in prod, no GlobalScope, no hardcoded dispatchers (use @IoDispatcher),
  sealed UiState per screen, `collectAsStateWithLifecycle`, Hilt everywhere.
- Tests: every ViewModel gets a unit test. Builders write their own.
- Delete nothing another builder owns. If a shared file must change,
  the integrator does it after builders finish.
- Strings in strings.xml, no hardcoded UI text. Dark theme on every screen.
