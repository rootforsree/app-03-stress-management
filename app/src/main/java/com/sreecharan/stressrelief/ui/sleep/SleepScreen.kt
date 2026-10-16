package com.sreecharan.stressrelief.ui.sleep

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sreecharan.stressrelief.R
import com.sreecharan.stressrelief.ui.components.AuroraButton

/**
 * Sleep mixer (SleepRoute): toggleable generated sound layers with per-layer
 * volume, a timer picker, and the big gradient "Begin wind-down" button.
 * Premium layers show a lock and route to the paywall.
 */
@Composable
fun SleepScreen(
    onNavigateToSession: () -> Unit,
    onOpenPaywall: () -> Unit,
    viewModel: SleepViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SleepEvent.NavigateToSession -> onNavigateToSession()
                SleepEvent.OpenPaywall -> onOpenPaywall()
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
                onUpsellClicked = viewModel::onPremiumUpsellClicked,
                onBeginClicked = viewModel::onBeginClicked,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SleepMixerContent(
    state: SleepUiState.Content,
    onLayerToggled: (String, Boolean) -> Unit,
    onVolumeChanged: (String, Float) -> Unit,
    onTimerSelected: (Int?) -> Unit,
    onUpsellClicked: () -> Unit,
    onBeginClicked: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text(
                    text = stringResource(R.string.sleep_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.sleep_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

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

        if (!state.isPremium && !state.billingLoading) {
            item {
                PremiumUpsellCard(onClicked = onUpsellClicked)
            }
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

@Composable
private fun SoundLayerCard(
    layerState: SleepLayerUiState,
    onToggled: (Boolean) -> Unit,
    onVolumeChanged: (Float) -> Unit,
) {
    val locked = !layerState.isUnlocked
    val shape = RoundedCornerShape(22.dp)
    val colors =
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        )
    // A locked layer is one tappable target (role + ripple handled by M3)
    // that opens the paywall; unlocked layers are plain cards whose Switch
    // is the only control.
    if (locked) {
        val cd =
            stringResource(
                R.string.sleep_layer_locked_cd,
                stringResource(layerState.layer.nameRes),
            )
        Card(
            onClick = { onToggled(true) },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = cd },
            shape = shape,
            colors = colors,
        ) {
            SoundLayerContent(
                layerState = layerState,
                onToggled = onToggled,
                onVolumeChanged = onVolumeChanged,
            )
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            colors = colors,
        ) {
            SoundLayerContent(
                layerState = layerState,
                onToggled = onToggled,
                onVolumeChanged = onVolumeChanged,
            )
        }
    }
}

@Composable
private fun SoundLayerContent(
    layerState: SleepLayerUiState,
    onToggled: (Boolean) -> Unit,
    onVolumeChanged: (Float) -> Unit,
) {
    val locked = !layerState.isUnlocked
    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = if (locked) Icons.Filled.Lock else Icons.Filled.Waves,
                contentDescription = null,
                tint =
                    if (locked) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(layerState.layer.nameRes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (layerState.layer.isPremium) {
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = stringResource(R.string.sleep_premium_badge),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        MaterialTheme.colorScheme.tertiaryContainer,
                                    ).padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    text = stringResource(layerState.layer.descriptionRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = layerState.isEnabled && layerState.isUnlocked,
                onCheckedChange = onToggled,
                enabled = layerState.isUnlocked,
            )
        }
        if (layerState.isEnabled && layerState.isUnlocked) {
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

@Composable
private fun PremiumUpsellCard(onClicked: () -> Unit) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClicked),
        shape = RoundedCornerShape(22.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.sleep_premium_upsell_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = stringResource(R.string.sleep_premium_upsell_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Text(
                text = stringResource(R.string.sleep_premium_upsell_cta),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}
