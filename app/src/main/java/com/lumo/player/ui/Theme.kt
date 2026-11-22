package com.lumo.player.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

object FnColors {
    val Bg = Color(0xFF070B12)
    val Surface = Color(0xFF101826)
    val Surface2 = Color(0xFF16202F)
    val Text = Color(0xFFF4F7FB)
    val Sub = Color(0xFF93A3B8)
    val Accent = Color(0xFF4C8DFF)
    val Line = Color(0x1AFFFFFF)
    val Badge = Color(0x99000000)
}

private val Colors = darkColorScheme(
    primary = FnColors.Accent,
    onPrimary = Color.White,
    background = FnColors.Bg,
    onBackground = FnColors.Text,
    surface = FnColors.Surface,
    onSurface = FnColors.Text,
)

/**
 * 以 1280×720dp 为设计画布：按屏幕宽度反推 density，
 * 保证任何分辨率/DPI 下，布局占屏比例与预览图一致。
 */
@Composable
fun FnTheme(content: @Composable () -> Unit) {
    val dm = LocalContext.current.resources.displayMetrics
    val designDensity = dm.widthPixels / 1280f
    CompositionLocalProvider(
        LocalDensity provides Density(designDensity, 1f),
    ) {
        MaterialTheme(colorScheme = Colors, content = content)
    }
}
