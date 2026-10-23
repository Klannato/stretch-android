package com.nous.stretchup.ui

import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * Plays imported stretch footage: silent, looping, no controls, cropped to fit.
 *
 * One stretch can have several clips (a front angle, a side angle, ...); tapping the
 * video cycles through them, and a small chip says which one is showing. If the file
 * is gone — moved, deleted, permission revoked — the error callback lets the caller
 * fall back to the drawn figure instead of showing a black rectangle for 60 seconds.
 */
@Composable
fun ImportedVideo(
    uris: List<String>,
    modifier: Modifier = Modifier,
    onUnplayable: () -> Unit = {}
) {
    if (uris.isEmpty()) return
    val context = LocalContext.current
    var index by remember(uris) { mutableStateOf(0) }
    var failed by remember(uris) { mutableStateOf(false) }

    val player = remember(uris, index) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uris[index]))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                failed = true
                onUnplayable()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    if (failed) return

    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
            },
            update = { view -> view.player = player }
        )

        if (uris.size > 1) {
            Text(
                text = "${index + 1} / ${uris.size} angles · tap to switch",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clickable { index = (index + 1) % uris.size }
            )
        }
    }
}
