package com.lumo.player.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumo.player.data.FnApi
import com.lumo.player.data.LoginQrServer
import com.lumo.player.data.Session
import com.lumo.player.ui.FnColors
import com.lumo.player.ui.PrimaryButton
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    initialHost: String,
    onLoggedIn: () -> Unit,
) {
    var host by remember { mutableStateOf(initialHost) }
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showQr by remember { mutableStateOf(false) }
    var qrUrl by remember { mutableStateOf<String?>(null) }
    var qrImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var qrFilled by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    DisposableEffect(Unit) {
        LoginQrServer.onConfig = { h, u, p ->
            host = h
            user = u
            password = p
            qrFilled = true
        }
        LoginQrServer.start()
        qrUrl = LoginQrServer.url()
        qrImage = qrUrl?.let { buildQrImage(it) }
        onDispose {
            LoginQrServer.onConfig = null
            LoginQrServer.stop()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(FnColors.Bg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(507.dp)
                .background(FnColors.Surface, RoundedCornerShape(17.dp))
                .padding(horizontal = 40.dp, vertical = 35.dp),
        ) {
            Text("LUMO", color = FnColors.Text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(12.dp))
            Text("登录流光影视", color = FnColors.Text, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(6.dp))
            Text("连接你的飞牛影视服务器，读取媒体库并播放", color = FnColors.Sub, fontSize = 12.sp)

            Spacer(Modifier.height(22.dp))
            Field("服务器地址", host, false) { host = it }
            Spacer(Modifier.height(14.dp))
            Field("账号", user, false) { user = it }
            Spacer(Modifier.height(14.dp))
            Field("密码", password, true) { password = it }

            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (qrFilled) "已接收手机填写的配置" else "手机扫码填写（同一 Wi-Fi）",
                    color = if (qrFilled) FnColors.Accent else FnColors.Sub,
                    fontSize = 12.sp,
                )
                Text(
                    if (showQr) "收起二维码" else "显示二维码",
                    color = FnColors.Accent,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { showQr = !showQr },
                )
            }

            if (showQr) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (qrImage != null) {
                        Image(
                            bitmap = qrImage!!,
                            contentDescription = "登录配置二维码",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(150.dp),
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("手机扫码打开配置页", color = FnColors.Text, fontSize = 13.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                qrUrl ?: "未获取到局域网地址",
                                color = FnColors.Sub,
                                fontSize = 11.sp,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "在手机上填写服务器地址、账号、密码，提交后电视端会自动填入。",
                                color = Color(0xFF6F8099),
                                fontSize = 11.sp,
                            )
                        }
                    } else {
                        Text("未获取到局域网地址，请检查网络", color = Color(0xFFFF6B6B), fontSize = 12.sp)
                    }
                }
            }

            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Text(error!!, color = Color(0xFFFF6B6B), fontSize = 12.sp)
            }

            Spacer(Modifier.height(22.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PrimaryButton(
                    text = if (loading) "登录中…" else "登录",
                    onClick = {
                        if (loading) return@PrimaryButton
                        loading = true
                        error = null
                        scope.launch {
                            runCatching { FnApi.login(host, user, password) }
                                .onSuccess { token ->
                                    Session.save(context, FnApi.host, token, user)
                                    onLoggedIn()
                                }
                                .onFailure { error = it.message ?: "登录失败" }
                            loading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "账号只保存在本机，不会上传",
                color = Color(0xFF6F8099),
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

private fun buildQrImage(text: String, size: Int = 480): ImageBitmap? = runCatching {
    val matrix = com.google.zxing.qrcode.QRCodeWriter()
        .encode(text, com.google.zxing.BarcodeFormat.QR_CODE, size, size)
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    bitmap.asImageBitmap()
}.getOrNull()

@Composable
private fun Field(label: String, value: String, password: Boolean, onChange: (String) -> Unit) {
    Column {
        Text(label, color = FnColors.Sub, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Text),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = FnColors.Accent,
                unfocusedBorderColor = Color(0x33FFFFFF),
                focusedContainerColor = Color(0xFF0C1420),
                unfocusedContainerColor = Color(0xFF0C1420),
                focusedTextColor = FnColors.Text,
                unfocusedTextColor = FnColors.Text,
                cursorColor = FnColors.Accent,
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
