package com.salvia.salviabrowxer.feature.player

import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.salvia.salviabrowxer.R
import com.salvia.salviabrowxer.ui.components.OrbitalBrandMark
import com.salvia.salviabrowxer.ui.theme.AuroraTeal
import com.salvia.salviabrowxer.ui.theme.BlushPink
import com.salvia.salviabrowxer.ui.theme.CharcoalSurface
import com.salvia.salviabrowxer.ui.theme.MatteCharcoal
import com.salvia.salviabrowxer.ui.theme.NebulaViolet
import com.salvia.salviabrowxer.ui.theme.NebulaVioletLight
import com.salvia.salviabrowxer.ui.theme.PearlWhite
import com.salvia.salviabrowxer.ui.theme.SilverMid
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val SPEED_STEPS = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** One entry of the player queue. */
data class PlaylistEntry(val url: String, val title: String)

@Composable
fun MediaPlayerScreen(
    mediaUrl: String,
    mediaTitle: String,
    playlist: List<PlaylistEntry> = emptyList(),
    startIndex: Int = 0,
    onBack: () -> Unit,
    /** Called after the file and its queue entry were removed, so the screen can leave. */
    onDeleted: () -> Unit = {},
    viewModel: MediaPlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val playerState by viewModel.uiState.collectAsStateWithLifecycle()
    val queue = remember(mediaUrl, playlist) {
        if (playlist.isEmpty()) listOf(PlaylistEntry(mediaUrl, mediaTitle)) else playlist
    }

    LaunchedEffect(mediaUrl) { viewModel.check(mediaUrl) }

    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(1f) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var seekPreview by remember { mutableLongStateOf(0L) }

    // ── Playlist state ──
    var currentIndex by remember { mutableIntStateOf(startIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0))) }
    var order by remember { mutableStateOf(PlaybackOrder.SEQUENTIAL) }
    var shuffleSeed by remember { mutableIntStateOf(0) }
    var speedIndex by remember { mutableIntStateOf(SPEED_STEPS.indexOfFirst { it == 1f }.coerceAtLeast(0)) }
    var showQueue by remember { mutableStateOf(false) }

    // Resolves the actual play position for the current order mode.
    fun orderedIndexOf(displayIndex: Int): Int = when (order) {
        PlaybackOrder.SEQUENTIAL, PlaybackOrder.LOOP_ALL -> displayIndex
        PlaybackOrder.REVERSE -> queue.size - 1 - displayIndex
        PlaybackOrder.SHUFFLE -> shuffleOrder(queue.size, shuffleSeed)[displayIndex]
    }

    fun loadEntry(displayIndex: Int, playWhenReady: Boolean = true) {
        val player = exoPlayer ?: return
        val clamped = displayIndex.coerceIn(0, queue.size - 1)
        currentIndex = clamped
        val entry = queue[orderedIndexOf(clamped)]
        player.setMediaItem(MediaItem.fromUri(Uri.parse(entry.url)))
        player.prepare()
        player.playWhenReady = playWhenReady
    }

    fun advance(delta: Int) {
        var next = currentIndex + delta
        when (order) {
            PlaybackOrder.SEQUENTIAL -> if (next > queue.size - 1) { exoPlayer?.pause(); return }
            PlaybackOrder.LOOP_ALL -> if (next > queue.size - 1) next = 0
            PlaybackOrder.REVERSE -> if (next < 0) { exoPlayer?.pause(); return }
            PlaybackOrder.SHUFFLE -> if (next > queue.size - 1) { shuffleSeed++; next = 0 }
        }
        loadEntry(next)
    }

    DisposableEffect(mediaUrl, queue, playerState.isChecking, playerState.fileMissing) {
        // A player built for a file that is not on disk can only produce a Media3 error.
        if (playerState.isChecking || playerState.fileMissing) return@DisposableEffect onDispose { }
        val player = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(queue[orderedIndexOf(currentIndex)].url)))
            prepare()
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) duration = this@apply.duration.coerceAtLeast(0L)
                    if (state == Player.STATE_ENDED) {
                        if (queue.size > 1) advance(1) else if (order == PlaybackOrder.LOOP_ALL) { seekTo(0); play() } else isPlaying = false
                    }
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    viewModel.onPlaybackFailed()
                }
            })
        }
        exoPlayer = player
        onDispose {
            player.release()
            exoPlayer = null
        }
    }

    // Apply speed changes
    LaunchedEffect(speedIndex) { exoPlayer?.setPlaybackSpeed(SPEED_STEPS[speedIndex]) }

    // Poll position at 5 Hz — cheap compose state only, no jank.
    LaunchedEffect(exoPlayer, isPlaying, isUserSeeking) {
        while (isActive) {
            if (!isUserSeeking) {
                exoPlayer?.let { p ->
                    currentPosition = p.currentPosition.coerceAtLeast(0L)
                    if (duration <= 0L) duration = p.duration.coerceAtLeast(0L)
                }
            }
            delay(if (isPlaying) 200 else 500)
        }
    }

    // Nothing to play: the file disappeared behind the queue's back. Say so and offer the only
    // two things that can help — leave, or clear the dead row.
    if (!playerState.isChecking && playerState.fileMissing) {
        UnplayableMedia(
            title = mediaTitle,
            message = stringResource(R.string.player_file_missing),
            onDelete = { viewModel.deleteDownload(mediaUrl, onDeleted) },
            onBack = onBack
        )
        return
    }

    val sliderPosition = if (isUserSeeking) seekPreview.toFloat() else currentPosition.toFloat()
    val sliderRange = 0f..(duration.coerceAtLeast(1L).toFloat())
    val currentEntry = queue[orderedIndexOf(currentIndex)]

    Column(modifier = Modifier.fillMaxSize().background(MatteCharcoal)) {
        // ── Top bar with brand mark ──
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.go_back), tint = PearlWhite)
            }
            OrbitalBrandMark(size = 26.dp, animate = false, glowIntensity = 0.85f)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = currentEntry.title.ifBlank { stringResource(R.string.media_fallback_title) },
                    style = MaterialTheme.typography.titleMedium,
                    color = PearlWhite,
                    maxLines = 1
                )
                if (queue.size > 1) {
                    Text(
                        text = stringResource(R.string.player_playlist, currentIndex + 1, queue.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = SilverMid
                    )
                }
            }
            if (queue.size > 1) {
                IconButton(onClick = { showQueue = !showQueue }) {
                    // AutoMirrored: the queue glyph lists items, and a list runs the other way in RTL.
                    Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.player_queue_toggle), tint = SilverMid)
                }
            }
            IconButton(onClick = { isFullscreen = !isFullscreen }) {
                Icon(
                    if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    if (isFullscreen) stringResource(R.string.player_exit_fullscreen) else stringResource(R.string.player_fullscreen),
                    tint = PearlWhite
                )
            }
        }

        // ── Queue drawer ──
        if (showQueue && queue.size > 1) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CharcoalSurface)
            ) {
                itemsIndexed(queue) { displayIndex, entry ->
                    val isActive = displayIndex == currentIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .background(if (isActive) NebulaViolet.copy(alpha = 0.18f) else Color.Transparent)
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${displayIndex + 1}.",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isActive) BlushPink else SilverMid,
                            modifier = Modifier.width(26.dp)
                        )
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isActive) BlushPink else PearlWhite,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { loadEntry(displayIndex) }) {
                            Icon(Icons.Default.PlayArrow, stringResource(R.string.player_play), tint = if (isActive) BlushPink else SilverMid)
                        }
                    }
                }
            }
        }

        // ── Video surface ──
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            exoPlayer?.let { player ->
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = false
                            setBackgroundColor(android.graphics.Color.BLACK)
                        }
                    },
                    update = { view -> if (view.player !== player) view.player = player },
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (playerState.playbackFailed) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.player_playback_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PearlWhite,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            viewModel.clearPlaybackFailure()
                            exoPlayer?.prepare()
                            exoPlayer?.playWhenReady = true
                        }) { Text(stringResource(R.string.player_retry), color = AuroraTeal) }
                        TextButton(onClick = { viewModel.deleteDownload(mediaUrl, onDeleted) }) {
                            Text(stringResource(R.string.player_delete_download), color = SilverMid)
                        }
                    }
                }
            } else if (!isPlaying) {
                IconButton(
                    onClick = { exoPlayer?.play(); isPlaying = true },
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, stringResource(R.string.player_play), tint = Color.White, modifier = Modifier.size(56.dp))
                }
            }
        }

        // ── Order + speed chips ──
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                AssistChip(
                    onClick = { order = order.next() },
                    label = { Text(stringResource(order.labelRes), style = MaterialTheme.typography.labelMedium) },
                    leadingIcon = {
                        Icon(
                            when (order) {
                                PlaybackOrder.SEQUENTIAL -> Icons.AutoMirrored.Filled.Sort
                                PlaybackOrder.LOOP_ALL -> Icons.Default.Repeat
                                // Not mirrored: play order is media order, not reading direction, so
                                // an RTL user still means "reverse the playlist" by this arrow.
                                PlaybackOrder.REVERSE -> Icons.Default.FastForward
                                PlaybackOrder.SHUFFLE -> Icons.Default.Shuffle
                            },
                            contentDescription = stringResource(R.string.player_order),
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = CharcoalSurface,
                        labelColor = if (order == PlaybackOrder.SEQUENTIAL) SilverMid else NebulaVioletLight
                    )
                )
            }
            itemsIndexed(SPEED_STEPS.toList()) { idx: Int, speed: Float ->
                AssistChip(
                    onClick = { speedIndex = idx },
                    label = { Text("${speed}x", style = MaterialTheme.typography.labelMedium) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (idx == speedIndex) NebulaViolet.copy(alpha = 0.28f) else CharcoalSurface,
                        labelColor = if (idx == speedIndex) NebulaVioletLight else SilverMid
                    )
                )
            }
        }

        // ── Seek + transport controls ──
        Column(
            modifier = Modifier.fillMaxWidth().background(MatteCharcoal).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            Slider(
                value = sliderPosition.coerceIn(sliderRange),
                onValueChange = { v ->
                    isUserSeeking = true
                    seekPreview = v.toLong()
                },
                onValueChangeFinished = {
                    exoPlayer?.seekTo(seekPreview)
                    currentPosition = seekPreview
                    isUserSeeking = false
                },
                valueRange = sliderRange,
                colors = SliderDefaults.colors(
                    thumbColor = NebulaVioletLight,
                    activeTrackColor = BlushPink,
                    inactiveTrackColor = SilverMid.copy(alpha = 0.22f),
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                )
            )

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(formatDuration(if (isUserSeeking) seekPreview else currentPosition), style = MaterialTheme.typography.bodySmall, color = SilverMid)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {
                    if (currentPosition > 3_000 || queue.size == 1) exoPlayer?.seekTo((currentPosition - 10_000).coerceAtLeast(0L)) else advance(-1)
                }) {
                    Icon(Icons.Default.SkipPrevious, stringResource(R.string.player_previous), tint = PearlWhite)
                }
                IconButton(onClick = {
                    if (isPlaying) exoPlayer?.pause() else exoPlayer?.play()
                }) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (isPlaying) stringResource(R.string.player_pause) else stringResource(R.string.player_play),
                        tint = PearlWhite,
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(onClick = {
                    if (queue.size == 1 || currentPosition < duration - 3_000) exoPlayer?.seekTo(currentPosition + 10_000) else advance(1)
                }) {
                    Icon(Icons.Default.SkipNext, stringResource(R.string.player_next), tint = PearlWhite)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {
                    volume = if (volume > 0f) 0f else 1f
                    exoPlayer?.volume = volume
                }) {
                    Icon(
                        if (volume > 0f) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        if (volume > 0f) stringResource(R.string.player_mute) else stringResource(R.string.player_unmute),
                        tint = SilverMid
                    )
                }
                Text(formatDuration(duration), style = MaterialTheme.typography.bodySmall, color = SilverMid, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

/**
 * Shown when the queued file no longer exists on disk. It states what happened and offers the one
 * useful action — removing the dead row — instead of a blank black screen or a fake spinner.
 */
@Composable
private fun UnplayableMedia(title: String, message: String, onDelete: () -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(MatteCharcoal)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.go_back), tint = PearlWhite)
            }
            Text(
                text = title.ifBlank { stringResource(R.string.media_fallback_title) },
                style = MaterialTheme.typography.titleMedium,
                color = PearlWhite,
                maxLines = 1
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)) {
                Icon(Icons.Default.ErrorOutline, null, tint = SilverMid, modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PearlWhite,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(14.dp))
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.player_delete_download), color = AuroraTeal)
                }
            }
        }
    }
}

/** Deterministic shuffle order derived from the seed — stable across recompositions. */
private fun shuffleOrder(size: Int, seed: Int): List<Int> {
    if (size <= 1) return List(size) { it }
    val indices = (0 until size).toMutableList()
    var state = (seed + 1) * 2654435761L
    for (i in size - 1 downTo 1) {
        state = state xor (state shl 13); state = state xor (state ushr 17); state = state xor (state shl 5)
        val j = ((state and 0x7FFFFFFF) % (i + 1)).toInt()
        val tmp = indices[i]; indices[i] = indices[j]; indices[j] = tmp
    }
    return indices
}

private fun formatDuration(milliseconds: Long): String {
    val s = (milliseconds.coerceAtLeast(0L) / 1000)
    val m = s / 60; val h = m / 60
    return when {
        h > 0 -> String.format("%02d:%02d:%02d", h, m % 60, s % 60)
        m > 0 -> String.format("%02d:%02d", m, s % 60)
        else -> String.format("00:%02d", s)
    }
}
