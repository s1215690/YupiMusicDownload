package com.tubetune.downloader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tubetune.downloader.AppViewModel
import com.tubetune.downloader.ui.screens.DownloadsScreen
import com.tubetune.downloader.ui.screens.LibraryScreen
import com.tubetune.downloader.ui.screens.PlayerScreen
import com.tubetune.downloader.ui.screens.SearchScreen
import com.tubetune.downloader.ui.screens.SettingsScreen

private data class TabItem(val label: String, val icon: ImageVector)

private val TABS = listOf(
    TabItem("搜尋", Icons.Filled.Search),
    TabItem("下載", Icons.Filled.Download),
    TabItem("音樂庫", Icons.Filled.LibraryMusic),
    TabItem("設定", Icons.Filled.Settings)
)

@Composable
fun AppRoot(vm: AppViewModel, onThemeChange: (String) -> Unit) {
    var tab by remember { mutableStateOf(0) }
    val playerState by vm.player.state.collectAsState()
    val openPlayer by vm.openPlayer.collectAsState()

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .threeFingerTap { vm.togglePlayback() }
    ) {
        // 頂部極光光暈（現代音樂 App 的氛圍感）
        Box(
            Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.06f),
                            Color.Transparent
                        )
                    )
                )
        )

        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp)) {
                    AnimatedVisibility(
                        visible = playerState != null && !openPlayer,
                        enter = slideInVertically { it / 2 } + fadeIn(),
                        exit = slideOutVertically { it / 2 } + fadeOut()
                    ) {
                        Column {
                            MiniPlayer(vm)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    FloatingNavBar(tab) { tab = it }
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

        // AI 歌詞對話框（任何畫面之上）
        LyricsDialog(vm)

        AnimatedVisibility(
            visible = openPlayer && playerState != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            PlayerScreen(vm)
        }
    }
}

@Composable
private fun FloatingNavBar(selected: Int, onSelect: (Int) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.97f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.32f)),
        shadowElevation = 14.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TABS.forEachIndexed { i, item ->
                val sel = selected == i
                val tint = if (sel) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
                // 標準底部導覽：圖示 24dp + 永遠顯示文字（唔會只有 icon 而睇落好細）
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            else Color.Transparent
                        )
                        .clickable { onSelect(i) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        item.icon,
                        contentDescription = item.label,
                        modifier = Modifier.size(24.dp),
                        tint = tint
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = tint,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
