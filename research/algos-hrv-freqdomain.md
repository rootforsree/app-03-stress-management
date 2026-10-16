# HRV Frequency-Domain Algorithms: GitHub Research

Goal: on-device (Kotlin/Android) computation of LF/HF ratio, cardiac coherence score,
HRV-based stress detection, recovery/readiness scoring, and respiratory rate from RSA
using Health Connect data (RR intervals). Research only; no code.

Key finding up front: the two most directly usable repos are
`jlamprou/hrvbiofeedback` (pure-Kotlin Android DSP package incl. HeartMath coherence
and AR-Burg LF/HF) and `tanarchytan/whoop-rs` (pure-Rust physio algorithms incl. a
portable Lomb-Scargle implementation and a z-score+logistic recovery formula).
Neither requires native deps.

---

## 1. jlamprou/hrvbiofeedback — pure-Kotlin Android DSP (BEST on-device fit)

- URL: https://github.com/jlamprou/hrvbiofeedback
- Stars: 1 | Last commit: 2026-04-18 | License: MIT | Language: Kotlin
- What it is: full Android app (Polar H10 BLE) with a dependency-light
  `domain/dsp/` package. Everything is pure Kotlin stdlib + javax.inject.

Algorithms and exact file paths (`app/src/main/java/com/hrv/biofeedback/domain/dsp/`):

- `ArSpectralAnalyzer.kt` — AR spectral estimation via **Burg's method**
  (order 16 at 4 Hz, DEFAULT_NFFT=512). AR coefficients -> power spectral density.
  LF/HF band powers are integrated from this PSD. Note: AR spectral is the
  Kubios-class approach; works on short segments and avoids an FFT library.
- `CoherenceCalculator.kt` — **HeartMath coherence algorithm** per McCraty (2022):
  1. Find the maximum PSD peak in the 0.04–0.26 Hz coherence band.
  2. Integrate power in a 0.030 Hz window centered on that peak.
  3. Coherence Score = peak power / (total power across the whole spectrum
     (0 to Nyquist) − peak power). Unbounded above, typically 0–16.
  Threshold constants included: Level 1 Low/Medium = 0.5, Medium/High = 1.8
  (Inner Balance challenge levels).
- `CrossSpectralAnalyzer.kt` — cross-spectral coherence between the respiratory
  signal and HRV (magnitude-squared coherence, MSC), plus phase angle and
  breathing rate from cross-spectra.
- `RespiratorySignalExtractor.kt` — breathing waveform from two sources:
  EDR (ECG R-wave amplitude modulation, Charlton et al. 2016) and
  accelerometer-derived respiration (chest z-axis). Bandpass 0.05–0.5 Hz,
  references Schafer & Kratky (2008) "Estimation of Breathing Rate from
  Respiratory Sinus Arrhythmia".
- Supporting modules: `ArtifactDetector.kt` (artifact detection),
  `CubicSplineInterpolator.kt` (spline resample to 4 Hz grid), `HrvProcessor.kt`
  (pipeline), `HrvMetricsCalculator.kt` (band integration), `NonlinearAnalyzer.kt`
  (Poincaré, DFA, SampEn), `PeakTroughAnalyzer.kt` (peak-to-trough RSA),
  `PhaseAnalyzer.kt`, `SignalQualityMonitor.kt`.
- `domain/model/HrvMetrics.kt` and `domain/usecase/analysis/HrvNorms.kt` —
  metric containers plus normative reference values.

On-device feasibility: EXCELLENT. Pure Kotlin, no native FFT needed because the
AR method replaces FFT. Burg-16 at 4 Hz is O(n·16) — trivial on a phone.

---

## 2. tanarchytan/whoop-rs — pure-Rust recovery + Lomb-Scargle (BEST for recovery score + Lomb)

- URL: https://github.com/tanarchytan/whoop-rs
- Stars: 2 | Last commit: 2026-10-10 | License: NONE DECLARED (no LICENSE file;
  do not copy code verbatim — use the documented formulas only) | Language: Rust
- What it is: from-scratch WHOOP BLE client whose `physio-algo` crate is pure,
  IO-free physiological math, exposed to Kotlin via uniffi FFI. Only std deps.

Algorithms and exact file paths (`crates/physio-algo/src/`):

- `hrv_freq.rs` — frequency-domain HRV via **Lomb-Scargle periodogram directly
  on the irregular tachogram** (no resampling). Task Force (1996) bands
  VLF 0.0033–0.04, LF 0.04–0.15, HF 0.15–0.40 Hz; span-gated: HF needs ≥60 s,
  LF/LF-HF/total needs ≥250 s; trapezoid band integration on a 0.005 Hz grid.
  The Lomb implementation is plain arithmetic — ports to Kotlin line-for-line.
