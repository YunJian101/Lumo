package com.lumo.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lumo.player.data.FnApi
import com.lumo.player.data.Library
import com.lumo.player.data.MediaItem
import com.lumo.player.data.Session
import com.lumo.player.ui.FloatingActions
import com.lumo.player.ui.FnColors
import com.lumo.player.ui.FnTheme
import com.lumo.player.ui.screens.DetailScreen
import com.lumo.player.ui.screens.HomeScreen
import com.lumo.player.ui.screens.LibraryScreen
import com.lumo.player.ui.screens.LoginScreen
import com.lumo.player.ui.screens.PlayerScreen
import com.lumo.player.ui.screens.SearchScreen
import com.lumo.player.ui.screens.SettingsScreen

sealed interface Screen {
    data object Login : Screen
    data object Home : Screen
    data class LibraryPage(val library: Library) : Screen
    data class Detail(val item: MediaItem) : Screen
    data class Player(val title: String, val mediaGuid: String) : Screen
    data object Search : Screen
    data object Settings : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val host = Session.host(this)
        val token = Session.token(this)
        FnApi.host = host
        FnApi.token = token

        setContent {
            FnTheme {
                AppRoot(loggedIn = token.isNotEmpty())
            }
        }
    }
}

@Composable
fun AppRoot(loggedIn: Boolean) {
    val context = LocalContext.current
    val stack = remember {
        mutableStateListOf<Screen>(if (loggedIn) Screen.Home else Screen.Login)
    }

    BackHandler(enabled = stack.size > 1) {
        stack.removeAt(stack.lastIndex)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(FnColors.Bg),
    ) {
        Crossfade(
            targetState = stack.last(),
            animationSpec = tween(180),
            label = "screen",
        ) { screen ->
            when (screen) {
                Screen.Login -> LoginScreen(initialHost = Session.host(context)) {
                    stack.clear()
                    stack.add(Screen.Home)
                }

                Screen.Home -> HomeScreen(
                    onOpenLibrary = { stack.add(Screen.LibraryPage(it)) },
                    onOpenDetail = { stack.add(Screen.Detail(it)) },
                    onPlay = { title, guid -> stack.add(Screen.Player(title, guid)) },
                    onSearch = { stack.add(Screen.Search) },
                    onSettings = { stack.add(Screen.Settings) },
                )

                is Screen.LibraryPage -> LibraryScreen(
                    library = screen.library,
                    onOpen = { stack.add(Screen.Detail(it)) },
                    onSearch = { stack.add(Screen.Search) },
                    onSettings = { stack.add(Screen.Settings) },
                )

                is Screen.Detail -> DetailScreen(
                    item = screen.item,
                    onPlay = { title, guid -> stack.add(Screen.Player(title, guid)) },
                    onSearch = { stack.add(Screen.Search) },
                    onSettings = { stack.add(Screen.Settings) },
                )

                is Screen.Player -> PlayerScreen(
                    title = screen.title,
                    mediaGuid = screen.mediaGuid,
                    onBack = { stack.removeAt(stack.lastIndex) },
                )

                Screen.Search -> SearchScreen(
                    onOpen = { stack.add(Screen.Detail(it)) },
                )

                Screen.Settings -> SettingsScreen(
                    host = FnApi.host,
                    user = Session.user(context),
                    onLogout = {
                        Session.clear(context)
                        FnApi.token = ""
                        stack.clear()
                        stack.add(Screen.Login)
                    },
                )
            }
        }
    }
}
