package com.salvia.salviabrowxer.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salvia.salviabrowxer.R
import com.salvia.salviabrowxer.ui.theme.Gold
import com.salvia.salviabrowxer.ui.theme.Surface
import kotlinx.coroutines.flow.collectLatest

private enum class SettingsDialog { None, SearchEngine, Homepage, Downloads, ButtonSize, ButtonPosition, DownloadDirectory, ClearData }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf(SettingsDialog.None) }

    LaunchedEffect(Unit) {
        viewModel.messages.collectLatest { message ->
            if (message.isNotBlank()) snackbarHostState.showSnackbar(message)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.go_back),
                        tint = Gold
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = stringResource(R.string.settings),
                    style = MaterialTheme.typography.titleLarge,
                    color = Gold
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                SettingsSectionTitle(
                    icon = Icons.Default.Search,
                    title = stringResource(R.string.settings_browser)
                )

                SettingsItem(
                    icon = Icons.Default.Search,
                    title = stringResource(R.string.settings_search_engine),
                    subtitle = state.searchEngine,
                    onClick = { dialog = SettingsDialog.SearchEngine }
                )

                SettingsItem(
                    icon = Icons.Default.Settings,
                    title = stringResource(R.string.settings_homepage),
                    subtitle = state.homepage,
                    onClick = { dialog = SettingsDialog.Homepage }
                )

                SwitchSettingsItem(
                    icon = Icons.Default.Sync,
                    title = stringResource(R.string.settings_desktop_site),
                    subtitle = stringResource(R.string.settings_desktop_site_subtitle),
                    isChecked = state.isDesktopSite,
                    onCheckedChange = { viewModel.updateDesktopSite(it) }
                )

                SwitchSettingsItem(
                    icon = Icons.Default.Security,
                    title = stringResource(R.string.settings_javascript),
                    subtitle = stringResource(R.string.settings_javascript_subtitle),
                    isChecked = state.isJavaScriptEnabled,
                    onCheckedChange = { viewModel.updateJavaScriptEnabled(it) }
                )

                SwitchSettingsItem(
                    icon = Icons.Default.Storage,
                    title = stringResource(R.string.settings_cookies),
                    subtitle = stringResource(R.string.settings_cookies_subtitle),
                    isChecked = state.areCookiesEnabled,
                    onCheckedChange = { viewModel.updateCookiesEnabled(it) }
                )

                SettingsItem(
                    icon = Icons.Default.Clear,
                    title = stringResource(R.string.settings_clear_browsing_data),
                    subtitle = stringResource(R.string.settings_clear_browsing_data_subtitle),
                    onClick = { dialog = SettingsDialog.ClearData }
                )

                Spacer(modifier = Modifier.height(16.dp))

                SettingsSectionTitle(
                    icon = Icons.Default.Download,
                    title = stringResource(R.string.settings_downloads)
                )

                SettingsItem(
                    icon = Icons.Default.Folder,
                    title = stringResource(R.string.settings_download_directory),
                    subtitle = state.downloadDirectoryLabel,
                    onClick = { dialog = SettingsDialog.DownloadDirectory }
                )

                SettingsItem(
                    icon = Icons.Default.Download,
                    title = stringResource(R.string.settings_simultaneous_downloads),
                    subtitle = stringResource(R.string.settings_simultaneous_downloads_subtitle, state.maxSimultaneousDownloads),
                    onClick = { dialog = SettingsDialog.Downloads }
                )

                SwitchSettingsItem(
                    icon = Icons.Default.Wifi,
                    title = stringResource(R.string.settings_wifi_only),
                    subtitle = stringResource(R.string.settings_wifi_only_subtitle),
                    isChecked = state.isWifiOnly,
                    onCheckedChange = { viewModel.updateWifiOnly(it) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                SettingsSectionTitle(
                    icon = Icons.Default.Nightlight,
                    title = stringResource(R.string.settings_appearance)
                )

                SwitchSettingsItem(
                    icon = Icons.Default.Nightlight,
                    title = stringResource(R.string.settings_dark_theme),
                    subtitle = stringResource(R.string.settings_dark_theme_subtitle),
                    isChecked = state.isDarkTheme,
                    onCheckedChange = { viewModel.updateDarkTheme(it) }
                )

                SettingsItem(
                    icon = Icons.Default.Settings,
                    title = stringResource(R.string.settings_floating_button_size),
                    subtitle = stringResource(R.string.settings_floating_button_size_subtitle, state.floatingButtonSize),
                    onClick = { dialog = SettingsDialog.ButtonSize }
                )

                SettingsItem(
                    icon = Icons.Default.Settings,
                    title = stringResource(R.string.settings_floating_button_position),
                    subtitle = stringResource(R.string.settings_floating_button_position_subtitle),
                    onClick = { dialog = SettingsDialog.ButtonPosition }
                )

                Spacer(modifier = Modifier.height(16.dp))

                SettingsSectionTitle(
                    icon = Icons.Default.Security,
                    title = stringResource(R.string.settings_privacy)
                )

                SettingsItem(
                    icon = Icons.Default.Clear,
                    title = stringResource(R.string.settings_clear_history),
                    onClick = { viewModel.clearHistory() }
                )

                SettingsItem(
                    icon = Icons.Default.Clear,
                    title = stringResource(R.string.settings_clear_cookies),
                    onClick = { viewModel.clearCookies() }
                )

                Spacer(modifier = Modifier.height(16.dp))

                SettingsSectionTitle(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.settings_about)
                )

                SettingsItem(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.about_title),
                    subtitle = String.format(
                        stringResource(R.string.about_description),
                        stringResource(R.string.app_name)
                    ),
                    onClick = { }
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp)
        )
    }

    when (dialog) {
        SettingsDialog.None -> Unit

        SettingsDialog.SearchEngine -> SelectionDialog(
            title = stringResource(R.string.settings_search_engine),
            options = state.searchEngineOptions,
            selected = state.searchEngine,
            onDismiss = { dialog = SettingsDialog.None },
            onSelect = { engine ->
                viewModel.updateSearchEngine(engine)
                dialog = SettingsDialog.None
            }
        )

        SettingsDialog.Homepage -> TextEditDialog(
            title = stringResource(R.string.settings_homepage),
            initial = state.homepage,
            onDismiss = { dialog = SettingsDialog.None },
            onConfirm = { url ->
                viewModel.updateHomepage(url)
                dialog = SettingsDialog.None
            }
        )

        SettingsDialog.DownloadDirectory -> TextEditDialog(
            title = stringResource(R.string.settings_download_directory),
            initial = state.downloadDirectory,
            onDismiss = { dialog = SettingsDialog.None },
            onConfirm = { directory ->
                viewModel.updateDownloadDirectory(directory)
                dialog = SettingsDialog.None
            }
        )

        SettingsDialog.Downloads -> SliderDialog(
            title = stringResource(R.string.settings_simultaneous_downloads),
            value = state.maxSimultaneousDownloads.toFloat(),
            valueRange = 1f..5f,
            steps = 3,
            label = stringResource(R.string.settings_simultaneous_downloads_subtitle, state.maxSimultaneousDownloads),
            onDismiss = { dialog = SettingsDialog.None },
            onConfirm = { value ->
                viewModel.updateMaxSimultaneousDownloads(value.toInt())
                dialog = SettingsDialog.None
            }
        )

        SettingsDialog.ButtonSize -> SliderDialog(
            title = stringResource(R.string.settings_floating_button_size),
            value = state.floatingButtonSize.toFloat(),
            valueRange = 40f..72f,
            steps = 0,
            label = stringResource(R.string.settings_floating_button_size_subtitle, state.floatingButtonSize),
            onDismiss = { dialog = SettingsDialog.None },
            onConfirm = { value ->
                viewModel.updateFloatingButtonSize(value.toInt())
                dialog = SettingsDialog.None
            }
        )

        SettingsDialog.ButtonPosition -> ConfirmationDialog(
            title = stringResource(R.string.settings_floating_button_position),
            message = stringResource(R.string.settings_floating_button_position_message),
            confirmLabel = stringResource(R.string.action_reset),
            onDismiss = { dialog = SettingsDialog.None },
            onConfirm = {
                viewModel.updateFloatingButtonPosition(0f, 0f)
                dialog = SettingsDialog.None
            }
        )

        SettingsDialog.ClearData -> ConfirmationDialog(
            title = stringResource(R.string.settings_clear_browsing_data),
            message = stringResource(R.string.settings_clear_browsing_data_message),
            confirmLabel = stringResource(R.string.action_clear),
            onDismiss = { dialog = SettingsDialog.None },
            onConfirm = {
                viewModel.clearBrowsingData()
                dialog = SettingsDialog.None
            }
        )
    }
}

