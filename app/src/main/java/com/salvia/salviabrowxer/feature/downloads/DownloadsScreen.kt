package com.salvia.salviabrowxer.feature.downloads

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import com.salvia.salviabrowxer.ui.components.PasswordField
import com.salvia.salviabrowxer.ui.theme.CharcoalElevated
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salvia.salviabrowxer.R
import com.salvia.salviabrowxer.core.database.entities.DownloadEntity
import com.salvia.salviabrowxer.media.downloader.TranscodePlan
import com.salvia.salviabrowxer.ui.components.DownloadItem
import com.salvia.salviabrowxer.ui.theme.AuroraTeal
import com.salvia.salviabrowxer.ui.theme.CharcoalBorder
import com.salvia.salviabrowxer.ui.theme.DeepCharcoal
import com.salvia.salviabrowxer.ui.theme.MatteCharcoal
import com.salvia.salviabrowxer.ui.theme.NebulaVioletLight
import com.salvia.salviabrowxer.ui.theme.PearlEdgeBrush
import com.salvia.salviabrowxer.ui.theme.PearlWhite
import com.salvia.salviabrowxer.ui.theme.SilverMid
import com.salvia.salviabrowxer.ui.theme.TopBarBrush
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow

@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onPlayInApp: (String, String) -> Unit = { _, _ -> },
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    // The password prompt is state of this screen, not of the list: it is a modal question, and the
    // folder locking again on its own must close it.
    var showUnlock by remember { mutableStateOf(false) }
    val lockedFolder = state.lockedFolder
    LaunchedEffect(lockedFolder.isUnlocked) { if (lockedFolder.isUnlocked) showUnlock = false }

    LaunchedEffect(Unit) { viewModel.messages.collectLatest { message -> if (message.isNotBlank()) snackbarHostState.showSnackbar(message) } }
    LaunchedEffect(Unit) {
        viewModel.playRequest.receiveAsFlow().collectLatest { (path, title) -> onPlayInApp(path, title) }
    }
    // Android 9 and below needs WRITE_EXTERNAL_STORAGE to publish into the public Movies/Music
    // folders. The view model only asks when a user-initiated export actually requires it.
    val exportPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onExportPermissionResult(granted)
    }
    LaunchedEffect(Unit) {
        viewModel.exportPermissionRequest.collectLatest { permission ->
            if (permission.isNotBlank()) exportPermissionLauncher.launch(permission)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MatteCharcoal)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Nebula header with iridescent hairline underneath
            Column(modifier = Modifier.fillMaxWidth().background(TopBarBrush)) {
                Row(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.go_back), tint = PearlWhite) }
                    Spacer(Modifier.width(8.dp))
                    Text(text = stringResource(R.string.downloads_title), style = MaterialTheme.typography.titleLarge, color = PearlWhite)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = {
                        when (selectedTabIndex) { 0 -> viewModel.clearAllDownloads(); 2 -> viewModel.clearCompletedDownloads(); 3 -> viewModel.clearFailedDownloads() }
                    }) { Icon(Icons.Default.ClearAll, stringResource(R.string.action_clear), tint = SilverMid) }
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PearlEdgeBrush))
            }

            // Only drawn when there is something behind it: a lock row on an empty folder would be a
            // control that changes nothing, and an empty one would be a promise of a feature that was
            // never set up.
            if (lockedFolder.isConfigured && lockedFolder.lockedCount > 0) {
                LockedFolderBar(
                    isUnlocked = lockedFolder.isUnlocked,
                    count = lockedFolder.lockedCount,
                    onUnlockClick = { showUnlock = true },
                    onLockClick = { viewModel.lockFolder() }
                )
            }

            TabRow(
                selectedTabIndex = selectedTabIndex, containerColor = DeepCharcoal, contentColor = PearlWhite,
                indicator = { tabPositions -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]), color = NebulaVioletLight, height = 2.dp) },
                divider = { androidx.compose.material3.HorizontalDivider(color = CharcoalBorder.copy(alpha = 0.6f), thickness = 1.dp) }
            ) {
                Tab(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }, selectedContentColor = NebulaVioletLight, unselectedContentColor = SilverMid, text = { Text("${stringResource(R.string.download_active)} (${state.active.size})", style = MaterialTheme.typography.labelMedium) })
                Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }, selectedContentColor = NebulaVioletLight, unselectedContentColor = SilverMid, text = { Text("${stringResource(R.string.download_queue)} (${state.queued.size})", style = MaterialTheme.typography.labelMedium) })
                Tab(selected = selectedTabIndex == 2, onClick = { selectedTabIndex = 2 }, selectedContentColor = NebulaVioletLight, unselectedContentColor = SilverMid, text = { Text("${stringResource(R.string.download_completed)} (${state.completed.size})", style = MaterialTheme.typography.labelMedium) })
                Tab(selected = selectedTabIndex == 3, onClick = { selectedTabIndex = 3 }, selectedContentColor = NebulaVioletLight, unselectedContentColor = SilverMid, text = { Text("${stringResource(R.string.download_failed)} (${state.failed.size})", style = MaterialTheme.typography.labelMedium) })
            }

            val items = when (selectedTabIndex) { 0 -> state.active; 1 -> state.queued; 2 -> state.completed; else -> state.failed }
            val emptyMessage = when (selectedTabIndex) { 0 -> stringResource(R.string.no_active_downloads); 1 -> stringResource(R.string.no_queued_downloads); 2 -> stringResource(R.string.no_completed_downloads); else -> stringResource(R.string.no_failed_downloads) }

            if (items.isEmpty()) {
                EmptyDownloadsState(icon = Icons.Default.ClearAll, message = if (state.isLoading) stringResource(R.string.download_queue) else emptyMessage)
            } else {
                DownloadList(
                    items = items,
                    canLock = lockedFolder.isConfigured,
                    converting = state.converting,
                    viewModel = viewModel
                )
            }
        }
        if (showUnlock) {
            UnlockFolderDialog(
                isUnlocking = lockedFolder.isUnlocking,
                onDismiss = { showUnlock = false },
                onConfirm = { password -> viewModel.unlockFolder(password) }
            )
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp))
    }
}

