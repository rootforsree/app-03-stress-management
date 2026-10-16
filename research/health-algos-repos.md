# Open-Source Health Connect Insight Engines — Research for Aura

Researched 2026-10-16. Goal: find how the best open-source projects turn raw
Health Connect / HealthKit data into user-facing insights, and what Aura should steal.

## The convergence pattern (what every good repo does)

1. **Self-referential baselines.** Nobody scores against population norms. Every repo
   scores HRV, resting HR, sleep against the user's own 7/14/30-day trailing baseline.
   This is the single most important pattern: "you are your own scale."
2. **Weights are published, not hidden.** The best repos document every weight and
   saturation bound in the README or an ADR, and mark the score as a non-clinical
   heuristic with a disclaimer.
3. **On-device, local-first.** Room cache + WorkManager periodic sync is the standard
   Android pipeline. Scores recompute when new data lands, not live.
4. **Missing data degrades, never collapses.** Contributors default to a neutral value
   (0.7 in Vitals) or lower confidence instead of fabricating numbers.

---

## 1. xsunnyyy/healthapp ("Vitals") — BEST ALGORITHM REFERENCE

- URL: https://github.com/xsunnyyy/healthapp
- Stars: 0 (created 2026-06-14, new) | License: not declared in README | Native Kotlin 2.0 + Compose + Health Connect
- Activity: 96 commits, releases exist, WorkManager background sync, Room + DataStore

**What it reads:** sleep stages, heart rate, HRV, SpO2, respiratory rate, steps,
calories, distance, exercise sessions.

**Insight engine (fully published in README, `domain/BodyBatteryCalculator.kt`,
`domain/ReadinessCalculator.kt`):**

Morning charge (run at first HR sample after wake):
```
charge = 100 * (0.40*sleep_quality + 0.30*hrv_balance + 0.20*rhr_balance + 0.10*sleep_consistency)
```
- sleep_quality = (hours/8) * efficiency * (0.5 + (deep+REM)/total * 0.5)
- hrv_balance = ((last_night_HRV / 14-day baseline) - 0.7) / 0.6, clamped 0..1
- rhr_balance = 1 - (last_night_RHR - 14-day baseline) / 10, clamped 0..1
- sleep_consistency = 1 - |bedtime - 7-day avg bedtime| / 120 min, clamped 0..1

Daytime drain per 30-min bucket: passive 2.5/hr + 0.20*elevated_min + 0.60*high_min
(110-149 / 150+ bpm) + 0.03*active_kcal. Recharge: 0.10/quiet minute (all HR <= 75
and no steps) + 0.40/minute of naps. Battery = clamp(charge - drain + recharge).

Status words: Charged >= 70, Coasting 40-69, Depleted < 40, NoData while calibrating
(needs ~14 nights). Missing contributors default to 0.7 so the score degrades gracefully.

**What to steal:** the exact weighting structure for Aura's stress score — replace
"morning charge" with an overnight recovery input, keep the 14-day self-baseline
pattern and the graceful-degradation default.

## 2. OpenVitals-MTU/android-app (OpenVitals) — BEST ARCHITECTURE

- URL: https://github.com/OpenVitals-MTU/android-app (mirror of Codeberg)
- Stars: 35 | Commits: 1,584 | License: AGPL-3.0-or-later | Actively developed
- Also on Google Play + F-Droid. 14 languages. No INTERNET permission in the app.

**What it reads:** everything Health Connect offers, plus Garmin watch BLE sync
(BLE HR, sleep, stress, watch Body Battery relayed into Health Connect), Xiaomi
scale BLE, Apple Health CSV import.

**Insight engine:** on-device derived metrics in `domain/insights/`:
- **Daily Readiness**, **Body Energy**, **Training Readiness**, HRV status,
  physiological stress, adaptive goals — all computed locally.
- Key design distinction (from `docs/features/body-energy.md`): a *score* answers
  "was this a healthy night" (threshold-shaped, cites NSF/AASM: efficiency >= 85%,
  awake <= 20 min) while the *energy charge* uses the same signals as a *continuous
  ramp* (efficiency all the way to 100%, awake all the way to none). A flawless
  night and a good night score identically, but charge differently.
- Algorithm versioning: `BodyEnergyTimelineAlgorithmVersion` bumps force recompute
  of cached timelines — schema discipline Aura should copy.
