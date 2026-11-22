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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lumo.player.data.FnApi
import com.lumo.player.data.Genre
import com.lumo.player.data.Library
import com.lumo.player.data.MediaItem
import com.lumo.player.data.TagOptions
import com.lumo.player.ui.ErrorBox
import com.lumo.player.ui.FnColors
import com.lumo.player.ui.LoadingBox
import com.lumo.player.ui.PosterCard
import com.lumo.player.ui.ScreenWithActions
import com.lumo.player.ui.focusGlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@Composable
fun LibraryScreen(
    library: Library,
    onOpen: (MediaItem) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var allItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var tagOptions by remember { mutableStateOf(TagOptions(emptyList(), emptyList())) }
    var genreNames by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var genresByGuid by remember { mutableStateOf<Map<String, List<Int>>>(emptyMap()) }
    var selectedGenre by remember { mutableStateOf<Genre?>(null) }
    var sortColumn by remember { mutableStateOf("create_time") }
    var sortType by remember { mutableStateOf("DESC") }
    var resolution by remember { mutableStateOf<String?>(null) }
    var showGenreDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    val firstFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val headerFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val gridFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    var selectedIndex by remember { mutableStateOf(0) }
    var gridFocused by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(Unit) {
        genreNames = runCatching { FnApi.genres() }
            .getOrDefault(emptyList())
            .associate { it.id to it.name }
    }
    LaunchedEffect(library.guid) {
        tagOptions = runCatching { FnApi.tagOptions() }
            .getOrDefault(TagOptions(emptyList(), emptyList()))
    }

    LaunchedEffect(library.guid) {
        loading = true
        error = null
        runCatching { allItems = FnApi.allItems(library.guid) }
            .onFailure { error = it.message ?: "加载失败" }
        loading = false
    }

    LaunchedEffect(allItems) {
        val map = mutableMapOf<String, List<Int>>()
        allItems.chunked(8).forEach { chunk ->
            val results = kotlinx.coroutines.coroutineScope {
                chunk.map { item ->
                    async(kotlinx.coroutines.Dispatchers.IO) {
                        item.guid to runCatching { FnApi.itemGenres(item.guid) }.getOrDefault(emptyList())
                    }
                }.map { it.await() }
            }
            results.forEach { (guid, genres) -> map[guid] = genres }
            genresByGuid = map.toMap()
        }
    }

    LaunchedEffect(allItems, selectedGenre, sortColumn, sortType, resolution) {
        val collator = java.text.Collator.getInstance(java.util.Locale.CHINA)
        var filtered = allItems
        selectedGenre?.let { genre ->
            filtered = filtered.filter { item ->
                genresByGuid[item.guid]?.contains(genre.id) == true
            }
        }
        resolution?.let { res ->
            filtered = filtered.filter { item ->
                item.resolutions.any { it.equals(res, ignoreCase = true) }
            }
        }
        items = when (sortColumn) {
            "vote_average" -> filtered.sortedByDescending { it.voteAverage.toDoubleOrNull() ?: 0.0 }
            "title" -> filtered.sortedWith { a, b -> collator.compare(a.title, b.title) }
            else -> filtered.sortedByDescending { it.createTime }
        }
        android.util.Log.d(
            "LumoLib",
            "all=${allItems.size} filtered=${filtered.size} res=$resolution genre=${selectedGenre?.id}",
        )
    }
    LaunchedEffect(loading, items.size) {
        if (!loading) {
            selectedIndex = 0
            runCatching { gridFocus.requestFocus() }
        }
    }

    ScreenWithActions(onSearch = onSearch, onSettings = onSettings, contentFocus = gridFocus) {
        Column(
            Modifier
                .fillMaxSize()
                .background(FnColors.Bg),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 43.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(library.title, color = FnColors.Text, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.width(12.dp))
                Text("共 ${items.size} 部", color = FnColors.Sub, fontSize = 12.sp)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 43.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeaderButton(
                    label = selectedGenre?.let { "分类：${it.name}" } ?: "分类",
                    modifier = Modifier.focusRequester(headerFocus),
                ) { showGenreDialog = true }
                Spacer(Modifier.width(8.dp))
                HeaderButton("筛选") { showFilterDialog = true }
            }
            Spacer(Modifier.height(14.dp))
            when {
                loading -> LoadingBox()
                error != null -> ErrorBox(error!!) { }
                items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("没有符合条件的内容", color = FnColors.Sub, fontSize = 14.sp)
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    state = gridState,
                    contentPadding = PaddingValues(start = 43.dp, end = 43.dp, top = 10.dp, bottom = 180.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(gridFocus)
                        .onFocusChanged { gridFocused = it.isFocused }
                        .onKeyEvent { event ->
                            if (event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) {
                                return@onKeyEvent false
                            }
                            when (event.key) {
                                androidx.compose.ui.input.key.Key.DirectionDown -> {
                                    val next = selectedIndex + 7
                                    if (next <= items.lastIndex) {
                                        selectedIndex = next
                                        scope.launch { gridState.animateScrollToItem(selectedIndex) }
                                    }
                                    true
                                }
                                androidx.compose.ui.input.key.Key.DirectionUp -> {
                                    if (selectedIndex < 7) {
                                        headerFocus.requestFocus()
                                    } else {
                                        selectedIndex -= 7
                                        scope.launch { gridState.animateScrollToItem(selectedIndex) }
                                    }
                                    true
                                }
                                androidx.compose.ui.input.key.Key.DirectionLeft -> {
                                    if (selectedIndex % 7 > 0) {
                                        selectedIndex -= 1
                                        scope.launch { gridState.animateScrollToItem(selectedIndex) }
                                        true
                                    } else {
                                        false
                                    }
                                }
                                androidx.compose.ui.input.key.Key.DirectionRight -> {
                                    if (selectedIndex % 7 < 6 && selectedIndex < items.lastIndex) {
                                        selectedIndex += 1
                                        scope.launch { gridState.animateScrollToItem(selectedIndex) }
                                        true
                                    } else {
                                        false
                                    }
                                }
                                androidx.compose.ui.input.key.Key.DirectionCenter -> {
                                    items.getOrNull(selectedIndex)?.let(onOpen)
                                    true
                                }
                                else -> false
                            }
                        }
                        .focusable(),
                ) {
                    itemsIndexed(items) { index, item ->
                        PosterCard(
                            item,
                            width = 140.dp,
                            focusedOverride = gridFocused && index == selectedIndex,
                            interactive = false,
                            onClick = {},
                        )
                    }
                }
            }
        }
    }

    val availableGenres = tagOptions.genres
    val availableResolutions = tagOptions.resolutions

    if (showGenreDialog) {
        ChoiceDialog(
            title = "分类",
            choices = listOf("全部" to (selectedGenre == null)) +
                availableGenres.map { it.name to (selectedGenre?.id == it.id) },
            onPick = { index ->
                selectedGenre = if (index == 0) null else availableGenres.getOrNull(index - 1)
                showGenreDialog = false
            },
            onDismiss = { showGenreDialog = false },
        )
    }

    if (showFilterDialog) {
        val sortChoices = listOf(
            "排序：最新入库" to (sortColumn == "create_time"),
            "排序：评分最高" to (sortColumn == "vote_average"),
            "排序：名称" to (sortColumn == "title"),
        )
        val resolutionChoices = listOf("分辨率：全部" to (resolution == null)) +
            availableResolutions.map { "分辨率：${it.uppercase()}" to (resolution == it) }
        ChoiceDialog(
            title = "筛选",
            choices = sortChoices + resolutionChoices,
            onPick = { index ->
                when (index) {
                    0 -> { sortColumn = "create_time"; sortType = "DESC" }
                    1 -> { sortColumn = "vote_average"; sortType = "DESC" }
                    2 -> { sortColumn = "title"; sortType = "ASC" }
                    else -> {
                        resolution = if (index == 3) null else availableResolutions.getOrNull(index - 4)
                    }
                }
                showFilterDialog = false
            },
            onDismiss = { showFilterDialog = false },
        )
    }
}

@Composable
private fun HeaderButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .height(26.dp)
            .focusGlow(focused, 13.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(FnColors.Surface2)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (focused) FnColors.Text else FnColors.Sub, fontSize = 11.sp)
    }
}

@Composable
private fun ChoiceDialog(
    title: String,
    choices: List<Pair<String, Boolean>>,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(380.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(FnColors.Surface)
                .padding(20.dp),
        ) {
            Text(title, color = FnColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                choices.forEachIndexed { index, (label, selected) ->
                    var focused by remember { mutableStateOf(false) }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (focused) Color(0x1A4C8DFF) else Color.Transparent)
                            .border(1.dp, if (focused) FnColors.Accent else Color.Transparent, RoundedCornerShape(8.dp))
                            .onFocusChanged { focused = it.isFocused }
                            .focusable()
                            .clickable { onPick(index) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            label,
                            color = if (selected) FnColors.Accent else FnColors.Text,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }
}
