package com.sreecharan.stressrelief.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sreecharan.stressrelief.BuildConfig
import com.sreecharan.stressrelief.R
import com.sreecharan.stressrelief.data.model.QuietHours
import com.sreecharan.stressrelief.data.model.ThemeMode
import com.sreecharan.stressrelief.ui.components.AuroraCard
import com.sreecharan.stressrelief.ui.components.LoadingSkeleton
import com.sreecharan.stressrelief.ui.components.SectionHeader
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Stateful route: owns the ViewModel. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // A denial must be honest: the toggle cannot stay ON while the OS
            // permission is missing, or reminders silently never fire.
            if (!granted) viewModel.setRemindersEnabled(false)
        }

    // Self-heal installs that persisted reminders ON without the OS permission
    // (the old ON-by-default): show the toggle OFF so flipping it ON fires the
    // permission prompt at that moment.
    val readyState = state as? SettingsUiState.Ready
    LaunchedEffect(readyState?.remindersEnabled) {
        if (readyState != null && readyState.remindersEnabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.setRemindersEnabled(false)
        }
    }

    SettingsContent(
        state = state,
        onBack = onBack,
        onThemeModeSelected = viewModel::setThemeMode,
        onDynamicColorChanged = viewModel::setDynamicColor,
        onRemindersChanged = { enabled ->
            if (enabled &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            viewModel.setRemindersEnabled(enabled)
        },
        onQuietHoursChanged = viewModel::setQuietHours,
        onResetConfirmed = viewModel::resetAllData,
    )
}