- Home-screen widgets for readiness, Body Energy, vitals.

**What to steal:** the `domain/insights/` separation (pure calculators, independently
testable), the score-vs-continuous-charge distinction for Aura's stress ring, and
the permission-category model (Activity & sleep / Heart & recovery requested as
separate in-context categories instead of one block).

## 3. bune1991/openhealth — BEST "ANALYZE" PATTERN (what Charan asked for)

- URL: https://github.com/bune1991/openhealth
- Stars: not shown (v2.0.0 shipped) | License: GPL-3.0 | M3 Expressive, like Aura

**What it reads:** HR, resting HR, HRV, sleep, SpO2, respiratory rate, steps,
calories, body composition, nutrition, exercise, VO2 max, BP, glucose, temperature.

**Insight engine:**
- **Readiness Score** with published weights: HRV 40%, Sleep 25%, Awake 20%,
  RHR 10%, Activity 5%.
- **AI Health Analysis (BYOK):** user pastes their own Claude / Gemini / ChatGPT
  key (AES-256 encrypted at rest); "Tap Analyze on the Pulse tab" sends a health
  summary to the LLM and shows personalized insights. No vendor API key in the
  repo, no server — the phone calls the LLM directly.
- Stress & Resilience arc gauge with coaching and listed contributing factors;
  HRV arc gauge with recovery insight; per-metric "neural insight" captions.

**What to steal:** the BYOK analyze button is exactly the "analyse data, show
insights" feature Charan wants — Aura can offer a rule-based free analysis plus
an optional BYOK deep-dive, with zero server cost and zero API keys in the repo.

---

## Honorable mentions

- **enslaved2die/cadence** (Flutter, AGPL-3.0): WHOOP-style Recovery/Strain/Sleep.
  Strain uses Bannister TRIMP with heart-rate-reserve weighting; every calculator in
  `packages/health_engine` is a pure deterministic function with a doc comment citing
  the published methodology (TRIMP, Karvonen %HRR). Algorithms are portable to
  Kotlin; the code isn't. https://github.com/enslaved2die/cadence
- **krtkygpta/vital.ity** (MIT, 2 stars): sleep score + interactive hypnogram with
  drag-to-scrub, M3 Expressive, shared chart components. Best UI reference for
  Aura's sleep/insight charts. https://github.com/krtkygpta/vital.ity
- **voslartomas/healthy** (`docs/adr/004-readiness-heuristic.md`): readiness as a
  transparent non-clinical heuristic — HRV vs baseline 0.50, RHR vs baseline 0.30,
  sleep vs 8h need 0.20, all scored against the user's own 30-day baseline with
  stated saturation bounds. Best-written rationale for the "don't fake clinical"
  boundary. https://github.com/voslartomas/healthy
- **iamishfaq07/zoon** (`docs/ALGORITHM-SPECS.md`): body battery + estimated
  alertness (cognitive energy curve) specs with provenance tracking and confidence
  levels. HealthKit-oriented, but the provenance/confidence framing is worth copying.
  https://github.com/iamishfaq07/zoon
- **marlobello/ghcv**: multi-period trend screens (week/2wk/month/3mo) with the
  Vico chart library, min/avg/max per metric. Good trend-UI reference.
  https://github.com/marlobello/ghcv

## Recommended score design for Aura (synthesis)

Keep the 0-100 stress ring, but rebuild the inputs on the self-baseline pattern:

```
stress = 100 * (w1*hr_signal + w2*sleep_signal + w3*hrv_signal + w4*mood_signal + w5*behavior_signal)
```

- hr_signal: resting HR vs 14-day baseline (elevated RHR = stress), saturation ~+6 bpm
- sleep_signal: duration vs 8h need, efficiency, deep+REM share, consistency
- hrv_signal: last-night HRV vs 14-day baseline (lower = stress)
- mood_signal: 7-day check-in average (already have)
- behavior_signal: breathing minutes, journaling, wind-down completion (rewards coping)
- Missing contributors default neutral (0.5-0.7) and the UI shows a "calibrating"
  state until ~7-14 days of history, per Vitals.
- Separate the *score* (threshold-shaped, "how stressed today") from a *charge*
  line (continuous, "how your week is trending") per OpenVitals.
- Rule-based insight cards free ("RHR 8% above your baseline 3 nights running —
  consider an early wind-down"); optional BYOK LLM deep analysis per openhealth.
