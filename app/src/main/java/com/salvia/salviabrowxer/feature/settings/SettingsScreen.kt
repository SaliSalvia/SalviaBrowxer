package com.salvia.salviabrowxer.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.ConfigurationCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salvia.salviabrowxer.BuildConfig
import com.salvia.salviabrowxer.R
import com.salvia.salviabrowxer.service.ConnectionKind
import com.salvia.salviabrowxer.service.ConnectivityGate
import com.salvia.salviabrowxer.ui.utils.Constants
import com.salvia.salviabrowxer.ui.components.OrbitalBrandMark
import com.salvia.salviabrowxer.ui.theme.AuroraTeal
import com.salvia.salviabrowxer.ui.theme.BlushPink
import com.salvia.salviabrowxer.ui.theme.BlushPinkLight
import com.salvia.salviabrowxer.ui.theme.CharcoalBorder
import com.salvia.salviabrowxer.ui.theme.CharcoalElevated
import com.salvia.salviabrowxer.ui.theme.CharcoalSurface
import com.salvia.salviabrowxer.ui.theme.MatteCharcoal
import com.salvia.salviabrowxer.ui.theme.NebulaEdge
import com.salvia.salviabrowxer.ui.theme.NebulaViolet
import com.salvia.salviabrowxer.ui.theme.NebulaMist
import com.salvia.salviabrowxer.ui.theme.NebulaVioletLight
import com.salvia.salviabrowxer.ui.theme.PearlEdgeBrush
import com.salvia.salviabrowxer.ui.theme.PearlWhite
import com.salvia.salviabrowxer.ui.theme.SilverMid
import com.salvia.salviabrowxer.ui.theme.TopBarBrush
import java.util.Locale
import kotlinx.coroutines.flow.collectLatest

