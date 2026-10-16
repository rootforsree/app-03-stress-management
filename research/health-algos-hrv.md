# Health algorithms for Aura: HR, HRV, sleep insights

Research synthesis for Aura (app-03-stress), an Android stress app reading Health Connect.
Three parallel research streams: HRV time-domain, HRV frequency-domain + recovery scoring,
resting-HR baselines + Health Connect data availability. Stream notes:
`algos-hrv-timedomain.md`, `algos-hrv-freqdomain.md`, `algos-hr-baseline-healthconnect.md`.

## 0. The one constraint that shapes everything

**Health Connect does NOT expose raw RR / inter-beat intervals to third-party apps.**
There is no record type for them (only Samsung's Privileged Health SDK has them, via a partner program).
Consequences:

| Need | Health Connect record | Reality |
|---|---|---|
| Continuous HR | `HeartRateRecord` (samples) | Yes, but sync is periodic, not live |
| Resting HR | `RestingHeartRateRecord` (instant bpm) | Type exists (API 34+) but **often unwritten** (Samsung Health reportedly doesn't write it). Build a derived fallback: average of lowest 20% of overnight samples |
| HRV | `HeartRateVariabilityRmssdRecord` (instant, ms) | Type exists but **almost nothing writes it** (Pixel Watch is the notable writer). Request the permission, never depend on it |
| Raw RR / IBI | none | Impossible. Cannot compute RMSSD/SDNN/pNN50/LF/HF/Poincare from HC data |
| Sleep | `SleepSessionRecord` (interval, optional stages) | Yes; stages often absent, code a no-stages fallback |

Permission gotchas: `READ_HEALTH_DATA_IN_BACKGROUND` (without it, background reads silently return zero records),
`READ_HEALTH_DATA_HISTORY` (without it, reads cap at 30 days before grant, so 28-day baselines can't build on fresh installs).
Every read needs its own try/catch; never score missing data as zero.

**Bottom line:** Aura's on-device algorithms must work on *daily aggregates*
(resting HR, overnight HR dip, sleep duration/debt, HRV-RMSSD-if-present, mood check-ins, breathing minutes) —
not on RR-interval math. Algorithms below are marked FEASIBLE (works on HC aggregates)
or RR-ONLY (needs raw intervals; skip unless a future data source provides them).

---

## 1. HRV time-domain algorithms

### 1.1 RMSSD — FEASIBLE only if `HeartRateVariabilityRmssdRecord` is present
- Math: root mean square of successive differences between adjacent RR intervals.
  `RMSSD = sqrt(mean((RR[i+1] - RR[i])^2))`. The gold-standard short-term HRV metric;
  high RMSSD = parasympathetic (rest/recover), low = sympathetic (stress/fight-or-flight).
- Inputs: RR intervals (unavailable) OR the precomputed RMSSD record (rare).
- Repos: neuropsychology/NeuroKit (MIT, ~2,395 stars, active) — `neurokit2/hrv/hrv_time.py`;
  jlamprou/HRVBioFeedback (MIT, 2026) — `app/src/main/java/` pure-Kotlin DSP, 26 validation tests;
  dermatoid-collab/hrv-rm (Kotlin, active, Baevsky added Sep 2026) — `HrvMetrics.kt`.
- Aura use: read the record when present, z-score it against personal baseline. Never compute from scratch.

### 1.2 SDNN, pNN50, MeanNN — RR-ONLY
- Math: SDNN = std dev of all NN intervals (overall variability); pNN50 = % of successive
  interval pairs differing by >50 ms (parasympathetic marker); MeanNN = mean interval.
- Inputs: RR intervals only. Not computable from Health Connect.
- Repos: NeuroKit2 `hrv_time.py`; PGomes92/pyhrv `pyhrv/time_domain.py` (MIT-ish, educational);
  Aura-healthcare/hrv-analysis `hrvanalysis/` (GPL-3.0 — reference only, do not copy).
- Aura use: skip. Document as future work if raw IBI ever becomes available.

### 1.3 Baevsky Stress Index (SI) — RR-ONLY
- Math: histogram of RR intervals in 50 ms bins. `SI = AMo / (2 * Mo * MxDMn)` where
  Mo = modal bin center (s), AMo = % of beats in the modal bin, MxDMn = max RR − min RR (s).
  Variants replace MxDMn with 3.92*SDNN. High SI = sympathetic dominance = stress.
  Typical rest values 50–150; stress pushes it into the hundreds.
- Inputs: RR intervals only.
- Repos: strikerdlm/HRV — `app/hrv_core.py::compute_baevsky_stress_index()` (MIT, only repo
  bundling triangular index + TINN + Baevsky); dermatoid-collab/hrv-rm — `HrvMetricsCalculator.stressIndex()`
  (Kotlin); openstrap/analytics (Dart) — windowed-median Baevsky variant `lib/src/onehz/clinical/stress_si.dart`.
- Aura use: skip for now (no RR data). It is the most-cited single-number stress formula in the
  literature, so keep it as the prime candidate if a wearable data source arrives later.

### 1.4 Triangular index / TINN — RR-ONLY
- Math: geometric HRV from the RR histogram (density / peak height; TINN = baseline width of
  the triangular interpolation). Robust to artifacts.
- Repos: NeuroKit2 `hrv_time.py`; hrv-analysis `get_geometrical_features()` (GPL, reference only).
- Aura use: skip.

---

## 2. HRV frequency-domain algorithms — all RR-ONLY, skip for Aura v1

### 2.1 LF/HF ratio
- Math: power spectral density of the RR tachogram; LF band 0.04–0.15 Hz (mixed sympathetic/
  parasympathetic), HF band 0.15–0.4 Hz (parasympathetic/respiratory). High LF/HF ≈ stress.
- Methods: Welch FFT on 4 Hz-interpolated NN (scipy-style), Lomb-Scargle on raw uneven NN
  (no resampling needed), or AR Burg order-16 (no FFT library at all).
- Repos: jlamprou/hrvbiofeedback — `ArSpectralAnalyzer.kt` (pure Kotlin, Burg's method, no FFT dep);
  tanarchytan/whoop-rs — `crates/physio-algo/src/hrv_freq.rs` (pure-arithmetic Lomb-Scargle,
  ports to Kotlin line-for-line; NO LICENSE DECLARED — use formulas only);
  saiko-psych/RRational — `_compute_kubios_frequency_powers()` (Kubios pipeline: 4 Hz cubic
  spline, Smoothness Priors detrending λ=500, Welch 180 s Hann windows 50% overlap, validated
  within ~6% of Kubios v4.3.0); NeuroKit2 `hrv_frequency.py` (MIT validation oracle).
- Aura use: skip — needs RR intervals. If ever needed, AR Burg-16 is the cheapest on-device path.

### 2.2 Cardiac coherence score (HeartMath-style)
- Math: peak power in 0.04–0.26 Hz band (±0.015 Hz integral around the peak);
  `coherence = peakPower / (totalPower − peakPower)`. This is the metric Breath Ball graphs live.
- Repos: jlamprou/hrvbiofeedback — `domain/dsp/CoherenceCalculator.kt` (pure Kotlin, Inner Balance
  thresholds 0.5/1.8); egelman/heartsong — single-file `hrv_recorder.py` reference (MIT).
- Aura use: skip for passive insights (needs RR). Revisit for *live* breathing-session biofeedback
  only if a camera-PPG or BLE HR strap source is added (jason-arcadia/phonecamerahrv shows the
  Kotlin pattern: `PPGprocessor.kt`, `HRVScores.kt`).

### 2.3 Respiratory rate from RR (RSA/EDR)
- Math: breathing modulates RR intervals; resample tachogram at 4 Hz, detrend, peak-pick the
  breathing modulation in 5-min windows, median rate (8–25 brpm plausible).
- Repos: tanarchytan/whoop-rs — `respiratory_rate.rs::resp_rate_from_rr()`; jlamprou/hrvbiofeedback —
  `RespiratorySignalExtractor.kt` (EDR + accelerometer, bandpass 0.05–0.5 Hz).
- Aura use: skip (RR-only).

---

## 3. Recovery / readiness composites — FEASIBLE (adapt to HC aggregates)

### 3.1 Whoop-style readiness composite
- Math: per-driver robust z-scores vs personal EWMA baseline, weighted sum, logistic squash to 0–100:
  `100 / (1 + e^(−1.6(z + 0.2)))`, with red/yellow/green bands at 34/67.
  Reference weights: HRV 0.55 / resting HR 0.20 / sleep 0.15 / resp 0.05 / skin-temp 0.05.
- Repos: tanarchytan/whoop-rs — `recovery.rs` (closest open formula found; no license — reimplement,
  don't copy); akshatksingh18/whoop (MIT) — `packages/analytics/.../wellness/readiness_composite.dart`
  (0–100 vs 28-night baseline, min 14 nights; HRV 40 / RHR 30 / breathing 20 / temp 10; missing inputs
  dropped with rescaled weights).
- Aura adaptation: HRV-rmssd 40 (when present, else drop + rescale) / resting HR 30 /
  sleep debt 20 / breathing minutes 10. Missing-input rescaling is the key trick to copy.

### 3.2 Overnight recovery score (emuqu pattern)
- Math: sleep-anchored 30–70% window of the night; lnRMSSD z-score vs personal baseline with
  anti-ratchet admission (baseline only updates on good data); score = HRV 60% / sleep 25% / vitals 15%.
- Repo: chrissharp80/emuqu (license NOASSERTION — verify before reuse) —
  `docs/whitepapers/01-overnight-recovery-measurement.md`, code `Emuqu/Sources/Analysis/`.
- Aura adaptation: replace lnRMSSD with resting-HR-dip z-score when RMSSD absent; keep the
  sleep-anchored window and anti-ratchet baseline ideas.

---

## 4. Resting-HR baseline, trend, anomaly detection — FEASIBLE (core of Aura insights)

### 4.1 Personal baseline + z-score anomaly (the strain engine)
- Math: rolling baseline (21–30 day exponentially-weighted mean/std, or simple windowed mean/std
  over days 5–29); daily z = (today − baseline_mean) / baseline_std, one-sided where appropriate
  (high HR = bad, low HRV = bad). Thresholds from literature: ~1.5σ elevated, ~3σ extreme.
- Repos: th0rgal/open_health (MIT, 31 stars, active Oct 2026) — `docs/algorithms/illness-detection.md`,
  `data/utils/SymptomRadarStatus.java`. Oura-style: 30-day nightly series, one-sided z-scores,
  per-biomarker sigma thresholds (1.457 avg HR, 1.30 lowest HR, 1.11 HRV). **Best direct template.**
  al3rez/symptom-radar-ultrahuman-cloudflare (MIT) — `src/strain.ts`: z vs 21-day EW baseline,
  weighted sum (RHR 0.25, HRV 0.25, temp 0.35), blended with 3-day trend; 1.5σ/3.0σ levels.
  williamscli/hrv_trainer (MIT) — morning z vs rolling median+MAD baseline, REST/BUILD/PERFORM bands at ±1.0.
  lucacadalora/whoop-stress-monitoring (MIT, 4 stars) — stress as dBPM = SD-units from personal baseline.
- Aura use: **this is the primary algorithm.** Signals: derived resting HR, overnight HR dip,
  sleep duration, HRV-RMSSD when present. Output: daily strain z per signal + composite.

### 4.2 Trend slopes (OLS on trailing windows)
- Math: ordinary least squares slope on trailing 7/14 nightly values (bpm/day for HR, min/day for sleep).
  ~15 lines of Kotlin. No repo ships a dedicated routine; write directly.
- Aura use: "your resting HR is climbing 0.8 bpm/day over the last 2 weeks" style insights.

### 4.3 Cross-correlation insights (the "insights" in Insights)
- Math: Pearson/Spearman correlation between pairs over trailing 14–30 days:
  sleep duration → next-day mood/stress score; breathing minutes → same-evening stress;
  resting HR → next-day check-in. Report r and a plain-language sentence when |r| > ~0.4
  with n ≥ 10.
- Aura use: generates the actual insight cards ("On days you sleep under 6h, your next-day
  stress averages 18 points higher"). All inputs are already in Room/Health Connect.

---

## 5. Ranked top 3 for Aura

1. **Personal-baseline z-score strain composite** (open_health / symptom-radar pattern) —
   detects "today is unusual for YOU" from resting HR + sleep + HRV-if-present; the anomaly engine
   everything else builds on, ~60 lines of pure Kotlin, works fully on Health Connect aggregates.
2. **Weighted recovery/readiness score 0–100** (whoop/emuqu pattern, Aura-adapted weights,
   missing-input rescaling) — turns raw signals into one number with red/yellow/green bands;
   the single value users actually understand and check daily.
3. **OLS trend slopes + cross-correlation insight cards** (sleep→stress, breathing→mood,
   RHR trend) — produces the plain-language insights ("your stress drops on 10+ min breathing days");
   this is what makes the Insights tab feel intelligent rather than just charted.

## 6. Implementation notes for the Kotlin build

- Derived resting HR fallback: when `RestingHeartRateRecord` is absent, use the mean of the
  lowest 20% of overnight `HeartRateRecord` samples (00:00–06:00). Validate against Samsung Health's
  displayed resting HR on Charan's device.
- Baselines: EWMA (α ≈ 2/(N+1), N=21) for the rolling mean; Welford's online algorithm for the
  rolling variance — both O(1) memory, no history arrays needed on-device.
- Anti-ratchet: only admit a day's value into the baseline if data completeness > 70%,
  so sick/travel days don't poison the baseline (emuqu pattern).
- Validation: port NeuroKit2's `hrv_time.py` formulas into unit tests with fixed RR series;
  cross-check z-score math against the open_health Java implementation.
- Licenses: prefer MIT/Apache-2.0 repos (NeuroKit2, open_health, HRVBioFeedback, whoop-dart).
  Avoid copying GPL-3.0 code (hrv-analysis); use only as math reference. Verify NOASSERTION /
  unlicensed repos (emuqu, whoop-rs, hrv-rm) before reusing verbatim — reimplement formulas.
