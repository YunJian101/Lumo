package com.lumo.player.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lumo.player.data.FnApi
import com.lumo.player.data.ItemDetail
import com.lumo.player.data.MediaItem

@Composable
fun FocusableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(10.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .focusGlow(focused, 12.dp)
            .clip(shape)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
        content = content,
    )
}

@Composable
fun Modifier.focusGlow(focused: Boolean, radiusDp: Dp = 12.dp): Modifier {
    if (!focused) return this
    val transition = rememberInfiniteTransition(label = "focusGlow")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(
                durationMillis = 1400,
                easing = androidx.compose.animation.core.LinearEasing,
            ),
        ),
        label = "shift",
    )
    return drawWithContent {
        drawContent()
        val angle = shift * 2f * Math.PI.toFloat()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = kotlin.math.max(size.width, size.height)
        val start = androidx.compose.ui.geometry.Offset(
            cx + kotlin.math.cos(angle) * radius,
            cy + kotlin.math.sin(angle) * radius,
        )
        val end = androidx.compose.ui.geometry.Offset(
            cx - kotlin.math.cos(angle) * radius,
            cy - kotlin.math.sin(angle) * radius,
        )
        val brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFFFF2D55),
                Color(0xFFFFD60A),
                Color(0xFF00E5FF),
                Color(0xFF7C4DFF),
                Color(0xFFFF2D55),
            ),
            start = start,
            end = end,
        )
        val corner = androidx.compose.ui.geometry.CornerRadius(radiusDp.toPx())
        drawRoundRect(
            brush = brush,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()),
            cornerRadius = corner,
        )
    }
}