/**
 * The one row that says the folder exists.
 *
 * It states the count and nothing else: how many files are hidden is not a secret, and the user needs
 * to know something is there before they can ask for it. Unlocking is offered once and locking again is
 * offered after — a session lock that cannot be re-locked without closing the app is a half lock.
 */
@Composable
private fun LockedFolderBar(isUnlocked: Boolean, count: Int, onUnlockClick: () -> Unit, onLockClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp)).background(CharcoalElevated).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
            contentDescription = null,
            tint = if (isUnlocked) AuroraTeal else SilverMid
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.downloads_locked_folder), style = MaterialTheme.typography.bodyMedium, color = PearlWhite)
            Text(
                text = stringResource(R.string.downloads_locked_count, count),
                style = MaterialTheme.typography.bodySmall,
                color = SilverMid
            )
        }
        TextButton(onClick = if (isUnlocked) onLockClick else onUnlockClick) {
            Text(
                text = stringResource(if (isUnlocked) R.string.action_lock_folder else R.string.action_unlock_folder),
                color = AuroraTeal
            )
        }
    }
}

@Composable
private fun UnlockFolderDialog(isUnlocking: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!isUnlocking) onDismiss() },
        title = { Text(stringResource(R.string.downloads_locked_folder), color = PearlWhite) },
        text = { PasswordField(value = password, onValueChange = { password = it }, hint = stringResource(R.string.locked_folder_password_label)) },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = !isUnlocking) {
                Text(stringResource(R.string.action_unlock_folder), color = AuroraTeal)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = SilverMid) }
        },
        containerColor = CharcoalElevated
    )
}

@Composable
private fun DownloadList(
    items: List<DownloadEntity>,
    canLock: Boolean,
    converting: Map<String, Int>,
    viewModel: DownloadsViewModel
) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 4.dp)) {
        items(items, key = { it.id }, contentType = { it.status }) { download ->
            DownloadItem(
                download = download,
                onOpenClick = {
                    val mime = download.mimeType ?: ""
                    if (mime.startsWith("video/") || mime.startsWith("audio/") || mime == "application/octet-stream") {
                        viewModel.playInApp(download.id)
                    } else {
                        viewModel.openDownload(download.id)
                    }
                },
                onPauseClick = { viewModel.pauseDownload(download.id) },
                onResumeClick = { viewModel.resumeDownload(download.id) },
                onCancelClick = { viewModel.cancelDownload(download.id) },
                onRetryClick = { viewModel.retryDownload(download.id) },
                onDeleteClick = { viewModel.deleteDownload(download.id) },
                onShareClick = { viewModel.shareDownload(download.id) },
                onExportClick = { viewModel.exportDownload(download.id) },
                onLockClick = if (canLock) ({ viewModel.setDownloadLocked(download.id, true) }) else null,
                onUnlockClick = if (canLock) ({ viewModel.setDownloadLocked(download.id, false) }) else null,
                // Only where the app can actually do something with the container: an MP4 row gets no
                // button at all, because there is nothing to convert.
                onConvertClick = if (TranscodePlan.isOffered(download.filename) && download.status == DownloadState.COMPLETED) {
                    ({ viewModel.convertDownload(download.id) })
                } else {
                    null
                },
                convertProgress = converting[download.id]
            )
        }
    }
}

@Composable
fun EmptyDownloadsState(icon: ImageVector, message: String) {
    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(28.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SilverMid.copy(alpha = 0.55f),
                modifier = Modifier.size(52.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = SilverMid
            )
        }
    }
}