@Composable
fun SettingsSectionTitle(
    icon: ImageVector,
    title: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Gold,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Gold
        )
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Gold,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = Gold
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun SwitchSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!isChecked) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Gold,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = Gold
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = Gold,
                checkedTrackColor = Gold.copy(alpha = 0.5f),
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionDialog(
    title: String,
    options: List<String>,
    selected: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, color = Gold) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = option == selected, onClick = { onSelect(option) })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = option, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = Gold)
            }
        },
        containerColor = Surface
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextEditDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, color = Gold) },
        text = {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Gold),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text(stringResource(R.string.action_ok), color = Gold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = Gold)
            }
        },
        containerColor = Surface
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SliderDialog(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    label: String,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit
) {
    var current by remember { mutableStateOf(value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, color = Gold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = label, style = MaterialTheme.typography.bodyMedium, color = Gold)
                Slider(
                    value = current,
                    onValueChange = { current = it },
                    valueRange = valueRange,
                    steps = steps,
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = Gold,
                        activeTrackColor = Gold
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(current) }) {
                Text(stringResource(R.string.action_ok), color = Gold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = Gold)
            }
        },
        containerColor = Surface
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, color = Gold) },
        text = { Text(text = message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = Gold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = Gold)
            }
        },
        containerColor = Surface
    )
}