- `recovery.rs` — **Whoop-style "Charge" recovery score**, fully explicit:
  - Per-driver robust z-score: (value − personal mean) / (1.253 × EWMA spread).
  - Weights: HRV 0.55, resting HR 0.20, sleep performance 0.15,
    respiration 0.05, skin-temp deviation 0.05, plus Oura-style terms:
    overnight HR-decline slope 0.05 and previous-day activity balance 0.05.
  - Composite z → score via logistic: score = 100 / (1 + e^(−1.6·(z+0.2))),
    so a baseline-average night maps to ~58%. Missing drivers drop and weights
    renormalize. Whoop colour bands: red <34, yellow <67, green ≥67.
  - This is the closest thing to an open Whoop/Oura readiness formula found.
- `respiratory_rate.rs` — **breathing rate from RR intervals via RSA**:
  `resp_rate_from_rr()`: beat-interval tachogram → resample to 4 Hz → detrend
  (8 s window) → 5-min windows → peak-pick the breathing modulation
  (min breath 2.5 s, max 10 s apart) → median rate. Plausibility band 8–25 brpm.
- Also: `hrv.rs` (RMSSD/time-domain, artifact-corrected), `resting_hr.rs`,
  `sleep/` (sleep staging from HR), `recovery_drivers.rs`,
  `docs/algorithms.md` (method notes).

On-device feasibility: EXCELLENT. The crate deliberately has no native/FFT deps;
the math is z-scores, logistic, and a textbook Lomb-Scargle — all trivial in
Kotlin. License caveat stands: formulas yes, verbatim code no.

---

## 3. aura-healthcare/hrv-analysis — Python reference (Welch vs Lomb)

- URL: https://github.com/aura-healthcare/hrv-analysis
- Stars: ~460 | Last commit: 2026-10-06 | License: GPL-3.0 | Language: Python
- What it is: the most-used open HRV Python package; good algorithm reference
  for porting (GPL — formulas fine, code copyleft).

Key file: `hrvanalysis/extract_features.py`

- `get_frequency_domain_features(nn_intervals, method='welch'|'lomb')`:
  - Welch path: interpolate NN intervals to 4 Hz grid, `scipy.signal.welch`
    (Hann window, nfft=4096), then VLF/LF/HF powers, LFnu/HFnu normalized units,
    total power, LF/HF ratio (`_get_features_from_psd()`).
  - Lomb path: astropy Lomb-Scargle applied directly to the uneven NN series.
- `hrvanalysis/preprocessing.py`: `remove_outliers`, `remove_ectopic_beats`
  (Malik/Kamath rules), `interpolate_nan_values` — the beat-cleaning steps that
  must run before any spectral math.

On-device feasibility: reference math only. The Kotlin port needs a small FFT
(hand-rolled or Apache Commons Math) for the Welch path; the Lomb path needs a
Lomb-Scargle implementation (see whoop-rs above).

---

## 4. neuropsychology/NeuroKit (neurokit2) — Python reference (large, MIT)

- URL: https://github.com/neuropsychology/NeuroKit
- Stars: ~2395 | Last commit: 2026-10-01 | License: MIT | Language: Python

Key files (`neurokit2/hrv/`):

- `hrv_frequency.py` — `hrv_frequency()` with `psd_method` options
  ('welch' | 'lomb' via scipy); Task Force bands; normalized powers; LF/HF.
- `hrv_rsa.py` — respiratory sinus arrhythmia metrics (peak-to-trough,
  Porges-Bohrer style), useful for a breath-session stress/relaxation metric.
- `hrv_time.py` — SDNN, RMSSD, pNN50, geometric.

On-device feasibility: reference math only (MIT is port-friendly). Best used as
a validation oracle: compute the same numbers in Kotlin and compare against
neurokit2 output on test RR series.

---

## 5. saiko-psych/RRational — Python, Kubios-validated pipeline

- URL: https://github.com/saiko-psych/rrational
- Stars: 6 | Active (docs updated ~Oct 2026) | License: Apache-2.0 per LICENSE
  file (README badge says MIT — treat as Apache-2.0 until clarified)
- What it is: free HRV research toolkit; its frequency pipeline is validated
  against Kubios HRV Scientific v4.3.0 (LF/HF within ~6% on matched segments).

Key file: `src/rrational/analysis/hrv_compute.py`

