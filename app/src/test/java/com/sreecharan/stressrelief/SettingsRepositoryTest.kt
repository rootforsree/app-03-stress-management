package com.sreecharan.stressrelief

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.sreecharan.stressrelief.data.datastore.SettingsRepositoryImpl
import com.sreecharan.stressrelief.data.model.QuietHours
import com.sreecharan.stressrelief.data.model.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Real Preferences DataStore on the JVM (temp file, no Android framework).
 * Verifies every setting round-trips and defaults match the contract.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {
    @TempDir
    lateinit var tempDir: File

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: SettingsRepositoryImpl

    @BeforeEach
    fun setUp() {
        val dataStore =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(testDispatcher + SupervisorJob()),
            ) { File(tempDir, "aura_prefs_test.preferences_pb") }
        repository = SettingsRepositoryImpl(dataStore)
    }

    @Test
    fun `defaults match the contract`() =
        runTest(testDispatcher) {
            assertEquals(ThemeMode.SYSTEM, repository.themeMode.first())
            assertFalse(repository.dynamicColor.first())
            assertTrue(repository.remindersEnabled.first())
            assertEquals(QuietHours(22, 0, 7, 0), repository.quietHours.first())
            assertFalse(repository.onboardingCompleted.first())
        }

    @Test
    fun `theme mode round-trips`() =
        runTest(testDispatcher) {
            repository.themeMode.test {
                assertEquals(ThemeMode.SYSTEM, awaitItem())

                repository.setThemeMode(ThemeMode.DARK)

                assertEquals(ThemeMode.DARK, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `dynamic color round-trips`() =
        runTest(testDispatcher) {
            repository.setDynamicColor(true)

            assertTrue(repository.dynamicColor.first())
        }

    @Test
    fun `reminders toggle round-trips`() =
        runTest(testDispatcher) {
            repository.setRemindersEnabled(false)

            assertFalse(repository.remindersEnabled.first())
        }

    @Test
    fun `quiet hours round-trip`() =
        runTest(testDispatcher) {
            repository.setQuietHours(QuietHours(23, 30, 6, 45))

            assertEquals(QuietHours(23, 30, 6, 45), repository.quietHours.first())
        }

    @Test
    fun `onboarding completion is one-way`() =
        runTest(testDispatcher) {
            repository.setOnboardingCompleted()

            assertTrue(repository.onboardingCompleted.first())
        }
}
