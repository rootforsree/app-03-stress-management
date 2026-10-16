# Aura (app-03-stress) - Code Quality Review

Reviewer: Root (static review only, no local Gradle run)
Date: 2026-10-15
Scope: full `app/src/main` tree vs CONTRACT.md v1 + android-app-architect skill (sections 0-5, 7-13)

## Verdict: SHIPPABLE WITH FIXES

The architecture is sound: MVVM with sealed UiState per screen, one StateFlow per ViewModel, UDF, Hilt throughout, repositories as the only data entry, Room v1 schema matching the contract exactly, no `!!`, no GlobalScope, no Date/Calendar, no runBlocking, no hardcoded dispatchers, graceful degradation everywhere it matters. All 14 ViewModels have test classes. Two blockers and four majors must be addressed before release; nothing found is unfixable.

---

## Blockers (must fix before ship)

### B1. Quiet-hours END time can never be changed
**File:** `app/src/main/java/com/sreecharan/stressrelief/ui/settings/SettingsScreen.kt:200`

The quiet-hours row always opens the START picker:
```kotlin
onClick = { editingQuietHour = true },
```
Nothing in the file ever sets `editingQuietHour = false`. The `editing != null` branch (lines 228-266) has full end-time support (`settings_quiet_hours_end_label`, the `else` branch), but it is unreachable dead UI. The user sees "No reminders between 10:00 PM and 7:00 AM" and can only ever edit the 10:00 PM half.

**Fix:** split into two tappable rows (start / end), each setting `editingQuietHour` to `true`/`false` respectively. One-line change per row:
```kotlin
// start row:
onClick = { editingQuietHour = true }
// end row:
onClick = { editingQuietHour = false }
```

### B2. Reminders are dead on arrival on Android 13+ (default ON, no permission request)
**Files:**
- `app/src/main/java/com/sreecharan/stressrelief/data/datastore/SettingsRepositoryImpl.kt:45` (`?: true`)
- `app/src/main/java/com/sreecharan/stressrelief/ui/onboarding/OnboardingViewModel.kt:52-58`

`remindersEnabled` defaults to `true`. `OnboardingViewModel.finishOnboarding()` calls `scheduler?.scheduleAll()`, so a first-launch user gets alarms scheduled with POST_NOTIFICATIONS never granted (no screen in onboarding requests it; the only request site is the Settings toggle in `SettingsScreen.kt:82-95`, which a default-ON user never touches). On API 33+ the notifications are silently dropped by the OS. The morning check-in / wind-down / nudge feature (contract section 6) effectively does not work for new users.

**Fix (pick one):**
1. Default `remindersEnabled` to `false` (contract does not mandate a default), so the Settings toggle + its permission launcher is the only path to ON; or
2. Request POST_NOTIFICATIONS in the onboarding completion flow before `scheduleAll()`.

Option 1 is the smaller, safer change.

---

## Major

### M1. Paywall sends Dismiss twice on purchase - double popBackStack race
**File:** `app/src/main/java/com/sreecharan/stressrelief/ui/paywall/PaywallViewModel.kt:65,111`

`onPurchaseClicked` sends `PaywallEvent.Dismiss` on `PurchaseResult.Success` (line 111), and the entitlement collector sends it again when RevenueCat reports premium (line 65). `PaywallScreen.kt:72` maps `Dismiss -> onDismiss()`, i.e. `navController.popBackStack()`. Two Dismiss events can pop the paywall AND the screen beneath it. Whether it bites depends on timing (the second event is usually swallowed when the screen leaves composition, but the Channel is BUFFERED so a fast double-collect is possible).

**Fix:** delete the send at line 111; rely solely on the entitlement listener (the code comment already notes "Dismiss is idempotent" - make it actually so by having exactly one sender).

### M2. `observeMoodHistory` loads the entire mood table into memory
**File:** `app/src/main/java/com/sreecharan/stressrelief/data/repository/WellnessRepositoryImpl.kt:29-36`