@Composable
fun TopBar(active: String, onNav: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(59.dp)
            .padding(horizontal = 43.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("LUMO", color = FnColors.Text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text("·", color = FnColors.Accent, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text("PLAY", color = FnColors.Text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.width(29.dp))
        listOf("首页", "媒体库", "搜索", "设置").forEach { label ->
            NavItem(label, label == active) { onNav(label) }
            Spacer(Modifier.width(24.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(FnApi.host.removePrefix("http://"), color = FnColors.Sub, fontSize = 12.sp)
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .size(27.dp)
                .clip(CircleShape)
                .background(Color(0xFF2B5FD9)),
            contentAlignment = Alignment.Center,
        ) {
            Text("H", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NavItem(label: String, active: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            color = if (active || focused) FnColors.Text else FnColors.Sub,
            fontSize = 14.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .clickable(onClick = onClick)
                .padding(vertical = 6.dp, horizontal = 4.dp),
        )
        Box(
            Modifier
                .height(2.dp)
                .width(if (active) 26.dp else 0.dp)
                .background(if (active) FnColors.Accent else Color.Transparent),
        )
    }
}

@Composable
fun SectionTitle(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 43.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = FnColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (action != null) {
            Spacer(Modifier.width(14.dp))
            Text(action, color = FnColors.Sub, fontSize = 12.sp, modifier = Modifier.clickable { onAction?.invoke() })
        }
    }
}

@Composable
fun PosterCard(
    item: MediaItem,
    width: Dp = 150.dp,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    upFocus: androidx.compose.ui.focus.FocusRequester? = null,
    focusedOverride: Boolean? = null,
    interactive: Boolean = true,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(9.dp)
    val isFocused = focusedOverride ?: focused
    Column(
        Modifier
            .width(width)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(
                if (interactive) {
                    Modifier
                        .onFocusChanged { focused = it.isFocused }
                        .then(
                            if (upFocus != null) {
                                Modifier.focusProperties { up = upFocus }
                            } else {
                                Modifier
                            },
                        )
                        .clickable(onClick = onClick)
                } else {
                    Modifier
                },
            ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .focusGlow(isFocused, 9.dp)
                .clip(shape)
                .background(FnColors.Surface2),
        ) {
            AsyncImage(
                model = FnApi.imageUrl(item.poster, 400),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            val rating = item.voteAverage.toDoubleOrNull()?.takeIf { it > 0 }
            if (rating != null) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0x99000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        String.format(java.util.Locale.US, "%.1f", rating),
                        color = Color(0xE6FFFFFF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            item.title.ifEmpty { item.displayTitle },
            color = FnColors.Text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!item.year.isNullOrEmpty()) {
            Text(
                item.year,
                color = FnColors.Sub,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun WideCard(
    item: MediaItem,
    width: Dp = 300.dp,
    badge: String? = null,
    progress: Float = 0f,
    onClick: () -> Unit,
) {
    Column(Modifier.width(width)) {
        FocusableCard(onClick, Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            Box(Modifier.fillMaxSize().background(FnColors.Surface2)) {
                AsyncImage(
                    model = FnApi.imageUrl(item.poster, 500),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (badge != null) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(7.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0x99000000))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    ) {
                        Text(badge, color = Color.White, fontSize = 9.sp)
                    }
                }
                if (progress > 0f) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(Color(0x55FFFFFF)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .height(2.dp)
                                .background(FnColors.Accent),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        val seriesTitle = item.tvTitle?.takeIf { it.isNotEmpty() } ?: item.title
        val secondLine = when {
            !item.tvTitle.isNullOrEmpty() && item.title.isNotEmpty() && item.title != item.tvTitle -> item.title
            item.episodeNumber > 0 -> "第 ${item.episodeNumber} 集"
            !item.year.isNullOrEmpty() -> item.year
            else -> ""
        }
        Text(
            seriesTitle,
            color = FnColors.Text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (secondLine.isNotEmpty()) {
            Text(
                secondLine,
                color = FnColors.Sub,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun Hero(
    detail: ItemDetail?,
    onPlay: () -> Unit,
    onInfo: () -> Unit,
    playFocus: androidx.compose.ui.focus.FocusRequester? = null,
    rootFocus: androidx.compose.ui.focus.FocusRequester? = null,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 43.dp)
            .height(307.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(FnColors.Surface)
            .then(
                if (rootFocus != null) {
                    Modifier.focusRequester(rootFocus).focusable()
                } else {
                    Modifier
                },
            ),
    ) {
        if (detail?.backdrops != null) {
            AsyncImage(
                model = FnApi.imageUrl(detail.backdrops, 1280),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to FnColors.Bg.copy(alpha = 0.97f),
                        0.45f to FnColors.Bg.copy(alpha = 0.7f),
                        0.8f to Color.Transparent,
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1f to FnColors.Bg.copy(alpha = 0.9f),
                    ),
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 37.dp, bottom = 29.dp),
        ) {
            Text(
                detail?.title ?: "飞牛影视",
                color = FnColors.Text,
                fontSize = 41.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            val meta = buildList {
                detail?.voteAverage?.takeIf { it.isNotEmpty() && it != "0" }?.let { add("评分 $it") }
                detail?.numberEpisodes?.takeIf { it > 0 }?.let { add("$it 集") }
                add("4K")
                add("HEVC")
            }.joinToString("    ")
            Text(meta, color = FnColors.Sub, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            if (!detail?.overview.isNullOrEmpty()) {
                Text(
                    detail!!.overview,
                    color = Color(0xFFC7D2E2),
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(520.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                PrimaryButton(
                    "播放",
                    onPlay,
                    modifier = if (playFocus != null) Modifier.focusRequester(playFocus) else Modifier,
                )
                GhostButton("详情", onInfo)
            }
        }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .height(40.dp)
            .focusGlow(focused, 9.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(FnColors.Accent)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 25.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .height(40.dp)
            .focusGlow(focused, 9.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Color(0x1AFFFFFF))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(9.dp))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = FnColors.Text, fontSize = 14.sp)
    }
}

@Composable
fun ScreenWithActions(
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    contentFocus: FocusRequester? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        content()
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp),
        ) {
            FloatingActions(onSearch = onSearch, onSettings = onSettings, contentFocus = contentFocus)
        }
    }
}

@Composable
fun FloatingActions(
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    contentFocus: FocusRequester? = null,
) {
    val searchFocus = remember { FocusRequester() }
    val settingsFocus = remember { FocusRequester() }
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FloatingCircle(
            onClick = onSearch,
            modifier = Modifier.focusRequester(searchFocus),
            onDown = { runCatching { settingsFocus.requestFocus() } },
            onLeft = { contentFocus?.let { fr -> runCatching { fr.requestFocus() } } },
        ) { color ->
            drawCircle(color = color, radius = size.minDimension * 0.30f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f))
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.72f, size.height * 0.72f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.92f, size.height * 0.92f),
                strokeWidth = 2.5f,
            )
        }
        FloatingCircle(
            onClick = onSettings,
            modifier = Modifier.focusRequester(settingsFocus),
            onUp = { runCatching { searchFocus.requestFocus() } },
            onDown = { contentFocus?.let { fr -> runCatching { fr.requestFocus() } } },
            onLeft = { contentFocus?.let { fr -> runCatching { fr.requestFocus() } } },
        ) { color ->
            val ys = listOf(0.25f, 0.5f, 0.75f)
            ys.forEachIndexed { index, y ->
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(size.width * 0.12f, size.height * y),
                    end = androidx.compose.ui.geometry.Offset(size.width * 0.88f, size.height * y),
                    strokeWidth = 2.2f,
                )
                val x = if (index == 1) 0.68f else 0.34f
                drawCircle(color = color, radius = size.minDimension * 0.10f, center = androidx.compose.ui.geometry.Offset(size.width * x, size.height * y))
            }
        }
    }
}

@Composable
private fun FloatingCircle(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    onLeft: (() -> Unit)? = null,
    icon: androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .size(46.dp)
            .focusGlow(focused, 23.dp)
            .clip(CircleShape)
            .background(Color(0xE6101826))
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val action = when (event.key) {
                    Key.DirectionUp -> onUp
                    Key.DirectionDown -> onDown
                    Key.DirectionLeft -> onLeft
                    else -> null
                }
                if (action != null) {
                    action.invoke()
                    true
                } else {
                    false
                }
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
            icon(if (focused) FnColors.Accent else Color(0xFFDCE6F5))
        }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = FnColors.Accent)
    }
}

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = FnColors.Sub, fontSize = 14.sp)
            Spacer(Modifier.height(14.dp))
            PrimaryButton("重试", onRetry)
        }
    }
}
