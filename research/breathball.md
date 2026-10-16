# Breathball Research — for Aura (app-03-stress)

Charan said "checkout breathball". This is what it is.

## What Breath Ball is

**Breath Ball: Breathing & HRV** — a free breathing-exercise app with live HRV biofeedback.
- Developer: Fun Driven (Michael Holl), contact michael@breathball.com, site breathball.com
- Android package: `com.fundriven.breath_ball`
- Play Store: https://play.google.com/store/apps/details?id=com.fundriven.breath_ball
- iOS: also on the App Store (app id 1230172997)
- Rating: ~4.9 stars from ~6,800 ratings (93%+ are five-star)
- Downloads: 100K+ (third-party estimates 360K-500K total)
- Price: completely free. No ads, no in-app purchases, no sign-ups, no data collection. Anonymous use.
- Active since May 2017, latest version 7.7.2 (June 2026). Co-developed with therapists.

### How the app works

One signature animated ball. Inhale while it expands, exhale while it shrinks. Five built-in exercises:
1. **Cardiac coherence** — 5s inhale / 5s exhale, for deep relaxation and HRV training
2. **4-7-8 breathing** — for falling asleep
3. **Slow-paced breathing (4-6)** — for anxiety and panic
4. **Box breathing (4-4-4-4)** — for focus and resilience
5. **COPD breathing** — pursed-lip technique for breathlessness

Power features:
- **Custom exercise editor**: build your own rhythms (inhale / hold / exhale / hold in seconds), add smooth transitions between rhythms, insert pauses. Rearrange with drag and drop.
- **Pace slider during a session**: speed up or slow down the rhythm mid-exercise.
- **Bluetooth HR monitor pairing**: live biofeedback while breathing. Real-time graphs of cardiac coherence, RMSSD, SDNN, pNN50, LF/HF ratios, and a Poincaré plot.
- **Health Connect write-back**: writes RMSSD, average heart rate, and breathing sessions to Health Connect.
- **Diary + mood tracker**: bar chart of daily exercise against a daily goal; see how consistent breathing changes calmness and HRV.
- **Screen-off / eyes-closed mode**: audio-only guides, so sessions work in bed or during meditation without looking at the phone.
- **Calming background sounds** that keep playing after the exercise ends.
- **Full visual customization**: own colors, brightness, accessibility settings for visually impaired users.

The developer's stated edge: the breathing animation curve is tuned from observations of hundreds of therapy clients, so the rhythm feels natural rather than mechanical. This is the most-cited reason in reviews for why it "just works."

### What users love (review themes)

- Works on the first session. "Even the first breathing session usually leads to great results" is the recurring claim.
- Stopped panic attacks in the moment (slow-paced 4-6 is the go-to).
- The custom editor: yoga and meditation coaches build their own protocols.
- Free with zero upsell pressure — trust builder, drives the 4.9 rating.
- Simplicity: one ball, no onboarding maze, no account.

### Note: same name, different things

- **Oxygen Care: Breath Ball** (`com.paychemist.oxygen_care`) is a separate, simpler 3-minute breathing-ball app. Not the one that matters.
- **Physical "breathing ball" toys**: expandable plastic sphere toys used in therapy, schools, and ADHD/autism support. Kids hold it at their belly, expand it on inhale, squeeze on exhale. Tactile version of the same technique. Aura could reference this as a companion concept but it is hardware, not software.

## 5 ideas Aura should steal

### 1. Custom breathing pattern builder (premium-tier feature)
Breath Ball's exercise editor is its power-user magnet. Aura ships 7 fixed patterns; add a "Create your own" screen: inhale / hold / exhale / hold durations, optional hold phases, smooth transition between two rhythms, timed or open-ended pause. Save as a personal pattern, reuse in programs.
Why: personalization is a natural premium gate, therapists and coaches (a word-of-mouth channel) love it, and it is a small build — it reuses the existing orb animation with new timings.

### 2. Eyes-closed / screen-off breathing mode
Breath Ball lets sessions run with the screen off using audio guides. Aura already has haptic phase cues — extend to a mode where the orb dims away and voice or tones carry the session. Critical for the Sleep and SOS goals Charan defined: nobody should stare at a bright screen mid-panic or in bed.
Why: closes a real gap in Aura's current design; directly serves the use cases that matter most.

### 3. Pace slider during a session
Breath Ball has an on-screen slider to stretch or compress the rhythm mid-exercise. Aura's fixed patterns (e.g. 4-7-8) are too long for beginners and too slow for some anxious users. Add a simple 0.5x–2x tempo control on the session screen.
Why: makes every pattern accessible on day one; reduces the "I can't keep up" drop-off that kills breathing apps.

### 4. Breathing diary with daily-goal bar chart
Breath Ball's diary shows minutes exercised per day against a daily goal, plus a mood tracker overlay. Aura has streak dots; add a minutes-per-day bar chart in the Breathe tab and correlate it with the stress score over time.
Why: visible progress is the retention engine Aura currently lacks; it turns breathing from a one-off tool into a habit with evidence.

### 5. Write breathing sessions back to Health Connect
Breath Ball writes RMSSD, average HR, and session records into Health Connect. Aura only reads. Writing back makes Aura a first-class citizen in the user's health graph (Samsung Health, Google Fit, and other apps can see the sessions), and it is cheap: one Health Connect write per completed session.
Why: differentiator against Headspace/Calm-style apps, and it strengthens Aura's Health Connect story beyond read-only.

## Bottom line

Breath Ball wins on: one perfect interaction (the ball), clinician-tuned rhythm, total customizability, biofeedback proof, and zero monetization friction. Aura should not copy the HRV graphs (needs Bluetooth sensors — keep that as a future premium idea), but the custom pattern builder, eyes-closed mode, pace slider, diary chart, and Health Connect write-back are all high-value, low-cost steals.
