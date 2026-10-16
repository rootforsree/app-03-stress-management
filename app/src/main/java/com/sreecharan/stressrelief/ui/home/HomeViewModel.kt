package com.sreecharan.stressrelief.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sreecharan.stressrelief.data.catalog.Programs
import com.sreecharan.stressrelief.data.catalog.journalPrompts
import com.sreecharan.stressrelief.data.catalog.sleepSounds
import com.sreecharan.stressrelief.data.health.HealthConnectStateProvider
import com.sreecharan.stressrelief.data.model.BreathingSession
import com.sreecharan.stressrelief.data.model.JournalEntry
import com.sreecharan.stressrelief.data.model.MoodCheckIn
import com.sreecharan.stressrelief.data.model.ProgramProgress
import com.sreecharan.stressrelief.data.model.SleepSession
import com.sreecharan.stressrelief.data.model.StressScore
import com.sreecharan.stressrelief.data.repository.BreathingRepository
import com.sreecharan.stressrelief.data.repository.SleepRepository
import com.sreecharan.stressrelief.data.repository.WellnessRepository
import com.sreecharan.stressrelief.di.IoDispatcher
import com.sreecharan.stressrelief.ui.journal.JournalEditorViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.roundToInt

@Immutable
sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Content(
        val stressScore: StressScore?,
        val greeting: GreetingTime,
        val nudge: NudgeKind,
        val today: LocalDate,
        val sparkline: ImmutableList<Float?>,
        val weekDelta: Int?,
        val weekBars: ImmutableList<WeekBar>,
        val streakDays: Int,
        val weeklyCalmMinutes: Int,
        val weeklyJournalCount: Int,
        val checkedInToday: Boolean,
        val calmMinutesToday: Int,
        val journalDoneToday: Boolean,
        val continueAction: ContinueAction?,
        val lastSleepSession: SleepSessionSummary?,
        val todayPrompt: String,
        val isEvening: Boolean,
        val healthConnectState: HealthConnectUiState = HealthConnectUiState.Unavailable,
    ) : HomeUiState

    data class Error(
        val message: String,
    ) : HomeUiState
}

/** Health Connect opt-in state for the slim banner. */
@Immutable
sealed interface HealthConnectUiState {
    /** Health Connect is not supported on this device at all. */
    data object Unavailable : HealthConnectUiState

    /** Health Connect can be installed/updated from the Play Store: show the install banner. */
    data object NeedsInstall : HealthConnectUiState

    /** Available but the stress-signal permissions are not granted: show the opt-in banner. */
    data object Available : HealthConnectUiState

    /** Available and granted: health signals feed the stress score. */
    data object Granted : HealthConnectUiState
}

enum class GreetingTime { MORNING, AFTERNOON, EVENING }

/** Score-driven nudge line under the greeting. */
enum class NudgeKind { NO_SCORE, HIGH, MODERATE, LOW }

/** One mood bar for the current week (Monday first). Null mood = hollow bar. */
@Immutable
data class WeekBar(
    val label: String,
    /** Average mood 1..5 for that day, null when nothing was logged. */
    val mood: Float?,
)

/** Last wind-down summary: duration plus human-readable sound names ("Rain + Ocean"). */
@Immutable
data class SleepSessionSummary(
    val durationMin: Int,
    val soundNames: String,
)

/**
 * The single best next step, first match wins. Routes resolved by the UI:
 * [CheckIn] -> MoodCheckInRoute, [Program] -> ProgramsRoute,
 * [BreatheReset] -> BreatheRoute, [WindDown] -> SleepSessionRoute.
 */
@Immutable
sealed interface ContinueAction {
    data object CheckIn : ContinueAction

    data class Program(
        val title: String,
        val day: Int,
        val minutes: Int,
    ) : ContinueAction

    data object BreatheReset : ContinueAction

    data object WindDown : ContinueAction
}

/** Pure display logic for the home dashboard. Covered by unit tests. */
object HomeStats {
    private val DayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
    const val EVENING_HOUR = 18

    fun greetingTime(hourOfDay: Int): GreetingTime =
        when {
            hourOfDay < 12 -> GreetingTime.MORNING
            hourOfDay < EVENING_HOUR -> GreetingTime.AFTERNOON
            else -> GreetingTime.EVENING
        }

    fun isEveningHour(hourOfDay: Int): Boolean = hourOfDay >= EVENING_HOUR

    /**
     * Nudge from the v2 stress bands (0-25 Low, 26-50 Moderate, 51-75 High,
     * 76-100 Very high). Higher = more stressed.
     */
    fun nudgeKind(score: Int?): NudgeKind =
        when {
            score == null -> NudgeKind.NO_SCORE
            score >= 51 -> NudgeKind.HIGH
            score >= 26 -> NudgeKind.MODERATE
            else -> NudgeKind.LOW
        }

