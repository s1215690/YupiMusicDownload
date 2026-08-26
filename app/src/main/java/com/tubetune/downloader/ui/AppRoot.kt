package com.tubetune.downloader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.tubetune.downloader.AppViewModel
import com.tubetune.downloader.ui.screens.DownloadsScreen
import com.tubetune.downloader.ui.screens.LibraryScreen
import com.tubetune.downloader.ui.screens.PlayerScreen
import com.tubetune.downloader.ui.screens.SearchScreen
import com.tubetune.downloader.ui.screens.SettingsScreen

@Composable
fun AppRoot(vm: AppViewModel, onThemeChange: (String) -> Unit) {
    var tab by remember { mutableStateOf(0) }
    val playerState by vm.player.state.collectAsState()
    val openPlayer by vm.openPlayer.collectAsState()

    Box(Modifier.fillMaxSize().threeFingerTap { vm.togglePlayback() }) {
        Scaffold(
            bottomBar = {
                Column {
                    AnimatedVisibility(visible = playerState != null && !openPlayer) {
                        MiniPlayer(vm)
                    }
                    NavigationBar {
                        NavigationBarItem(
                            selected = tab == 0, onClick = { tab = 0 },
                            icon = { Icon(Icons.Filled.Search, contentDescription = "搜尋") },
                            label = { Text("搜尋") }
                        )
                        NavigationBarItem(
                            selected = tab == 1, onClick = { tab = 1 },
                            icon = { Icon(Icons.Filled.Download, contentDescription = "下載") },
                            label = { Text("下載") }
                        )
                        NavigationBarItem(
                            selected = tab == 2, onClick = { tab = 2 },
                            icon = { Icon(Icons.Filled.LibraryMusic, contentDescription = "音樂庫") },
                            label = { Text("音樂庫") }
                        )
                        NavigationBarItem(
                            selected = tab == 3, onClick = { tab = 3 },
                            icon = { Icon(Icons.Filled.Settings, contentDescription = "設定") },
                            label = { Text("設定") }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (tab) {
                    0 -> SearchScreen(vm)
                    1 -> DownloadsScreen(vm)
                    2 -> LibraryScreen(vm)
                    else -> SettingsScreen(vm, onThemeChange)
                }
            }
        }
        AnimatedVisibility(
            visible = openPlayer && playerState != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            PlayerScreen(vm)
        }
    }
}
