package com.sreecharan.stressrelief.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sreecharan.stressrelief.R
import com.sreecharan.stressrelief.ui.components.AuraButton
import com.sreecharan.stressrelief.ui.components.AuraGradients

/**
 * Journal editor route. New entries (entryId = -1) show a rotating guided prompt
 * from JournalPrompts; existing entries load their saved prompt and text.
 * Save persists via the repository and marks today's journaling done.
 */
@Composable
fun JournalEditorScreen(
    onBack: () -> Unit,
    viewModel: JournalEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state is JournalEditorUiState.Saved) {
        LaunchedEffect(Unit) { onBack() }
    }
    JournalEditorContent(
        state = state,
        onTextChanged = viewModel::onTextChanged,
        onSave = viewModel::onSave,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JournalEditorContent(
    state: JournalEditorUiState,
    onTextChanged: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editing = state as? JournalEditorUiState.Editing
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (editing?.isNew == false) {
                            stringResource(R.string.journal_edit_title)
                        } else {
                            stringResource(R.string.journal_new_title)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.b4_action_back),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = onSave,
                        enabled = editing?.canSave == true && state !is JournalEditorUiState.Saving,
                    ) {
                        Text(
                            stringResource(R.string.b4_action_save),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Box(Modifier.fillMaxSize().background(AuraGradients.calmWash()))
            when (state) {
                JournalEditorUiState.Loading,
                JournalEditorUiState.Saving,
                JournalEditorUiState.Saved,
                -> {
                    // Kept simple: the editor chrome stays up while saving.
                }
                is JournalEditorUiState.Error -> {
                    Text(
                        state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(24.dp),
                    )
                }
                is JournalEditorUiState.Editing -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                    ) {
                        // Guided prompt card. The gradient band is decorative; the prompt
                        // text sits on a solid surface container.
                        Card(
                            shape = RoundedCornerShape(22.dp),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor =
                                        MaterialTheme.colorScheme.surfaceContainerHigh,
                                ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(18.dp)) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(CircleShape)
                                        .background(AuraGradients.dawn()),
                                )
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 8.dp),
                                    )
                                    Text(
                                        stringResource(R.string.journal_prompt_label),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    editing.prompt,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        TextField(
                            value = editing.text,
                            onValueChange = onTextChanged,
                            placeholder = {
                                Text(stringResource(R.string.journal_editor_placeholder))
                            },
                            textStyle = MaterialTheme.typography.bodyLarge,
                            colors =
                                TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                ),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                        )
                        Spacer(Modifier.height(12.dp))
                        // Primary save action: a pill CTA above the keyboard area,
                        // not just the top-bar text button.
                        AuroraButton(
                            text = stringResource(R.string.b4_action_save),
                            onClick = onSave,
                            gradient = true,
                            enabled = editing.canSave && state !is JournalEditorUiState.Saving,
                            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}
