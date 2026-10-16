package com.sreecharan.stressrelief.ui.sleep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sreecharan.stressrelief.R
import com.sreecharan.stressrelief.ui.components.AuroraButton

/**
 * Sleep mixer (SleepRoute): toggleable generated sound layers with per-layer
 * volume, a timer picker, and the big gradient "Begin wind-down" button.
 * All sound layers are free for everyone.
 */
@Composable
fun SleepScreen(
    onNavigateToSession: () -> Unit,
    viewModel: SleepViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SleepEvent.NavigateToSession -> onNavigateToSession()
            }
        }
    }

    when (val s = state) {
        SleepUiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        is SleepUiState.Content -> {
            SleepMixerContent(
                state = s,
                onLayerToggled = viewModel::onLayerToggled,
                onVolumeChanged = viewModel::onVolumeChanged,
                onTimerSelected = viewModel::onTimerSelected,
                onBeginClicked = viewModel::onBeginClicked,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SleepMixerContent(
    state: SleepUiState.Content,
    onLayerToggled: (String, Boolean) -> Unit,
    onVolumeChanged: (String, Float) -> Unit,
    onTimerSelected: (Int?) -> Unit,
    onBeginClicked: () -> Unit,
) {
    // Fixed top bar like Home/Insights/Journal, so the header pattern and
    // the 8dp top content padding are uniform across all tabs. The title
    // used to scroll away as the first list item.
    Scaffold(
        // Nested inside MainTabScaffold's Scaffold: it already applies the
        // system-bar insets, so this one must not add them a second time.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.sleep_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.sleep_section_sounds),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

        items(state.layers, key = { it.layer.id }) { layerState ->
            SoundLayerCard(
                layerState = layerState,
                onToggled = { enabled -> onLayerToggled(layerState.layer.id, enabled) },
                onVolumeChanged = { volume -> onVolumeChanged(layerState.layer.id, volume) },
            )
        }

        item {
            Text(
                text = stringResource(R.string.sleep_section_timer),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SleepUiState.TIMER_OPTIONS.forEach { option ->
                    val selected = state.timerMinutes == option
                    FilterChip(
                        selected = selected,
                        onClick = { onTimerSelected(option) },
                        label = {
                            Text(
                                text =
                                    if (option == null) {
                                        stringResource(R.string.sleep_timer_infinite)
                                    } else {
                                        stringResource(R.string.sleep_timer_minutes, option)
                                    },
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            AuroraButton(
                text = stringResource(R.string.sleep_begin),
                onClick = onBeginClicked,
                gradient = true,
                enabled = state.canBegin,
                modifier = Modifier.fillMaxWidth().height(64.dp),
            )
            if (!state.canBegin) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.sleep_begin_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
        }
    }
}

@Composable
private fun SoundLayerCard(
    layerState: SleepLayerUiState,
    onToggled: (Boolean) -> Unit,
    onVolumeChanged: (Float) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
    ) {
        SoundLayerContent(
            layerState = layerState,
            onToggled = onToggled,
            onVolumeChanged = onVolumeChanged,
        )
    }
}

@Composable
private fun SoundLayerContent(
    layerState: SleepLayerUiState,
    onToggled: (Boolean) -> Unit,
    onVolumeChanged: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Filled.Waves,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(layerState.layer.nameRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(layerState.layer.descriptionRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = layerState.isEnabled,
                onCheckedChange = onToggled,
            )
        }
        if (layerState.isEnabled) {
            Spacer(Modifier.height(4.dp))
            Slider(
                value = layerState.volume,
                onValueChange = onVolumeChanged,
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
