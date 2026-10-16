# HR Baseline / Trend / Anomaly Algorithms + Health Connect Data Availability
Research date: 2026-10-16. For app-03 (stress app, Kotlin, Health Connect). Research only, no code.

---

## PART A: Open-source repos with real algorithm code

### 1. th0rgal/open_health (illness detection / "Symptom Radar")
- URL: https://github.com/th0rgal/open_health
- Stars: 31, Forks: 22, License: MIT, Last commit: ~2026-10-08 (docs updated 8 days before research)
- Language: Swift iOS app + Rust; Android-side decompiled Java in docs; the docs are the algorithm reference
- Algorithm: Oura on-device illness-detection model I/O. 30-day nightly time series (sleep avg HR, lowest HR, avg HRV RMSSD, respiratory rate, skin-temp deviation). Per-biomarker one-sided z-scores:
  - windowed baseline = nanmean/nanstd over days 5-29 for avg HR (1.457 sigma), avg HRV (1.11 sigma, one-sided low), temperature deviation (1.00 sigma vs long-term temp std)
  - long-term baseline (resting_hr_avg / resting_hr_dev) for lowest HR (1.30 sigma, one-sided high)
  - missing days encoded as NaN and skipped by nanmean/nanstd; hard requirement of at most 7 missing of last 14 nights
  - calibrated 0-1 illness score with decision thresholds 0.78 (minor) / 0.85 (major)
- Math file paths:
  - docs/algorithms/illness-detection.md (complete I/O reference, thresholds, windows)
  - data/utils/SymptomRadarStatus.java, data/utils/g.java (status mapping)
  - SymptomRadarPytorchLiteModel.java (model input builder, decompiled)
- Notes: baselines are explicitly long-term personal, not population. Best direct template for "resting HR baseline + z-score anomaly" in a stress app.

### 2. al3rez/symptom-radar-ultrahuman-cloudflare (TS port of anshdhawann/symptom-radar-ultrahuman)
- URL: https://github.com/al3rez/symptom-radar-ultrahuman-cloudflare (original Python: https://github.com/anshdhawann/symptom-radar-ultrahuman)
- Stars: 0, License: MIT, Last commit: ~2026-07-07
- Language: TypeScript (Cloudflare Worker); original is Python
- Algorithm (TemPredict-study inspired strain detection): each metric is a z-score against a 21-day exponentially-weighted rolling baseline, blended with a 3-day trend (whichever is worse), then weighted sum:
  - Resting HR 0.25 (elevated = strain), Sleep HRV 0.25 (depressed = strain), skin-temp deviation 0.35, recovery-index modifier up to 0.40
  - levels: no signs < 1.5 sigma, minor 1.5-3.0 sigma, major >= 3.0 sigma
  - validated byte-for-byte against the Python original on live API data (756/756 cells) plus 1000+ synthetic histories
- Math file paths:
  - src/strain.ts (z-score strain detection, the math to port)
  - src/extract.ts (daily metric extraction)
  - test/ (vitest unit + integration tests, incl. synthetic fuzzing)
  - original Python: symptom_radar.py in anshdhawann/symptom-radar-ultrahuman
- Notes: cleanest portable implementation of the rolling-baseline + z-score pattern; the 21-day EWMA baseline and 3-day trend blend map directly onto the requested features.

### 3. williamscli/hrv_trainer (morning readiness via robust baseline)
- URL: https://github.com/williamscli/hrv_trainer
- Stars: 0, License: MIT, Last commit: ~2026-01-05 (README crawl)
- Language: JavaScript web app (index.html, genysys_premvp_app.jsx)
- Algorithm: morning HRV z-score against rolling baseline using median + MAD (median absolute deviation, robust to outliers). Decision bands:
  - z below -1.0 -> REST, |z| below 1.0 -> BUILD, z above +1.0 -> PERFORM
  - outcome loop: "crashed" if next morning's z below -1.0; tracks compliance vs outcome over 60 days
- Math file paths: index.html and genysys_premvp_app.jsx (z-score + command logic inline)
- Notes: simple, self-contained example of a readiness decision from a single z-score with robust baseline stats. Good pattern for a first-version morning score.