```kotlin
moodCheckInDao.observeRecent(Int.MAX_VALUE)
    .map { list -> list.filter { it.timestampMs >= cutoff } ... }
```
Called by `HomeViewModel` (7 days) and `InsightsViewModel` (TREND_DAYS) on every collection. Unbounded read; the table grows forever.

**Fix:** add a DAO query and use it:
```kotlin
@Query("SELECT * FROM mood_checkins WHERE timestampMs >= :sinceMs ORDER BY timestampMs DESC")
fun observeSince(sinceMs: Long): Flow<List<MoodCheckInEntity>>
```

### M3. Nullable-default @Inject params on 4 ViewModels weaken the DI contract
**Files:**
- `ui/mood/MoodCheckInViewModel.kt:40` (`widgetUpdater: AuraWidgetUpdater? = null`)
- `ui/breathe/BreathingSessionViewModel.kt:111` (`widgetUpdater: AuraWidgetUpdater? = null`)
- `ui/onboarding/OnboardingViewModel.kt:44` (`scheduler: AuraReminderScheduler? = null`)
- `ui/settings/SettingsViewModel.kt:38` (`scheduler: AuraReminderScheduler? = null`)

Added "so unit tests can construct the VM without" the dependency. In prod Hilt always injects the real singleton, so nothing is broken today. But every call site is a silent no-op safe-call (`scheduler?.scheduleAll()`, `widgetUpdater?.refresh()`): any future manual construction, or a Hilt graph change that drops the binding, silently disables alarm scheduling and widget refresh with zero signal. The tests that motivated this already use fakes for repositories.

**Fix:** make the params non-null; in tests pass a no-op/fake (`FakeAuraWidgetUpdater {}` / a scheduler fake). Hilt keeps working unchanged.

### M4. Health Connect permission flow is dead code - stress signals always null
**File:** `app/src/main/java/com/sreecharan/stressrelief/data/health/HealthConnectClient.kt:22-25`

`requiredPermissions()` and `permissionContract()` are never called from any UI (verified: zero references outside the class). `hasAllPermissions()` therefore always returns false and `readStressSignals()` always returns `StressSignals(null, null)`. The Health Connect half of `computeStressScore()` (contract section 2) can never work; every score is check-in-only forever.

**Fix:** add an opt-in entry point - a row in Settings (privacy section) or a "connect" affordance on the Home stress-score card - that registers `permissionContract()` via `rememberLauncherForActivityResult` and explains why (contract: permissions requested in context).

---

## Minor

### m1. UiState not sealed on 4 screens (contract section 7: "sealed UiState per screen")
- `ui/paywall/PaywallViewModel.kt:19` (`data class PaywallUiState`)
- `ui/sleep/SleepViewModel.kt:27` (`data class SleepUiState`)
- `ui/sleep/SleepSessionViewModel.kt:20` (`data class SleepSessionUiState`)
- `ui/sleep/SleepViewModel.kt:16` (`data class SleepLayerUiState`, nested state)

**Fix:** convert to sealed interfaces with Loading/Content branches (BillingGateViewModel has the same shape - it exposes `entitlement` directly with no UiState at all; acceptable as-is since it is a pure gate, but note the inconsistency).

### m2. Missing `@Immutable` on most sealed UiStates
Only `SettingsUiState`, `OnboardingUiState`, `AppThemeUiState` carry `@Immutable`. `HomeUiState`, `MoodCheckInUiState`, `JournalListUiState`, `JournalEditorUiState`, `InsightsUiState`, `BreathingSessionUiState`, `BreatheUiState`, `ProgramsUiState` do not. Add the annotation (skill section 2).

### m3. Plain `List` instead of `ImmutableList` in UiState
`HomeUiState.Content.weekDots`, `JournalListUiState.Content.entries`, `InsightsUiState.Content` (trend, monthCells, topActivities), `SleepUiState.layers`. `BreatheViewModel` already uses `toImmutableList()` - follow that pattern everywhere (skill section 2).

