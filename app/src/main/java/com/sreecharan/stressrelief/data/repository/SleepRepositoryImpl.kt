package com.sreecharan.stressrelief.data.repository

import com.sreecharan.stressrelief.data.local.JsonCodec
import com.sreecharan.stressrelief.data.local.SleepSessionDao
import com.sreecharan.stressrelief.data.local.SleepSessionEntity
import com.sreecharan.stressrelief.data.local.toDomain
import com.sreecharan.stressrelief.data.model.SleepSession
import com.sreecharan.stressrelief.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepRepositoryImpl
    @Inject
    constructor(
        private val sleepSessionDao: SleepSessionDao,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : SleepRepository {
        override suspend fun recordSleepSession(
            durationMin: Int,
            sounds: List<String>,
        ) {
            withContext(ioDispatcher) {
                val minutes = durationMin.coerceAtLeast(1)
                sleepSessionDao.insert(
                    SleepSessionEntity(
                        startMs = System.currentTimeMillis() - minutes * 60_000L,
                        durationMin = minutes,
                        soundsJson = JsonCodec.encodeStrings(sounds),
                    ),
                )
            }
        }

        override fun observeRecent(limit: Int): Flow<List<SleepSession>> =
            sleepSessionDao
                .observeRecent(limit.coerceAtLeast(1))
                .map { list -> list.map { it.toDomain() } }
                .flowOn(ioDispatcher)
    }