/** Stateless content: state in, events out. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    state: SettingsUiState,
    onBack: () -> Unit,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onRemindersChanged: (Boolean) -> Unit,
    onQuietHoursChanged: (QuietHours) -> Unit,
    onResetConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    // null = closed, true = editing start, false = editing end
    var editingQuietHour by remember { mutableStateOf<Boolean?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineMedium,
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
            )
        },
    ) { innerPadding ->
        when (val s = state) {
            SettingsUiState.Loading -> {
                LoadingSkeleton(modifier = Modifier.padding(innerPadding).padding(20.dp))
            }

            is SettingsUiState.Ready -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        SectionHeader(
                            title = stringResource(R.string.settings_section_appearance),
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    item {
                        AuroraCard(modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                            ThemeModeRow(
                                mode = ThemeMode.SYSTEM,
                                title = stringResource(R.string.settings_theme_system),
                                subtitle = stringResource(R.string.settings_theme_system_sub),
                                selected = s.themeMode == ThemeMode.SYSTEM,
                                onSelect = { onThemeModeSelected(ThemeMode.SYSTEM) },
                            )
                            ThemeModeRow(
                                mode = ThemeMode.LIGHT,
                                title = stringResource(R.string.settings_theme_light),
                                subtitle = stringResource(R.string.settings_theme_light_sub),
                                selected = s.themeMode == ThemeMode.LIGHT,
                                onSelect = { onThemeModeSelected(ThemeMode.LIGHT) },
                            )
                            ThemeModeRow(
                                mode = ThemeMode.DARK,
                                title = stringResource(R.string.settings_theme_dark),
                                subtitle = stringResource(R.string.settings_theme_dark_sub),
                                selected = s.themeMode == ThemeMode.DARK,
                                onSelect = { onThemeModeSelected(ThemeMode.DARK) },
                            )
                            SettingsSwitchRow(
                                icon = Icons.Filled.Wallpaper,
                                title = stringResource(R.string.settings_dynamic_color_title),
                                subtitle = stringResource(R.string.settings_dynamic_color_sub),
                                checked = s.dynamicColor,
                                onCheckedChange = onDynamicColorChanged,
                            )
                        }
                    }

                    item { SectionHeader(title = stringResource(R.string.settings_section_reminders)) }
                    item {
                        AuroraCard(modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                            SettingsSwitchRow(
                                icon = Icons.Filled.NotificationsActive,
                                title = stringResource(R.string.settings_reminders_title),
                                subtitle = stringResource(R.string.settings_reminders_sub),
                                checked = s.remindersEnabled,
                                onCheckedChange = onRemindersChanged,
                            )
                            SettingsActionRow(
                                icon = Icons.Filled.NightsStay,
                                title = stringResource(R.string.settings_quiet_hours_start_label),
                                subtitle = formatTime(s.quietHours.startHour, s.quietHours.startMin),
                                onClick = { editingQuietHour = true },
                            )
                            SettingsActionRow(
                                icon = Icons.Filled.NightsStay,
                                title = stringResource(R.string.settings_quiet_hours_end_label),
                                subtitle = formatTime(s.quietHours.endHour, s.quietHours.endMin),
                                onClick = { editingQuietHour = false },
                            )
                        }
                    }

                    item { SectionHeader(title = stringResource(R.string.settings_section_privacy)) }
                    item {
                        AuroraCard(modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                            SettingsActionRow(
                                icon = Icons.Filled.PrivacyTip,
                                title = stringResource(R.string.settings_privacy_title),
                                subtitle = stringResource(R.string.settings_privacy_sub),
                                onClick = { showPrivacyDialog = true },
                            )
                        }
                    }

                    item { SectionHeader(title = stringResource(R.string.settings_section_data)) }
                    item {
                        AuroraCard(modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                            SettingsActionRow(
                                icon = Icons.Filled.DeleteForever,
                                title = stringResource(R.string.settings_reset_title),
                                subtitle = stringResource(R.string.settings_reset_sub),
                                onClick = { showResetDialog = true },
                                destructive = true,
                            )
                        }
                    }

                    item { SectionHeader(title = stringResource(R.string.settings_section_about)) }
                    item {
                        AuroraCard(modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                            SettingsInfoRow(
                                icon = Icons.Filled.Info,
                                title = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text(stringResource(R.string.settings_privacy_title)) },
            text = { Text(stringResource(R.string.settings_privacy_body)) },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text(stringResource(R.string.settings_quiet_hours_confirm))
                }
            },
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.settings_reset_dialog_title)) },
            text = { Text(stringResource(R.string.settings_reset_dialog_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        onResetConfirmed()
                    },
                ) {
                    Text(
                        stringResource(R.string.settings_reset_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.settings_reset_cancel))
                }
            },
        )
    }

    val ready = state as? SettingsUiState.Ready
    val editing = editingQuietHour
    if (ready != null && editing != null) {
        val initial =
            if (editing) {
                ready.quietHours.startHour to ready.quietHours.startMin
            } else {
                ready.quietHours.endHour to ready.quietHours.endMin
            }
        val pickerState =
            rememberTimePickerState(
                initialHour = initial.first,
                initialMinute = initial.second,
                is24Hour = false,
            )
        AlertDialog(
            onDismissRequest = { editingQuietHour = null },
            title = {
                Text(
                    stringResource(
                        if (editing) {
                            R.string.settings_quiet_hours_start_label
                        } else {
                            R.string.settings_quiet_hours_end_label
                        },
                    ),
                )
            },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val updated =
                            if (editing) {
                                ready.quietHours.copy(startHour = pickerState.hour, startMin = pickerState.minute)
                            } else {
                                ready.quietHours.copy(endHour = pickerState.hour, endMin = pickerState.minute)
                            }
                        onQuietHoursChanged(updated)
                        editingQuietHour = null
                    },
                ) {
                    Text(stringResource(R.string.settings_quiet_hours_confirm))
                }
            },
        )
    }
}

@Composable
private fun ThemeModeRow(
    mode: ThemeMode,
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect)
                .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector =
                when (mode) {
                    ThemeMode.SYSTEM -> Icons.Filled.Palette
                    ThemeMode.LIGHT -> Icons.Filled.LightMode
                    ThemeMode.DARK -> Icons.Filled.DarkMode
                },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RadioButton(selected = selected, onClick = onSelect)
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsInfoRow(
    icon: ImageVector,
    title: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

/** Locale-aware short time (e.g. 10:00 PM); never hardcodes AM/PM markers. */
private fun formatTime(
    hour: Int,
    minute: Int,
): String {
    val formatter = DateTimeFormatter.ofPattern("h:mm a")
    return LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59)).format(formatter)
}
