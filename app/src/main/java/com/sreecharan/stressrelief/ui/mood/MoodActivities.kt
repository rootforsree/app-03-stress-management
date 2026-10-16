package com.sreecharan.stressrelief.ui.mood

import androidx.annotation.StringRes
import com.sreecharan.stressrelief.R

/**
 * Activity tags offered on the 10-second check-in. The tag keys are what gets
 * persisted via WellnessRepository.saveMoodCheckIn; labels are UI only.
 */
data class MoodActivity(
    val key: String,
    @StringRes val labelRes: Int,
)

val MoodActivities =
    listOf(
        MoodActivity("work", R.string.mood_activity_work),
        MoodActivity("sleep", R.string.mood_activity_sleep),
        MoodActivity("exercise", R.string.mood_activity_exercise),
        MoodActivity("social", R.string.mood_activity_social),
        MoodActivity("food", R.string.mood_activity_food),
        MoodActivity("rest", R.string.mood_activity_rest),
        MoodActivity("outdoors", R.string.mood_activity_outdoors),
        MoodActivity("mindfulness", R.string.mood_activity_mindfulness),
    )

/** 5-point mood scale used by the check-in sheet. */
val MoodScale = (1..5).toList()
