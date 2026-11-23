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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
    val speedState = remember { SpeedState() }
    val player = remember { buildPlayer(context, speedState) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var stream by remember { mutableStateOf<com.lumo.player.data.StreamInfo?>(null) }
    var episodeLabel by remember { mutableStateOf("") }
    var speed by remember { mutableFloatStateOf(1f) }
    var showInfo by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf("") }
    var interactionTick by remember { mutableIntStateOf(0) }
    var controlFocused by remember { mutableStateOf(false) }
    var speedMb by remember { mutableStateOf(0.0) }
    var panel by remember { mutableStateOf<String?>(null) }
    var qualityLabel by remember { mutableStateOf("原画") }
    var activeRes by remember { mutableStateOf("") }
    var audioLabel by remember { mutableStateOf("默认") }
    var subtitleLabel by remember { mutableStateOf("关") }
    var selectedQuality by remember { mutableStateOf<com.lumo.player.data.Quality?>(null) }
    var retryOnce by remember { mutableStateOf(false) }
    var liveVideo by remember { mutableStateOf("") }
    var liveAudio by remember { mutableStateOf("") }
    var bufferedMs by remember { mutableLongStateOf(0L) }
    var nextItem by remember { mutableStateOf<com.lumo.player.data.MediaItem?>(null) }
    var nextMediaGuid by remember { mutableStateOf("") }
    var episodes by remember { mutableStateOf<List<com.lumo.player.data.MediaItem>>(emptyList()) }
    var epIndex by remember { mutableIntStateOf(-1) }
    val prevEpisode = episodes.getOrNull(epIndex - 1)
    val nextEpisode = episodes.getOrNull(epIndex + 1)
    var curItemGuid by remember { mutableStateOf(itemGuid) }
    var curMediaGuid by remember { mutableStateOf(mediaGuid) }
    var ended by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val onControlFocus: (Boolean) -> Unit = { focused ->
        controlFocused = focused
        if (focused) interactionTick += 1
    }
    val focus = remember { FocusRequester() }
    val playButtonFocus = remember { FocusRequester() }

    LaunchedEffect(itemGuid) {
        if (itemGuid.isNotEmpty()) {
            runCatching {
                val d = com.lumo.player.data.FnApi.itemDetail(itemGuid)
                val code = "S%02dE%02d".format(d.seasonNumber, d.episodeNumber)
                episodeLabel = if (d.title.isNotEmpty()) "$code · ${d.title}" else code
                val season = com.lumo.player.data.FnApi.itemDetail(d.parentGuid)
                val eps = com.lumo.player.data.FnApi.episodes(season.guid)
                episodes = eps
                epIndex = eps.indexOfFirst { it.guid == itemGuid }
            }
        }
    }

    suspend fun playEpisode(ep: com.lumo.player.data.MediaItem) {
        android.util.Log.d("LumoPlay", "playEpisode guid=${ep.guid} ep=${ep.episodeNumber} sn=${ep.seasonNumber}")
        LastPlayed.episodeGuid = ep.guid
        LastPlayed.episodeItem = ep
        runCatching { com.lumo.player.data.FnApi.playInfo(ep.guid) }.onSuccess { info ->
            curItemGuid = info.guid
            curMediaGuid = info.mediaGuid
            epIndex = episodes.indexOfFirst { it.guid == ep.guid }
            LastPlayed.episodeGuid = ep.guid
            LastPlayed.episodeItem = ep
            player.setMediaItem(ExoMediaItem.fromUri(com.lumo.player.data.FnApi.playUrl(info.mediaGuid)))
            player.prepare()
            player.playWhenReady = true
            val code = "S%02dE%02d".format(ep.seasonNumber, ep.episodeNumber)
            episodeLabel = if (ep.title.isNotEmpty() && ep.title != "第 ${ep.episodeNumber} 集") {
                "$code · ${ep.title}"
            } else {
                code
            }
        }
    }

    LaunchedEffect(retryOnce) {
        if (retryOnce) {
            val q = selectedQuality
            val s = stream
            if (q != null && s != null) {
                val pos = player.currentPosition
                val link = FnApi.transcodeLink(
                    curMediaGuid,
                    s.videoGuid,
                    s.videoCodec,
                    q.resolution,
                    q.bitrate,
                    pos / 1000,
                    s.audioGuid,
                    s.audioCodec,
                    s.channels,
                )
                if (link != null) {
                    player.setMediaItem(ExoMediaItem.fromUri(FnApi.host.trimEnd('/') + link))
                    player.prepare()
                    player.seekTo(pos)
                    player.playWhenReady = true
                    error = null
                }
            }
            retryOnce = false
        }
    }

    LaunchedEffect(stream) {
        val s = stream ?: return@LaunchedEffect
        s.audioTracks.firstOrNull { it.guid == s.audioGuid }?.let {
            audioLabel = formatAudioLabel(it, true)
        }
    }

    LaunchedEffect(nextEpisode) {
        val nxt = nextEpisode
        if (nxt != null && nxt.guid != nextItem?.guid) {
            runCatching { FnApi.playInfo(nxt.guid) }.onSuccess { info ->
                nextItem = nxt
                nextMediaGuid = info.mediaGuid
                runCatching {
                    player.addMediaItem(ExoMediaItem.fromUri(FnApi.playUrl(info.mediaGuid)))
                }
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

    BackHandler(enabled = panel != null) {
        panel = null
    }

    BackHandler {
        val ts = (player.currentPosition / 1000).coerceAtLeast(0)
        kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withTimeoutOrNull(2000) {
                FnApi.reportProgress(curItemGuid, curMediaGuid, ts)
            }
        }
        onBack()
    }

    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(e: androidx.media3.common.PlaybackException) {
                error = e.errorCodeName
                if (selectedQuality != null && !retryOnce) retryOnce = true
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                if (player.currentMediaItemIndex > 0 && nextItem != null) {
                    val item = nextItem!!
                    curItemGuid = item.guid
                    curMediaGuid = nextMediaGuid
                    epIndex = episodes.indexOfFirst { it.guid == item.guid }
                    val code = "S%02dE%02d".format(item.seasonNumber, item.episodeNumber)
                    episodeLabel = if (item.title.isNotEmpty() && item.title != "第 ${item.episodeNumber} 集") {
                        "$code · ${item.title}"
                    } else {
                        code
                    }
                    LastPlayed.episodeGuid = item.guid
                    LastPlayed.episodeItem = item
                    runCatching { player.removeMediaItem(0) }
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == androidx.media3.common.Player.STATE_BUFFERING
                if (state == androidx.media3.common.Player.STATE_READY) error = null
                if (state == androidx.media3.common.Player.STATE_ENDED) ended = true
            }
        }
        player.addListener(listener)
        onDispose {
            val ts = (player.currentPosition / 1000).coerceAtLeast(0)
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { FnApi.reportProgress(curItemGuid, curMediaGuid, ts) }
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
                    FnApi.reportProgress(
                        curItemGuid,
                        curMediaGuid,
                        (player.currentPosition / 1000).coerceAtLeast(0),
                    )
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
            speedMb = speedState.mbps
            bufferedMs = (player.bufferedPosition - player.currentPosition).coerceAtLeast(0L)
            val v = player.videoFormat
            if (v != null) {
                liveVideo = buildString {
                    append(v.width).append("x").append(v.height)
                    v.sampleMimeType?.substringAfterLast('/')?.let { append(" · ").append(it) }
                    if (v.frameRate > 0) append(" · ").append(v.frameRate.toInt()).append("fps")
                    if (v.bitrate > 0) append(" · ").append(v.bitrate / 1_000_000).append("Mbps")
                }
            }
            val a = player.audioFormat
            if (a != null) {
                liveAudio = buildString {
                    append(a.sampleMimeType?.substringAfterLast('/') ?: "audio")
                    append(" ").append(a.channelCount).append("ch")
                }
            }
            delay(500)
        }
    }

    LaunchedEffect(controlsVisible, playing, interactionTick, controlFocused) {
        if (controlsVisible && playing && !controlFocused) {
            delay(5000)
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
                    interactionTick += 1
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
                var backFocused by remember { mutableStateOf(false) }
                Box(
                    Modifier
                        .size(32.dp)
                        .focusGlow(backFocused, 16.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                        .onFocusChanged { backFocused = it.isFocused }
                        .focusable()
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
                Text(
                    String.format(Locale.US, "%.1f MB/s", speedMb),
                    color = Color(0xFF9FB0C7),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(end = 10.dp),
                )
                val info = stream
                if (info != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Pill(
                            activeRes.ifEmpty {
                                when {
                                    info.width >= 3800 -> "4K"
                                    info.width >= 1900 -> "1080P"
                                    else -> "SD"
                                }
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
                ControlCircle("−10s", onFocusChange = onControlFocus) {
                    player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                }
                ControlCircle(
                    label = if (playing) "II" else "▶",
                    big = true,
                    modifier = androidx.compose.ui.Modifier.focusRequester(playButtonFocus),
                    onFocusChange = onControlFocus,
                ) {
                    if (player.isPlaying) player.pause() else player.play()
                }
                ControlCircle("+10s", onFocusChange = onControlFocus) {
                    player.seekTo(player.currentPosition + 10_000)
                }
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
                    prevEpisode?.let { p ->
                        ActionPill("上一集", onFocusChange = onControlFocus) {
                            scope.launch { playEpisode(p) }
                        }
                    }
                    nextEpisode?.let { n ->
                        ActionPill("下一集", onFocusChange = onControlFocus) {
                            scope.launch { playEpisode(n) }
                        }
                    }
                    ActionPill("画质 · $qualityLabel", onFocusChange = onControlFocus) { panel = "quality" }
                    ActionPill("音轨 · $audioLabel", onFocusChange = onControlFocus) { panel = "audio" }
                    ActionPill("字幕 · $subtitleLabel", onFocusChange = onControlFocus) { panel = "subtitle" }
                    ActionPill("选集", onFocusChange = onControlFocus) { onBack() }
                    ActionPill(
                        "倍速 · ${String.format(Locale.US, "%.1f", speed)}x",
                        onFocusChange = onControlFocus,
                    ) {
                        speed = when (speed) {
                            1f -> 1.25f
                            1.25f -> 1.5f
                            1.5f -> 2f
                            else -> 1f
                        }
                        player.setPlaybackSpeed(speed)
                    }
                    Spacer(Modifier.weight(1f))
                    ActionPill("信息", onFocusChange = onControlFocus) { showInfo = !showInfo }
                }
                if (hint.isNotEmpty()) {
                    Text(hint, color = Color(0xFFFFD479), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                }
                if (showInfo) {
                    Text(
                        "${liveVideo.ifEmpty { "--" }} · ${liveAudio.ifEmpty { "--" }} · 缓冲 ${bufferedMs / 1000}s · ${String.format(Locale.US, "%.1f", speedMb)} MB/s",
                        color = Color(0xFFC9D5E6),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        panel?.let { p ->
            val options = mutableListOf<Pair<String, () -> Unit>>()
            when (p) {
                "quality" -> {
                    options += "原画" to {
                        qualityLabel = "原画"
                        activeRes = ""
                        selectedQuality = null
                        hint = ""
                        val pos = player.currentPosition
                        player.setMediaItem(ExoMediaItem.fromUri(FnApi.playUrl(curMediaGuid)))
                        player.prepare()
                        player.seekTo(pos)
                        player.playWhenReady = true
                        panel = null
                    }
                    val seen = mutableSetOf<String>()
                    stream?.qualities?.forEach { q ->
                        if (q.resolution.isNotEmpty() && seen.add(q.resolution)) {
                            val label = if (q.resolution.contains("k", true)) {
                                q.resolution.uppercase()
                            } else {
                                q.resolution.uppercase() + "P"
                            }
                            options += label to {
                                scope.launch {
                                    val s = stream
                                    if (s != null) {
                                        val link = FnApi.transcodeLink(
                                            curMediaGuid,
                                            s.videoGuid,
                                            s.videoCodec,
                                            q.resolution,
                                            q.bitrate,
                                            player.currentPosition / 1000,
                                            s.audioGuid,
                                            s.audioCodec,
                                            s.channels,
                                        )
                                        if (link != null) {
                                            qualityLabel = label
                                            activeRes = label
                                            selectedQuality = q
                                            hint = ""
                                            val pos = player.currentPosition
                                            player.setMediaItem(
                                                ExoMediaItem.fromUri(FnApi.host.trimEnd('/') + link),
                                            )
                                            player.prepare()
                                            player.seekTo(pos)
                                            player.playWhenReady = true
                                        } else {
                                            hint = "画质切换失败"
                                        }
                                    }
                                }
                                panel = null
                            }
                        }
                    }
                }
                "audio" -> {
                    val s = stream
                    s?.audioTracks?.forEach { a ->
                        val label = formatAudioLabel(a, s.audioGuid == a.guid)
                        options += label to {
                            scope.launch {
                                val link = FnApi.playWithTracks(curMediaGuid, s.videoGuid, a.guid, "")
                                if (link != null) {
                                    val pos = player.currentPosition
                                    player.setMediaItem(ExoMediaItem.fromUri(FnApi.host.trimEnd('/') + link))
                                    player.prepare()
                                    player.seekTo(pos)
                                    player.playWhenReady = true
                                    audioLabel = label
                                    hint = ""
                                } else {
                                    hint = "音轨切换失败"
                                }
                            }
                            panel = null
                        }
                    }
                }
                "subtitle" -> {
                    val s = stream
                    s?.subtitleTracks?.forEach { t ->
                        val label = formatSubtitleLabel(t)
                        options += label to {
                            scope.launch {
                                val link = FnApi.playWithTracks(curMediaGuid, s.videoGuid, s.audioGuid, t.guid)
                                if (link != null) {
                                    val pos = player.currentPosition
                                    player.setMediaItem(ExoMediaItem.fromUri(FnApi.host.trimEnd('/') + link))
                                    player.prepare()
                                    player.seekTo(pos)
                                    player.playWhenReady = true
                                    subtitleLabel = label
                                    hint = ""
                                } else {
                                    hint = "字幕切换失败"
                                }
                            }
                            panel = null
                        }
                    }
                    options += "关闭字幕" to {
                        scope.launch {
                            val s2 = stream
                            if (s2 != null) {
                                val link = FnApi.playWithTracks(
                                    curMediaGuid,
                                    s2.videoGuid,
                                    s2.audioGuid,
                                    "_no_display_",
                                )
                                if (link != null) {
                                    val pos = player.currentPosition
                                    player.setMediaItem(ExoMediaItem.fromUri(FnApi.host.trimEnd('/') + link))
                                    player.prepare()
                                    player.seekTo(pos)
                                    player.playWhenReady = true
                                    subtitleLabel = "关"
                                }
                            }
                        }
                        panel = null
                    }
                }
            }
            Column(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 50.dp)
                    .width(320.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xE6101826))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    when (p) {
                        "quality" -> "画质"
                        "audio" -> "音轨"
                        else -> "字幕"
                    },
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                if (options.isEmpty()) {
                    Text(
                        if (p == "subtitle") "该片源无内封字幕" else "暂无可选项",
                        color = Color(0xFF9FB0C7),
                        fontSize = 11.sp,
                    )
                }
                options.forEach { (label, action) ->
                    val selected = when (p) {
                        "audio" -> label == audioLabel
                        "subtitle" -> label == subtitleLabel
                        "quality" -> label == qualityLabel
                        else -> false
                    }
                    PanelRow(label, selected) { action() }
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
    onFocusChange: (Boolean) -> Unit = {},
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .size(if (big) 64.dp else 44.dp)
            .focusGlow(focused, if (big) 32.dp else 22.dp)
            .clip(CircleShape)
            .background(if (big) com.lumo.player.ui.FnColors.Accent else Color(0x33FFFFFF))
            .onFocusChanged {
                focused = it.isFocused
                onFocusChange(it.isFocused)
            }
            .focusable()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = if (big) 20.sp else 12.sp)
    }
}

@Composable
private fun PanelRow(text: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(38.dp)
            .focusGlow(focused, 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    focused -> com.lumo.player.ui.FnColors.Accent.copy(alpha = 0.85f)
                    selected -> com.lumo.player.ui.FnColors.Accent.copy(alpha = 0.35f)
                    else -> Color(0x33FFFFFF)
                },
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, color = Color(0xFFE7EEF8), fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun ActionPill(text: String, selected: Boolean = false, onFocusChange: (Boolean) -> Unit = {}, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .focusGlow(focused, 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    focused -> com.lumo.player.ui.FnColors.Accent.copy(alpha = 0.85f)
                    selected -> com.lumo.player.ui.FnColors.Accent.copy(alpha = 0.35f)
                    else -> Color(0x33FFFFFF)
                },
            )
            .onFocusChanged {
                focused = it.isFocused
                onFocusChange(it.isFocused)
            }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, color = Color(0xFFE7EEF8), fontSize = 10.sp)
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

private fun formatAudioLabel(a: com.lumo.player.data.AudioTrack, isDefault: Boolean): String {
    val lang = when (a.language.lowercase()) {
        "chi", "zho", "zh", "cmn" -> "国语"
        "eng", "en" -> "英语"
        "jpn", "ja" -> "日语"
        "kor", "ko" -> "韩语"
        "yue" -> "粤语"
        "und", "" -> "未知语言"
        else -> a.language
    }
    val codec = when (a.codec.lowercase()) {
        "eac3" -> "杜比数字+"
        "ac3" -> "杜比数字"
        "truehd" -> "杜比全景声"
        "dts" -> "DTS"
        "aac" -> "AAC"
        "flac" -> "FLAC"
        "opus" -> "Opus"
        "mp3" -> "MP3"
        else -> a.codec.uppercase()
    }
    val ch = when (a.channels) {
        1 -> "单声道"
        2 -> "2.0"
        6 -> "5.1"
        8 -> "7.1"
        else -> "${a.channels} 声道"
    }
    return buildString {
        append(lang).append(" · ").append(codec).append(" · ").append(ch)
        if (isDefault) append(" · 默认")
    }
}

private fun formatSubtitleLabel(t: com.lumo.player.data.SubtitleTrack): String {
    val title = t.title.trim()
    val raw = (title + "." + t.language).lowercase()
    val tokens = raw.split('.', '_', '-', ' ')
    fun has(vararg keys: String) = keys.any { k -> tokens.any { it == k } }
    if (raw.contains("双语")) return "中英双语"
    if (has("cc") || raw.contains("cc")) return "CC 字幕"
    if (has("zh", "cn", "chs", "hans", "zhcn") || raw.contains("zh-cn") || raw.contains("简体")) return "简体中文"
    if (has("tw", "cht", "hant") || raw.contains("zh-tw") || raw.contains("繁体")) return "繁体中文"
    if (has("en", "eng")) return "英语"
    if (has("ja", "jpn")) return "日语"
    if (has("ko", "kor")) return "韩语"
    if (has("fr", "fra", "fre")) return "法语"
    if (has("de", "deu", "ger")) return "德语"
    if (has("es", "spa")) return "西班牙语"
    if (has("ru", "rus")) return "俄语"
    if (title.isNotEmpty() && title.any { it.code > 127 }) return title
    return "字幕"
}

private fun formatTime(millis: Long): String {
    val total = millis / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}

private fun buildPlayer(context: Context, speed: SpeedState): ExoPlayer {
    val okHttp = OkHttpDataSource.Factory(FnApi.httpClient())
        .setTransferListener(object : androidx.media3.datasource.TransferListener {
            override fun onTransferInitializing(source: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean) = Unit
            override fun onTransferStart(source: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean) = Unit
            override fun onBytesTransferred(source: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean, byteCount: Int) {
                if (isNetwork) speed.add(byteCount.toLong())
            }
            override fun onTransferEnd(source: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean) = Unit
        })
    val dataSourceFactory = DefaultDataSource.Factory(context, okHttp)
    val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(context)
        .setEnableDecoderFallback(true)
    return ExoPlayer.Builder(context, renderersFactory)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .build()
}

class SpeedState {
    private var bytes = 0L
    private var last = System.currentTimeMillis()

    @Volatile
    var mbps: Double = 0.0

    fun add(count: Long) {
        synchronized(this) {
            bytes += count
            val now = System.currentTimeMillis()
            val dt = now - last
            if (dt >= 400) {
                mbps = bytes / 1048576.0 / (dt / 1000.0)
                bytes = 0L
                last = now
            }
        }
    }
}