### 4. chrissharp80/emuqu (overnight recovery, sleep-anchored HRV)
- URL: https://github.com/chrissharp80/emuqu
- Stars: 1, License: NOASSERTION (non-standard, check before reusing code), Last commit: 9fb2ea1 (~Oct 2026)
- Language: Swift 6 (iOS/watchOS)
- Algorithm: raw beat-to-beat RR intervals -> artifact detection -> window selection inside 30-70% of measured sleep -> RMSSD, lnRMSSD, SDNN, DFA alpha-1 -> z-score vs personal baseline (up to 60 nights) with anti-ratchet baseline admission (admission test ignores whether a reading is above/below the mean, so atypical nights cannot ratchet the baseline). Recovery score = HRV 60% / sleep 25% / vitals 15%, versioned (v3.2.oct2026), frozen once accepted.
- Math file paths:
  - docs/whitepapers/01-overnight-recovery-measurement.md (full spec: source assembly, window search, anti-ratchet baseline)
  - Emuqu/Sources/Analysis/ (HRVAnalysisPipeline: RMSSD, SDNN, LF/HF, DFA alpha-1; RecoveryScoreCalculator)
  - docs/hrv-reference-data.md (PhysioNet validation of the time-domain arithmetic)
- Notes: the most rigorous overnight-HRV methodology found; the sleep-anchored 30-70% window and anti-ratchet baseline are directly reusable ideas for a morning score. Requires raw RR intervals, which Health Connect does NOT expose (see Part B), so the window-selection part only applies if we compute RMSSD from another source.

### 5. vedant251105/heartrate-anomaly-detection-esp32 (personal z-score features)
- URL: https://github.com/vedant251105/heartrate-anomaly-detection-esp32
- Stars: 0, License: MIT, Last commit: 1ad33cf (~Apr 2026)
- Language: Python (training) + Arduino/ESP32 (inference)
- Algorithm: 8 features incl. HR z-score = (HR - personal_mean)/personal_std, rolling-window HR std, RMSSD, HR delta per beat, activity-HR ratio. TinyML autoencoder (4.7 KB); anomaly threshold = mean + 3*std of reconstruction MSE from per-user 90-sec calibration.
- Math file paths:
  - step2_feature_extraction.py (feature extraction incl. z-score and RMSSD)
  - UserProfile.h (adaptive per-user baseline on device)
  - HeartRateAI.ino (inference + threshold)
- Notes: illustrates training on z-scores (person-agnostic) and per-user calibration thresholds; activity-context gating of HR is a useful pattern.

### 6. akshatksingh18/whoop (readiness composite, Dart)
- URL: https://github.com/akshatksingh18/whoop
- Stars: 1, License: MIT, Last commit: ~2026-10-09 (1,541 commits)
- Language: Dart (Flutter) + Kotlin/Swift platform
- Algorithm: readiness composite 0-100 from overnight signals vs 28-night personal baseline (each input needs >= 14 of 28 nights; missing inputs are dropped and weights rescaled). Weights: HRV 40%, resting HR 30%, breathing rate 20%, skin temp 10%. Score 50 at baseline, about 88 at +2 sigma better than usual. No sleep-duration input in this implementation (pure cardiac signal score).
- Math file paths:
  - packages/analytics/.../wellness/readiness_composite.dart
  - build-81-audit.md (weights, baseline rules, interpretation notes)
- Notes: practical template for a weighted composite score with graceful missing-input degradation (never score missing as zero).

### Gap: HR trend slope
No repo above ships a dedicated linear trend-slope routine; two cover trend implicitly (strain.ts blends a 3-day trend with the daily z-score; open_health compares today vs the days-5-29 window mean). For an HR trend slope, the standard move is ordinary least squares on the trailing 7 or 14 nightly values (slope in bpm/day, plus R^2 for confidence). That is a ~15-line function to write directly; no third-party reference implementation needed.

---

## PART B: Health Connect record types for HR / HRV / sleep (AndroidX client, as of 2026)

androidx.health.connect:connect-client 1.1.0 is the current stable (alpha11 seen in active projects). Platform side: Health Connect ships natively on Android 14+ (API 34), as a Play Store module on API 28-33.

