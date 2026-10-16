# UI/UX Review: Aura (App 03) — Aurora Calm Compliance

**Reviewer:** UI/UX reviewer subagent · **Date:** 2026-10-15
**Scope:** static review of every composable under `ui/` plus `ui/theme/`, `navigation/AppNavHost.kt`, against `research/design-direction.md` and CONTRACT.md section 4. No gradle run, no build, code read only.

## Verdict: NEEDS POLISH

The foundation is genuinely good. Theme tokens are byte-exact with the doc, the gradient brushes are defined once, Nunito is wired with the right weights, shapes follow the contrast rule, and there are zero Apple patterns anywhere. This is not a rebuild. It is a polish pass: 2 high-severity fixes, 10 medium, and a list of low-severity items. Ship it after the highs (and ideally the mediums) land.

### What is right (do not touch)
- **Hex values exact:** `AuraLightColors`/`AuraDarkColors` match the doc tables verbatim (primary `#00838F`, secondary `#5B5BD6`, tertiary `#C2185B`, bg `#F7FDFD`, dark bg `#0E1516`). Dark mode is a designed night aurora, not inverted.
- **Gradients centralized:** `AuroraBrushes.dawn` / `nightAurora` / `calmWash(alpha 0.12-0.18)` match the doc stops exactly. Calm wash stays at 15% default and never sits behind body text (checked every screen: text always sits on cards/scrims).
- **No banned colors:** grep over all of `ui/` found zero red/orange/yellow in structural UI. Pinks appear only as tertiary brand accents (streak bloom, confetti, upsell cards), which the doc explicitly reserves. `error` red only for errors and swipe-to-delete.
- **Typography:** Nunito ExtraBold/Black headlines, Nunito Sans body, bundled in `res/font/` (all 6 weights present). Display styles defined per doc. No cold grotesque type anywhere.
- **Shapes:** hero 28dp, card 22dp, pill CTAs, chip 12dp. No uniform 8dp rounding (8.dp appears only as spacing). `AuroraButton` enforces the pill-CTA-vs-card shape contrast rule.
- **Motion tokens:** `GentleSpring(250/0.85)` and `CelebrationSpring(300/0.6)` centralized; staggered entrances 30-50ms with 16dp rise; reduced motion respected on the breathing screen.
- **M3 Expressive patterns:** `NavigationBar` with 4 tabs on nested graphs, bottom bar only on tab routes. No iOS tab bar, no inset grouped lists, no frosted-glass chrome, no large-title collapse.
- **Breathing hero moment:** orb scales 1.0x -> 1.35x over the actual phase duration (exactly what the doc specifies), gradient crossfades teal -> violet -> pink with the phase, halo breathes with it, haptic tick on phase change, celebration pop uses CelebrationSpring with tertiary-pink confetti. This is the app's identity and it is built right. Two medium items dull it (see M3, M4 below).

---

## HIGH severity (fix before ship)

### H1. Manual dark-mode override breaks every gradient and icon tint
**Files:**
- `app/src/main/java/com/sreecharan/stressrelief/ui/home/AuraGradients.kt` (`dawn()`, `sweep()`, `calmWash()`)
- `app/src/main/java/com/sreecharan/stressrelief/ui/components/AuroraButton.kt`
- `app/src/main/java/com/sreecharan/stressrelief/ui/components/EmptyState.kt`
- `app/src/main/java/com/sreecharan/stressrelief/ui/breathe/BreathingSessionScreen.kt` (halo brush)
- `app/src/main/java/com/sreecharan/stressrelief/ui/onboarding/OnboardingScreen.kt`

**Problem:** `MainActivity` applies the user's Settings theme override (`SYSTEM | LIGHT | DARK`) through `AuraTheme(darkTheme = ...)`, but every gradient helper and tint decision inside screens calls `isSystemInDarkTheme()` directly, which only reads the OS setting. If Charan sets Dark manually while his phone is in light mode, he gets the dark color scheme with the light dawn gradient and dark-ink (`0xFF002021`) text on gradient buttons — off-brand at best. Worse the other way: Settings = Light, OS = dark gives white icon tints on the light dawn gradient over a light surface — near-invisible.

**Fix:** add one source of truth, e.g. in `ui/theme/`:
```kotlin
val LocalAuroraDark = compositionLocalOf { false } // set by MainActivity from the resolved darkTheme
```
Set it in `MainActivity` inside `AuraTheme { ... }` (MainActivity already resolves `darkTheme`), and replace all six `isSystemInDarkTheme()` calls in the files above with `LocalAuroraDark.current`. Then manual Light/Dark/System all render the correct gradient family.

