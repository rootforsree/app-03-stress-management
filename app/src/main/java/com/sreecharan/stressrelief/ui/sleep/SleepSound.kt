package com.sreecharan.stressrelief.ui.sleep

import androidx.annotation.StringRes
import com.sreecharan.stressrelief.R
import com.sreecharan.stressrelief.ui.sleep.audio.SynthMath

/**
 * Sound layers for the sleep mixer. Free layers are synthesized tones from
 * [SynthMath]; premium packs are extra soundscapes gated by the paywall.
 *
 * Ids double as the values persisted in SleepSessionEntity.soundsJson and as
 * [SynthMath.Layer] keys. Builder 2's data/catalog/SleepSounds.kt should use
 * these same ids for the base sounds.
 */
data class SleepSoundLayer(
    val id: String,
    @StringRes val nameRes: Int,
    @StringRes val descriptionRes: Int,
    val isPremium: Boolean,
)

object SleepSoundLayers {
    val FREE =
        listOf(
            SleepSoundLayer(
                id = SynthMath.Layer.RAIN,
                nameRes = R.string.sleep_sound_rain,
                descriptionRes = R.string.sleep_sound_rain_desc,
                isPremium = false,
            ),
            SleepSoundLayer(
                id = SynthMath.Layer.OCEAN,
                nameRes = R.string.sleep_sound_ocean,
                descriptionRes = R.string.sleep_sound_ocean_desc,
                isPremium = false,
            ),
            SleepSoundLayer(
                id = SynthMath.Layer.WHITE,
                nameRes = R.string.sleep_sound_white,
                descriptionRes = R.string.sleep_sound_white_desc,
                isPremium = false,
            ),
            SleepSoundLayer(
                id = SynthMath.Layer.BROWN,
                nameRes = R.string.sleep_sound_brown,
                descriptionRes = R.string.sleep_sound_brown_desc,
                isPremium = false,
            ),
            SleepSoundLayer(
                id = SynthMath.Layer.CHIME,
                nameRes = R.string.sleep_sound_chime,
                descriptionRes = R.string.sleep_sound_chime_desc,
                isPremium = false,
            ),
        )

    val PREMIUM =
        listOf(
            SleepSoundLayer(
                id = SynthMath.Layer.FOREST,
                nameRes = R.string.sleep_sound_forest,
                descriptionRes = R.string.sleep_sound_forest_desc,
                isPremium = true,
            ),
            SleepSoundLayer(
                id = SynthMath.Layer.THUNDERSTORM,
                nameRes = R.string.sleep_sound_thunderstorm,
                descriptionRes = R.string.sleep_sound_thunderstorm_desc,
                isPremium = true,
            ),
            SleepSoundLayer(
                id = SynthMath.Layer.FIREPLACE,
                nameRes = R.string.sleep_sound_fireplace,
                descriptionRes = R.string.sleep_sound_fireplace_desc,
                isPremium = true,
            ),
        )

    val ALL: List<SleepSoundLayer> = FREE + PREMIUM

    fun byId(id: String): SleepSoundLayer? = ALL.find { it.id == id }
}
