package com.sreecharan.stressrelief.data.health

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.sreecharan.stressrelief.data.model.StressSignals
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import androidx.health.connect.client.HealthConnectClient as AndroidHealthConnectClient

/**
 * Health Connect access for stress signals and insight inputs.
 *
 * Every read degrades to nulls/empties on any failure (SDK missing,
 * permissions denied, read error). This class never throws.
 */
class HealthConnectClient(
    private val context: Context,
) : HealthConnectStateProvider {
    private val readPermissions: Set<String> =
        setOf(
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        )

    private val writePermissionSet: Set<String> =
        setOf(
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        )

    /** Permissions the app needs for stress signals; request via [permissionContract]. */
    override fun requiredPermissions(): Set<String> = readPermissions

    /** Write permissions needed for session write-back. */
    override fun writePermissions(): Set<String> = writePermissionSet

    /** Contract for the Health Connect permission flow; register it in an Activity. */
    override fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    override suspend fun isAvailable(): Boolean =
        runCatching {
            sdkStatus() == AndroidHealthConnectClient.SDK_AVAILABLE
        }.getOrDefault(false)

    override suspend fun sdkStatus(): Int =
        runCatching {
            AndroidHealthConnectClient.getSdkStatus(context)
        }.getOrDefault(AndroidHealthConnectClient.SDK_UNAVAILABLE)

    override suspend fun hasAllPermissions(): Boolean =
        runCatching {
            if (!isAvailable()) return false
            val client = AndroidHealthConnectClient.getOrCreate(context)
            client.permissionController.getGrantedPermissions().containsAll(readPermissions)
        }.getOrDefault(false)

    /**
     * Average resting heart rate (bpm) and average nightly sleep (hours) over
     * the last 7 days. Either value is null when unavailable. Resting HR falls
     * back to the derived overnight-sample estimate when no
     * RestingHeartRateRecord exists. Never throws.
     */
    suspend fun readStressSignals(): StressSignals =
        runCatching {
            if (!isAvailable() || !hasAllPermissions()) return StressSignals(null, null)
            val avgRestingHr =
                readRestingHrDays(READ_WINDOW_DAYS)
                    .takeIf { it.isNotEmpty() }
                    ?.map { it.bpm }
                    ?.average()
                    ?.toInt()
            val sleepMinutes =
                readSleepNights(READ_WINDOW_DAYS)
                    .takeIf { it.isNotEmpty() }
                    ?.sumOf { night ->
                        val tst = night.stages.filter { it.type.isAsleep() }.sumOf { it.durationMs() }
                        if (tst > 0) tst else night.sessionEndMs - night.sessionStartMs
                    }?.let { it / 60_000 }
            StressSignals(
                avgRestingHr = avgRestingHr,
                sleepHours = sleepMinutes?.let { it / 60f / READ_WINDOW_DAYS },
            )
        }.getOrElse { StressSignals(null, null) }

    /**
     * Writes a completed breathing session as an ExerciseSessionRecord with
     * EXERCISE_TYPE_GUIDED_BREATHING (the Health Connect constant; no plain
     * EXERCISE_TYPE_BREATHING exists). Returns false on any failure.
     * Never throws.
     */
    override suspend fun writeBreathingSession(
        startMs: Long,
        endMs: Long,
    ): Boolean =
        runCatching {
            if (endMs <= startMs) return false
            if (!isAvailable()) return false
            val client = AndroidHealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            if (!granted.containsAll(writePermissionSet)) return false
            val zone = ZoneId.systemDefault()
            val start = Instant.ofEpochMilli(startMs)
            val end = Instant.ofEpochMilli(endMs)
            val record =
                ExerciseSessionRecord(
                    startTime = start,
                    startZoneOffset = zone.rules.getOffset(start),
                    endTime = end,
                    endZoneOffset = zone.rules.getOffset(end),
                    metadata = Metadata.manualEntry(device = Device(type = Device.TYPE_PHONE)),
                    exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_GUIDED_BREATHING,
                    title = "Breathing session",
                )
            client.insertRecords(listOf(record))
            true
        }.getOrDefault(false)

    /**
     * Sleep sessions over the trailing [days] days, oldest first.
     * Empty when unavailable. Never throws.
     */
    suspend fun readSleepNights(days: Int): List<SleepNight> =
        runCatching {
            if (!isAvailable() || !hasAllPermissions()) return emptyList()
            val client = AndroidHealthConnectClient.getOrCreate(context)
            val zone = ZoneId.systemDefault()
            val end = Instant.now()
            val records =
                client
                    .readRecords(
                        ReadRecordsRequest(
                            recordType = SleepSessionRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(end.minus(days.toLong(), ChronoUnit.DAYS), end),
                        ),
                    ).records
            records
                .map { record ->
                    SleepNight(
                        epochDay = record.endTime.atZone(zone).toLocalDate().toEpochDay(),
                        sessionStartMs = record.startTime.toEpochMilli(),
                        sessionEndMs = record.endTime.toEpochMilli(),
                        stages =
                            record.stages.map { stage ->
                                SleepStageSegment(
                                    startMs = stage.startTime.toEpochMilli(),
                                    endMs = stage.endTime.toEpochMilli(),
                                    type = mapStageType(stage.stage),
                                )
                            },
                    )
                }.sortedBy { it.sessionStartMs }
        }.getOrElse { emptyList() }

    /**
     * Minute-level asleep/awake strips for the trailing [days] days, ascending.
     * Built from sleep sessions; minutes outside any session count as awake.
     * Empty when unavailable. Never throws.
     */
    suspend fun readSleepStrips(days: Int): List<SleepDayStrip> =
        runCatching {
            val zone = ZoneId.systemDefault()
            val strips = mutableMapOf<Long, BooleanArray>()
            for (night in readSleepNights(days)) {
                for (segment in night.stages) {
                    if (!segment.type.isAsleep()) continue
                    var cursor = segment.startMs
                    while (cursor < segment.endMs) {
                        val zoned = Instant.ofEpochMilli(cursor).atZone(zone)
                        val day = zoned.toLocalDate().toEpochDay()
                        val minuteOfDay = zoned.hour * 60 + zoned.minute
                        strips.getOrPut(day) { BooleanArray(MINUTES_PER_DAY) }[minuteOfDay] = true
                        cursor += 60_000L
                    }
                }
            }
            strips.entries.sortedBy { it.key }.map { (day, asleep) -> SleepDayStrip(day, asleep) }
        }.getOrElse { emptyList() }

    /**
     * Nightly resting HR over the trailing [days] days, ascending. Uses
     * RestingHeartRateRecord where present; otherwise the derived fallback
     * (mean of the lowest 20% of overnight 00:00-06:00 samples) with a
     * coverage-based completeness. Empty when unavailable. Never throws.
     */
    suspend fun readRestingHrDays(days: Int): List<RestingHrDay> =
        runCatching {
            if (!isAvailable() || !hasAllPermissions()) return emptyList()
            val client = AndroidHealthConnectClient.getOrCreate(context)
            val zone = ZoneId.systemDefault()
            val end = Instant.now()
            val range = TimeRangeFilter.between(end.minus(days.toLong(), ChronoUnit.DAYS), end)

            val recordBased =
                client
                    .readRecords(
                        ReadRecordsRequest(
                            recordType = RestingHeartRateRecord::class,
                            timeRangeFilter = range,
                        ),
                    ).records
                    .groupBy { it.time.atZone(zone).toLocalDate().toEpochDay() }
                    .mapValues { (_, records) -> records.map { it.beatsPerMinute.toDouble() }.average() }

            val overnightSamples =
                client
                    .readRecords(
                        ReadRecordsRequest(
                            recordType = HeartRateRecord::class,
                            timeRangeFilter = range,
                        ),
                    ).records
                    .flatMap { it.samples }
                    .map { sample -> sample.time.atZone(zone) to sample.beatsPerMinute.toDouble() }
                    .filter { (zoned, _) -> zoned.hour < OVERNIGHT_END_HOUR }
                    .groupBy { (zoned, _) -> zoned.toLocalDate().toEpochDay() }

            val daysOut = mutableListOf<RestingHrDay>()
            val startDay = LocalDate.now(zone).minusDays(days.toLong()).toEpochDay()
            val endDay = LocalDate.now(zone).toEpochDay()
            for (epochDay in startDay..endDay) {
                val recordBpm = recordBased[epochDay]
                if (recordBpm != null) {
                    daysOut += RestingHrDay(epochDay, recordBpm, completeness = 1.0, derived = false)
                    continue
                }
                val samples = overnightSamples[epochDay] ?: continue
                val bpms = samples.map { it.second }
                if (bpms.isEmpty()) continue
                val lowestCount = (bpms.size * DERIVED_LOWEST_FRACTION).toInt().coerceAtLeast(1)
                val derived = bpms.sorted().take(lowestCount).average()
                val coveredBuckets =
                    samples
                        .map { (zoned, _) -> (zoned.hour * 60 + zoned.minute) / BUCKET_MINUTES }
                        .toSet()
                        .size
                val completeness = (coveredBuckets.toDouble() / OVERNIGHT_BUCKETS).coerceIn(0.0, 1.0)
                daysOut += RestingHrDay(epochDay, derived, completeness, derived = true)
            }
            daysOut
        }.getOrElse { emptyList() }

    /**
     * Daily mean HRV RMSSD over the trailing [days] days, ascending.
     * Empty when no writer supplies the record (expected). Never throws.
     */
    suspend fun readHrvDays(days: Int): List<HrvDay> =
        runCatching {
            if (!isAvailable() || !hasAllPermissions()) return emptyList()
            val client = AndroidHealthConnectClient.getOrCreate(context)
            val zone = ZoneId.systemDefault()
            val end = Instant.now()
            client
                .readRecords(
                    ReadRecordsRequest(
                        recordType = HeartRateVariabilityRmssdRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(end.minus(days.toLong(), ChronoUnit.DAYS), end),
                    ),
                ).records
                .groupBy { it.time.atZone(zone).toLocalDate().toEpochDay() }
                .map { (day, records) -> HrvDay(day, records.map { it.heartRateVariabilityMillis }.average()) }
                .sortedBy { it.epochDay }
        }.getOrElse { emptyList() }

    private fun mapStageType(stage: Int): SleepStageType =
        when (stage) {
            SleepSessionRecord.STAGE_TYPE_AWAKE,
            SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
            -> SleepStageType.AWAKE
            SleepSessionRecord.STAGE_TYPE_SLEEPING -> SleepStageType.SLEEPING
            SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> SleepStageType.OUT_OF_BED
            SleepSessionRecord.STAGE_TYPE_LIGHT -> SleepStageType.LIGHT
            SleepSessionRecord.STAGE_TYPE_DEEP -> SleepStageType.DEEP
            SleepSessionRecord.STAGE_TYPE_REM -> SleepStageType.REM
            else -> SleepStageType.UNKNOWN
        }

    companion object {
        private const val READ_WINDOW_DAYS = 7
        private const val MINUTES_PER_DAY = 1440
        private const val OVERNIGHT_END_HOUR = 6
        private const val BUCKET_MINUTES = 30
        private const val OVERNIGHT_BUCKETS = 12 // 00:00-06:00 in 30-min buckets
        private const val DERIVED_LOWEST_FRACTION = 0.2
    }
}

/** True for stages that count as asleep when scoring. Visible inside the module for the repository. */
internal fun SleepStageType.isAsleep(): Boolean =
    this == SleepStageType.LIGHT ||
        this == SleepStageType.DEEP ||
        this == SleepStageType.REM ||
        this == SleepStageType.SLEEPING

private fun SleepStageSegment.durationMs(): Long = (endMs - startMs).coerceAtLeast(0L)
