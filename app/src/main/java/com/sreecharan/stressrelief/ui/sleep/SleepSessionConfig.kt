package com.sreecharan.stressrelief.ui.sleep

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carries the mixer configuration from SleepScreen to the active
 * wind-down session. SleepSessionRoute takes no nav args (contract), so the
 * handoff goes through this singleton, set by [SleepViewModel] on
 * "Begin wind-down" and read by [SleepSessionViewModel] on entry.
 */
@Singleton
class SleepSessionConfig
    @Inject
    constructor() {
        /** Layer id -> volume 0..1 for the layers enabled at session start. */
        @Volatile var layers: Map<String, Float> = emptyMap()

        /** Timer minutes, or null for an infinite session. */
        @Volatile var timerMinutes: Int? = null
    }