### m4. `BillingGate` cold-start composable is dead code
`ui/paywall/BillingGate.kt:47-59` defines `BillingGate(...)` but nothing calls it; `ProGate` (line 68) is the gate actually used (InsightsScreen). Delete `BillingGate` or wire it as the app cold-start gate it claims to be.

### m5. Cross-builder imports violate contract section 5 ownership
`ui/home/AuraGradients.kt`, `ui/home/StaggerIn`, `ui/home/AuraSectionTitle` are imported by mood, journal, and insights screens:
- `ui/mood/MoodCheckInScreen.kt:49`, `ui/journal/JournalScreen.kt:52-53`, `ui/journal/JournalEditorScreen.kt:43`, `ui/insights/InsightsScreen.kt:56-58`

Contract: "Shared components used by all: import from `ui.components` (Builder 1). Nobody else creates a components/ dir." Move these three into `ui/components/`.

### m6. Early-exit breathing session records 0 minutes for sub-60s sessions
`ui/breathe/BreathingSessionViewModel.kt:196`: `markBreathingDone(durationSec / SECS_PER_MIN)` (integer division). Natural finish uses `max(1, ...)` (line 229). A 59-second session is recorded with 0 calm minutes while a completed one gets at least 1.

**Fix:** `markBreathingDone(max(1, durationSec / SECS_PER_MIN))`.

### m7. JournalEditorViewModel loads all entries to find one
`ui/journal/JournalEditorViewModel.kt:65`: `wellness.observeJournalEntries().first().firstOrNull { it.id == entryId }`. `JournalDao.observeById(id)` exists. Minor perf; the repository interface (contract-final) has no by-id method, so this needs an interface addition approved by the integrator, or accept as-is.

### m8. Hardcoded UI strings and duplicated hexes
- `ui/settings/SettingsScreen.kt:455-459` (`formatTime` hardcodes "AM"/"PM"; contract section 7: strings in strings.xml).
- `ui/sleep/SleepScreen.kt:181-184` duplicates the dawn gradient hexes already defined in `ui/theme/AuroraBrushes.kt` (contract section 4). Use `AuroraBrushes.dawn()`.

### m9. `LaunchedEffect(Unit)` for one-shot event collection
`ui/paywall/PaywallScreen.kt:69`, `ui/sleep/SleepScreen.kt:61`, `ui/sleep/SleepSessionScreen.kt:56` collect `viewModel.events` keyed on `Unit`; `ui/mood/MoodCheckInScreen.kt:65` fires dismiss from a conditional `LaunchedEffect(Unit)`. Works (the ViewModel instance is stable), but the skill keys effects by what restarts them - `LaunchedEffect(viewModel)` as used in `BreathingSessionScreen.kt:108`. Align for consistency.

### m10. `PaywallViewModel.onPurchaseClicked(activity: Activity)` takes an Activity
`ui/paywall/PaywallViewModel.kt:100`. The Activity is used immediately by `billing.purchase()` and never stored, so no leak in practice, but a ViewModel API that requires an Activity is a smell. Consider a purchase launcher owned by the screen.

### m11. AI companion entry point does not exist
Contract section 6 lists the paywall opening from "premium-gated features (Insights, AI companion entry point, premium sleep packs)". Only a code comment mentions it (`ui/paywall/BillingGate.kt:64`). Either add the stub button or amend the contract.

### m12. "Reset all data" only resets preferences
`ui/settings/SettingsViewModel.kt:61-69` (`resetAllData`) plus the honest dialog copy in `res/values/strings.xml:57`. The row title overpromises; the dialog walks it back. Either implement the Room wipe (`AuraDatabase.clearAllTables()` on the IO dispatcher, then re-seed) or rename the row to "Reset settings".

### m13. Mutable @Singleton handoff objects
`ui/sleep/SleepSessionConfig.kt` and `ui/paywall/PaywallLaunchContext.kt` are mutable singletons used to pass data between screens. Works, defaults are handled (rain fallback; allowClose=true), and process death between the two screens degrades gracefully. Note as tech debt, not a defect.

