# Design Direction: Stress Management App (App 03)

**Status:** Research-complete. Awaiting Charan's pick before builders start.
**Date:** 2026-10-15
**Charan's mandate:** COLORFUL, vibrant, Android-native. NOT Apple HIG, NOT iPhone-style, NOT muted/minimal. The opposite of the monochrome aesthetic.

---

## The Verdict

**Recommended direction: "Aurora Calm"** — a vibrant dawn-gradient system (teal → cyan → violet → pink) built on Material 3 Expressive.

Why this one wins:

1. It is the only direction that satisfies BOTH of Charan's demands at once: maximal color vibrancy AND genuine stress relief. The color psychology research is decisive: blues, turquoises, greens, and soft pinks are the colors that actually calm people down. A 26,596-person study across 100+ countries (University of Sussex / G.F Smith, led by Prof. Anna Franklin) found **navy blue the most relaxing color**, with turquoise and pastel pink close behind. Blue stimulates the parasympathetic nervous system and lowers heart rate and blood pressure. Green is the most restful color for the human eye and the most balancing of all colors. Pink has a documented tranquilizing effect that suppresses anxious and hostile behavior within minutes.
2. Red, orange, and hot yellow increase arousal, heart rate, and anxiety. They read as "emergency." A stress app whose primary palette screams urgency is a product contradiction. The playful candy/sunset directions look fun in screenshots but fight the app's job.
3. Aurora Calm is still loud. Teal, cyan, violet, and magenta gradients are as far from "brown minimal" as you can get while staying on the calming side of the psychology.

**Do not** build the primary system around red/orange/yellow. Use them only as tiny celebration accents (streak confetti, achievement pops), never as structural color.

---

## 1. Recommended Palette: "Aurora Calm"

Built as a full Material 3 color scheme (light + dark). These hex values are designer-chosen and mapped to M3 roles, so builders can drop them straight into a `ColorScheme`. Seed hues: teal (primary), violet (secondary), pink (tertiary). Hero gradient: dawn.

### Light scheme

| Role | Hex | Usage |
|---|---|---|
| `primary` | `#00838F` | Vibrant teal. FABs, key CTAs, active states |
| `onPrimary` | `#FFFFFF` | Text/icons on primary |
| `primaryContainer` | `#A6F3F5` | Light aqua. Selected chips, highlighted cards |
| `onPrimaryContainer` | `#002021` | Text on primary container |
| `secondary` | `#5B5BD6` | Expressive violet. Filters, toggles, secondary buttons |
| `onSecondary` | `#FFFFFF` | Text/icons on secondary |
| `secondaryContainer` | `#E3E0FF` | Light lavender. Category cards, mood tiles |
| `onSecondaryContainer` | `#17144D` | Text on secondary container |
| `tertiary` | `#C2185B` | Warm pink. Badges, warmth accents, streak highlights |
| `onTertiary` | `#FFFFFF` | Text/icons on tertiary |
| `tertiaryContainer` | `#FFD9E3` | Soft pink. Celebration surfaces, affirmation cards |
| `onTertiaryContainer` | `#3F001D` | Text on tertiary container |
| `background` | `#F7FDFD` | App background, faint aqua tint |
| `surface` | `#F7FDFD` | Cards base |
| `surfaceVariant` | `#E4F2F2` | Dividers, subtle wells |
| `surfaceContainerHigh` | `#EAF6F6` | Elevated cards |
| `onSurface` | `#171D1E` | Primary text |
| `onSurfaceVariant` | `#3F494B` | Secondary text |
| `outline` | `#6F797B` | Borders, inactive outlines |
| `error` | `#BA1A1A` | Errors only, never decorative |

### Dark scheme

