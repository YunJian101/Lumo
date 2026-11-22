package com.lumo.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lumo.player.data.FnApi
import com.lumo.player.data.ItemDetail
import com.lumo.player.data.Library
import com.lumo.player.data.MediaItem
import com.lumo.player.ui.ErrorBox
import com.lumo.player.ui.FnColors
import com.lumo.player.ui.FocusableCard
import com.lumo.player.ui.Hero
import com.lumo.player.ui.LoadingBox
import com.lumo.player.ui.PosterCard
import com.lumo.player.ui.ScreenWithActions
import com.lumo.player.ui.SectionTitle
import com.lumo.player.ui.WideCard
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onOpenLibrary: (Library) -> Unit,
    onOpenDetail: (MediaItem) -> Unit,
    onPlay: (String, String) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    var libraries by remember { mutableStateOf<List<Library>>(emptyList()) }
    var recentByLibrary by remember { mutableStateOf<List<Pair<Library, List<MediaItem>>>>(emptyList()) }
    var continueWatching by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var hero by remember { mutableStateOf<ItemDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val playFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val scrollState = rememberScrollState()

    suspend fun load() {
        loading = true
        error = null
        runCatching {
            val libs = FnApi.libraries()
            libraries = libs
            continueWatching = runCatching { FnApi.playHistory() }.getOrDefault(emptyList()).take(8)
            recentByLibrary = libs.map { library ->
                library to runCatching { FnApi.items(library.guid, 12) }.getOrDefault(emptyList())
            }.filter { it.second.isNotEmpty() }
            val firstItem = recentByLibrary.firstOrNull()?.second?.firstOrNull()
            if (firstItem != null) {
                hero = runCatching { FnApi.itemDetail(firstItem.guid) }.getOrNull()
            }
        }.onFailure { error = it.message ?: "加载失败" }
        loading = false
    }

    LaunchedEffect(Unit) { load() }
    LaunchedEffect(loading) {
        if (!loading) {
            androidx.compose.runtime.withFrameNanos { }
            androidx.compose.runtime.withFrameNanos { }
            runCatching { playFocus.requestFocus() }
            kotlinx.coroutines.delay(60)
            scrollState.scrollTo(0)
        }
    }

    ScreenWithActions(onSearch = onSearch, onSettings = onSettings, contentFocus = playFocus) {
        Column(
            Modifier
                .fillMaxSize()
                .background(FnColors.Bg),
        ) {
            when {
                loading -> LoadingBox()
                error != null -> ErrorBox(error!!) { scope.launch { load() } }
                else -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState),
                ) {
                    Spacer(Modifier.height(8.dp))
                    Hero(
                        detail = hero,
                        onPlay = {
                            val item = recentByLibrary.firstOrNull()?.second?.firstOrNull() ?: return@Hero
                            scope.launch {
                                runCatching { FnApi.playInfo(item.playableGuid) }
                                    .onSuccess { onPlay(item.displayTitle, it.mediaGuid) }
                            }
                        },
                        onInfo = {
                            recentByLibrary.firstOrNull()?.second?.firstOrNull()?.let(onOpenDetail)
                        },
                        playFocus = playFocus,
                    )
                    SectionTitle("媒体库")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 43.dp),
                        horizontalArrangement = Arrangement.spacedBy(13.dp),
                    ) {
                        items(libraries) { library ->
                            LibraryCard(library) { onOpenLibrary(library) }
                        }
                    }
                    SectionTitle("继续观看")
                    if (continueWatching.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 43.dp),
                            horizontalArrangement = Arrangement.spacedBy(13.dp),
                        ) {
                            items(continueWatching) { item ->
                                WideCard(
                                    item = item,
                                    badge = continueBadge(item),
                                    progress = progressOf(item),
                                    onClick = { onOpenDetail(item) },
                                )
                            }
                        }
                    }
                    recentByLibrary.forEach { (library, items) ->
                        SectionTitle(library.title)
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 43.dp),
                            horizontalArrangement = Arrangement.spacedBy(13.dp),
                        ) {
                            items(items) { item ->
                                PosterCard(item, onClick = { onOpenDetail(item) })
                            }
                        }
                    }
                    Spacer(Modifier.height(120.dp))
                }
            }
        }
    }
}

private fun progressOf(item: MediaItem): Float =
    if (item.duration > 0 && item.ts > 0) (item.ts.toFloat() / item.duration).coerceIn(0f, 1f) else 0f

private fun continueBadge(item: MediaItem): String {
    val episode = if (item.episodeNumber > 0) "第 ${item.episodeNumber} 集" else ""
    val remain = if (item.duration > 0 && item.ts > 0) {
        val left = (item.duration - item.ts).coerceAtLeast(0)
        "剩余 ${left / 60} 分"
    } else {
        ""
    }
    return listOf(episode, remain).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { "继续观看" }
}

@Composable
private fun LibraryCard(library: Library, onClick: () -> Unit) {
    Column(Modifier.width(220.dp)) {
        FocusableCard(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            shape = RoundedCornerShape(12.dp),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(FnColors.Surface2)
                    .padding(8.dp),
            ) {
                AsyncImage(
                    model = FnApi.imageUrl(library.poster, 500),
                    contentDescription = library.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            library.title,
            color = FnColors.Text,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