private enum class SettingsDialog { None, SearchEngine, Homepage, Downloads, ButtonSize, ClearData }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf(SettingsDialog.None) }
    val context = LocalContext.current
    // Live connection readout. The app browses and downloads on whatever network the phone has, so
    // the Wi-Fi-only switch shows what is actually active instead of asking the user to guess.
    val connectivityGate = remember(context) { ConnectivityGate(context) }
    val connectionFlow = remember(connectivityGate) { connectivityGate.observeConnection() }
    val connectionKind by connectionFlow.collectAsStateWithLifecycle(initialValue = ConnectionKind.OFFLINE)
    // About rows open real destinations; nothing here is a placeholder link.
    val openUrl: (String) -> Unit = remember(context) {
        { url ->
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
            Unit
        }
    }

    // The app language is the device language, or an app-only one chosen on Android 13+. The row
    // only exists where the picker does, so it is never a control that cannot act.
    val locales = ConfigurationCompat.getLocales(LocalConfiguration.current)
    val languageLabel = remember(locales) {
        val locale = if (locales.isEmpty) Locale.getDefault() else locales[0] ?: Locale.getDefault()
        locale.getDisplayName(locale).replaceFirstChar { it.uppercase() }
    }
    val openAppLanguageSettings: (() -> Unit)? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        remember(context) {
            {
                runCatching {
                    context.startActivity(
                        Intent(
                            android.provider.Settings.ACTION_APP_LOCALE_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                    )
                }
                Unit
            }
        }
    } else {
        null
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collectLatest { m ->
            if (m.isNotBlank()) snackbarHostState.showSnackbar(m)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(MatteCharcoal)) {
            Column(modifier = Modifier.fillMaxWidth().background(TopBarBrush)) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.go_back), tint = PearlWhite)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge, color = PearlWhite)
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PearlEdgeBrush))
            }
            // Brand strip: mark plus app name, so the header reads as app chrome.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OrbitalBrandMark(size = 34.dp, animate = false)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    color = PearlWhite,
                    letterSpacing = 2.sp
                )
            }
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                SettingsSectionTitle(Icons.Default.Search, stringResource(R.string.settings_browser))
                SettingsItem(Icons.Default.Search, stringResource(R.string.settings_search_engine), state.searchEngine) { dialog = SettingsDialog.SearchEngine }
                SettingsItem(Icons.Default.Settings, stringResource(R.string.settings_homepage), state.homepage) { dialog = SettingsDialog.Homepage }
                SwitchSettingsItem(Icons.Default.Sync, stringResource(R.string.settings_desktop_site), stringResource(R.string.settings_desktop_site_hint), state.isDesktopSite) { viewModel.updateDesktopSite(it) }
                SwitchSettingsItem(Icons.Default.Security, stringResource(R.string.settings_javascript), stringResource(R.string.settings_javascript_hint), state.isJavaScriptEnabled) { viewModel.updateJavaScriptEnabled(it) }
                SwitchSettingsItem(Icons.Default.Storage, stringResource(R.string.settings_cookies), stringResource(R.string.settings_cookies_hint), state.areCookiesEnabled) { viewModel.updateCookiesEnabled(it) }
                SettingsItem(Icons.Default.Clear, stringResource(R.string.settings_clear_browsing_data), stringResource(R.string.settings_clear_browsing_data_hint)) { dialog = SettingsDialog.ClearData }
                if (openAppLanguageSettings != null) {
                    SettingsItem(
                        Icons.Default.Language,
                        stringResource(R.string.settings_language),
                        "$languageLabel · ${stringResource(R.string.settings_language_hint)}",
                        onClick = openAppLanguageSettings
                    )
                }
                Spacer(Modifier.height(16.dp))
                SettingsSectionTitle(Icons.Default.Download, stringResource(R.string.settings_downloads))
                SettingsItem(Icons.Default.Folder, stringResource(R.string.settings_download_directory), state.downloadDirectoryPath.ifBlank { stringResource(R.string.settings_download_directory_hint) })
                SettingsItem(Icons.Default.Download, stringResource(R.string.settings_simultaneous_downloads), stringResource(R.string.settings_downloads_at_a_time, state.maxSimultaneousDownloads)) { dialog = SettingsDialog.Downloads }
                SwitchSettingsItem(Icons.Default.Wifi, stringResource(R.string.settings_wifi_only), stringResource(R.string.settings_wifi_only_hint), state.isWifiOnly) { viewModel.updateWifiOnly(it) }
                SwitchSettingsItem(Icons.Default.Folder, stringResource(R.string.settings_export_to_gallery), stringResource(R.string.settings_export_to_gallery_hint), state.isExportToGallery) { viewModel.updateExportToGallery(it) }
                SettingsItem(Icons.Default.Wifi, stringResource(R.string.settings_connection_label), connectionLabel(connectionKind))
                Spacer(Modifier.height(16.dp))
                SettingsSectionTitle(Icons.Default.Nightlight, stringResource(R.string.settings_appearance))
                SwitchSettingsItem(
                    Icons.Default.TouchApp,
                    stringResource(R.string.settings_floating_button_always),
                    stringResource(R.string.settings_floating_button_always_hint),
                    state.isFloatingButtonAlwaysVisible
                ) { viewModel.updateFloatingButtonAlwaysVisible(it) }
                SettingsItem(Icons.Default.Settings, stringResource(R.string.settings_floating_button_size), stringResource(R.string.settings_dp_value, state.floatingButtonSize)) { dialog = SettingsDialog.ButtonSize }
                SettingsItem(Icons.Default.TouchApp, stringResource(R.string.settings_floating_button_position), stringResource(R.string.settings_floating_button_position_hint))
                Spacer(Modifier.height(16.dp))
                SettingsSectionTitle(Icons.Default.Security, stringResource(R.string.settings_privacy))
                SettingsItem(Icons.Default.Clear, stringResource(R.string.settings_clear_history)) { viewModel.clearHistory() }
                SettingsItem(Icons.Default.Clear, stringResource(R.string.settings_clear_cookies)) { viewModel.clearCookies() }
                SwitchSettingsItem(Icons.Default.Security, stringResource(R.string.settings_allow_cleartext), stringResource(R.string.settings_allow_cleartext_hint), state.isCleartextAllowed) { viewModel.updateCleartextAllowed(it) }
                Spacer(Modifier.height(16.dp))
                SettingsSectionTitle(Icons.Default.Info, stringResource(R.string.settings_about))
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                    OrbitalBrandMark(size = 84.dp, animate = true)
                }
                Text(
                    text = stringResource(R.string.brand_tagline),
                    style = MaterialTheme.typography.bodySmall,
                    color = SilverMid,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)
                )
                SettingsItem(Icons.Default.Info, stringResource(R.string.about_title), stringResource(R.string.about_version, BuildConfig.VERSION_NAME))
                SettingsItem(Icons.Default.Info, stringResource(R.string.about_license), "GPL-3.0") { openUrl(Constants.LICENSE_URL) }
                SettingsItem(Icons.Default.Security, stringResource(R.string.about_privacy_policy)) { openUrl(Constants.PRIVACY_POLICY_URL) }
                SettingsItem(Icons.Default.Search, stringResource(R.string.about_source_code)) { openUrl(Constants.SOURCE_CODE_URL) }
                Spacer(Modifier.height(32.dp))
            }
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp))
    }

    when (dialog) {
        SettingsDialog.None -> Unit
        SettingsDialog.SearchEngine -> SelectionDialog(
            title = stringResource(R.string.settings_search_engine),
            options = state.searchEngineOptions,
            selected = state.searchEngine,
            onDismiss = { dialog = SettingsDialog.None },
            onSelect = { e -> viewModel.updateSearchEngine(e); dialog = SettingsDialog.None }
        )
        SettingsDialog.Homepage -> TextEditDialog(
            title = stringResource(R.string.settings_homepage),
            initial = state.homepage,
            onDismiss = { dialog = SettingsDialog.None },
            onConfirm = { url -> viewModel.updateHomepage(url); dialog = SettingsDialog.None }
        )
        SettingsDialog.Downloads -> SliderDialog(
            title = stringResource(R.string.settings_simultaneous_downloads),
            value = state.maxSimultaneousDownloads.toFloat(),
            valueRange = 1f..5f,
            steps = 3,
            label = stringResource(R.string.settings_downloads_at_a_time, state.maxSimultaneousDownloads),
            onDismiss = { dialog = SettingsDialog.None },
            onConfirm = { v -> viewModel.updateMaxSimultaneousDownloads(v.toInt()); dialog = SettingsDialog.None }
        )
        SettingsDialog.ButtonSize -> SliderDialog(
            title = stringResource(R.string.settings_floating_button_size),
            value = state.floatingButtonSize.toFloat(),
            valueRange = 40f..72f,
            steps = 0,
            label = stringResource(R.string.settings_dp_value, state.floatingButtonSize),
            onDismiss = { dialog = SettingsDialog.None },
            onConfirm = { v -> viewModel.updateFloatingButtonSize(v.toInt()); dialog = SettingsDialog.None }
        )
        SettingsDialog.ClearData -> ConfirmationDialog(
            title = stringResource(R.string.settings_clear_browsing_data),
            message = stringResource(R.string.settings_clear_browsing_data_hint),
            confirmLabel = stringResource(R.string.action_clear),
            onDismiss = { dialog = SettingsDialog.None },
            onConfirm = { viewModel.clearBrowsingData(); dialog = SettingsDialog.None }
        )
    }
}

