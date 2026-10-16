package com.sreecharan.stressrelief.data.catalog

/**
 * Generated tones (no binary assets; Builder 5 synthesizes with AudioTrack).
 */
data class SleepSound(
    val id: String,
    val name: String,
    val description: String,
)

val sleepSounds: List<SleepSound> =
    listOf(
        SleepSound("rain", "Rain", "Soft filtered noise, like steady rainfall."),
        SleepSound("ocean", "Ocean", "Slow rolling waves on a low-frequency swell."),
        SleepSound("white-noise", "White Noise", "Even static that masks sudden sounds."),
        SleepSound("brown-noise", "Brown Noise", "Deep, low rumble that fades into the background."),
        SleepSound("chime-pads", "Gentle Chime Pads", "Slow soft chimes drifting over silence."),
    )
