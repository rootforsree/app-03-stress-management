package com.sreecharan.stressrelief.ui.mood

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sreecharan.stressrelief.di.IoDispatcher
import com.sreecharan.stressrelief.data.repository.WellnessRepository
import com.sreecharan.stressrelief.widget.AuraWidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Immutable
sealed interface MoodCheckInUiState {
    data class Editing(
        val mood: Int?,
        val activities: Set<String>,
        val note: String,
    ) : MoodCheckInUiState

    data object Saving : MoodCheckInUiState

    data object Saved : MoodCheckInUiState

    data class Error(val message: String) : MoodCheckInUiState
}

/**
 * 10-second check-in. One mood tap, optional tags and note, one save.
 * The repository marks today's DailyActivity row as checked-in.
 */
@HiltViewModel
class MoodCheckInViewModel @Inject constructor(
    private val wellness: WellnessRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val widgetUpdater: AuraWidgetUpdater,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow<MoodCheckInUiState>(
            MoodCheckInUiState.Editing(null, emptySet(), ""),
        )
    val uiState: StateFlow<MoodCheckInUiState> = _uiState.asStateFlow()

    fun onMoodSelected(score: Int) {
        val current = uiState.value as? MoodCheckInUiState.Editing ?: return
        _uiState.value = current.copy(mood = score.coerceIn(1, 5))
    }

    fun onActivityToggled(key: String) {
        val current = uiState.value as? MoodCheckInUiState.Editing ?: return
        val updated =
            if (key in current.activities) {
                current.activities - key
            } else {
                current.activities + key
            }
        _uiState.value = current.copy(activities = updated)
    }

    fun onNoteChanged(note: String) {
        val current = uiState.value as? MoodCheckInUiState.Editing ?: return
        _uiState.value = current.copy(note = note)
    }

    fun onSave() {
        val current = uiState.value as? MoodCheckInUiState.Editing ?: return
        val mood = current.mood ?: return
        _uiState.value = MoodCheckInUiState.Saving
        viewModelScope.launch(ioDispatcher) {
            runCatching {
                wellness.saveMoodCheckIn(
                    score = mood,
                    activities = current.activities.toList(),
                    note = current.note.trim(),
                )
            }.onSuccess {
                _uiState.value = MoodCheckInUiState.Saved
                runCatching { widgetUpdater.refresh() }
            }.onFailure { e ->
                _uiState.value =
                    MoodCheckInUiState.Error(e.message ?: "Could not save your check-in.")
            }
        }
    }
}
