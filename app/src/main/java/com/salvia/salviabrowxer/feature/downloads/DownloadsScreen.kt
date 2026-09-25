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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salvia.salviabrowxer.R
import com.salvia.salviabrowxer.core.database.entities.DownloadEntity
import com.salvia.salviabrowxer.ui.components.DownloadItem
import com.salvia.salviabrowxer.ui.theme.AuroraTeal
import com.salvia.salviabrowxer.ui.theme.CharcoalBorder
import com.salvia.salviabrowxer.ui.theme.DeepCharcoal
import com.salvia.salviabrowxer.ui.theme.MatteCharcoal
import com.salvia.salviabrowxer.ui.theme.PearlWhite
import com.salvia.salviabrowxer.ui.theme.SilverMid
import kotlinx.coroutines.flow.collectLatest

@Composable
fun DownloadsScreen(onBack: () -> Unit, viewModel: DownloadsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { viewModel.messages.collectLatest { message -> if (message.isNotBlank()) snackbarHostState.showSnackbar(message) } }

    Box(modifier = Modifier.fillMaxSize().background(MatteCharcoal)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.go_back), tint = PearlWhite) }
                Spacer(Modifier.width(8.dp))
                Text(text = stringResource(R.string.downloads_title), style = MaterialTheme.typography.titleLarge, color = PearlWhite)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {
                    when (selectedTabIndex) { 0 -> viewModel.clearAllDownloads(); 2 -> viewModel.clearCompletedDownloads(); 3 -> viewModel.clearFailedDownloads() }
                }) { Icon(Icons.Default.ClearAll, stringResource(R.string.action_clear), tint = SilverMid) }
            }

            TabRow(
                selectedTabIndex = selectedTabIndex, containerColor = DeepCharcoal, contentColor = PearlWhite,
                indicator = { tabPositions -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]), color = AuroraTeal, height = 2.dp) },
                divider = { androidx.compose.material3.HorizontalDivider(color = CharcoalBorder.copy(alpha = 0.6f), thickness = 1.dp) }
            ) {
                Tab(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }, selectedContentColor = AuroraTeal, unselectedContentColor = SilverMid, text = { Text("${stringResource(R.string.download_active)} (${state.active.size})", style = MaterialTheme.typography.labelMedium) })
                Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }, selectedContentColor = AuroraTeal, unselectedContentColor = SilverMid, text = { Text("${stringResource(R.string.download_queue)} (${state.queued.size})", style = MaterialTheme.typography.labelMedium) })
                Tab(selected = selectedTabIndex == 2, onClick = { selectedTabIndex = 2 }, selectedContentColor = AuroraTeal, unselectedContentColor = SilverMid, text = { Text("${stringResource(R.string.download_completed)} (${state.completed.size})", style = MaterialTheme.typography.labelMedium) })
                Tab(selected = selectedTabIndex == 3, onClick = { selectedTabIndex = 3 }, selectedContentColor = AuroraTeal, unselectedContentColor = SilverMid, text = { Text("${stringResource(R.string.download_failed)} (${state.failed.size})", style = MaterialTheme.typography.labelMedium) })
            }

            val items = when (selectedTabIndex) { 0 -> state.active; 1 -> state.queued; 2 -> state.completed; else -> state.failed }
            val emptyMessage = when (selectedTabIndex) { 0 -> stringResource(R.string.no_active_downloads); 1 -> stringResource(R.string.no_queued_downloads); 2 -> stringResource(R.string.no_completed_downloads); else -> stringResource(R.string.no_failed_downloads) }

            if (items.isEmpty()) {
                EmptyDownloadsState(icon = Icons.Default.ClearAll, message = if (state.isLoading) stringResource(R.string.download_queue) else emptyMessage)
            } else {
                DownloadList(items = items, viewModel = viewModel)
            }
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp))
    }
}

@Composable
private fun DownloadList(items: List<DownloadEntity>, viewModel: DownloadsViewModel) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 4.dp)) {
        items(items, key = { it.id }, contentType = { it.status }) { download ->
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
fun EmptyDownloadsState(icon: ImageVector, message: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(imageVector = icon, contentDescription = null, tint = SilverMid.copy(alpha = 0.6f), modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(12.dp))
            Text(text = message, style = MaterialTheme.typography.bodyMedium, color = SilverMid)
        }
    }
}
