package com.sreecharan.stressrelief.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import com.sreecharan.stressrelief.data.model.CustomPattern
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Real Preferences DataStore on the JVM (temp file, no Android framework),
 * driving CustomPatternStoreCore directly. Verifies save/delete round-trips,
 * goal coercion, field coercion, and corrupt-JSON tolerance.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CustomPatternStoreTest {
    @TempDir
    lateinit var tempDir: File

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var store: CustomPatternStoreCore
    private lateinit var rawDataStore: DataStore<Preferences>

    private fun pattern(id: String) =
        CustomPattern(
            id = id,
            name = "Test $id",
            inhaleSec = 4,
            holdSec = 7,
            exhaleSec = 8,
            holdAfterSec = 0,
            rounds = 4,
        )

    @BeforeEach
    fun setUp() {
        rawDataStore =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(testDispatcher + SupervisorJob()),
            ) { File(tempDir, "custom_patterns_test.preferences_pb") }
        store = CustomPatternStoreCore(rawDataStore)
    }

    @Test
    fun `patterns start empty`() =
        runTest(testDispatcher) {
            assertEquals(emptyList(), store.patterns.first())
        }

    @Test
    fun `daily goal defaults to 10 minutes`() =
        runTest(testDispatcher) {
            assertEquals(10, store.breathingGoalMin.first())
        }

    @Test
    fun `saved pattern round-trips`() =
        runTest(testDispatcher) {
            val saved = pattern("custom-1")

            store.savePattern(saved)

            assertEquals(listOf(saved), store.patterns.first())
        }

    @Test
    fun `saving the same id replaces instead of duplicating`() =
        runTest(testDispatcher) {
            store.savePattern(pattern("custom-1"))
            store.savePattern(pattern("custom-2"))

            store.savePattern(pattern("custom-1").copy(name = "Renamed"))

            val all = store.patterns.first()
            assertEquals(2, all.size)
            assertEquals("Renamed", all.first { it.id == "custom-1" }.name)
        }

    @Test
    fun `delete removes only the matching pattern`() =
        runTest(testDispatcher) {
            store.savePattern(pattern("custom-1"))
            store.savePattern(pattern("custom-2"))

            store.deletePattern("custom-1")

            assertEquals(listOf(pattern("custom-2")), store.patterns.first())
        }

    @Test
    fun `delete of unknown id is a no-op`() =
        runTest(testDispatcher) {
            store.savePattern(pattern("custom-1"))

            store.deletePattern("custom-nope")

            assertEquals(listOf(pattern("custom-1")), store.patterns.first())
        }

    @Test
    fun `goal is coerced to 5..120`() =
        runTest(testDispatcher) {
            store.setBreathingGoalMin(3)
            assertEquals(5, store.breathingGoalMin.first())

            store.setBreathingGoalMin(200)
            assertEquals(120, store.breathingGoalMin.first())

            store.setBreathingGoalMin(25)
            assertEquals(25, store.breathingGoalMin.first())
        }

    @Test
    fun `saved fields are coerced into their ranges`() =
        runTest(testDispatcher) {
            store.savePattern(
                CustomPattern(
                    id = "custom-wild",
                    name = "x".repeat(50),
                    inhaleSec = 0,
                    holdSec = 99,
                    exhaleSec = -3,
                    holdAfterSec = 40,
                    rounds = 0,
                ),
            )

            val saved = store.patterns.first().single()
            assertEquals(40, saved.name.length)
            assertEquals(1, saved.inhaleSec)
            assertEquals(30, saved.holdSec)
            assertEquals(1, saved.exhaleSec)
            assertEquals(30, saved.holdAfterSec)
            assertEquals(1, saved.rounds)
        }

    @Test
    fun `corrupt patterns json reads back as empty list`() =
        runTest(testDispatcher) {
            rawDataStore.edit { prefs ->
                prefs[stringPreferencesKey("custom_patterns")] = "not-json{{{"
            }

            assertTrue(store.patterns.first().isEmpty())
        }
}
