# Sleep Analysis Algorithms — Research for Aura (app-03-stress)

Goal: on-device (Kotlin) sleep insights from Health Connect `SleepSessionRecord`s:
sleep quality score, sleep debt, consistency / social jetlag, sleep-stress correlations.

Health Connect `SleepSessionRecord` gives us, per session: start/end timestamps,
`stages` list (AWAKE, SLEEPING, OUT_OF_BED, LIGHT, DEEP, REM), title, notes.
It does NOT give: sleep onset latency, WASO, or time-in-bed directly — but all
three are derivable from the stage timeline (see algorithm notes).

## Repo inventory

| Repo | Lang | Stars | Activity | License | What it gives us |
|---|---|---|---|---|---|
| [wadpac/ggir](https://github.com/wadpac/ggir) | R | ~500+ (research standard) | Very active (docs updated Oct 2026) | GPL-3.0 | Gold-standard actigraphy pipeline; `CalcSleepRegularityIndex` (SRI1/SRI2) |
| [dpwindred/sleepreg](https://github.com/dpwindred/sleepreg) | R | 21 | Low (2021-era) | LGPL-3.0 | SRI from binary sleep-wake data; `SRI_from_binary` is the portable core |
| [ghammad/pyActigraphy](https://github.com/ghammad/pyActigraphy) | Python | established | Active | (permissive) | `SleepRegularityIndex` with the exact published formula in docs |
| [itsokay-co/hypnospy](https://github.com/itsokay-co/hypnospy) | Python | fork of MIT/Cambridge project | Active | BSD 2-Clause | Sleep efficiency, WASO, awakenings; Cole-Kripke / Sadeh / ScrippsClinic staging; HR-based sleep detection; circadian (cosinor, SSA) |
| [youngmoon-hub/srt-sri-nhanes-validation](https://github.com/youngmoon-hub/srt-sri-nhanes-validation) | Python/R | new (2026) | Active | — | SRI + SRT (Sleep Regularity Test) validated on NHANES actigraphy |
| [stansz/smart-ring docs](https://github.com/stansz/smart-ring/blob/HEAD/docs/RESEARCH.md) | docs | — | Updated Sep 2026 | — | Validated 5-component sleep-quality composite mirroring Oura's architecture, with literature refs |
| [pugson Oura debt gist](https://gist.github.com/pugson/4c2107eb5a4ef7cc52e6a107c6a8b0fe) | — | — | 2026 | — | Reverse-engineered Oura sleep-debt formula validated against real app readings |

## Algorithm catalog

### 1. Sleep quality composite score (Oura-style, 0-100)

- **What it is:** Weighted sum of 5 sub-scores, each computed with trapezoidal
  scoring (full credit inside an optimal range, linear falloff outside it).
  From [stansz/smart-ring RESEARCH.md](https://github.com/stansz/smart-ring/blob/HEAD/docs/RESEARCH.md),
  mirroring Oura's contributor architecture:
  `Score = 30%·Duration + 25%·Efficiency + 25%·Architecture + 15%·Continuity + 5%·Latency`
- **The math, plainly:** For each component you define an optimal band and a
  zero band. E.g. duration: 100 points for 7-9 h, sliding to 0 at <4 h or >10 h.
  Efficiency: 100 at >=90%, 0 at <60%. Architecture: deep 13-23% and REM 20-25%
  of total sleep (Ohayon et al. 2004 meta-analysis). Continuity: WASO <20 min and
  <2 awakenings is perfect; WASO >60 min or >6 awakenings is 0. Latency: 10-20 min
  optimal; <5 min suggests sleep debt, >30 min is poor (PSQI convention).
- **Inputs needed:** nightly total sleep, time in bed, stage breakdown (deep/REM %),
  WASO + awakening count, sleep-onset latency. Every one is derivable from
  Health Connect stages: TST = sum of LIGHT+DEEP+REM+SLEEPING; TIB = session
  start to end (minus OUT_OF_BED); latency = first-sleep-stage minus session start;
  WASO = AWAKE minutes between first sleep and final wake.
- **Why it fits Aura:** one explainable 0-100 number, five inspectable sub-scores
  ("your efficiency is dragging your score"), zero ML, trivial on-device cost.

### 2. Weighted sleep debt (Oura model, 14-night window)

- **What it is:** A running debt balance, not a single-night shortfall.
  Reverse-engineered and validated in [pugson's gist](https://gist.github.com/pugson/4c2107eb5a4ef7cc52e6a107c6a8b0fe):
  `debt = Σ w(i)·(need − slept_i)` over the last 14 nights, with linear-decay
  weights `w(i) = 1 − 0.75·i/13` (last night counts 1.00, 13 nights ago counts 0.25),
  clamped to [0, 10 h]. Nights with no data are skipped but still consume their
  weight slot; fewer than 5 valid nights = hide the number.
- **The math, plainly:** each night you miss your personal sleep need adds to the
  pile; recent misses weigh more than old ones; the pile caps at 10 h so one bad
  month doesn't permanently red-line you. Personal need starts at a default
  (8 h) and can be learned from the user's own 90-day median once enough data exists.
- **Inputs needed:** nightly total sleep only. Fully covered by Health Connect.
- **Why it fits Aura:** the most actionable sleep metric for a stress app
  ("you owe 3.5 h — that's why today feels heavy"). Powers pay-back insights and
  the wind-down nudge.

### 3. Sleep Regularity Index — SRI (Phillips et al. 2017)

- **What it is:** The probability that your sleep/wake state at any minute matches
  your state at the same minute 24 h later, scaled to −100..+100 (100 = identical
  days, 0 = random). Formula (from [pyActigraphy docs](https://ghammad.github.io/pyActigraphy/_autosummary/pyActigraphy.sleep.ScoringMixin.SleepRegularityIndex.html)):
  `SRI = −100 + 200·Σδ(same state)/(M·(N−1))` over M minutes × N day pairs.
  Reference implementations: [GGIR's CalcSleepRegularityIndex](https://github.com/wadpac/ggir/blob/HEAD/docs/reference/CalcSleepRegularityIndex.md)
  ([background](https://github.com/wadpac/ggir/blob/HEAD/docs/articles/SleepRegularityIndex.md)),
  [sleepreg](https://github.com/dpwindred/sleepreg), and the NHANES-validated
  [srt-sri-nhanes-validation](https://github.com/youngmoon-hub/srt-sri-nhanes-validation).
- **The math, plainly:** build a minute-by-minute asleep/awake strip for 7+ days
  (from Health Connect sessions, gaps = awake), then for every minute compare
  today vs yesterday. Count matches, scale. Needs ≥5 overlapping day-pairs to be valid.
- **Inputs needed:** minute-level sleep/wake timeline. Constructible from Health
  Connect: mark every minute inside a sleep stage as asleep, everything else awake.
- **Why it fits Aura:** regularity predicts health outcomes independently of
  duration (Windred et al. 2021, 60k UK Biobank participants). A user sleeping
  8 h at chaotic times scores badly — exactly the insight a generic "8 h = good"
  tracker misses. Simpler fallback: the SRT (`mean daily sleep / union of sleep
  minutes × 100`, 0-100) from the NHANES repo if SRI feels too abstract to display.

### 4. Social jetlag (Roenneberg & Wittmann 2006)

- **What it is:** `|mid-sleep on work days − mid-sleep on free days|`, where
  mid-sleep = halfway between sleep onset and wake. >1 h is notable, >2 h is
  clinically relevant circadian misalignment.
- **The math, plainly:** average your sleep midpoint across weekdays vs weekends;
  the gap is your social jetlag. Aura can approximate work/free as Mon-Fri vs
  Sat-Sun (no calendar access needed).
- **Inputs needed:** sleep onset + wake per night. Directly in Health Connect.
- **Why it fits Aura:** one sentence users instantly get ("your body lives in a
  different timezone on weekends"). Cheap to compute, strong stress correlation story.

### 5. Sleep efficiency (clinical standard)

- **What it is:** `TST / time-in-bed × 100`. ≥85% normal, <80% suggests fragmented
  sleep. Implemented in [hypnospy](https://github.com/itsokay-co/hypnospy) alongside
  WASO and awakening counts; definitions follow AASM practice.
- **Inputs needed:** TST + TIB, both derivable from Health Connect stages.
- **Note:** efficiency is already a 25% component of algorithm #1; surface it
  standalone too, since it is the single most intuitive "was my sleep solid" number.

### 6. Sleep staging from movement/HR (Cole-Kripke, Sadeh, Scripps Clinic)

- **What it is:** classic actigraphy scoring rules that turn accelerometer counts
  into sleep/wake epochs. [hypnospy](https://github.com/itsokay-co/hypnospy) ships
  all three plus an HR-volatility method (ECDF quantile thresholds → block merging →
  HR-volatility refinement) from a Nature Scientific Reports 2022 paper.
- **Verdict for Aura:** NOT needed. Health Connect already delivers staged sessions
  from the OS/watch. Re-staging raw sensor data on-device adds nothing unless Aura
  ever records its own actigraphy. Skip.

### 7. Chronotype from mid-sleep on free days (MSFsc)

- **What it is:** the Munich ChronoType Questionnaire method — your free-day
  sleep midpoint (corrected for oversleep debt) estimates chronotype
  (early/intermediate/late). One number per user, recomputed monthly.
- **Why it fits Aura:** personalizes wind-down timing ("as a late chronotype your
  wind-down should start at 11:30 PM, not 10"). Near-zero compute. Good v2 addition.

## Recommended top 3 for Aura

1. **5-component sleep quality score** (stansz/smart-ring / Oura architecture) —
   one explainable 0-100 nightly number built only from Health Connect-derivable
   inputs; sub-scores make every insight writable in plain language.
2. **Oura-style weighted sleep debt** (14-night linear-decay window) —
   the single most actionable sleep metric for stress; trivial arithmetic,
   drives "pay it back" coaching.
3. **Sleep Regularity Index (SRI)** (Phillips 2017; GGIR/sleepreg/pyActigraphy) —
   captures timing chaos that duration-based scores miss; computable from
   minute-level Health Connect timelines; validated against 60k-person cohorts.

Runners-up: social jetlag (simplest circadian insight, add with #3) and standalone
sleep efficiency (already inside #1, surface it separately). Skip on-device sleep
staging — Health Connect stages are sufficient.
