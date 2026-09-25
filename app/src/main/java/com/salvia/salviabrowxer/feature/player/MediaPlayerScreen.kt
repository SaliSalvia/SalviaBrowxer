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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.salvia.salviabrowxer.R
import com.salvia.salviabrowxer.ui.theme.AuroraTeal
import com.salvia.salviabrowxer.ui.theme.MatteCharcoal
import com.salvia.salviabrowxer.ui.theme.PearlWhite
import com.salvia.salviabrowxer.ui.theme.SilverMid
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun MediaPlayerScreen(
    mediaUrl: String,
    mediaTitle: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(1f) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var seekPreview by remember { mutableLongStateOf(0L) }

    DisposableEffect(mediaUrl) {
        val player = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(mediaUrl)))
            prepare()
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) duration = this@apply.duration.coerceAtLeast(0L)
                    if (state == Player.STATE_ENDED) isPlaying = false
                }
            })
        }
        exoPlayer = player
        onDispose {
            player.release()
            exoPlayer = null
        }
    }

    // Poll position at 5 Hz (200 ms) — cheap, no DB, only compose state. Throttled to avoid 60fps churn.
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

    val sliderPosition = if (isUserSeeking) seekPreview.toFloat() else currentPosition.toFloat()
    val sliderRange = 0f..(duration.coerceAtLeast(1L).toFloat())

    Column(modifier = Modifier.fillMaxSize().background(MatteCharcoal)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.go_back), tint = PearlWhite)
            }
            Spacer(Modifier.size(8.dp))
            Text(
                text = mediaTitle.ifBlank { mediaUrl.substringAfterLast('/').substringBefore('?').ifBlank { "Media" } },
                style = MaterialTheme.typography.titleMedium,
                color = PearlWhite,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { isFullscreen = !isFullscreen }) {
                Icon(
                    if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    if (isFullscreen) "Exit fullscreen" else "Fullscreen",
                    tint = PearlWhite
                )
            }
        }

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            exoPlayer?.let { player ->
                // Key on player instance so recompositions don't recreate the view unnecessarily
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
            if (!isPlaying) {
                IconButton(
                    onClick = { exoPlayer?.play(); isPlaying = true },
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(56.dp))
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().background(MatteCharcoal).padding(16.dp),
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
                    thumbColor = AuroraTeal,
                    activeTrackColor = AuroraTeal,
                    inactiveTrackColor = SilverMid.copy(alpha = 0.22f),
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                )
            )

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(formatDuration(if (isUserSeeking) seekPreview else currentPosition), style = MaterialTheme.typography.bodySmall, color = SilverMid)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { exoPlayer?.seekTo((currentPosition - 10_000).coerceAtLeast(0L)) }) {
                    Icon(Icons.Default.SkipPrevious, "Back 10s", tint = PearlWhite)
                }
                IconButton(onClick = {
                    if (isPlaying) exoPlayer?.pause() else exoPlayer?.play()
                }) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (isPlaying) "Pause" else "Play",
                        tint = PearlWhite,
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(onClick = { exoPlayer?.seekTo(currentPosition + 10_000) }) {
                    Icon(Icons.Default.SkipNext, "Forward 10s", tint = PearlWhite)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {
                    volume = if (volume > 0f) 0f else 1f
                    exoPlayer?.volume = volume
                }) {
                    Icon(
                        if (volume > 0f) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        if (volume > 0f) "Mute" else "Unmute",
                        tint = SilverMid
                    )
                }
                Text(formatDuration(duration), style = MaterialTheme.typography.bodySmall, color = SilverMid, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
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
