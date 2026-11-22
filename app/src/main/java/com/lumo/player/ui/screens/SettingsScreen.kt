package com.lumo.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumo.player.ui.FnColors
import com.lumo.player.ui.PrimaryButton

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
            .padding(top = 24.dp),
    ) {
        Column(Modifier.padding(horizontal = 43.dp)) {
            Text("服务器", color = FnColors.Sub, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Text(host, color = FnColors.Text, fontSize = 15.sp)
            Spacer(Modifier.height(18.dp))
            Text("账号", color = FnColors.Sub, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Text(user.ifEmpty { "未登录" }, color = FnColors.Text, fontSize = 15.sp)
            Spacer(Modifier.height(28.dp))
            PrimaryButton("退出登录", onLogout, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Text(
                "退出后需要重新输入服务器和账号",
                color = Color(0xFF6F8099),
                fontSize = 11.sp,
            )
        }
    }
}
