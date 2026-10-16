package com.sreecharan.stressrelief.data.repository

import com.sreecharan.stressrelief.data.catalog.Programs
import com.sreecharan.stressrelief.data.local.BreathingSessionDao
import com.sreecharan.stressrelief.data.local.BreathingSessionEntity
import com.sreecharan.stressrelief.data.local.ProgramProgressDao
import com.sreecharan.stressrelief.data.local.toDomain
import com.sreecharan.stressrelief.data.local.toEntity
import com.sreecharan.stressrelief.data.model.BreathingSession
import com.sreecharan.stressrelief.data.model.ProgramProgress
import com.sreecharan.stressrelief.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BreathingRepositoryImpl
    @Inject
    constructor(
        private val breathingSessionDao: BreathingSessionDao,
        private val programProgressDao: ProgramProgressDao,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : BreathingRepository {
        override fun observeRecentSessions(limit: Int): Flow<List<BreathingSession>> =
            breathingSessionDao
                .observeRecent(limit.coerceAtLeast(1))
                .map { list -> list.map { it.toDomain() } }
                .flowOn(ioDispatcher)

        override suspend fun recordSession(
            goal: String,
            patternId: String,
            durationSec: Int,
            completed: Boolean,
        ) {
            withContext(ioDispatcher) {
                breathingSessionDao.insert(
                    BreathingSessionEntity(
                        startMs = System.currentTimeMillis() - durationSec.coerceAtLeast(0) * 1_000L,
                        durationSec = durationSec.coerceAtLeast(0),
                        goal = goal,
                        patternId = patternId,
                        completed = completed,
                    ),
                )
            }
        }

        override fun observePrograms(): Flow<List<ProgramProgress>> =
            programProgressDao
                .observeAll()
                .map { stored -> Programs.withProgress(stored.map { it.toDomain() }) }
                .flowOn(ioDispatcher)

        override suspend fun completeProgramDay(
            programId: String,
            day: Int,
        ) {
            withContext(ioDispatcher) {
                val existing = programProgressDao.getById(programId)?.toDomain()
                val updated = Programs.afterDayCompleted(existing, programId, day, System.currentTimeMillis())
                programProgressDao.upsert(updated.toEntity())
            }
        }
    }
