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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumo.player.data.FnApi
import com.lumo.player.data.MediaItem
import com.lumo.player.ui.ErrorBox
import com.lumo.player.ui.FnColors
import com.lumo.player.ui.LoadingBox
import com.lumo.player.ui.PosterCard
import com.lumo.player.ui.TopBar
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    onOpen: (MediaItem) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("lumo_prefs", android.content.Context.MODE_PRIVATE) }
    var recent by remember {
        mutableStateOf(
            prefs.getString("recent_search", "")
                ?.split("|")
                ?.filter { it.isNotBlank() }
                ?: emptyList(),
        )
    }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = emptyList()
            error = null
            return@LaunchedEffect
        }
        delay(350)
        loading = true
        error = null
        runCatching { FnApi.search(query) }
            .onSuccess {
                results = it
                if (it.isNotEmpty()) {
                    recent = (listOf(query) + recent).distinct().take(6)
                    prefs.edit().putString("recent_search", recent.joinToString("|")).apply()
                }
            }
            .onFailure { error = it.message ?: "搜索失败" }
        loading = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(FnColors.Bg)
            .padding(top = 14.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("搜索电影、剧集、动漫", color = FnColors.Sub, fontSize = 13.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = FnColors.Accent,
                unfocusedBorderColor = Color(0x33FFFFFF),
                focusedContainerColor = FnColors.Surface2,
                unfocusedContainerColor = FnColors.Surface2,
                focusedTextColor = FnColors.Text,
                unfocusedTextColor = FnColors.Text,
                cursorColor = FnColors.Accent,
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 43.dp),
        )
        Spacer(Modifier.height(8.dp))
        if (recent.isNotEmpty()) {
            Row(
                Modifier.padding(horizontal = 43.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text("最近搜索", color = FnColors.Sub, fontSize = 10.sp)
                recent.forEach { value ->
                    RecentChip(value) { query = value }
                }
            }
        }
        when {
            loading -> LoadingBox()
            error != null -> ErrorBox(error!!) { }
            results.isEmpty() -> Text(
                if (query.isBlank()) "输入关键字开始搜索" else "没有找到相关内容",
                color = FnColors.Sub,
                fontSize = 13.sp,
                modifier = Modifier.padding(43.dp),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                contentPadding = PaddingValues(horizontal = 43.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(results) { item ->
                    PosterCard(item, width = 140.dp, onClick = { onOpen(item) })
                }
            }
        }
    }
}

@Composable
private fun RecentChip(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(FnColors.Surface2)
            .border(1.dp, if (focused) FnColors.Accent else Color(0x14FFFFFF), RoundedCornerShape(14.dp))
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(label, color = FnColors.Sub, fontSize = 11.sp)
    }
}