    /**
     * Seven daily average moods (1..5), oldest first, for the trailing 7 days.
     * Null when nothing was logged that day.
     */
    fun sparkline(
        checkIns: List<MoodCheckIn>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Float?> {
        val byDay = checkIns.groupBy { dateOf(it.timestampMs, zone) }
        return (6 downTo 0).map { back ->
            val day = today.minusDays(back.toLong())
            byDay[day]?.map { it.moodScore }?.average()?.toFloat()
        }
    }

    /**
     * Mood delta between the newer and older halves of [series], mapped to a
     * 100-ish scale ((mood 1..5 range of 4) x 20) so it reads like the score.
     * Null when fewer than 4 non-null days.
     */
    fun weekDelta(series: List<Float?>): Int? {
        val points = series.filterNotNull()
        if (points.size < 4) return null
        val half = points.size / 2
        val older = points.take(half).average()
        val newer = points.drop(points.size - half).average()
        return ((newer - older) * 20).roundToInt()
    }

    /** Seven mood bars for the current week (Monday first). */
    fun weekBars(
        checkIns: List<MoodCheckIn>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<WeekBar> {
        val byDay = checkIns.groupBy { dateOf(it.timestampMs, zone) }
        val monday = today.with(DayOfWeek.MONDAY)
        return (0..6).map { offset ->
            val day = monday.plusDays(offset.toLong())
            WeekBar(
                label = DayLabels[offset],
                mood = byDay[day]?.map { it.moodScore }?.average()?.toFloat(),
            )
        }
    }

    fun streakLabel(days: Int): String =
        when {
            days <= 0 -> "Start your streak"
            days == 1 -> "1-day streak"
            else -> "$days-day streak"
        }

    /**
     * Petal count for the bloom visual. Always at least 3 petals so the bloom reads
     * as a flower even on day zero, capped at 12 so it never gets cluttered.
     */
    fun bloomPetals(streakDays: Int): Int = streakDays.coerceIn(3, 12)

    fun isToday(
        timestampMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean = dateOf(timestampMs, zone) == LocalDate.now(zone)

    /** Calm minutes from breathing sessions in the last 7 days (sum of durationSec). */
    fun weeklyCalmMinutes(
        sessions: List<BreathingSession>,
        weekStartMs: Long,
    ): Int = sessions.filter { it.startMs >= weekStartMs }.sumOf { it.durationSec } / 60

    fun calmMinutesToday(
        sessions: List<BreathingSession>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Int {
        val today = LocalDate.now(zone)
        return sessions
            .filter { dateOf(it.startMs, zone) == today }
            .sumOf { it.durationSec } / 60
    }

    fun journalCountThisWeek(
        entries: List<JournalEntry>,
        weekStartMs: Long,
    ): Int = entries.count { it.timestampMs >= weekStartMs }

    /**
     * First-match-wins continue logic:
     * check-in missing -> program active -> no breathing today -> evening
     * wind-down -> nothing.
     */
    fun resolveContinueAction(
        checkedInToday: Boolean,
        programs: List<ProgramProgress>,
        breathingToday: Boolean,
        sleptToday: Boolean,
        hourOfDay: Int,
    ): ContinueAction? {
        if (!checkedInToday) return ContinueAction.CheckIn
        programs.firstOrNull { it.startedMs > 0 && it.currentDay < 7 }?.let { progress ->
            val catalog = Programs.byId(progress.programId)
            val minutes =
                catalog?.days?.firstOrNull { it.day == progress.currentDay }?.minutes ?: 5
            return ContinueAction.Program(
                title = catalog?.title ?: progress.programId,
                day = progress.currentDay,
                minutes = minutes,
            )
        }
        if (!breathingToday) return ContinueAction.BreatheReset
        if (isEveningHour(hourOfDay) && !sleptToday) return ContinueAction.WindDown
        return null
    }

    fun sleepSessionSummary(session: SleepSession): SleepSessionSummary {
        val names =
            session.sounds.map { id ->
                sleepSounds.firstOrNull { it.id == id }?.name ?: id
            }
        return SleepSessionSummary(
            durationMin = session.durationMin,
            soundNames = names.joinToString(" + "),
        )
    }

    private fun dateOf(
        timestampMs: Long,
        zone: ZoneId,
    ): LocalDate = Instant.ofEpochMilli(timestampMs).atZone(zone).toLocalDate()
}

/**
 * Intermediate holder: kotlinx-coroutines only ships typed [combine]
 * overloads up to five flows, so the dashboard combine runs in stages.
 */
private data class HomePartial(
    val score: StressScore?,
    val todayCheckIn: MoodCheckIn?,
    val streakDays: Int,
    val weekHistory: List<MoodCheckIn>,
    val journals: List<JournalEntry>,
    val breathingSessions: List<BreathingSession>,
    val programs: List<ProgramProgress>,
    val sleepSessions: List<SleepSession>,
)

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val wellness: WellnessRepository,
        val healthConnect: HealthConnectStateProvider,
        private val breathing: BreathingRepository,
        private val sleep: SleepRepository,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
        val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

        private val stressScore = MutableStateFlow<StressScore?>(null)
        private val healthConnectState =
            MutableStateFlow<HealthConnectUiState>(HealthConnectUiState.Unavailable)
        private var collectJob: Job? = null

        init {
            start()
        }

        /** Re-reads the stress score (Health Connect + check-ins). Never throws. */
        fun refreshStressScore() {
            viewModelScope.launch(ioDispatcher) {
                stressScore.value = runCatching { wellness.computeStressScore() }.getOrNull()
            }
        }

        /** Re-reads Health Connect availability/permission state. Never throws. */
        fun checkHealthConnect() {
            viewModelScope.launch(ioDispatcher) {
                healthConnectState.value =
                    runCatching {
                        when {
                            healthConnect.hasAllPermissions() -> HealthConnectUiState.Granted
                            healthConnect.isAvailable() -> HealthConnectUiState.Available
                            healthConnect.sdkStatus() ==
                                androidx.health.connect.client.HealthConnectClient
                                    .SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                                HealthConnectUiState.NeedsInstall
                            else -> HealthConnectUiState.Unavailable
                        }
                    }.getOrDefault(HealthConnectUiState.Unavailable)
            }
        }

        /** Called with the Health Connect permission-flow result; re-checks and refreshes. */
        fun onHealthPermissionResult() {
            checkHealthConnect()
            refreshStressScore()
        }

        fun retry() {
            start()
        }

        private fun start() {
            collectJob?.cancel()
            _uiState.value = HomeUiState.Loading
            refreshStressScore()
            checkHealthConnect()
            val contentFlow =
                combine(
                    stressScore,
                    wellness.observeTodayCheckIn(),
                    wellness.observeStreakDays(),
                    wellness.observeMoodHistory(7),
                    wellness.observeJournalEntries(),
                ) {
                    score: StressScore?,
                    todayCheckIn: MoodCheckIn?,
                    streakDays: Int,
                    weekHistory: List<MoodCheckIn>,
                    journals: List<JournalEntry>,
                    ->
                    HomePartial(
                        score = score,
                        todayCheckIn = todayCheckIn,
                        streakDays = streakDays,
                        weekHistory = weekHistory,
                        journals = journals,
                        breathingSessions = emptyList(),
                        programs = emptyList(),
                        sleepSessions = emptyList(),
                    )
                }.combine(breathing.observeRecentSessions(RECENT_BREATHING_LIMIT)) { partial, sessions ->
                    partial.copy(breathingSessions = sessions)
                }.combine(breathing.observePrograms()) { partial, programs ->
                    partial.copy(programs = programs)
                }.combine(sleep.observeRecent(1)) { partial, sleepSessions ->
                    val zone = ZoneId.systemDefault()
                    val today = LocalDate.now(zone)
                    val monday = today.with(DayOfWeek.MONDAY)
                    val weekStartMs = monday.atStartOfDay(zone).toInstant().toEpochMilli()
                    val spark = HomeStats.sparkline(partial.weekHistory, today, zone)
                    val hour = LocalTime.now(zone).hour
                    val breathingToday = partial.breathingSessions.any { HomeStats.isToday(it.startMs, zone) }
                    val sleptToday = sleepSessions.any { HomeStats.isToday(it.startMs, zone) }
                    HomeUiState.Content(
                        stressScore = partial.score,
                        greeting = HomeStats.greetingTime(hour),
                        nudge = HomeStats.nudgeKind(partial.score?.score),
                        today = today,
                        sparkline = spark.toImmutableList(),
                        weekDelta = HomeStats.weekDelta(spark),
                        weekBars = HomeStats.weekBars(partial.weekHistory, today, zone).toImmutableList(),
                        streakDays = partial.streakDays,
                        weeklyCalmMinutes = HomeStats.weeklyCalmMinutes(partial.breathingSessions, weekStartMs),
                        weeklyJournalCount = HomeStats.journalCountThisWeek(partial.journals, weekStartMs),
                        checkedInToday = partial.todayCheckIn != null,
                        calmMinutesToday = HomeStats.calmMinutesToday(partial.breathingSessions, zone),
                        journalDoneToday = partial.journals.any { HomeStats.isToday(it.timestampMs, zone) },
                        continueAction =
                            HomeStats.resolveContinueAction(
                                checkedInToday = partial.todayCheckIn != null,
                                programs = partial.programs,
                                breathingToday = breathingToday,
                                sleptToday = sleptToday,
                                hourOfDay = hour,
                            ),
                        lastSleepSession = sleepSessions.firstOrNull()?.let(HomeStats::sleepSessionSummary),
                        todayPrompt =
                            journalPrompts[
                                JournalEditorViewModel.promptIndex(
                                    today.toEpochDay(),
                                    journalPrompts.size,
                                ),
                            ],
                        isEvening = HomeStats.isEveningHour(hour),
                    )
                }
            collectJob =
                viewModelScope.launch(ioDispatcher) {
                    combine(contentFlow, healthConnectState) { content, health ->
                        content.copy(healthConnectState = health)
                    }.catch { e ->
                        _uiState.value =
                            HomeUiState.Error(e.message ?: "Could not load your dashboard.")
                    }.collect { _uiState.value = it }
                }
        }

        companion object {
            /** Enough recent sessions to cover a full week of breathing for weekly totals. */
            private const val RECENT_BREATHING_LIMIT = 500
        }
    }
