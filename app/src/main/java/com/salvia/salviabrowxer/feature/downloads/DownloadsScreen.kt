package com.salvia.salviabrowxer.feature.downloads

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salvia.salviabrowxer.R
import com.salvia.salviabrowxer.core.database.entities.DownloadEntity
import com.salvia.salviabrowxer.ui.components.DownloadItem
import com.salvia.salviabrowxer.ui.theme.Gold
import com.salvia.salviabrowxer.ui.theme.Surface
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showClearConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.messages.collectLatest { message ->
            if (message.isNotBlank()) snackbarHostState.showSnackbar(message)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                    text = stringResource(R.string.downloads_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = Gold
                )

                Spacer(modifier = Modifier.weight(1f))

                IconButton(onClick = {
                    if (itemsForTab(selectedTabIndex, state).isNotEmpty()) {
                        showClearConfirmation = true
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.ClearAll,
                        contentDescription = stringResource(R.string.action_clear),
                        tint = Gold
                    )
                }
            }

            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Surface,
                contentColor = Gold
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("${stringResource(R.string.download_active)} (${state.active.size})") }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("${stringResource(R.string.download_queue)} (${state.queued.size})") }
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    text = { Text("${stringResource(R.string.download_completed)} (${state.completed.size})") }
                )
                Tab(
                    selected = selectedTabIndex == 3,
                    onClick = { selectedTabIndex = 3 },
                    text = { Text("${stringResource(R.string.download_failed)} (${state.failed.size})") }
                )
            }

            val emptyMessage = when (selectedTabIndex) {
                0 -> stringResource(R.string.no_active_downloads)
                1 -> stringResource(R.string.no_queued_downloads)
                2 -> stringResource(R.string.no_completed_downloads)
                else -> stringResource(R.string.no_failed_downloads)
            }
            val items = itemsForTab(selectedTabIndex, state)

            if (items.isEmpty()) {
                EmptyDownloadsState(
                    icon = Icons.Default.ClearAll,
                    message = if (state.isLoading) {
                        stringResource(R.string.download_queue)
                    } else {
                        emptyMessage
                    }
                )
            } else {
                DownloadList(items = items, viewModel = viewModel)
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp)
        )
    }

    if (showClearConfirmation) {
        val scopeLabel = when (selectedTabIndex) {
            1 -> stringResource(R.string.download_queue)
            2 -> stringResource(R.string.download_completed)
            3 -> stringResource(R.string.download_failed)
            else -> stringResource(R.string.downloads_title)
        }
        val hasItems = itemsForTab(selectedTabIndex, state).isNotEmpty()
        if (hasItems) {
            AlertDialog(
                onDismissRequest = { showClearConfirmation = false },
                title = { Text(text = stringResource(R.string.settings_clear_browsing_data), color = Gold) },
                text = {
                    Text(
                        stringResource(
                            R.string.clear_download_confirmation,
                            scopeLabel
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        when (selectedTabIndex) {
                            1 -> viewModel.clearAllDownloads()
                            2 -> viewModel.clearCompletedDownloads()
                            3 -> viewModel.clearFailedDownloads()
                            else -> viewModel.clearAllDownloads()
                        }
                        showClearConfirmation = false
                    }) {
                        Text(stringResource(R.string.action_clear), color = Gold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirmation = false }) {
                        Text(stringResource(R.string.action_cancel), color = Gold)
                    }
                },
                containerColor = Surface
            )
        }
    }
}

private fun itemsForTab(
    tabIndex: Int,
    state: DownloadsUiState
): List<DownloadEntity> = when (tabIndex) {
    0 -> state.active
    1 -> state.queued
    2 -> state.completed
    else -> state.failed
}

@Composable
private fun DownloadList(
    items: List<DownloadEntity>,
    viewModel: DownloadsViewModel
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items) { download ->
            DownloadItem(
                download = download,
                onOpenClick = { viewModel.openDownload(download.id) },
                onPauseClick = { viewModel.pauseDownload(download.id) },
                onResumeClick = { viewModel.resumeDownload(download.id) },
                onCancelClick = { viewModel.cancelDownload(download.id) },
                onRetryClick = { viewModel.retryDownload(download.id) },
                onDeleteClick = { viewModel.deleteDownload(download.id) }
            )
        }
    }
}

@Composable
fun EmptyDownloadsState(
    icon: ImageVector,
    message: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Gold,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}
