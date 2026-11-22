package com.lumo.player.ui.screens

import android.os.Build
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumo.player.data.FnApi
import com.lumo.player.data.MediaItem
import com.lumo.player.ui.FnColors
import com.lumo.player.ui.LoadingBox
import com.lumo.player.ui.PosterCard
import com.lumo.player.ui.focusGlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SearchScreen(onOpen: (MediaItem) -> Unit) {
    var allItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var initials by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching {
            val libraries = FnApi.libraries()
            val items = libraries.flatMap { library ->
                runCatching { FnApi.allItems(library.guid, maxItems = 400) }.getOrDefault(emptyList())
            }.distinctBy { it.guid }
            allItems = items
            initials = withContext(Dispatchers.Default) {
                items.mapNotNull { item ->
                    pinyinInitials(item.title.ifEmpty { item.displayTitle })?.let { item.guid to it }
                }.toMap()
            }
        }
        loading = false
        runCatching { firstFocus.requestFocus() }
    }

    val results = if (query.isEmpty()) {
        emptyList()
    } else {
        allItems.filter { initials[it.guid]?.startsWith(query) == true }
    }

    Row(
        Modifier
            .fillMaxSize()
            .background(FnColors.Bg)
            .onPreviewKeyEvent { event ->
                if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
                    (event.key == androidx.compose.ui.input.key.Key.Backspace ||
                        event.key == androidx.compose.ui.input.key.Key.Delete)
                ) {
                    query = query.dropLast(1)
                    true
                } else {
                    false
                }
            }
            .padding(horizontal = 43.dp, vertical = 20.dp),
    ) {
        Column(Modifier.width(330.dp)) {
            Text("搜索", color = FnColors.Text, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(4.dp))
            Text("按拼音首字母连续搜索（中英文）", color = FnColors.Sub, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                if (query.isEmpty()) "输入首字母，例如 A S D S" else query.toCharArray().joinToString(" "),
                color = if (query.isEmpty()) FnColors.Sub else FnColors.Accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            val keys = ('A'..'Z').map { it.toString() } + "撤销"
            keys.chunked(4).forEach { rowKeys ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowKeys.forEach { key ->
                        KeyButton(
                            label = key,
                            selected = key.length == 1 && query.lastOrNull() == key[0],
                            modifier = if (key == "A") Modifier.focusRequester(firstFocus) else Modifier,
                        ) {
                            query = if (key == "撤销") {
                                query.dropLast(1)
                            } else {
                                query + key
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.width(24.dp))

        Box(Modifier.fillMaxSize()) {
            when {
                loading -> LoadingBox()
                query.isEmpty() -> Text(
                    "选择字母开始搜索，可连续输入",
                    color = FnColors.Sub,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(8.dp),
                )
                results.isEmpty() -> Text(
                    "没有找到相关内容",
                    color = FnColors.Sub,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(8.dp),
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    contentPadding = PaddingValues(bottom = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(results) { item ->
                        PosterCard(item, width = 140.dp, onClick = { onOpen(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .width(74.dp)
            .height(48.dp)
            .focusGlow(focused, 10.dp)
            .clip(shape)
            .background(
                when {
                    selected -> FnColors.Accent.copy(alpha = 0.35f)
                    else -> FnColors.Surface2
                },
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = when {
                selected -> FnColors.Accent
                focused -> FnColors.Text
                else -> FnColors.Sub
            },
            fontSize = if (label.length > 1) 12.sp else 16.sp,
            fontWeight = if (label.length == 1) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

private fun pinyinInitials(title: String): String? {
    if (title.isBlank()) return null
    val builder = StringBuilder()
    val transliterator = if (Build.VERSION.SDK_INT >= 29) {
        runCatching { android.icu.text.Transliterator.getInstance("Han-Latin; Latin-ASCII") }.getOrNull()
    } else {
        null
    }
    title.forEach { ch ->
        when {
            ch.code < 128 -> if (ch.isLetter()) builder.append(ch.uppercaseChar())
            transliterator != null -> {
                val latin = transliterator.transliterate(ch.toString())
                latin.firstOrNull { it.isLetter() }?.let { builder.append(it.uppercaseChar()) }
            }
        }
    }
    return builder.toString().ifEmpty { null }
}
