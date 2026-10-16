# Aura launcher icon — wiring instructions (for integrator)

Concept: a glowing breath-orb with expanding ripple rings (cyan→violet→pink) on
deep teal. It IS the app's signature breathing orb. Outside the box, reflects
the core loop (guided breathing) and the "Aura" name.

## Files
- `ic_launcher_foreground.png` — 512px master (full-bleed art, bg baked in)
- `ic_launcher_{xxxhdpi,xxhdpi,xhdpi,hdpi,mdpi}.png` — legacy density PNGs
- Background color sampled from art: **#022C34**

## Wiring
1. Copy density PNGs → `app/src/main/res/mipmap-{d}/ic_launcher.png`
2. `res/mipmap-anydpi-v26/ic_launcher.xml`:
   <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
       <background android:drawable="@color/ic_launcher_background"/>
       <foreground android:drawable="@mipmap/ic_launcher"/>
   </adaptive-icon>
   (same for ic_launcher_round.xml)
3. `res/values/colors.xml`: `<color name="ic_launcher_background">#022C34</color>`
4. Manifest already references @mipmap/ic_launcher.
