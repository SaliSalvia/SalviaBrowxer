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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.salvia.salviabrowxer.R
import com.salvia.salviabrowxer.core.model.DownloadState
import com.salvia.salviabrowxer.ui.components.DownloadItem
import com.salvia.salviabrowxer.ui.theme.Gold
import com.salvia.salviabrowxer.ui.theme.Surface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }

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
                text = stringResource(R.string.downloads_title),
                style = MaterialTheme.typography.titleLarge,
                color = Gold
            )

            Spacer(modifier = Modifier.weight(1f))

            IconButton(
                onClick = {
                    when (selectedTabIndex) {
                        0 -> viewModel.clearAllDownloads()
                        2 -> viewModel.clearCompletedDownloads()
                        3 -> viewModel.clearFailedDownloads()
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.ClearAll,
                    contentDescription = "Clear all",
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
                text = { Text(stringResource(R.string.download_active)) }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                text = { Text(stringResource(R.string.download_queue)) }
            )
            Tab(
                selected = selectedTabIndex == 2,
                onClick = { selectedTabIndex = 2 },
                text = { Text(stringResource(R.string.download_completed)) }
            )
            Tab(
                selected = selectedTabIndex == 3,
                onClick = { selectedTabIndex = 3 },
                text = { Text(stringResource(R.string.download_failed)) }
            )
        }

        when (selectedTabIndex) {
            0 -> {
                if (viewModel.activeDownloads.isEmpty()) {
                    EmptyDownloadsState(
                        icon = Icons.Default.ClearAll,
                        message = stringResource(R.string.no_active_downloads)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(viewModel.activeDownloads) { download ->
                            DownloadItem(
                                download = download,
                                onPauseClick = { viewModel.pauseDownload(download.id) },
                                onResumeClick = { viewModel.resumeDownload(download.id) },
                                onCancelClick = { viewModel.cancelDownload(download.id) },
                                onRetryClick = { viewModel.retryDownload(download.id) },
                                onDeleteClick = { viewModel.deleteDownload(download.id) }
                            )
                        }
                    }
                }
            }
            1 -> {
                if (viewModel.queuedDownloads.isEmpty()) {
                    EmptyDownloadsState(
                        icon = Icons.Default.ClearAll,
                        message = stringResource(R.string.no_queued_downloads)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(viewModel.queuedDownloads) { download ->
                            DownloadItem(
                                download = download,
                                onPauseClick = {},
                                onResumeClick = {},
                                onCancelClick = { viewModel.cancelDownload(download.id) },
                                onRetryClick = { viewModel.retryDownload(download.id) },
                                onDeleteClick = { viewModel.deleteDownload(download.id) }
                            )
                        }
                    }
                }
            }
            2 -> {
                if (viewModel.completedDownloads.isEmpty()) {
                    EmptyDownloadsState(
                        icon = Icons.Default.ClearAll,
                        message = stringResource(R.string.no_completed_downloads)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(viewModel.completedDownloads) { download ->
                            DownloadItem(
                                download = download,
                                onPauseClick = {},
                                onResumeClick = {},
                                onCancelClick = {},
                                onRetryClick = {},
                                onDeleteClick = { viewModel.deleteDownload(download.id) }
                            )
                        }
                    }
                }
            }
            3 -> {
                if (viewModel.failedDownloads.isEmpty()) {
                    EmptyDownloadsState(
                        icon = Icons.Default.ClearAll,
                        message = stringResource(R.string.no_failed_downloads)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(viewModel.failedDownloads) { download ->
                            DownloadItem(
                                download = download,
                                onPauseClick = {},
                                onResumeClick = {},
                                onCancelClick = {},
                                onRetryClick = { viewModel.retryDownload(download.id) },
                                onDeleteClick = { viewModel.deleteDownload(download.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyDownloadsState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = Gold
            )
        }
    }
}