Dark mode is NOT an inverted light mode. It is a "night aurora": deep tinted surfaces where the same hues glow brighter. M3 tonal logic: accents move to tone 80 (lighter, slightly desaturated so they don't vibrate on dark). Never pure black — it kills gradient depth and reads cheap.

| Role | Hex | Usage |
|---|---|---|
| `primary` | `#4DD0E1` | Glowing teal. CTAs, active states |
| `onPrimary` | `#00363D` | Text/icons on primary |
| `primaryContainer` | `#004F58` | Deep teal. Selected chips, highlighted cards |
| `onPrimaryContainer` | `#A6F3F5` | Text on primary container |
| `secondary` | `#C2C3FF` | Soft glowing violet |
| `onSecondary` | `#2E2E6E` | Text/icons on secondary |
| `secondaryContainer` | `#43437A` | Deep violet. Category cards |
| `onSecondaryContainer` | `#E3E0FF` | Text on secondary container |
| `tertiary` | `#FFB1C8` | Glowing pink. Badges, streaks |
| `onTertiary` | `#65002E` | Text/icons on tertiary |
| `tertiaryContainer` | `#7A2950` | Deep rose. Celebration surfaces |
| `onTertiaryContainer` | `#FFD9E3` | Text on tertiary container |
| `background` | `#0E1516` | App background, deep teal-black |
| `surface` | `#0E1516` | Cards base |
| `surfaceVariant` | `#1E2A2B` | Dividers, wells |
| `surfaceContainerHigh` | `#263434` | Elevated cards |
| `onSurface` | `#DDE4E4` | Primary text |
| `onSurfaceVariant` | `#B0BCBD` | Secondary text |
| `outline` | `#8A9698` | Borders |

### Signature gradients (the "colorful" the user asked for)

Gradients are the hero move. Flat M3 roles handle structure; gradients deliver the wow. Use them on: the breathing exercise orb, hero cards, the mood check-in header, streak celebrations, and the app icon.

- **Dawn gradient (light mode):** `#2DD4BF` (teal) → `#22D3EE` (cyan) → `#A78BFA` (violet) → `#F9A8D4` (pink)
- **Night aurora (dark mode):** `#0D9488` → `#0891B2` → `#7C6CF0` → `#E0558A` — jewel tones that glow against the dark surface
- **Calm wash (backgrounds):** the dawn gradient at 12–18% opacity over the surface color. Gives every screen a tint of color without hurting readability.
- Rule: gradients never sit behind body text. Text always sits on a solid scrim or a surface container. Contrast first, beauty second.

### Dynamic color stance

Material You dynamic theming (palette extracted from the user's wallpaper) is supported by M3 Expressive, but **the branded Aurora scheme is the default**. Brand consistency wins for a business app; dynamic color becomes a Settings toggle ("Match my wallpaper") for users who want it. Never let dynamic color override the signature gradients.

---

## 2. Alternate Directions (if Charan rejects Aurora)

### Alternate A: "Deep Ocean" — premium calm

For a more premium, less playful feel. Leans on the single strongest research finding: navy is the most relaxing color known.

- `primary` light `#1B4D8F` / dark `#7FB3FF` (deep ocean blue)
- `secondary` light `#00838F` / dark `#4DD0E1` (turquoise)
- `tertiary` light `#2E7D5B` / dark `#7FD6A8` (sea green)
- Hero gradient: `#0A2463` → `#1B4D8F` → `#00838F` → `#4DD0E1` (deep sea to shallow turquoise)
- Mood: premium, trustworthy, slightly masculine. Less playful than Aurora. Think Oura-ring aesthetic with real color.

### Alternate B: "Sunset Playground" — maximum play

The loudest option. Coral, amber, magenta. Finch-meets-candy-store energy.

- `primary` light `#E5484D` / dark `#FF8A80` (coral)
- `secondary` light `#F5A623` / dark `#FFD54F` (amber)
- `tertiary` light `#C026D3` / dark `#F0ABFC` (magenta)
- Hero gradient: `#FF6B6B` → `#FFA94D` → `#F72585` → `#B5179E`
- **Warning:** warm hues raise arousal. This direction is the most fun and the least calming. Only pick it if Charan values playfulness over the stress-relief psychology. Keep red/orange out of the breathing and sleep flows even here; confine them to streaks, celebrations, and social features.

---

## 3. Typography

M3 Expressive's type guidance: emphasize hierarchy with strong contrast between headers and body, and use emphasized styles to guide attention. For a joyful, premium, non-iPhone feel:

- **Display / headlines: Nunito ExtraBold / Black.** Rounded, warm, friendly letterforms. This single choice does more than anything else to kill the cold iPhone look. Duolingo proved rounded type reads as joyful without reading as childish.
- **Body / UI text: Nunito Sans Regular / SemiBold** (same family, keeps the system cohesive) or Inter if tighter data-dense screens need it.
- **Scale:** oversized hero numerals for the signature stats (streak count, calm minutes) — M3 Expressive encourages strategic size. `displayLarge` (57sp) for the breathing timer, `headlineMedium` for screen titles, generous line height on body (1.5x).
- Load via Google Fonts (downloadable fonts) to keep APK size down. One family, multiple weights — not three different families.

---

## 4. Shape Language

M3 Expressive ships a 35-shape library with shape-morph animation, and its official tactic #1 is "use a variety of shapes — mix round and square, use corner-radius contrast to create focus."

- **Hero moments:** oversized radii, 28–32dp. Breathing orb is a perfect circle that morphs (circle → soft blob → circle) with the breath cycle. Mood check-in cards: 28dp.
- **Interactive elements:** buttons 16–20dp full-round. Chips 12dp. Cards 20–24dp.
- **Contrast rule:** the single most important shape decision. Make the primary CTA a *different* shape family than the surrounding cards (e.g., pill button on square-ish cards) so the eye lands on it. Google's research: expressive shape contrast is part of why key elements get found up to 4x faster.
- **Organic accents:** soft blob dividers between sections, arched header backgrounds (the dawn gradient in an arch, not a rectangle). This is the anti-iPhone move — iOS is all rectangles with 12dp corners.
- **Never:** uniform 8dp rounding everywhere. That is the "boring" the M3 Expressive research rebelled against.

---

## 5. Motion Principles

M3 Expressive's motion system is physics-based: spatial and effects springs, not eased tweens. For a stress app, the springs must feel like *breathing*, not like a game.

- **Spring tuning (Compose):** `spring(stiffness = 250f, dampingRatio = 0.85f)` as the default. Gentle settle, no harsh bounce. Reserve bouncier springs (`dampingRatio = 0.6f`) for celebrations only (streak milestones, achievements).
- **The signature hero moment:** the breathing exercise. Orb scales 1.0x → 1.35x over 4s (inhale), holds, releases over 4s, gradient crossfades teal → violet → pink with the phase. Haptic tick on phase change. This one screen must feel incredible — it is the app's identity.
- **Entrance:** staggered list/card entry (30–50ms stagger, fade + 16dp rise). Screens feel alive without feeling busy.
- **State morphs:** shape morph on the mood selector (selected mood blob grows and rounds), progress rings that fill with the gradient, not a flat color.
- **Micro-interactions:** button press = subtle scale (0.97) + spring back. Toggle = thumb slides with a spring and the track floods with gradient. Every interactive element acknowledges touch physically.
- **Haptics:** pair motion with light haptics on key moments (breath phase change, streak complete, milestone unlock). M3 Expressive treats haptics as part of motion.
- **Restraint rule:** motion everywhere, hero motion in few places. Google's tactic #7: "combine tactics into a limited number of hero moments." The breathing orb and the streak celebration are the heroes. Everything else supports.

---

## 6. Dark Mode Strategy (summary)

1. Separate designed scheme, not an inversion. Deep teal-black `#0E1516` surfaces, tone-80 glowing accents.
2. Gradients get *richer* at night (jewel tones), never muted. Dark mode must feel like the premium version, not the sad version.
3. Saturated light-mode colors never go directly on dark backgrounds — they vibrate. Always step accents up to their tone-80 equivalents.
4. Illustrations: single asset sets drawn with translucent fills and dynamic tints so they work on both themes. No asset duplication.
5. Follow system theme by default; offer manual override in Settings (Light / Dark / System).
6. WCAG AA contrast on all text, checked in both schemes. Vibrant ≠ unreadable.

---

## 7. What the Top Colorful Wellness Apps Teach Us

- **Finch:** pastel world + virtual companion. Lesson: a *character or living element* (the birb) makes self-care feel like nurturing, not homework. Our breathing orb can play this role — a living gradient creature, not a clinical timer.
- **Headspace:** playful flat illustrations, simple colorful UI, short structured content. Lesson: illustration style IS the brand. Commission or generate a consistent illustration set (soft shapes, dawn palette, rounded characters) and use it everywhere: empty states, onboarding, milestones.
- **Calm:** sunset visualizations, celebrity-narrated sleep stories. Lesson: full-bleed immersive color moments (their sunset gradients) are the premium signal. Our equivalent: the breathing screen and a "wind down" evening gradient mode.
- Common thread: none of them look like iOS Settings. They all commit to a world — a palette, a character language, an illustration style. We commit to the aurora world.

---

## 8. Builder Handoff Notes

- Base theme on `androidx.compose.material3` with a hand-built `lightColorScheme` / `darkColorScheme` from the tables above. Do NOT rely on dynamic color as the default.
- Define the gradients as `Brush` objects in the theme (`AuroraBrushes.dawn`, `.nightAurora`, `.calmWash`) so every screen uses identical stops.
- Typography: downloadable-font Nunito via Google Fonts; wire into M3 `Typography` with emphasized display styles.
- Shapes: define an expressive shape set (hero 28dp, card 22dp, button pill, chip 12dp) in the theme; use shape-morph (`animate` corner sizes) on the mood selector and breathing orb.
- Motion: centralize spring specs in one `MotionTokens` object. Breathing screen gets its own choreography.
- Accessibility gates before ship: contrast ratios in both schemes, reduced-motion setting respected (springs → instant or fade when the OS requests reduced motion), minimum 48dp touch targets, content descriptions on all illustrative elements.

---

## Sources

- Material 3 Expressive official blog and research (46 studies, 18,000+ participants; expressive UIs preferred across ages; key elements found up to 4x faster) — m3.material.io/blog/building-with-m3-expressive; Android Developers M3 in Compose docs; Google I/O 2025 coverage (Android Central, SiliconANGLE, Android Authority)
- Color psychology: University of Sussex / G.F Smith global color study (26,596 people, 100+ countries; navy most relaxing; turquoise and pink also top); Journal of Environmental Psychology (blue lowers stress, stimulates parasympathetic response); chromotherapy literature (blue/pink stress reduction in clinical settings); UBC research on blue environments and creative output
- App references: Finch (virtual-pet gamified self-care), Headspace (playful illustration-led mindfulness), Calm (immersive sunset visualizations)
- Dark mode practice: M3 tonal dark schemes (tone-80 accents), off-black surfaces, WCAG contrast discipline