### H2. White icons/face features on the light-mode dawn gradient (WCAG fail)
**Files:**
- `ui/home/HomeScreen.kt` — `QuickActionCard` (`tint = Color.White`), `InsightsPromoCard` (white AutoAwesome icon)
- `ui/insights/InsightsScreen.kt` — `SleepCard` medallion (`tint = Color.White`)
- `ui/breathe/BreatheScreen.kt` — `GoalCard` header (`tint = Color.White` on `goalGradient`, whose CALM/FOCUS variants are light)
- `ui/mood/MoodCheckInScreen.kt` — `MoodFace` selected state (`featureSelected = Color.White` on the light dawn gradient in light mode)

**Problem:** the light dawn stops (`#2DD4BF #22D3EE #A78BFA #F9A8D4`) are all pastel. White on them is roughly 1.3-1.6:1 contrast — fails WCAG AA everywhere, including the selected mood face, the screen whose whole job is one glanceable tap. The codebase already solved this correctly twice: `AuroraButton` and `OnboardingScreen` use `if (darkTheme) Color.White else Color(0xFF002021)` (dark ink on light dawn). Five spots did not copy the pattern.

**Fix:** in each of the four files, replace the unconditional `Color.White` with the same conditional the button uses:
```kotlin
tint = if (darkTheme) Color.White else Color(0xFF002021)
```
For `MoodFace`, `featureSelected` becomes that conditional (it is inside a Canvas, so hoist the dark-theme boolean in as a parameter). Once H1's `LocalAuroraDark` exists, use it here too.

---

## MEDIUM severity (should fix this pass)

### M1. Paywall purchase CTA is not a pill
**File:** `ui/paywall/PaywallScreen.kt` (`PaywallPlans`), also `ui/paywall/BillingGate.kt` (`ProLockedCard`)

**Problem:** the single most important CTA in the app (the money button) uses a stock `Button` with the default M3 small corner radius, and the ProGate locked screen uses the same. Contract: "Primary CTAs: always a pill." The rest of the app obeys; the paywall does not.

**Fix:** pass `shape = AuroraShapeTokens.pill` to both Buttons (or reuse `AuroraButton`, which already exists and is the pill CTA). While there: `PlanCard` border uses `outlineVariant`, which is fine, but the selected state would read better with a `primary` border plus the pill treatment already present.

### M2. Paywall header band uses dark aurora stops in light mode
**File:** `ui/paywall/PaywallScreen.kt` (`PaywallScreenContent`, the 220dp "Aurora wash header band")

**Problem:** hardcoded `Color(0xFF0D9488)` / `Color(0xFF7C6CF0)` — the *dark-mode* night-aurora stops — shown in both themes. In light mode the paywall opens with a dark jewel band the design doc reserves for night.

**Fix:** replace with the theme-aware brush, e.g. a vertical fade of `AuroraBrushes.hero(darkTheme)`:
```kotlin
.background(Brush.verticalGradient(listOf(AuroraBrushes.hero(darkTheme) ... )))
```
Simpler: reuse `AuroraBrushes.calmWash(darkTheme, alpha = 0.35f)` as the band fill. `darkTheme` should come from the H1 CompositionLocal.

### M3. Breathing timer and phase label have swapped type scale
**File:** `ui/breathe/BreathingSessionScreen.kt` (`ActiveSession`)

**Problem:** the doc is explicit — `displayLarge` (57sp) is for the breathing timer. The code gives `displayLarge` to the *phase label* ("Breathe in") and `displayMedium` (45sp) to the actual countdown number. The timer, the thing a mid-session user glances at with eyes half open, is the smaller of the two.

**Fix:** phase label -> `MaterialTheme.typography.headlineMedium`; `state.secondsRemainingInPhase` text -> `displayLarge`. Keep both centered as now.

### M4. Breathing orb ignores night-aurora stops in dark mode
**Files:** `ui/breathe/BreathingSessionScreen.kt` (`BreathingOrb`), `ui/breathe/BreatheMotion.kt`

