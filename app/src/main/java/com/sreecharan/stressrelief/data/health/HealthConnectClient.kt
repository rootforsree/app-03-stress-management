package com.sreecharan.stressrelief.data.health

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.sreecharan.stressrelief.data.model.StressSignals
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import androidx.health.connect.client.HealthConnectClient as AndroidHealthConnectClient

/**
 * Health Connect access for stress signals: average resting heart rate and
 * average nightly sleep over the last 7 days.
 *
 * Every read degrades to nulls on any failure (SDK missing, permissions
 * denied, read error). This class never throws.
 */
class HealthConnectClient(
    private val context: Context,
) : HealthConnectStateProvider {
    private val readPermissions: Set<String> =
        setOf(
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
        )

    /** Permissions the app needs for stress signals; request via [permissionContract]. */
    override fun requiredPermissions(): Set<String> = readPermissions

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
     * the last 7 days. Either value is null when unavailable. Never throws.
     */
    suspend fun readStressSignals(): StressSignals =
        runCatching {
            if (!isAvailable() || !hasAllPermissions()) return StressSignals(null, null)
            val client = AndroidHealthConnectClient.getOrCreate(context)
            val end = Instant.now()
            val range = TimeRangeFilter.between(end.minus(7, ChronoUnit.DAYS), end)

            val avgRestingHr =
                client
                    .aggregate(
                        AggregateRequest(
                            metrics = setOf(RestingHeartRateRecord.BPM_AVG),
                            timeRangeFilter = range,
                        ),
                    )[RestingHeartRateRecord.BPM_AVG]
                    ?.toInt()

            val sleepMinutes =
                client
                    .readRecords(
                        ReadRecordsRequest(
                            recordType = SleepSessionRecord::class,
                            timeRangeFilter = range,
                        ),
                    ).records
                    .takeIf { it.isNotEmpty() }
                    ?.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }

            StressSignals(
                avgRestingHr = avgRestingHr,
                sleepHours = sleepMinutes?.let { it / 60f / 7f },
            )
        }.getOrElse { StressSignals(null, null) }
}
