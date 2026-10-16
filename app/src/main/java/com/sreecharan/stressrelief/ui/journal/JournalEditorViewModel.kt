package com.sreecharan.stressrelief.ui.journal

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.sreecharan.stressrelief.di.IoDispatcher
import com.sreecharan.stressrelief.data.catalog.journalPrompts
import com.sreecharan.stressrelief.data.repository.WellnessRepository
import com.sreecharan.stressrelief.navigation.JournalEditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Immutable
sealed interface JournalEditorUiState {
    data object Loading : JournalEditorUiState

    data class Editing(
        val entryId: Long,
        val prompt: String,
        val text: String,
        val isNew: Boolean,
        val canSave: Boolean,
    ) : JournalEditorUiState

    data object Saving : JournalEditorUiState

    data object Saved : JournalEditorUiState

    data class Error(val message: String) : JournalEditorUiState
}

@HiltViewModel
class JournalEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val wellness: WellnessRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val entryId: Long = savedStateHandle.toRoute<JournalEditorRoute>().entryId

    private val _uiState = MutableStateFlow<JournalEditorUiState>(JournalEditorUiState.Loading)
    val uiState: StateFlow<JournalEditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(ioDispatcher) {
            if (entryId < 0) {
                val prompt = journalPrompts[promptIndex(LocalDate.now().toEpochDay())]
                _uiState.value =
                    JournalEditorUiState.Editing(
                        entryId = -1L,
                        prompt = prompt,
                        text = "",
                        isNew = true,
                        canSave = false,
                    )
            } else {
                val entry =
                    runCatching {
                        wellness.observeJournalEntry(entryId).first()
                    }.getOrNull()
                _uiState.value =
                    if (entry != null) {
                        JournalEditorUiState.Editing(
                            entryId = entry.id,
                            prompt = entry.prompt,
                            text = entry.text,
                            isNew = false,
                            canSave = entry.text.isNotBlank(),
                        )
                    } else {
                        JournalEditorUiState.Error("That entry is gone.")
                    }
            }
        }
    }

    fun onTextChanged(text: String) {
        val current = uiState.value as? JournalEditorUiState.Editing ?: return
        _uiState.value = current.copy(text = text, canSave = text.isNotBlank())
    }

    fun onSave() {
        val current = uiState.value as? JournalEditorUiState.Editing ?: return
        if (!current.canSave) return
        _uiState.value = JournalEditorUiState.Saving
        viewModelScope.launch(ioDispatcher) {
            runCatching {
                if (current.isNew) {
                    wellness.saveJournalEntry(prompt = current.prompt, text = current.text.trim())
                } else {
                    wellness.updateJournalEntry(id = current.entryId, text = current.text.trim())
                }
                wellness.markJournalDone()
            }.onSuccess {
                _uiState.value = JournalEditorUiState.Saved
            }.onFailure { e ->
                _uiState.value =
                    JournalEditorUiState.Error(e.message ?: "Could not save your entry.")
            }
        }
    }

    companion object {
        /**
         * Rotating guided prompt: the prompt of the day, stable for a given date and
         * always a valid index into the prompt list.
         */
        fun promptIndex(epochDay: Long, promptCount: Int = journalPrompts.size): Int {
            require(promptCount > 0)
            return (((epochDay % promptCount) + promptCount) % promptCount).toInt()
        }
    }
}