/** The live connection, phrased as what it means for a download rather than as a radio name. */
@Composable
private fun connectionLabel(kind: ConnectionKind): String = stringResource(
    when (kind) {
        ConnectionKind.WIFI -> R.string.settings_connection_wifi
        ConnectionKind.MOBILE -> R.string.settings_connection_mobile
        ConnectionKind.OTHER -> R.string.settings_connection_other
        ConnectionKind.OFFLINE -> R.string.settings_connection_offline
    }
)

@Composable
fun SettingsSectionTitle(icon: ImageVector, title: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = BlushPink, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = PearlWhite)
    }
}

@Composable
fun SettingsItem(icon: ImageVector, title: String, subtitle: String? = null, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(NebulaMist.copy(alpha = 0.45f))
            .border(0.8.dp, NebulaEdge.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = 11.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = BlushPinkLight, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = PearlWhite)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SilverMid)
        }
    }
}

@Composable
fun SwitchSettingsItem(icon: ImageVector, title: String, subtitle: String? = null, isChecked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(NebulaMist.copy(alpha = 0.45f))
            .border(0.8.dp, NebulaEdge.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            // The row owns the action. The switch below is a state indicator only.
            .toggleable(value = isChecked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 9.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = BlushPinkLight, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = PearlWhite)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SilverMid)
        }
        // `onCheckedChange = null` is what keeps TalkBack from offering the same toggle twice:
        // taps land on the row's toggleable instead of a second, separate switch control.
        Switch(
            checked = isChecked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PearlWhite,
                checkedTrackColor = NebulaViolet,
                checkedBorderColor = NebulaVioletLight,
                uncheckedThumbColor = SilverMid,
                uncheckedTrackColor = CharcoalSurface,
                uncheckedBorderColor = CharcoalBorder
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
        title = { Text(title, color = PearlWhite) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(
                                selected = option == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(option) }
                            )
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option == selected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = AuroraTeal, unselectedColor = SilverMid)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(option, style = MaterialTheme.typography.bodyLarge, color = PearlWhite)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = AuroraTeal) }
        },
        containerColor = CharcoalElevated,
        titleContentColor = PearlWhite,
        textContentColor = PearlWhite
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
        title = { Text(title, color = PearlWhite) },
        text = {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(color = PearlWhite),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(AuroraTeal),
                // The dialog title names the value being edited, so the field carries it too.
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CharcoalSurface, RoundedCornerShape(10.dp))
                    .padding(12.dp)
                    .semantics { contentDescription = title }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.action_ok), color = AuroraTeal) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = SilverMid) }
        },
        containerColor = CharcoalElevated
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
        title = { Text(title, color = PearlWhite) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = PearlWhite)
                Slider(
                    value = current,
                    onValueChange = { current = it },
                    valueRange = valueRange,
                    steps = steps,
                    colors = SliderDefaults.colors(
                        thumbColor = AuroraTeal,
                        activeTrackColor = AuroraTeal,
                        inactiveTrackColor = CharcoalSurface
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(current) }) { Text(stringResource(R.string.action_ok), color = AuroraTeal) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = SilverMid) }
        },
        containerColor = CharcoalElevated
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
        title = { Text(title, color = PearlWhite) },
        text = { Text(message, color = SilverMid) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel, color = AuroraTeal) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = SilverMid) }
        },
        containerColor = CharcoalElevated
    )
}