### m14. compileSdk 36 vs skill pin 37
`build-logic/.../apptemplate.android.application.gradle.kts:27`. Skill section 0 pins compileSdk 37 / targetSdk 36 / minSdk 26. Play's hard requirement (targetSdk 36+) is met. Informational only.

### m15. Cold-start deep link can bypass onboarding
`MainActivity.kt:57-60` + `navigation/AppNavHost.kt:103-108`: `aura://mood` handled by `navController.handleDeepLink(intent)` builds its own back stack (Home + mood sheet) even when `onboardingCompleted=false`. Edge case (widget installed before onboarding finishes); acceptable, but be aware the mood sheet can appear over Home pre-onboarding.

---

## What was verified clean

- **Architecture:** one StateFlow per ViewModel, UDF (state in, events out), repository is the only data entry (no DAO/DataStore imports in `ui/`), sealed UiState on 10 of 14 screens, one-shot events via `Channel(BUFFERED)` + `receiveAsFlow()`.
- **Hilt:** every `@Inject` has a binding (`RepositoryModule` @Binds x4, `BillingModule`, `SleepAudioModule`, `DatabaseModule`, `DataStoreModule`, `HealthModule`, `ReminderModule`, `BillingDataStoreModule`, `DispatchersModule`). Correct scoping (@Singleton on app-wide coordinators). No field injection in ViewModels. The INTEGRATION_NOTES DispatchersModule package mismatch is FIXED (now `com.sreecharan.stressrelief.di`, no `apptemplate` references remain).
- **Room:** v1 schema matches contract table-by-table, DAOs expose cold Flows, no destructive migration, `exportSchema=true`, all writes on injected IO dispatcher.
- **Compose:** `collectAsStateWithLifecycle` everywhere (zero `collectAsState()`), `hiltViewModel()` only at route level, stable lazy keys on both `items()` call sites, high-churn orb scale read in `graphicsLayer`.
- **Coroutines:** `viewModelScope` throughout, `WhileSubscribed(5_000)` on hot UI flows, dispatchers injected via `@IoDispatcher`, no GlobalScope, no runBlocking in prod. (Receiver uses a `SupervisorJob` scope with `goAsync()` - correct.)
- **Ban list:** zero `!!`, zero `java.util.Date`/`Calendar` (java.time used), zero `lateinit` outside DI fields, no runtime reflection (the `KClass` in AppNavHost is Navigation's `hasRoute(KClass)` API, not reflection).
- **Error handling:** repository failures map to `Error` UiStates with retry; Health Connect/RevenueCat/alarms all degrade gracefully (BillingRepositoryImpl serves `EntitlementState.Unavailable`, free tier fully works with no API key).
- **Tests:** all 14 ViewModels have test classes, including both journal VMs and BillingGateViewModel. (Tests were not executed - static review only, per instructions.)
- **Contract compliance:** all 13 routes verbatim in Routes.kt; all 6 wiring rules in section 6 verified in code (onboarding persist, breathing/journal/sleep recording, exact alarms + 3 types + quiet hours + BOOT_COMPLETED, widget deep links + score/streak cache, RevenueCat init gated on API key, scheduleAll on onboarding + settings changes, widget refresh after sessions and check-ins).
- **Manifest:** all INTEGRATION_NOTES items verified done (Health Connect permissions + rationale alias, receivers, widget receiver + metadata, aura:// intent filters, launcher icons wired).

## Suggested fix order
1. B1 (quiet-hours end) - 15 minutes.
2. B2 (reminders default / permission) - 15 minutes.
3. M1 (double Dismiss) - 5 minutes, delete one line.
4. M4 (Health Connect opt-in) - needs a small UI addition.
5. M3 (nullable @Inject params) - mechanical, touches 4 VMs + their tests.
6. M2 (observeSince query) - 10 minutes.
7. Minors m1-m15 as cleanup.
