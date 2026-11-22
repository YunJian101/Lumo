package com.lumo.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumo.player.BuildConfig
import com.lumo.player.ui.FnColors
import com.lumo.player.ui.focusGlow

@Composable
fun SettingsScreen(
    host: String,
    user: String,
    onLogout: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(FnColors.Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 43.dp, vertical = 24.dp),
    ) {
        Text("设置", color = FnColors.Text, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(4.dp))
        Text("账号与应用信息", color = FnColors.Sub, fontSize = 12.sp)

        Spacer(Modifier.height(22.dp))
        InfoCard(title = "账号信息") {
            InfoRow("服务器", host.ifEmpty { "未登录" })
            InfoRow("账号", user.ifEmpty { "未登录" })
            InfoRow("状态", if (user.isNotEmpty()) "已登录" else "未登录")
        }

        Spacer(Modifier.height(16.dp))
        InfoCard(title = "应用信息") {
            InfoRow("名称", "流光影视 · Lumo")
            InfoRow("版本", "v${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）")
            InfoRow("包名", BuildConfig.APPLICATION_ID)
        }

        Spacer(Modifier.height(26.dp))
        LogoutButton(onLogout)
        Spacer(Modifier.height(10.dp))
        Text(
            "退出后需要重新输入服务器和账号",
            color = Color(0xFF6F8099),
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(FnColors.Surface, RoundedCornerShape(16.dp))
            .padding(horizontal = 22.dp, vertical = 18.dp),
    ) {
        Text(title, color = FnColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = FnColors.Sub, fontSize = 12.sp, modifier = Modifier.width(90.dp))
        Text(
            value,
            color = FnColors.Text,
            fontSize = 13.sp,
            maxLines = 2,
        )
    }
}

@Composable
private fun LogoutButton(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .focusGlow(focused, 11.dp)
            .background(Color(0x1AFF5A5F), RoundedCornerShape(11.dp))
            .border(1.dp, Color(0x66FF5A5F), RoundedCornerShape(11.dp))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("退出登录", color = Color(0xFFFF6B6B), fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