- `_compute_kubios_frequency_powers()`: the exact Kubios chain —
  cubic-spline interpolation to 4 Hz, Smoothness Priors detrending
  (Tarvainen et al. 2002, λ=500), Welch PSD with **180 s Hann windows,
  50% overlap**, trapezoid integration of VLF/LF/HF bands. This is the
  "correctness reference" for on-device Welch: copy the parameters, not the
  code (it's scipy-based).
- Also implements the Lipponen & Tarvainen (2019) automatic beat-correction
  algorithm for artifact handling (`src/rrational/cleaning/`).

On-device feasibility: reference only (scipy/streamlit). The parameter set
(180 s / 50% / 4 Hz spline / λ=500 detrend) is the spec to reproduce in Kotlin.

---

## 6. scottleimroth/hrvxo — Kotlin Multiplatform Android HRV app (coherence + Welch)

- URL: https://github.com/scottleimroth/hrvxo
- Stars: unknown | Last updated: ~May 2026 per search index | License: unknown
  | Language: Kotlin (Compose Multiplatform)
- Caveat: GitHub's API returned 404 for repo metadata at research time
  (possible rename/private move) — treat the below as captured from the public
  README; verify before relying on it.

What it claimed: Android HRV app with BLE chest-strap HRV processing:
- HRV processing: artifact correction (cubic spline), RMSSD, **FFT spectral
  analysis, HeartMath coherence scoring with Welch's method PSD estimation**.
- Code home: `composeApp/src/commonMain/kotlin/com/heartsyncradio/hrv/`
  (artifact detection, FFT, coherence modules) — the shared KMP module means the
  algorithm code is plain Kotlin usable from Android.
- Testing: 40 unit tests covering FFT accuracy (Parseval's theorem), coherence
  calculation, and PhysioNet real-world data validation — i.e. validated math.

On-device feasibility: if the repo is still public, its `hrv/` module is the
most direct drop-in reference for a Kotlin Welch+coherence implementation.

---

## 7. egelman/heartsong — Python coherence snippet (small, MIT)

- URL: https://github.com/egelman/heartsong
- Stars: 0 | Last commit: Oct 2025 | License: MIT | Language: Python
- What it is: single-file (`hrv_recorder.py`) Arduino HRV recorder.
  Real-time coherence in plain scipy: peak-band power / total-band power ratio
  (0–1) plus HeartMath-style peak/(total−peak) mode, sliding 60–90 s windows
  at 4 Hz resample. Useful as a minimal readable reference for the coherence
  computation.

---

## 8. jlamprou note, mirkancal/OpenHRV — Python/Qt biofeedback (time-domain only)

- URL: https://github.com/mirkancal/OpenHRV
- Stars: 0 | Last commit: 2024-06-28 | License: LGPL-2.1 | Language: Python
- `openhrv/model.py`: real-time HRV biofeedback from BLE chest straps with a
  breathing pacer and an adjustable HRV target slider — but time-domain only
  (IBI buffers, local HRV target), no spectral/coherence math. UI/reference
  inspiration for a Breath Ball-style session screen, not algorithms.

---

## 9. lucacadalora/whoop-stress-monitoring — simple deviation stress model

- URL: https://github.com/lucacadalora/whoop-stress-monitoring
- Stars: 4 | Last commit: 2026-06-12 | License: MIT | Language: Python
- `whoops/analysis/scoring.py`, `whoops/analysis/baseline.py`: stress estimated
  as deviation of calendar-window heart rate from a personal baseline
  (dBPM = deviation in baseline-SD units), with ridge-regression attribution of
  stress to meetings/people. No HRV frequency math, but the
  "stress = z-score vs personal baseline" pattern is exactly how the app should
  frame an HRV-based stress score, and it ports to Kotlin in a dozen lines.

---

## Recommended on-device stack for the app

1. Beat cleaning: Malik/Kamath ectopic rules (hrv-analysis preprocessing)
   or Lipponen-Tarvainen correction (RRational); Health Connect RR intervals
   need this before anything spectral.
2. LF/HF: AR Burg-16 at 4 Hz (copy pattern from
   `hrvbiofeedback/.../domain/dsp/ArSpectralAnalyzer.kt`) — no FFT library
   needed. Or Kubios parameters via Welch (180 s Hann, 50% overlap, 4 Hz spline,
   λ=500 detrend) with a small Kotlin FFT (Apache Commons Math
   `FastFourierTransformer` or hand-rolled; 5-min windows are only 1200 samples).
3. Cardiac coherence: HeartMath formula from `CoherenceCalculator.kt`
   (peak in 0.04–0.26 Hz, ±0.015 Hz integral, score = peak/(total−peak)),
   updated every ~5 s on a 64 s window — the Breath Ball metric.
4. Lomb-Scargle alternative for uneven RR series: port the pure arithmetic from
   `whoop-rs/crates/physio-algo/src/hrv_freq.rs`.
5. Recovery/readiness: adopt `whoop-rs/crates/physio-algo/src/recovery.rs` —
   per-driver robust z vs EWMA personal baseline, weights
   0.55 HRV / 0.20 RHR / 0.15 sleep / 0.05 resp / 0.05 skin-temp (+ optional
   overnight HR slope 0.05, activity balance 0.05), logistic squash to 0–100.
   Replace skin-temp (no sensor) with coherence or sleep-debt terms; weights
   renormalize by design.
6. Respiratory rate from RSA: `resp_rate_from_rr()` in `respiratory_rate.rs`
   (tachogram → 4 Hz → detrend → 5-min peak-pick, median) or the EDR route in
   `RespiratorySignalExtractor.kt` when ECG/ACC streams exist.

Validation strategy: replicate the Kotlin numbers against neurokit2
(`hrv_frequency.py`) and RRational (Kubios-aligned) on shared test RR series.
