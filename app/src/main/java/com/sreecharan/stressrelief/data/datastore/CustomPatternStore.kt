package com.sreecharan.stressrelief.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sreecharan.stressrelief.data.local.JsonCodec
import com.sreecharan.stressrelief.data.model.CustomPattern
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private const val DATASTORE_NAME = "custom_patterns"
private const val DEFAULT_GOAL_MIN = 10
private const val MIN_GOAL_MIN = 5
private const val MAX_GOAL_MIN = 120
private const val MAX_NAME_CHARS = 40
private const val MIN_PHASE_SEC = 0
private const val MAX_PHASE_SEC = 30
private const val MIN_ROUNDS = 1
private const val MAX_ROUNDS = 30

private val Context.customPatternsDataStore by preferencesDataStore(name = DATASTORE_NAME)

/**
 * All CustomPatternStore read/write logic, driven by an injected DataStore so
 * unit tests can supply a temp-file instance without an Android Context.
 * Corrupt JSON always reads back as an empty pattern list, never a crash.
 */
internal class CustomPatternStoreCore(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val PATTERNS = stringPreferencesKey("custom_patterns")
        val GOAL_MIN = intPreferencesKey("breathing_goal_min")
    }

    val patterns: Flow<List<CustomPattern>> =
        dataStore.data.map { prefs ->
            JsonCodec.decodeList<CustomPattern>(prefs[Keys.PATTERNS].orEmpty())
        }

    val breathingGoalMin: Flow<Int> =
        dataStore.data.map { prefs ->
            (prefs[Keys.GOAL_MIN] ?: DEFAULT_GOAL_MIN).coerceIn(MIN_GOAL_MIN, MAX_GOAL_MIN)
        }

    suspend fun savePattern(pattern: CustomPattern) {
        val coerced =
            pattern.copy(
                name = pattern.name.take(MAX_NAME_CHARS),
                inhaleSec = pattern.inhaleSec.coerceIn(MIN_ROUNDS, MAX_PHASE_SEC),
                holdSec = pattern.holdSec.coerceIn(MIN_PHASE_SEC, MAX_PHASE_SEC),
                exhaleSec = pattern.exhaleSec.coerceIn(MIN_ROUNDS, MAX_PHASE_SEC),
                holdAfterSec = pattern.holdAfterSec.coerceIn(MIN_PHASE_SEC, MAX_PHASE_SEC),
                rounds = pattern.rounds.coerceIn(MIN_ROUNDS, MAX_ROUNDS),
            )
        dataStore.edit { prefs ->
            val current = JsonCodec.decodeList<CustomPattern>(prefs[Keys.PATTERNS].orEmpty())
            val updated = current.filterNot { it.id == coerced.id } + coerced
            prefs[Keys.PATTERNS] = JsonCodec.encodeList(updated)
        }
    }

    suspend fun deletePattern(id: String) {
        dataStore.edit { prefs ->
            val current = JsonCodec.decodeList<CustomPattern>(prefs[Keys.PATTERNS].orEmpty())
            prefs[Keys.PATTERNS] = JsonCodec.encodeList(current.filterNot { it.id == id })
        }
    }

    suspend fun setBreathingGoalMin(minutes: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.GOAL_MIN] = minutes.coerceIn(MIN_GOAL_MIN, MAX_GOAL_MIN)
        }
    }
}

/**
 * User-created breathing patterns plus the daily breathing goal, persisted as
 * JSON in a Preferences DataStore. Constructor shape matches the builder
 * contract; constructor injection means no new Hilt module is needed.
 */
class CustomPatternStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val core = CustomPatternStoreCore(context.customPatternsDataStore)

        val patterns: Flow<List<CustomPattern>> = core.patterns
        val breathingGoalMin: Flow<Int> = core.breathingGoalMin

        suspend fun savePattern(pattern: CustomPattern) = core.savePattern(pattern)

        suspend fun deletePattern(id: String) = core.deletePattern(id)

        suspend fun setBreathingGoalMin(minutes: Int) = core.setBreathingGoalMin(minutes)
    }