**Problem:** the orb crossfade targets and the progress-ring sweep use `DawnTeal/Cyan/Violet/Pink` (the *light* dawn stops) unconditionally. In dark mode the hero moment renders the light palette instead of the richer night-aurora jewel tones (`#0D9488 #0891B2 #7C6CF0 #E0558A`) the doc requires ("gradients get richer at night, never muted"). The background wash correctly switches; the orb does not, so they clash.

**Fix:** in `BreatheMotion.kt` add the night stops next to the dawn ones:
```kotlin
val NightTeal = Color(0xFF0D9488); val NightCyan = Color(0xFF0891B2)
val NightViolet = Color(0xFF7C6CF0); val NightPink = Color(0xFFE0558A)
```
In `BreathingOrb`, pick the pair by `darkTheme` (from H1's local) for both `colorTargets` and the ring `sweep`. Single source of truth preserved: both sets are now declared in one file.

### M5. "Begin wind-down" duplicates gradient hexes inline, ignores dark mode
**File:** `ui/sleep/SleepScreen.kt` (`SleepScreenContent`)

**Problem:** the 64dp CTA builds `Brush.horizontalGradient(listOf(Color(0xFF2DD4BF), ...))` with hardcoded dawn stops instead of `AuroraBrushes.hero(darkTheme)` — light gradient in dark mode, plus brand drift (a third copy of the stops). Content color is hardcoded `Color(0xFF06201D)`, one digit off the token `0xFF002021` used by `AuroraButton`.

**Fix:** replace the inline brush with `AuroraBrushes.hero(darkTheme)` and set `contentColor = if (darkTheme) Color.White else Color(0xFF002021)`. Honestly, prefer reusing `AuroraButton(gradient = true)` here — it already implements exactly this.

### M6. Program progress bar is flat, not gradient
**File:** `ui/breathe/ProgramsScreen.kt` (`ProgramCard`)

**Problem:** `LinearProgressIndicator` renders a flat `primary` fill. The doc's motion section is explicit: "progress rings that fill with the gradient, not a flat color." The streak ring on Home got the gradient sweep; programs did not.

**Fix:** replace with a small custom progress bar — a 10dp track (`surfaceVariant`) with the fill drawn via `Brush.horizontalGradient` using `AuroraBrushes.hero(darkTheme)` stops, clipped to a pill. Or wrap `LinearProgressIndicator` and override `color` per progress fraction — no, flat color per fraction is not a gradient; draw it with Canvas/brush.

### M7. Reduced motion not respected outside the breathing screen
**Files:** `ui/home/AuraGradients.kt` (`StaggerIn`), `ui/sleep/SleepSessionScreen.kt` (`AuroraBackground`)

**Problem:** `rememberReduceMotion()` exists and the breathing screen honors it, but `StaggerIn` (used on Home, Journal, Insights) always animates, and the sleep session's 20-second infinite aurora drift never checks it. The doc's accessibility gate requires reduced-motion respected.

**Fix:** in `StaggerIn`, `if (rememberReduceMotion()) { visible = true; skip AnimatedVisibility }` (render content immediately). In `AuroraBackground`, when reduce motion is on, freeze `drift` at a fixed pleasant value (e.g. 0.8f) instead of running the infinite transition.

### M8. Entrances use tween(300) instead of the GentleSpring
**Files:** `ui/breathe/BreatheScreen.kt` (`GoalCard`), `ui/breathe/ProgramsScreen.kt` (`ProgramCard`)

**Problem:** both hand-roll `fadeIn(tween(300)) + slideInVertically(tween(300))`. The doc's motion system is physics-based: "springs, not eased tweens." The tokens exist for this exact call.

**Fix:** use `MotionTokens.GentleSpring` / `MotionTokens.gentleSpring<IntOffset>()` like `StaggerIn` does, or just reuse `StaggerIn` (Builder 4's helper is in a shared-importable spot and already implements the stagger + spring).

### M9. Journal editor's primary action is an iOS-style top-bar TextButton
**File:** `ui/journal/JournalEditorScreen.kt` (`JournalEditorContent`)

**Problem:** the only save affordance is a `TextButton` in the `TopAppBar` actions — literally the iOS navigation-bar text-button pattern the doc forbids, and it violates the shape-contrast rule (primary CTA must be a pill, visually distinct from cards). A first-time user can easily miss it.

**Fix:** add a bottom pill CTA — `AuroraButton(text = "Save entry", gradient = true, modifier = Modifier.fillMaxWidth().navigationBarsPadding(), onClick = onSave, enabled = canSave)` — above the keyboard area. Keep the top-bar action or drop it; the pill is the primary.

### M10. Locked sound-layer card: clickable modifier wrapping a Card, no semantics
**File:** `ui/sleep/SleepScreen.kt` (`SoundLayerCard`)

**Problem:** the whole `Card` gets `.clickable(enabled = locked) { onToggled(true) }` as a modifier while also containing a `Switch` with its own `onCheckedChange` — nested competing targets, no `Role`, no content description, and the ripple is not clipped to the card shape. TalkBack users get an unlabeled clickable region.

**Fix:** use `Card(onClick = { ... }, enabled = locked)` (M3 handles role + ripple + shape clipping) and give it a content description like "Rain sounds, premium, opens upgrade". Alternatively make the locked card's tap explicitly open the paywall via `onUpsellClicked` with that label.

---

## LOW severity (polish, batch into one commit)

- **L1. Touch targets under 48dp:** `SettingsScreen` rows (`ThemeModeRow`, `SettingsSwitchRow`, `SettingsActionRow`) use `padding(vertical = 10.dp)` around 24dp icons = ~44dp rows. Bump to 14dp vertical padding or add `defaultMinSize(minHeight = 48.dp)`. Home's refresh `IconButton` is forced to `40.dp` — remove the size override (M3 IconButton is 48dp by default).
- **L2. Missing TalkBack labels:** Settings back arrow `contentDescription = null` (give it the back string); `SleepSessionScreen` `AssistChip(onClick = {})` is an actionable chip that does nothing — either `enabled = false` or remove `onClick`. `HomeError`'s whole-column `.clickable(onClick = onRetry)` has no `Role.Button` — add `role = Role.Button` and a content description.
- **L3. Hardcoded UI strings (contract: strings in strings.xml):** `"Stress score $score out of 100"` (HomeScreen), `"$label, $score out of 5"` (MoodCheckInScreen), weekday labels `"M","T","W","T","F","S","S"` (InsightsScreen — also hardcodes Sunday-first week order), `"AM"/"PM"` in `formatTime` (SettingsScreen — locale-unsafe; use `DateFormat`), `"--:--"` (SleepSessionScreen).
- **L4. JournalCard prompt in `primary` teal on `surfaceContainerHigh`** (`#00838F` on `#EAF6F6` ≈ 3.4:1 at 14sp — under AA). Use `onSurface` for the prompt text or `onPrimaryContainer`.
- **L5. Screen-title scale drift:** doc says `headlineMedium` for screen titles. Home/Journal/Insights use `headlineSmall`, Breathe/Programs `headlineMedium`, Sleep/Paywall `headlineLarge`. Align all on `headlineMedium` (keep the top-app-bar pattern).
- **L6. Sleep timer at hardcoded 72.sp** (SleepSessionScreen). The doc's 57sp `displayLarge` is reserved for the breathing timer, but 72sp here skips the type scale entirely. Use `displayLarge` with a size override only if needed, or define the oversized hero numeral in `AuroraType`.
- **L7. Sleep session is always-dark with hardcoded text colors** (`0xFFB8C9C6`, `0xFFDFF3F0`, base `0xFF060D0C` vs token `0xFF0E1516`). Immersive dark wind-down is on-brand (doc's "evening gradient mode"), but prefer `onSurface`/`onSurfaceVariant` from the dark scheme so it stays correct if tokens evolve.
- **L8. No haptic on mood save or celebration finish.** Haptics exist only on breath phase change. Doc lists streak/milestone moments; add a light tick on mood-save and on the celebration pop.
- **L9. Sleep `FilterChip`s use the default chip shape;** Mood sets 12dp explicitly per the doc. Set `shape = RoundedCornerShape(12.dp)` on the timer chips.
- **L10. `StaggerIn` runs its delay even on recomposition re-entry** and has no `reduceMotion` gate (see M7). Also `GoalCard`/`ProgramCard` each reimplement stagger instead of reusing `StaggerIn` (see M8).
- **L11. Stock `Switch` on sleep layers / settings** does not "flood the track with gradient" per the doc's micro-interaction spec. Aspirational; skip unless cheap — but at least the selected `FilterChip`/`Switch` colors already come from the scheme, so nothing is broken.
- **L12. `ThemeModeRow` reuses the Palette icon for all three rows.** Give Light/Dark their own icons (`Icons.Filled.LightMode` / `DarkMode`) for scannability.
- **L13. Onboarding has no calm-wash background** — plain surface behind the medallion. One `Box(...background(AuroraBrushes.calmWash(darkTheme)))` would make the first impression match the rest of the app.
- **L14. Fonts are bundled, not downloadable** as the doc requests. Builder 1's note documents the rationale (unverifiable font-provider certs in this environment). Acceptable — but flag for the integrator to revisit with Android Studio's generated `font_certs.xml` if APK size matters.
- **L15. `JournalEditorScreen` saving state:** `Loading/Saving/Saved` branches render nothing but keep chrome — fine, but `Saved` fires `onBack()` via `LaunchedEffect(Unit)` keyed on composition; acceptable pattern, no change needed. Noting only because it is unusual.
- **L16. `ProLockedCard` upsell is visually quiet** for a conversion surface the doc would treat as a hero moment. Consider the dawn gradient on its CTA (see M1) and a gradient medallion like `EmptyState`.

---

## Screen-by-screen scorecard

| Screen | Aurora tokens | Gradients | Type | Shapes | Motion | Dark mode | A11y | Notes |
|---|---|---|---|---|---|---|---|---|
| Home | ✅ exact | ✅ wash+hero, text on cards | ✅ | ✅ 28/22/pill | ✅ stagger | ⚠️ H1/H2 | ⚠️ 40dp refresh btn, H2 | Streak bloom in tertiary pink is the right call |
| Breathe (programs) | ✅ | ⚠️ M4 dark orb, M6 flat bar | ✅ | ✅ | ⚠️ M8 tween | ⚠️ M4 | ✅ | GoalCard header white icon = H2 |
| Breathe (session) | ✅ | ⚠️ M4 | ⚠️ M3 swapped scale | ✅ | ✅ springs+haptics+reduceMotion | ⚠️ M4 | ✅ orb has CD | The hero moment; M3+M4 dull it |
| Mood check-in | ✅ | ✅ header band, text on surface | ✅ | ✅ 12dp chips | ⚠️ no save haptic (L8) | ⚠️ H1/H2 | ⚠️ hardcoded CD strings (L3) | Canvas faces beat emoji; white-on-dawn is the one real bug |
| Journal list | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ H1 | ✅ swipe delete labeled | Prompt color L4 |
| Journal editor | ✅ | ✅ | ✅ | ⚠️ M9 no pill CTA | ✅ | ⚠️ H1 | ✅ | Top-bar TextButton save is the iOS smell |
| Insights | ✅ | ✅ chart line+fill gradient | ✅ | ✅ | ✅ | ⚠️ H1/H2 | ⚠️ weekday labels (L3) | Pixels lerp lavender->pink is on-palette |
| Sleep mixer | ✅ | ⚠️ M5 inline hexes | ✅ | ✅ | ✅ | ⚠️ H1/M5 | ⚠️ M10 card semantics | Switch/Slider stock but fine |
| Sleep session | ⚠️ L7 hardcoded colors | ✅ night aurora blobs | ⚠️ L6 72sp | ✅ | ⚠️ M7 infinite drift | n/a (always dark) | ⚠️ L2 dead chip | Immersive and pretty; needs the polish items |
| Paywall | ✅ | ⚠️ M2 wrong-theme band | ✅ | ❌ M1 not pill | ✅ | ⚠️ H1/M2 | ✅ plan cards labeled | The money screen needs the pill |
| Onboarding | ✅ | ✅ medallion | ✅ | ✅ | ✅ pager | ⚠️ H1 | ✅ indicator CD | 3 pages, skippable, no hard paywall ✅ |
| Settings | ✅ | n/a | ✅ | ✅ | n/a | ⚠️ H1 | ⚠️ L1/L2 targets+back CD | Rows are cards, not iOS grouped lists ✅ |

**Forbidden-pattern check:** no red/orange/yellow structural color; no iOS tab bar (M3 NavigationBar); no inset grouped lists; no frosted glass; no large-title collapse; no SF-style type. Clean on all five.

## The breathing orb, honestly

It is 85% of the way to the signature moment the doc demands. The choreography is right — 1.35x inhale over the real phase duration, halo, phase-synced gradient crossfade, haptic ticks, reduced-motion fallback, celebration with the right spring and pink confetti. What keeps it from feeling incredible: the 57sp display style is on the words "Breathe in" instead of the countdown number (M3), and in dark mode the orb wears the light palette while the background wears night aurora (M4). Fix those two and it is the hero the app is named around.
