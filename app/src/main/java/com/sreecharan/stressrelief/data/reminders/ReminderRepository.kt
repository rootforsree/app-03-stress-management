package com.sreecharan.stressrelief.data.reminders

import kotlinx.coroutines.flow.Flow

interface ReminderRepository {
    val preferences: Flow<ReminderPreferences>

    suspend fun update(transform: (ReminderPreferences) -> ReminderPreferences)
}
