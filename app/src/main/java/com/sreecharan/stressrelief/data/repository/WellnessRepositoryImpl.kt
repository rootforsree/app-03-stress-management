package com.sreecharan.stressrelief.data.repository

import com.sreecharan.stressrelief.di.IoDispatcher
import com.sreecharan.stressrelief.data.health.HealthConnectClient
import com.sreecharan.stressrelief.data.local.BreathingSessionDao
import com.sreecharan.stressrelief.data.local.DailyActivityDao
import com.sreecharan.stressrelief.data.local.DailyActivityEntity
import com.sreecharan.stressrelief.data.local.JournalDao
import com.sreecharan.stressrelief.data.local.JournalEntryEntity
import com.sreecharan.stressrelief.data.local.JsonCodec
import com.sreecharan.stressrelief.data.local.MoodCheckInDao
import com.sreecharan.stressrelief.data.local.MoodCheckInEntity
import com.sreecharan.stressrelief.data.local.toDomain
import com.sreecharan.stressrelief.data.model.JournalEntry
import com.sreecharan.stressrelief.data.model.MoodCheckIn
import com.sreecharan.stressrelief.data.model.StressScore
import com.sreecharan.stressrelief.data.util.TimeUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WellnessRepositoryImpl
    @Inject
    constructor(
        private val moodCheckInDao: MoodCheckInDao,
        private val breathingSessionDao: BreathingSessionDao,
        private val journalDao: JournalDao,
        private val dailyActivityDao: DailyActivityDao,
        private val healthConnectClient: HealthConnectClient,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : WellnessRepository {
        override fun observeMoodHistory(days: Int): Flow<List<MoodCheckIn>> {
            val dayStart = TimeUtils.startOfDayMs(System.currentTimeMillis())
            val cutoff = dayStart - (days.coerceAtLeast(1) - 1) * TimeUtils.DAY_MS
            return moodCheckInDao
                .observeSince(cutoff)
                .map { list -> list.map { it.toDomain() } }
                .flowOn(ioDispatcher)
        }

        override fun observeTodayCheckIn(): Flow<MoodCheckIn?> {
            val dayStart = TimeUtils.startOfDayMs(System.currentTimeMillis())
            return moodCheckInDao
                .observeForDay(dayStart, dayStart + TimeUtils.DAY_MS)
                .map { it.firstOrNull()?.toDomain() }
                .flowOn(ioDispatcher)
        }

        override suspend fun saveMoodCheckIn(score: Int, activities: List<String>, note: String) {
            withContext(ioDispatcher) {
                moodCheckInDao.insert(
                    MoodCheckInEntity(
                        timestampMs = System.currentTimeMillis(),
                        moodScore = score.coerceIn(1, 5),
                        activitiesJson = JsonCodec.encodeStrings(activities),
                        note = note,
                    ),
                )
                markActivity { copy(checkInDone = true) }
            }
        }

        override fun observeJournalEntries(): Flow<List<JournalEntry>> =
            journalDao
                .observeAll()
                .map { list -> list.map { it.toDomain() } }
                .flowOn(ioDispatcher)

        override fun observeJournalEntry(id: Long): Flow<JournalEntry?> =
            journalDao
                .observeById(id)
                .map { it?.toDomain() }
                .flowOn(ioDispatcher)

        override suspend fun saveJournalEntry(prompt: String, text: String): Long =
            withContext(ioDispatcher) {
                val id =
                    journalDao.insert(
                        JournalEntryEntity(
                            timestampMs = System.currentTimeMillis(),
                            prompt = prompt,
                            text = text,
                        ),
                    )
                markActivity { copy(journalDone = true) }
                id
            }

        override suspend fun updateJournalEntry(id: Long, text: String) {
            withContext(ioDispatcher) {
                val existing = journalDao.observeById(id).first() ?: return@withContext
                journalDao.update(existing.copy(text = text))
            }
        }

        override suspend fun deleteJournalEntry(id: Long) {
            withContext(ioDispatcher) {
                journalDao.deleteById(id)
            }
        }

        override fun observeStreakDays(): Flow<Int> =
            dailyActivityDao
                .observeLastNDays(STREAK_LOOKBACK_DAYS)
                .map { rows ->
                    StreakCalculator.computeStreak(rows.map { it.toDomain() }, TimeUtils.todayEpochDay())
                }
                .flowOn(ioDispatcher)

        override fun observeTotalCalmMinutes(): Flow<Int> =
            breathingSessionDao.observeTotalMinutes().flowOn(ioDispatcher)

        override suspend fun computeStressScore(): StressScore =
            withContext(ioDispatcher) {
                runCatching {
                    val moods = moodCheckInDao.observeRecent(SCORE_HISTORY_LIMIT).first()
                    val weekAgo = System.currentTimeMillis() - 7 * TimeUtils.DAY_MS
                    val recent = moods.filter { it.timestampMs >= weekAgo }
                    val avgMood = recent.map { it.moodScore }.average().takeIf { recent.isNotEmpty() }
                    val signals = healthConnectClient.readStressSignals()
                    StressScoreCalculator.compute(avgMood = avgMood, signals = signals, checkInCount = recent.size)
                }.getOrElse {
                    StressScore(
                        score = 50,
                        label = "Moderate",
                        factors = listOf("Could not compute your stress score right now"),
                    )
                }
            }

        override suspend fun markBreathingDone(minutes: Int) {
            withContext(ioDispatcher) {
                markActivity { copy(breathingMinutes = breathingMinutes + minutes.coerceAtLeast(0)) }
            }
        }

        override suspend fun markJournalDone() {
            withContext(ioDispatcher) {
                markActivity { copy(journalDone = true) }
            }
        }

        companion object {
            private const val STREAK_LOOKBACK_DAYS = 730
            private const val SCORE_HISTORY_LIMIT = 200
        }

        private suspend fun markActivity(transform: DailyActivityEntity.() -> DailyActivityEntity) {
            val today = TimeUtils.todayEpochDay()
            val existing = dailyActivityDao.getDay(today)
            val updated =
                existing?.transform()
                    ?: DailyActivityEntity(
                        dateEpochDay = today,
                        checkInDone = false,
                        breathingMinutes = 0,
                        journalDone = false,
                    ).transform()
            dailyActivityDao.upsert(updated)
        }
    }
