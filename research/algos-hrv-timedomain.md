# HRV Time-Domain Algorithm Implementations on GitHub

Research date: 2026-10-16. Goal: open-source reference implementations of time-domain HRV metrics (RMSSD, SDNN, pNN50, NN50, AVNN, Baevsky stress index SI, HRV triangular index, TINN) for an on-device Android (Kotlin) stress app reading Health Connect data. Research only, no code copied.

Note: AVNN (average NN) = MeanNN in every library below; all of them compute it.

---

## Tier 1 - Best math references (Python, permissive or well-documented)

### 1. NeuroKit2 - neuropsychology/NeuroKit
- URL: https://github.com/neuropsychology/NeuroKit
- Stars: ~2,346 | License: MIT | Status: ACTIVE (commits into 2026; peer-reviewed, 94+ publications)
- Implements: MeanNN (=AVNN), SDNN, SDANN1/2/5, SDNNI1/2/5, RMSSD, SDSD, CVNN, CVSD, MedianNN, MadNN, IQRNN, pNN50, pNN20, MinNN, MaxNN, **TINN**, **HTI (HRV triangular index)**
- Does NOT implement: Baevsky stress index
- Math lives in: `neurokit2/hrv/hrv_time.py` (function `hrv_time()`; TINN via `_hrv_TINN()` least-squares triangle fit in same file)
- Notes: cleanest single-file reference for the full time-domain + geometric set. HTI = len(rri)/max(histogram); TINN bins at 7.8125 ms (1/128 s). pNN50 denominator is len(diff)+1.

### 2. strikerdlm/HRV (Mission Control - Flight Surgeon)
- URL: https://github.com/strikerdlm/HRV
- Stars: ~1 | License: MIT | Status: ACTIVE (v1.43.0, heavy 2026 development)
- Implements: SDNN, RMSSD, pNN50, LnRMSSD, CVI/CSI, Poincaré SD1/SD2, **HRV Triangular Index**, **TINN**, **Baevsky Stress Index** (`compute_baevsky_stress_index`), parasympathetic index
- Math lives in: `app/hrv_core.py` (`compute_geometric_metrics()` covers triangular index + TINN; `compute_baevsky_stress_index()`)
- Notes: only repo found that has BOTH the geometric pair (triangular index, TINN) AND Baevsky SI in one file. Large app around a small core; the core is what matters.

### 3. Aura-healthcare/hrv-analysis (canonical upstream)
- URL: https://github.com/Aura-healthcare/hrv-analysis
- License: GPL-3.0 (copyleft - check with legal before shipping in a commercial app) | Status: STALE upstream (v1.0.4 era); several 0-star 2025-2026 forks (samehaisaa, martinlugh, flaviomartins) are content mirrors, not improvements
- Implements: Mean_NNI (=AVNN), SDNN, SDSD, NN50, pNN50, NN20, pNN20, RMSSD, Median_NN, Range_NN, CVSD, CV_NNI, **Triangular_index**, **TINN**, plus ectopic-beat/outlier preprocessing (Malik/Kamath methods)
- Math lives in: `hrvanalysis/` package - `get_time_domain_features()`, `get_geometrical_features()` (triangular index, TINN), `remove_outliers()`, `remove_ectopic_beats()`
- Notes: valuable for its preprocessing pipeline (outlier/ectopic handling), which matters a lot for noisy Health Connect data. GPL-3.0 is the price.

