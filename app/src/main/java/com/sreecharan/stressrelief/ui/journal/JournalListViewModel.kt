package com.sreecharan.stressrelief.ui.journal

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sreecharan.stressrelief.data.model.JournalEntry
import com.sreecharan.stressrelief.data.repository.WellnessRepository
import com.sreecharan.stressrelief.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
sealed interface JournalListUiState {
    data object Loading : JournalListUiState

    data class Content(
        val entries: ImmutableList<JournalEntry>,
    ) : JournalListUiState

    data class Error(
        val message: String,
    ) : JournalListUiState
}

@HiltViewModel
class JournalListViewModel
    @Inject
    constructor(
        private val wellness: WellnessRepository,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<JournalListUiState>(JournalListUiState.Loading)
        val uiState: StateFlow<JournalListUiState> = _uiState.asStateFlow()

        private var collectJob: Job? = null

        init {
            start()
        }

        fun onDeleteEntry(id: Long) {
            viewModelScope.launch(ioDispatcher) {
                runCatching { wellness.deleteJournalEntry(id) }
                    .onFailure { e ->
                        _uiState.value =
                            JournalListUiState.Error(
                                e.message ?: "Could not delete that entry.",
                            )
                    }
            }
        }

        fun retry() {
            start()
        }

        private fun start() {
            collectJob?.cancel()
            _uiState.value = JournalListUiState.Loading
            collectJob =
                viewModelScope.launch(ioDispatcher) {
                    wellness
                        .observeJournalEntries()
                        .catch { e ->
                            _uiState.value =
                                JournalListUiState.Error(
                                    e.message ?: "Could not load your journal.",
                                )
                        }.collect { entries ->
                            _uiState.value = JournalListUiState.Content(entries.toImmutableList())
                        }
                }
        }
    }
