package com.sreecharan.stressrelief.data.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal object TimeUtils {
    const val DAY_MS: Long = 86_400_000L

    fun startOfDayMs(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    fun todayEpochDay(zone: ZoneId = ZoneId.systemDefault()): Long = LocalDate.now(zone).toEpochDay()
}
