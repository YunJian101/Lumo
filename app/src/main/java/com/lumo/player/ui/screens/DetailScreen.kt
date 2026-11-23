package com.lumo.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lumo.player.data.FnApi
import com.lumo.player.data.ItemDetail
import com.lumo.player.data.MediaItem
import com.lumo.player.data.Person
import com.lumo.player.data.PlayInfo
import com.lumo.player.ui.ErrorBox
import com.lumo.player.ui.FnColors
import com.lumo.player.ui.FloatingActions
import com.lumo.player.ui.FocusableCard
import com.lumo.player.ui.focusGlow
import com.lumo.player.ui.GhostButton
import com.lumo.player.ui.LoadingBox
import com.lumo.player.ui.PrimaryButton
import kotlinx.coroutines.launch

object LastPlayed {
    var episodeGuid: String? = null
    var seasonGuid: String? = null
    var seriesGuid: String? = null
    var episodeItem: MediaItem? = null
}

object DetailRefresh {
    var token by androidx.compose.runtime.mutableStateOf(0)
}

@Composable
fun DetailScreen(    item: MediaItem,
    onPlay: (String, String, String, Long) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    var detail by remember { mutableStateOf<ItemDetail?>(null) }
    var playInfo by remember { mutableStateOf<PlayInfo?>(null) }
    var episodes by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var seasons by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var selectedSeasonGuid by remember { mutableStateOf<String?>(null) }
    var persons by remember { mutableStateOf<List<Person>>(emptyList()) }
    var selectedEpisodeGuid by remember { mutableStateOf<String?>(null) }
    var seasonTitle by remember { mutableStateOf("第 1 季") }
    val episodeFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    var stream by remember { mutableStateOf<com.lumo.player.data.StreamInfo?>(null) }
    var currentEpisode by remember { mutableStateOf<MediaItem?>(null) }
    val episodeListState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(episodes, selectedEpisodeGuid) {
        val index = episodes.indexOfFirst { it.guid == selectedEpisodeGuid }
        if (index >= 0) episodeListState.scrollToItem(index)
    }
    LaunchedEffect(DetailRefresh.token) {
        if (DetailRefresh.token > 0) {
            val guid = selectedEpisodeGuid ?: playInfo?.guid ?: item.guid
            playInfo = runCatching { FnApi.playInfo(guid) }.getOrNull() ?: playInfo
            currentEpisode = episodes.firstOrNull { it.guid == guid } ?: currentEpisode
            stream = playInfo?.mediaGuid?.let { runCatching { FnApi.stream(it) }.getOrNull() } ?: stream
            val index = episodes.indexOfFirst { it.guid == selectedEpisodeGuid }
            if (index >= 0) episodeListState.scrollToItem(index)
            repeat(4) {
                kotlinx.coroutines.delay(80)
                if (runCatching { episodeFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            }
        }
    }

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(loading, episodes, selectedEpisodeGuid) {
        if (!loading && episodes.isNotEmpty() &&
            LastPlayed.episodeGuid != null && selectedEpisodeGuid == LastPlayed.episodeGuid
        ) {
            val index = episodes.indexOfFirst { it.guid == selectedEpisodeGuid }
            if (index >= 0) episodeListState.scrollToItem(index)
            repeat(6) {
                kotlinx.coroutines.delay(80)
                androidx.compose.runtime.withFrameNanos { }
                if (runCatching { episodeFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            }
        }
    }
    val scope = rememberCoroutineScope()
    val playFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val pageScroll = rememberScrollState()
    var playFocused by remember { mutableStateOf(false) }

    LaunchedEffect(playFocused) {
        if (playFocused) {
            androidx.compose.runtime.withFrameNanos { }
            androidx.compose.runtime.withFrameNanos { }
            pageScroll.animateScrollTo(0)
        }
    }

    suspend fun load() {
        loading = true
        error = null
        runCatching {
            if (item.type == "Episode" || item.episodeNumber > 0) {
                val episodeDetail = FnApi.itemDetail(item.guid)
                val seasonDetail = FnApi.itemDetail(episodeDetail.parentGuid)
                val seriesGuid = seasonDetail.parentGuid
                detail = FnApi.itemDetail(seriesGuid)
                episodes = FnApi.episodes(seasonDetail.guid)
                seasonTitle = seasonDetail.title.ifEmpty { "第 1 季" }
                selectedSeasonGuid = seasonDetail.guid
                seasons = runCatching { FnApi.seasons(seriesGuid) }.getOrDefault(emptyList())
                val last = LastPlayed.episodeGuid
                val lastEpisode = episodes.firstOrNull { it.guid == last }
                if (lastEpisode != null) {
                    selectedEpisodeGuid = lastEpisode.guid
                    currentEpisode = lastEpisode
                    playInfo = runCatching { FnApi.playInfo(lastEpisode.guid) }.getOrNull() ?: playInfo
                }
                playInfo = FnApi.playInfo(item.guid)
                selectedEpisodeGuid = item.guid
                currentEpisode = item
                persons = runCatching { FnApi.persons(seasonDetail.guid) }.getOrDefault(emptyList())
            } else {
                detail = FnApi.itemDetail(item.guid)
                val seasonList = FnApi.seasons(item.guid)
                seasons = seasonList
                val lastSeason = seasonList.firstOrNull { it.guid == LastPlayed.seasonGuid }
                val season = lastSeason ?: seasonList.firstOrNull()
                episodes = if (season != null) FnApi.episodes(season.guid) else emptyList()
                selectedSeasonGuid = season?.guid
                seasonTitle = season?.title?.ifEmpty { "第 1 季" } ?: "第 1 季"
                val lastEpisode = episodes.firstOrNull { it.guid == LastPlayed.episodeGuid }
                if (lastEpisode != null) {
                    selectedEpisodeGuid = lastEpisode.guid
                    currentEpisode = lastEpisode
                    playInfo = runCatching { FnApi.playInfo(lastEpisode.guid) }.getOrNull() ?: playInfo
                }
                val target = episodes.firstOrNull()?.guid ?: item.playableGuid
                playInfo = runCatching { FnApi.playInfo(target) }
                    .getOrElse { FnApi.playInfo(item.guid) }
                selectedEpisodeGuid = playInfo?.guid
                currentEpisode = episodes.firstOrNull()
                persons = runCatching { FnApi.persons(season?.guid ?: item.guid) }
                    .getOrDefault(emptyList())
            }
            stream = playInfo?.mediaGuid?.takeIf { it.isNotEmpty() }?.let {
                runCatching { FnApi.stream(it) }.getOrNull()
            }
        }.onFailure { error = it.message ?: "加载失败" }
        loading = false
    }

    LaunchedEffect(item.guid) { load() }
    LaunchedEffect(loading) {
        if (!loading && LastPlayed.episodeGuid == null) runCatching { playFocus.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(FnColors.Bg),
    ) {
        val backdrop = detail?.backdrops
        if (backdrop != null) {
            AsyncImage(
                model = FnApi.imageUrl(backdrop, 1280),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to FnColors.Bg.copy(alpha = 0.35f),
                        0.5f to FnColors.Bg.copy(alpha = 0.78f),
                        1f to FnColors.Bg,
                    ),
                ),
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(pageScroll),
        ) {
            Spacer(Modifier.height(14.dp))
            when {
                loading -> LoadingBox()
                error != null -> ErrorBox(error!!) { scope.launch { load() } }
                else -> {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 43.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                    ) {
                        Box(
                            Modifier
                                .width(213.dp)
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(13.dp))
                                .background(FnColors.Surface2),
                        ) {
                            AsyncImage(
                                model = FnApi.imageUrl(detail?.poster ?: item.poster, 500),
                                contentDescription = detail?.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Column(Modifier.padding(top = 4.dp)) {
                            val episodeNumber = currentEpisode?.episodeNumber?.takeIf { it > 0 }
                                ?: item.episodeNumber
                            val seasonNumber = currentEpisode?.seasonNumber?.takeIf { it > 0 }
                                ?: item.seasonNumber
                            val mainTitle = detail?.title ?: item.displayTitle
                            Text(
                                mainTitle,
                                color = FnColors.Text,
                                fontSize = 37.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            if (item.type == "Episode" || item.episodeNumber > 0) {
                                Spacer(Modifier.height(4.dp))
                                val code = "S%02dE%02d".format(seasonNumber, episodeNumber)
                                val epTitle = currentEpisode?.title
                                    ?.takeIf { it.isNotEmpty() && it != "第 $episodeNumber 集" }
                                Text(
                                    if (epTitle != null) "$code · $epTitle" else code,
                                    color = FnColors.Sub,
                                    fontSize = 13.sp,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            val streamRes = stream?.let {
                                when {
                                    it.width >= 3800 -> "4K"
                                    it.width > 0 -> "1080P"
                                    else -> null
                                }
                            }
                            val streamHdr = stream?.hdr?.takeIf { it.isNotEmpty() }
                            val streamCodec = stream?.videoCodec?.takeIf { it.isNotEmpty() }?.uppercase()
                            val meta = buildList {
                                detail?.voteAverage?.toDoubleOrNull()?.takeIf { it > 0 }?.let {
                                    add("评分 " + String.format(java.util.Locale.US, "%.1f", it))
                                }
                                streamRes?.let { add(it) }
                                streamHdr?.let { add(it) }
                                streamCodec?.let { add(it) }
                            }.joinToString("    ")
                            Text(meta, color = FnColors.Sub, fontSize = 13.sp)
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                Tag(detail?.type?.let { typeLabel(it) } ?: "视频")
                                streamRes?.let { Tag(it) }
                                streamHdr?.let { Tag(it) }
                                streamCodec?.let { Tag(it) }
                                stream?.let { info ->
                                    if (info.audioCodec.isNotEmpty()) {
                                        Tag("${info.audioCodec.uppercase()} ${info.channels}ch")
                                    }
                                }
                                val watchedTs = playInfo?.ts ?: item.ts
                                if (watchedTs > 0) Tag("已看 ${watchedTs / 60} 分钟")
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                detail?.overview ?: item.overview,
                                color = Color(0xFFC3CFDF),
                                fontSize = 13.sp,
                                lineHeight = 22.sp,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(620.dp),
                            )
                            Spacer(Modifier.height(18.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                                PrimaryButton(
                                    text = run {
                                        val ts = playInfo?.ts ?: 0
                                        val duration = playInfo?.duration?.takeIf { it > 0 }
                                            ?: currentEpisode?.duration?.takeIf { it > 0 }
                                            ?: item.duration
                                        val remaining = if (duration > ts) (duration - ts) / 60 else 0
                                        when {
                                            ts > 0 && remaining > 0 -> "继续（剩余 $remaining 分钟）"
                                            ts > 0 -> "继续"
                                            else -> "播放"
                                        }
                                    },
                                    onClick = {
                                        val epGuid = currentEpisode?.guid
                                            ?: selectedEpisodeGuid
                                            ?: playInfo?.guid
                                            ?: item.playableGuid
                                        scope.launch {
                                            runCatching { FnApi.playInfo(epGuid) }
                                                .onSuccess { info ->
                                                    playInfo = info
                                                    LastPlayed.episodeGuid = info.guid
                                                    LastPlayed.seasonGuid = selectedSeasonGuid
                                                    LastPlayed.seriesGuid = detail?.guid ?: item.guid
                                                    LastPlayed.episodeItem =
                                                        episodes.firstOrNull { it.guid == info.guid } ?: currentEpisode
                                                    onPlay(
                                                        detail?.title ?: item.displayTitle,
                                                        info.guid,
                                                        info.mediaGuid,
                                                        info.ts * 1000,
                                                    )
                                                }
                                                .onFailure { error = it.message }
                                        }
                                    },
                                    modifier = Modifier
                                        .focusRequester(playFocus)
                                        .onFocusChanged { playFocused = it.isFocused },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    if (episodes.isNotEmpty()) {
                        Text(
                            "选集 · $seasonTitle",
                            color = FnColors.Text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 43.dp, vertical = 8.dp),
                        )
                        LazyRow(
                            state = episodeListState,
                            contentPadding = PaddingValues(horizontal = 43.dp),
                            horizontalArrangement = Arrangement.spacedBy(11.dp),
                            modifier = Modifier.padding(bottom = 16.dp),
                        ) {
                            items(episodes) { episode ->
                                EpisodeCard(
                                    episode = episode,
                                    selected = episode.guid == selectedEpisodeGuid,
                                    focusRequester = if (episode.guid == selectedEpisodeGuid) episodeFocus else null,
                                ) {
                                    scope.launch {
                                        runCatching { FnApi.playInfo(episode.guid) }
                                            .onSuccess { info ->
                                                playInfo = info
                                                selectedEpisodeGuid = episode.guid
                                                currentEpisode = episode
                                                LastPlayed.episodeGuid = episode.guid
                                                stream = runCatching { FnApi.stream(info.mediaGuid) }
                                                    .getOrNull()
                                            }
                                    }
                                }
                            }
                        }
                    }
                    if (seasons.isNotEmpty()) {
                        Text(
                            "季",
                            color = FnColors.Text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 43.dp, vertical = 8.dp),
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 43.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(bottom = 18.dp),
                        ) {
                            items(seasons) { season ->
                                SeasonCard(
                                    season = season,
                                    selected = season.guid == selectedSeasonGuid,
                                ) {
                                    scope.launch {
                                        runCatching { FnApi.episodes(season.guid) }.onSuccess {
                                            episodes = it
                                            seasonTitle = season.title.ifEmpty { "第 1 季" }
                                            selectedSeasonGuid = season.guid
                                            selectedEpisodeGuid = null
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (persons.isNotEmpty()) {
                        Text(
                            "演职人员",
                            color = FnColors.Text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 43.dp, vertical = 8.dp),
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 43.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(bottom = 24.dp),
                        ) {
                            items(persons) { person ->
                                PersonCard(person)
                            }
                        }
                    }
                    stream?.let { info ->
                        Column(
                            Modifier
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        scope.launch { pageScroll.animateScrollTo(pageScroll.maxValue) }
                                    }
                                }
                                .focusable(),
                        ) {
                            InfoSectionView(
                                title = "文件信息",
                                rows = listOf(
                                    "路径" to info.filePath,
                                    "文件名" to info.fileName,
                                    "大小" to formatSize(info.fileSize),
                                ),
                            )
                            InfoSectionView(
                                title = "视频信息",
                                rows = listOf(
                                    "编码" to listOf(info.videoCodec, info.videoProfile)
                                        .filter { it.isNotEmpty() }.joinToString(" "),
                                    "分辨率" to "${info.width}×${info.height}",
                                    "帧率" to info.fps,
                                    "码率" to if (info.bitrate > 0) "${info.bitrate / 1_000_000} Mbps" else "",
                                    "HDR" to info.hdr,
                                    "色彩" to listOf(info.colorSpace, info.colorTransfer)
                                        .filter { it.isNotEmpty() }.joinToString(" / "),
                                    "位深" to if (info.bitDepth > 0) "${info.bitDepth} bit" else "",
                                ),
                            )
                            InfoSectionView(
                                title = "音频信息",
                                rows = listOf(
                                    "编码" to info.audioCodec,
                                    "声道" to if (info.channels > 0) "${info.channels}" else "",
                                    "采样率" to info.audioSampleRate,
                                    "语言" to info.audioLanguage,
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(120.dp))
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp),
        ) {
            FloatingActions(onSearch = onSearch, onSettings = onSettings, contentFocus = playFocus)
        }
    }
}

@Composable
private fun Tag(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(Color(0x14FFFFFF))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, color = Color(0xFFC9D5E6), fontSize = 9.sp)
    }
}

@Composable
private fun EpisodeCard(
    episode: MediaItem,
    selected: Boolean,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    onClick: () -> Unit,
) {
    Column(Modifier.width(260.dp)) {
        FocusableCard(
            onClick,
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .then(
                    if (focusRequester != null) {
                        Modifier.focusRequester(focusRequester)
                    } else {
                        Modifier
                    },
                ),
            RoundedCornerShape(9.dp),
        ) {
            Box(Modifier.fillMaxSize().background(FnColors.Surface2)) {
                AsyncImage(
                    model = FnApi.imageUrl(episode.poster, 400),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (selected) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .border(3.dp, FnColors.Accent, RoundedCornerShape(9.dp)),
                    )
                }
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0x99000000))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    Text(
                        if (episode.episodeNumber > 0) "第 ${episode.episodeNumber} 集" else episode.title,
                        color = Color.White,
                        fontSize = 11.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        val episodeLabel = if (episode.episodeNumber > 0) "第 ${episode.episodeNumber} 集" else ""
        val caption = episode.title.takeIf { it.isNotEmpty() && it != episodeLabel } ?: episodeLabel
        if (caption.isNotEmpty()) {
            Text(
                caption,
                color = FnColors.Sub,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SeasonCard(season: MediaItem, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(9.dp)
    Column(
        Modifier
            .width(160.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .focusGlow(focused, 9.dp)
                .clip(shape)
                .then(
                    if (selected) Modifier.border(3.dp, FnColors.Accent, shape) else Modifier,
                )
                .background(FnColors.Surface2),
        ) {
            AsyncImage(
                model = FnApi.imageUrl(season.poster, 400),
                contentDescription = season.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            season.title.ifEmpty { "第 1 季" },
            color = if (selected || focused) FnColors.Accent else FnColors.Text,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PersonCard(person: Person) {
    Column(Modifier.width(90.dp)) {
        Box(
            Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(FnColors.Surface2),
        ) {
            if (!person.profilePath.isNullOrEmpty()) {
                AsyncImage(
                    model = FnApi.imageUrl(person.profilePath, 200),
                    contentDescription = person.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF232E3D), Color(0xFF1A222E)),
                            ),
                        )
                        .border(1.dp, Color(0x22FFFFFF), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        person.name.take(1).uppercase(),
                        color = Color(0xFF9FB0C7),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            person.name,
            color = FnColors.Text,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            person.role.ifEmpty { person.job },
            color = FnColors.Sub,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun InfoSectionView(
    title: String,
    rows: List<Pair<String, String>>,
) {
    Column(
        Modifier.padding(horizontal = 43.dp, vertical = 6.dp),
    ) {
        Text(
            title,
            color = FnColors.Text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x14FFFFFF))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            rows.filter { it.second.isNotBlank() }.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(label, color = FnColors.Sub, fontSize = 11.sp, modifier = Modifier.width(90.dp))
                    Text(
                        value,
                        color = FnColors.Text,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(java.util.Locale.US, "%.2f GB", bytes / 1073741824.0)
    bytes >= 1L shl 20 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0)
    else -> "$bytes B"
}

private fun typeLabel(type: String): String = when (type) {    "TV" -> "剧集"
    "Movie" -> "电影"
    "Episode" -> "剧集"
    else -> "视频"
}
