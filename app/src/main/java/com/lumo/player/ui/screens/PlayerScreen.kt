package com.lumo.player.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.lumo.player.data.FnApi
import com.lumo.player.ui.focusGlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun PlayerScreen(
    title: String,
    mediaGuid: String,
    itemGuid: String = "",
    startPositionMs: Long = 0L,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val player = remember { buildPlayer(context) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var stream by remember { mutableStateOf<com.lumo.player.data.StreamInfo?>(null) }
    var episodeLabel by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val playButtonFocus = remember { FocusRequester() }

    LaunchedEffect(itemGuid) {
        if (itemGuid.isNotEmpty()) {
            runCatching { com.lumo.player.data.FnApi.itemDetail(itemGuid) }.onSuccess { d ->
                val code = "S%02dE%02d".format(d.seasonNumber, d.episodeNumber)
                episodeLabel = if (d.title.isNotEmpty()) "$code · ${d.title}" else code
            }
        }
    }

    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            androidx.compose.runtime.withFrameNanos { }
            androidx.compose.runtime.withFrameNanos { }
            runCatching { playButtonFocus.requestFocus() }
        }
    }

    BackHandler {
        val ts = (player.currentPosition / 1000).coerceAtLeast(0)
        kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withTimeoutOrNull(2000) {
                FnApi.reportProgress(itemGuid, mediaGuid, ts)
            }
        }
        onBack()
    }

    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(e: androidx.media3.common.PlaybackException) {
                error = e.errorCodeName
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == androidx.media3.common.Player.STATE_BUFFERING
                if (state == androidx.media3.common.Player.STATE_READY) error = null
            }
        }
        player.addListener(listener)
        onDispose {
            val ts = (player.currentPosition / 1000).coerceAtLeast(0)
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { FnApi.reportProgress(itemGuid, mediaGuid, ts) }
            }
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(itemGuid, mediaGuid) {
        while (true) {
            delay(10_000)
            if (player.isPlaying) {
                runCatching {
                    FnApi.reportProgress(itemGuid, mediaGuid, (player.currentPosition / 1000).coerceAtLeast(0))
                }
            }
        }
    }

    LaunchedEffect(mediaGuid) {
        player.setMediaItem(ExoMediaItem.fromUri(FnApi.playUrl(mediaGuid)))
        if (startPositionMs > 0) player.seekTo(startPositionMs)
        player.prepare()
        player.playWhenReady = true
        runCatching { focus.requestFocus() }
    }

    LaunchedEffect(mediaGuid) {
        runCatching { FnApi.stream(mediaGuid) }.onSuccess { stream = it }
    }

    LaunchedEffect(Unit) {
        while (true) {
            playing = player.isPlaying
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    LaunchedEffect(controlsVisible, playing) {
        if (controlsVisible && playing) {
            delay(3000)
            controlsVisible = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    controlsVisible = true
                }
                false
            },
    ) {
        AndroidView(
            factory = { ctx ->
                val exo = player
                PlayerView(ctx).apply {
                    useController = false
                    setKeepScreenOn(true)
                    this.player = exo
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (controlsVisible) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to Color(0x99000000),
                            0.35f to Color.Transparent,
                            0.7f to Color.Transparent,
                            1f to Color(0xCC000000),
                        ),
                    ),
            )
        }

        if (buffering && error == null) {
            androidx.compose.material3.CircularProgressIndicator(
                color = com.lumo.player.ui.FnColors.Accent,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (error != null) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 96.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 22.dp, vertical = 14.dp),
            ) {
                Text("播放失败：$error", color = Color(0xFFFF9A9A), fontSize = 14.sp)
            }
        }

        if (controlsVisible) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 37.dp, vertical = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("←", color = Color.White, fontSize = 16.sp)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(episodeLabel.ifEmpty { title }, color = Color(0xFFB9C6D8), fontSize = 11.sp)
                }
                Spacer(Modifier.weight(1f))
                val info = stream
                if (info != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Pill(
                            when {
                                info.width >= 3800 -> "4K"
                                info.width >= 1900 -> "1080P"
                                else -> "SD"
                            },
                        )
                        if (info.videoCodec.isNotEmpty()) Pill(info.videoCodec.uppercase())
                        if (info.fps.isNotEmpty()) Pill(info.fps)
                        if (info.hdr.isNotEmpty()) Pill(info.hdr)
                    }
                }
            }

            Row(
                Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ControlCircle("−10s") { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)) }
                ControlCircle(
                    label = if (playing) "II" else "▶",
                    big = true,
                    modifier = androidx.compose.ui.Modifier.focusRequester(playButtonFocus),
                ) {
                    if (player.isPlaying) player.pause() else player.play()
                }
                ControlCircle("+10s") { player.seekTo(player.currentPosition + 10_000) }
            }

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 37.dp, vertical = 22.dp),
            ) {
                Slider(
                    value = if (duration > 0) position.toFloat().coerceIn(0f, duration.toFloat()) else 0f,
                    onValueChange = { position = it.toLong() },
                    onValueChangeFinished = { player.seekTo(position) },
                    valueRange = 0f..(if (duration > 0) duration.toFloat() else 1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = com.lumo.player.ui.FnColors.Accent,
                        inactiveTrackColor = Color(0x44FFFFFF),
                    ),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(position), color = Color(0xFFC9D5E6), fontSize = 11.sp)
                    Text(formatTime(duration), color = Color(0xFFC9D5E6), fontSize = 11.sp)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Pill("画质 · 原画")
                    Pill("音轨 · 默认")
                    Pill("字幕 · 关")
                    Pill("选集")
                    Pill("倍速 · 1.0x")
                    Spacer(Modifier.weight(1f))
                    Pill("信息")
                }
            }
        }
    }
}

@Composable
private fun ControlCircle(
    label: String,
    big: Boolean = false,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .size(if (big) 64.dp else 44.dp)
            .focusGlow(focused, if (big) 32.dp else 22.dp)
            .clip(CircleShape)
            .background(if (big) com.lumo.player.ui.FnColors.Accent else Color(0x33FFFFFF))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = if (big) 20.sp else 12.sp)
    }
}

@Composable
private fun Pill(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x33FFFFFF))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, color = Color(0xFFE7EEF8), fontSize = 10.sp)
    }
}

private fun formatTime(millis: Long): String {
    val total = millis / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}

private fun buildPlayer(context: Context): ExoPlayer {
    val dataSourceFactory = DefaultDataSource.Factory(
        context,
        OkHttpDataSource.Factory(FnApi.httpClient()),
    )
    val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(context)
        .setEnableDecoderFallback(true)
    return ExoPlayer.Builder(context, renderersFactory)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .build()
}
