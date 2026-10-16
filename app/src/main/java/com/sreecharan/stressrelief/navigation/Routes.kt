package com.sreecharan.stressrelief.navigation

import kotlinx.serialization.Serializable

/**
 * All Aura routes. Verbatim from CONTRACT.md section 3; owned by Builder 1.
 * Do not rename or restructure these: other builders navigate to them.
 */
@Serializable object OnboardingRoute
@Serializable object HomeRoute
@Serializable object BreatheRoute
@Serializable object JournalRoute
@Serializable object SleepRoute
@Serializable data class BreathingSessionRoute(val goal: String, val patternId: String)
@Serializable object MoodCheckInRoute
@Serializable data class JournalEditorRoute(val entryId: Long = -1L)
@Serializable object ProgramsRoute
@Serializable object InsightsRoute // premium-gated
@Serializable object SleepSessionRoute // active wind-down (sounds+timer)
@Serializable object PaywallRoute
@Serializable object SettingsRoute

/**
 * Internal nested-graph wrappers (Builder 1 navigation plumbing, not feature
 * routes). Each bottom tab gets its own nested graph so tab back stacks keep
 * saveState/restoreState per the architecture guide.
 */
@Serializable internal data object HomeTabGraph
@Serializable internal data object BreatheTabGraph
@Serializable internal data object JournalTabGraph
@Serializable internal data object SleepTabGraph
