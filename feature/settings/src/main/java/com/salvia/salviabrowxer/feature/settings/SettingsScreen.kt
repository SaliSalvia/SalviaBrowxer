package com.salvia.salviabrowxer.feature.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.salvia.salviabrowxer.R
import com.salvia.salviabrowxer.ui.theme.Gold
import com.salvia.salviabrowxer.ui.theme.Surface

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val scrollState = rememberScrollState()

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

            Spacer(modifier = Modifier.padding(8.dp))

            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.titleLarge,
                color = Gold
            )
        }

        Divider(color = MaterialTheme.colorScheme.surfaceVariant)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            SettingsSectionTitle(
                icon = Icons.Default.Search,
                title = stringResource(R.string.settings_browser)
            )

            SettingsItem(
                icon = Icons.Default.Search,
                title = stringResource(R.string.settings_search_engine),
                subtitle = viewModel.searchEngine.value,
                onClick = { /* TODO: Show search engine selection */ }
            )

            SettingsItem(
                icon = Icons.Default.Settings,
                title = stringResource(R.string.settings_homepage),
                subtitle = viewModel.homepage.value,
                onClick = { /* TODO: Show homepage editor */ }
            )

            SwitchSettingsItem(
                icon = Icons.Default.Sync,
                title = stringResource(R.string.settings_desktop_site),
                isChecked = viewModel.isDesktopSite.value,
                onCheckedChange = { viewModel.updateDesktopSite(it) }
            )

            SwitchSettingsItem(
                icon = Icons.Default.Security,
                title = stringResource(R.string.settings_javascript),
                isChecked = viewModel.isJavaScriptEnabled.value,
                onCheckedChange = { viewModel.updateJavaScriptEnabled(it) }
            )

            SwitchSettingsItem(
                icon = Icons.Default.Storage,
                title = stringResource(R.string.settings_cookies),
                isChecked = viewModel.areCookiesEnabled.value,
                onCheckedChange = { viewModel.updateCookiesEnabled(it) }
            )

            SettingsItem(
                icon = Icons.Default.Clear,
                title = stringResource(R.string.settings_clear_browsing_data),
                onClick = { /* TODO: Show clear browsing data dialog */ }
            )

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSectionTitle(
                icon = Icons.Default.Folder,
                title = stringResource(R.string.settings_downloads)
            )

            SettingsItem(
                icon = Icons.Default.Folder,
                title = stringResource(R.string.settings_download_directory),
                subtitle = viewModel.downloadDirectory.value.ifEmpty { "Downloads" },
                onClick = { /* TODO: Show directory picker */ }
            )

            SettingsItem(
                icon = Icons.Default.Sync,
                title = stringResource(R.string.settings_simultaneous_downloads),
                subtitle = viewModel.maxSimultaneousDownloads.value.toString(),
                onClick = { /* TODO: Show slider for max downloads */ }
            )

            SwitchSettingsItem(
                icon = Icons.Default.Wifi,
                title = stringResource(R.string.settings_wifi_only),
                isChecked = viewModel.isWifiOnly.value,
                onCheckedChange = { viewModel.updateWifiOnly(it) }
            )

            SettingsItem(
                icon = Icons.Default.Security,
                title = stringResource(R.string.settings_notifications),
                onClick = { /* TODO: Show notification settings */ }
            )

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSectionTitle(
                icon = Icons.Default.Nightlight,
                title = stringResource(R.string.settings_appearance)
            )

            SwitchSettingsItem(
                icon = Icons.Default.Nightlight,
                title = stringResource(R.string.settings_dark_theme),
                isChecked = viewModel.isDarkTheme.value,
                onCheckedChange = { viewModel.updateDarkTheme(it) }
            )

            SettingsItem(
                icon = Icons.Default.Settings,
                title = stringResource(R.string.settings_floating_button_size),
                subtitle = "${viewModel.floatingButtonSize.value}dp",
                onClick = { /* TODO: Show size slider */ }
            )

            SettingsItem(
                icon = Icons.Default.Settings,
                title = stringResource(R.string.settings_floating_button_position),
                subtitle = "Custom",
                onClick = { /* Position is updated via drag */ }
            )

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSectionTitle(
                icon = Icons.Default.Security,
                title = stringResource(R.string.settings_privacy)
            )

            SwitchSettingsItem(
                icon = Icons.Default.Security,
                title = stringResource(R.string.settings_private_browsing),
                isChecked = false,
                onCheckedChange = { /* TODO: Update private browsing */ }
            )

            SettingsItem(
                icon = Icons.Default.Clear,
                title = stringResource(R.string.settings_clear_history),
                onClick = { /* TODO: Clear history */ }
            )

            SettingsItem(
                icon = Icons.Default.Clear,
                title = stringResource(R.string.settings_clear_cookies),
                onClick = { /* TODO: Clear cookies */ }
            )

            SettingsItem(
                icon = Icons.Default.Clear,
                title = stringResource(R.string.settings_clear_cache),
                onClick = { /* TODO: Clear cache */ }
            )

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSectionTitle(
                icon = Icons.Default.Info,
                title = stringResource(R.string.settings_about)
            )

            SettingsItem(
                icon = Icons.Default.Info,
                title = stringResource(R.string.about_title),
                onClick = { /* TODO: Show about dialog */ }
            )
        }
    }
}

@Composable
fun SettingsSectionTitle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Gold,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = Gold
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
        Icon(
            imageVector = Icons.Default.ArrowDropDown,
            contentDescription = null,
            tint = Gold
        )
    }
}

@Composable
fun SwitchSettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = { onCheckedChange(!isChecked) })
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Gold,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = Gold,
            modifier = Modifier.weight(1f)
        )
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