package com.tubetune.downloader.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tubetune.downloader.AppViewModel
import com.tubetune.downloader.data.Prefs
import com.tubetune.downloader.playback.PlaybackService

@Composable
fun SettingsScreen(vm: AppViewModel, onThemeChange: (String) -> Unit) {
    val context = LocalContext.current
    var quality by remember { mutableStateOf(Prefs.quality(context)) }
    var theme by remember { mutableStateOf(Prefs.theme(context)) }
    var floating by remember { mutableStateOf(Prefs.floatingPlayer(context)) }
    val hasOverlay = Settings.canDrawOverlays(context)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text("設定", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        Text("懸浮播放視窗", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Card {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("迷你播放器", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "在任何 App 上方顯示可拖動的迷你播放視窗（封面、曲名、控制鍵）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = floating,
                        onCheckedChange = { v ->
                            floating = v
                            Prefs.setFloatingPlayer(context, v)
                            if (v && Settings.canDrawOverlays(context)) {
                                try {
                                    context.startService(
                                        Intent(context, PlaybackService::class.java)
                                    )
                                } catch (t: Throwable) {
                                }
                            }
                        }
                    )
                }
                if (!hasOverlay) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "開啟後需要授予「顯示在其他應用程式上層」權限：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = {
                        try {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:" + context.packageName)
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (t: Throwable) {
                        }
                    }) {
                        Text("前往系統授權")
                    }
                } else if (floating) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "已授權。播放音樂時懸浮視窗即會出現，可拖動到任何位置。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("音訊品質", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QualityOption(
                "AAC / M4A（相容性最佳，推薦）",
                "多數播放器與音樂 App 都支援",
                quality == "aac"
            ) {
                quality = "aac"
                Prefs.setQuality(context, "aac")
            }
            QualityOption(
                "最高音質（可能是 Opus / WebM）",
                "位元率更高，但部分 App 不支援",
                quality == "best"
            ) {
                quality = "best"
                Prefs.setQuality(context, "best")
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("外觀主題", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = theme == "system",
                onClick = { theme = "system"; Prefs.setTheme(context, "system"); onThemeChange("system") },
                label = { Text("跟隨系統") }
            )
            FilterChip(
                selected = theme == "light",
                onClick = { theme = "light"; Prefs.setTheme(context, "light"); onThemeChange("light") },
                label = { Text("淺色") }
            )
            FilterChip(
                selected = theme == "dark",
                onClick = { theme = "dark"; Prefs.setTheme(context, "dark"); onThemeChange("dark") },
                label = { Text("深色") }
            )
        }

        Spacer(Modifier.height(24.dp))
        Card {
            Column(Modifier.padding(16.dp)) {
                Text("使用說明", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Text("1. 在「搜尋」頁輸入歌名或歌手，點擊結果右側的下載圖示即可下載。", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                Text("2. 長按搜尋結果進入多選模式，可一次勾選多首批量下載。", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                Text("3. 下載的音樂會存到手機的 Music/TubeTune 資料夾，並按分類顯示在「音樂庫」。", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                Text("4. 也可以從 YouTube App「分享」連結到本 App 直接下載。", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "免責聲明：本 App 僅供個人備份與學習使用，請尊重創作者版權，勿下載未經授權的內容或用於商業用途。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Card {
            Column(Modifier.padding(16.dp)) {
                Text("關於", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Text("TubeTune 音樂下載 v1.0", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(2.dp))
                Text(
                    "作者：Keith Yu",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "原始碼（GitHub）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        try {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/s1215690/YupiMusicDownload")
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (t: Throwable) {
                        }
                    }
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "© Keith Yu · TubeTune v1.0",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun QualityOption(title: String, desc: String, selected: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