### 4. PGomes92/pyhrv
- URL: https://github.com/PGomes92/pyhrv
- Status: semi-active (v0.5.0 in development badge, 2025 commits); widely forked (flaviomartins, aviatoryan, neuraptica, narayanschuetz mirrors)
- Implements: SDNN, SDNNindex, SDANN, RMSSD, SDSD, NN50/pNN50, NN20/pNN20, NNx/pNNx, **triangular_index()**, **tinn()**, Poincaré SD1/SD2, plus Welch/AR/Lomb frequency domain
- Math lives in: `pyhrv/time_domain.py` (`pyhrv.time_domain.rmssd()`, `.sdnn()`, `.nn50()`, `.triangular_index()`, `.tinn()`); helpers in `pyhrv/tools.py`, `pyhrv/utils.py`
- Notes: 78 metrics, education-oriented code that is easy to read and port. Known open issue: TINN can return misleading results (PGomes92/pyhrv#5) - cross-check any TINN port against NeuroKit2.

## Tier 2 - Kotlin / Android (directly portable to the app)

### 5. dermatoid-collab/hrv-rm - BEST Kotlin reference for Baevsky SI
- URL: https://github.com/dermatoid-collab/hrv-rm (Baevsky commit: /commit/543063a7dfc2576451dbf462a4e0f194c95b0bff)
- Stars: ~1 | Status: ACTIVE (Baevsky SI added 2026-09-15)
- Implements: RMSSD, SDNN, pNN50, Mean RR (=AVNN), Poincaré SD1 (=RMSSD/sqrt2), SD2 (=sqrt(2*SDNN^2 - SD1^2)), **Baevsky Stress Index**
- Math lives in: `app/src/main/java/com/hrvrm/app/hrv/HrvMetrics.kt` - `object HrvMetricsCalculator`, private fun `stressIndex()`:
  50 ms histogram bins on clean IBI series, Mo = modal bin center in seconds, AMo = % of beats in modal bin, MxDMn = (max-min) RR in seconds, SI = AMo / (2 * Mo * MxDMn)
- Notes: pure Kotlin, no native deps, Room-persisted, Compose UI. Closest thing to drop-in logic for our app. License not shown on repo page - verify before reusing.

### 6. jlamprou/HRVBioFeedback
- URL: https://github.com/jlamprou/HRVBioFeedback
- Stars: ~1 | License: MIT | Status: ACTIVE but young (created 2026-04-15, 7 commits)
- Implements: RMSSD, SDNN, pNN50, Poincaré SD1/SD2, DFA alpha1, Sample Entropy, AR-16 spectral (Burg), 26 synthetic validation tests
- Math lives in: DSP modules under `app/src/main/java/` (README describes a 12-module pure-Kotlin pipeline; exact file listing not verified)
- Notes: pure Kotlin, no native/C deps, Polar H10 BLE input. Good for validation-test patterns (synthetic RSA tests). Overkill for our needs (biofeedback-focused).

### 7. jason-arcadia/phonecamerahrv
- URL: https://github.com/jason-arcadia/phonecamerahrv
- Status: active-ish 2026 | Kotlin Multiplatform (shared Kotlin usable on Android)
- Implements: RMSSD from PPG valley detection; stress/energy/recovery scoring vs personal baseline
- Math lives in: `composeApp/commonMain/.../PPGprocessor.kt` (bandpass, valley detection, RMSSD), `HRVScores.kt` (stress/energy/recovery scoring)
- Notes: RMSSD computed in shared Kotlin - directly reusable. PPG-specific front end; the HRV math is the portable part.

## Tier 3 - Niche / partial

### 8. openstrap/analytics
- URL: https://github.com/openstrap/analytics
- Stars: ~11 | Status: ACTIVE (2026-06) | Language: Dart (Flutter-adjacent, easy to read)
- Implements: **Baevsky Stress Index** (`stress_si`, windowed-median transparent-histogram variant), RMSSD-based sleep staging
- Math lives in: `lib/src/onehz/clinical/stress_si.dart` (was `lib/src/hrv.dart`)
- Notes: Dart reads like pseudocode; useful second opinion on Baevsky SI variants (windowed-median vs single-window).

### 9. swluciferase/rppg-webapp
- URL: https://github.com/swluciferase/rppg-webapp
- Implements: Baevsky SI ("SI pressure index", >=10 beats), RMSSD, LF/HF from camera rPPG
- Notes: web/JS app, not a library; only useful to sanity-check Baevsky SI interpretation bands.

### 10. samantha-chan/e4_data_recording
- URL: https://github.com/samantha-chan/e4_data_recording
- Status: DEAD (last activity ~2022)
- Implements: SDNN, RMSSD on-device in Java (Android, Empatica E4 IBI stream, 60-sample rolling window)
- Math lives in: `MainActivity.java` (HRV calc inline)
- Notes: shows the rolling-window on-device pattern, but code is old and app-specific.

## Baevsky Stress Index formula (consensus across sources)
SI = AMo / (2 * Mo * MxDMn), where the RR series is binned in 50 ms bins:
- Mo = center of the most populated bin (seconds)
- AMo = percentage of beats in that bin
- MxDMn = max RR - min RR (seconds)
Variants seen: using 3.92*SDNN instead of MxDMn for noise robustness (Beam AI paper, arxiv 2210.01791); windowed-median SI (openstrap). Expect small numeric differences between implementations due to binning/mode-estimation details.

## Recommendation for our app
Port from hrv-rm's `HrvMetrics.kt` for the Kotlin baseline (RMSSD/SDNN/pNN50/SD1/SD2/Baevsky SI) and cross-check the geometric pair (triangular index, TINN) against NeuroKit2's `hrv_time.py` when porting. Use hrv-analysis's ectopic-beat/outlier methods as the preprocessing reference (rewrite, don't copy - GPL-3.0).
