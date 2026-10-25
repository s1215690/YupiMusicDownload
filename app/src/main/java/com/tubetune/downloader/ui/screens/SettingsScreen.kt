package com.tubetune.downloader.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tubetune.downloader.AppViewModel
import com.tubetune.downloader.data.Prefs
import com.tubetune.downloader.playback.PlaybackService
import com.tubetune.downloader.ui.SectionTitle
import com.tubetune.downloader.ui.cardBorder

@Composable
fun SettingsScreen(vm: AppViewModel, onThemeChange: (String) -> Unit) {
    val context = LocalContext.current
    var format by remember { mutableStateOf(Prefs.format(context)) }
    var bitrate by remember { mutableStateOf(Prefs.mp3Bitrate(context)) }
    var theme by remember { mutableStateOf(Prefs.theme(context)) }
    var floating by remember { mutableStateOf(Prefs.floatingPlayer(context)) }
    val hasOverlay = Settings.canDrawOverlays(context)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("設定", style = MaterialTheme.typography.headlineSmall)
        }

        SectionTitle("下載格式")
        SettingCard {
            OptionRow(
                icon = Icons.Filled.AudioFile,
                title = "MP3",
                desc = "最通用，車機／舊播放器都播到（下載後自動轉檔）",
                selected = format == "mp3"
            ) {
                format = "mp3"
                Prefs.setFormat(context, "mp3")
            }
            if (format == "mp3") {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().padding(start = 33.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(128, 192, 320).forEach { kb ->
                        ChoicePill(
                            label = kb.toString() + "k",
                            selected = bitrate == kb,
                            modifier = Modifier.weight(1f)
                        ) {
                            bitrate = kb
                            Prefs.setMp3Bitrate(context, kb)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "位元率越高越好聽、檔案越大（192k 是平衡點）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = 33.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            OptionRow(
                icon = Icons.Filled.AudioFile,
                title = "M4A（原始 AAC）",
                desc = "不轉檔，速度最快、檔案較小；部分舊播放器不支援",
                selected = format == "m4a"
            ) {
                format = "m4a"
                Prefs.setFormat(context, "m4a")
            }
        }

        SectionTitle("外觀")
        SettingCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.DarkMode, null, Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Text("主題", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "跟隨系統", "light" to "淺色", "dark" to "深色").forEach { (v, label) ->
                    ChoicePill(label, theme == v, Modifier.weight(1f)) {
                        theme = v
                        Prefs.setTheme(context, v)
                        onThemeChange(v)
                    }
                }
            }
        }

        SectionTitle("懸浮播放視窗")
        SettingCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.PictureInPicture, null, Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("迷你播放器", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "在任何 App 上方顯示可拖動的播放視窗",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = floating,
                    onCheckedChange = { v ->
                        floating = v
                        Prefs.setFloatingPlayer(context, v)
                        try {
                            context.startService(Intent(context, PlaybackService::class.java))
                        } catch (t: Throwable) {
                        }
                        PlaybackService.serviceInstance?.refreshOverlayVisibility()
                    }
                )
            }
            if (!hasOverlay) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "需要「顯示在其他應用程式上層」權限",
                    style = MaterialTheme.typography.labelSmall,
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
                }) { Text("前往授權") }
            }
        }

        SectionTitle("使用說明")
        SettingCard {
            GuideLine("1", "在「搜尋」頁輸入歌名或歌手")
            GuideLine("2", "按 ＋ 把歌曲加入資料夾（⚠ 長按可多選）")
            GuideLine("3", "到「音樂庫」按資料夾的「下載全部」，一次下載整個資料夾")
            GuideLine("4", "檔案存到手機 Music/TubeTune/<資料夾>")
        }

        SectionTitle("關於")
        SettingCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.TipsAndUpdates, null, Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("TubeTune 1.2", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "作者：Keith Yu",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "原始碼（GitHub）",
                style = MaterialTheme.typography.labelLarge,
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
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Filled.Info, null, Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "僅供個人備份與學習使用，請尊重創作者版權。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = cardBorder()
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun OptionRow(
    icon: ImageVector,
    title: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (selected) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
            null,
            Modifier.size(21.dp),
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                desc,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChoicePill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(14.dp)).clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        border = if (selected) null else cardBorder()
    ) {
        Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun GuideLine(no: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                no,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