### Read table

| Need | Record type | Shape | Permissions (READ_*) | Reality check |
|---|---|---|---|---|
| Continuous HR | HeartRateRecord | Interval, list of Sample(time, beatsPerMinute) | READ_HEART_RATE | Widely available; sync is delayed/periodic, not live. Good source for deriving overnight resting HR and daily trend slopes. |
| Resting HR | RestingHeartRateRecord | InstantRecord, beatsPerMinute (Long) | READ_RESTING_HEART_RATE | Exists since 1.0.0-alpha08 platform API 34. Often NOT written: Samsung Health is repeatedly reported not to write it to Health Connect (Samsung dev forum). Design a derived fallback: overnight samples in the sleep window (or 00:00-06:00) -> average of the lowest 20% plausible readings, needs >= 10 samples, flag as estimate. |
| HRV | HeartRateVariabilityRmssdRecord | InstantRecord, heartRateVariabilityMillis (Double, ms) | READ_HEART_RATE_VARIABILITY | Exists since connect-client 1.0.0-alpha08 (Dec 2022), platform API 34. Almost no consumer devices write it. Pixel Watch is the notable writer; third-party apps can write it but few do. Treat as opportunistic: request the permission, never depend on it. |
| Raw RR / inter-beat intervals | NONE | n/a | n/a | No Health Connect record type exposes raw RR or IBI. Only Samsung Privileged Health SDK (Galaxy Watch4+, requires Samsung Partner Program acceptance) gives beat-to-beat data. Cannot compute our own RMSSD from phone-side HC data; use HeartRateVariabilityRmssdRecord if present. |
| Sleep | SleepSessionRecord | Interval, optional stages list | READ_SLEEP | Stages are optional and not always attached. Code a no-stages fallback. |

### Permission / data gotchas
1. Two extra permissions matter more than the record ones:
   - READ_HEALTH_DATA_IN_BACKGROUND: without it, background/worker reads silently return zero records. Required for any WorkManager sync.
   - READ_HEALTH_DATA_HISTORY: without it, reads are capped at 30 days before the permission grant. A 28-day baseline cannot be built on a fresh install without this.
2. Permissions are per record type; any single read can throw SecurityException. Wrap reads individually (safeRead pattern) so one declined type cannot abort the sync.
3. Missing data must never be scored as zero (missing != zero). Follow the repos above: exclude missing inputs from denominators, lower confidence instead.
4. Heart-rate permissions are sensitive: on Play-distributed builds they need the Play Console health-permissions declaration/approval; also Health Connect itself is now opt-in for users on some builds (separate Health Connect module must be installed on API < 34).
5. RMSSD values from Health Connect carry no method label; treat HRV as baseline-relative only (same user, same device), never as an absolute number.
6. Kotlin integration examples (HC read/write in Kotlin, RHR + RMSSD mapping): senswear docs/HEALTH_CONNECT.md (https://github.com/krtvysinghh/senswear), vitalcoreai docs/CAPABILITY_MATRIX.md with the derived-RHR fallback (https://github.com/shauurrya/vitalcoreai), hangry HEALTH_CONNECT.md (https://github.com/sqrlwallet/hangry), dbxwearables androidHealthConnect README (https://github.com/mkgs-databricks-demos/dbxwearables).

### Suggested algorithm stack for the stress app (from the above)
- Resting HR baseline: 28-day rolling mean/std of nightly RHR (device-written RestingHeartRateRecord, or derived fallback). Min 14 of 28 nights before scoring.
- Anomaly: one-sided z-scores (elevated RHR 1.3-1.5 sigma flags, depressed RMSSD ~1.1 sigma flags) following open_health thresholds; daily strain sum with 1.5/3.0 sigma levels following symptom-radar weights.
- Morning score: weighted composite (HRV 40 / RHR 30 / sleep 25 / vitals) per akshatksingh18/whoop + emuqu, 0-100 centered 50 at baseline; drop missing inputs and rescale weights.
- Trend: least-squares slope on trailing 7 and 14 nightly RHR values; show bpm/week.
- All scoring on personal baselines, frozen/dated with an algorithm version; store per-day rows so history never gets reinterpreted under new weights.
