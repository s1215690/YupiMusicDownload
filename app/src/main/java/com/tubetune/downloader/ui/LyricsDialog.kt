package com.tubetune.downloader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tubetune.downloader.AppViewModel

/**
 * 歌詞對話框：先由真歌詞庫（LRCLIB／酷狗）搵；搵唔到才可選擇 AI 推測，並明確標示可能不準。
 */
@Composable
fun LyricsDialog(vm: AppViewModel) {
    val ui by vm.lyricsUi.collectAsState()
    val state = ui ?: return
    val isAi = state.source?.startsWith("AI") == true

    Dialog(
        onDismissRequest = { vm.closeLyrics() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            Modifier.fillMaxWidth(0.94f),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = cardBorder(),
            shadowElevation = 18.dp
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                RoundedCornerShape(13.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Lyrics, null, Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("歌詞", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.width(8.dp))
                            if (state.saved) StatusPill("已儲存", MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            state.track.title,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            state.track.artist,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { vm.closeLyrics() }, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Filled.Close, contentDescription = "關閉",
                            Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                state.source?.let { src ->
                    Spacer(Modifier.height(8.dp))
                    StatusPill(
                        "來源：" + src,
                        if (src.startsWith("AI")) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.height(12.dp))

                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    when {
                        state.loading -> Column(
                            Modifier.fillMaxWidth().padding(vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.5.dp)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "正在歌詞庫搵歌詞…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        !state.text.isNullOrBlank() -> Column(
                            Modifier.verticalScroll(rememberScrollState())
                        ) {
                            SelectionContainer {
                                Text(
                                    state.text.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 25.sp
                                )
                            }
                        }

                        else -> Column(
                            Modifier.fillMaxWidth().padding(vertical = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("還沒有歌詞", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "按下面「找歌詞」由 LRCLIB／酷狗歌詞庫搜尋",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                state.error?.let { err ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        err,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    if (isAi) "以上由 AI 推測，同原曲可能有出入，唔建議當準。"
                    else "歌詞來自公開歌詞庫，只供個人參考。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { vm.findLyricsOnline() },
                        enabled = !state.loading,
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Filled.Search, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(if (state.text.isNullOrBlank()) "找歌詞" else "重新找")
                    }
                    if (!state.text.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = { vm.saveLyrics() },
                            enabled = !state.saved,
                            modifier = Modifier.height(46.dp),
                            shape = RoundedCornerShape(15.dp)
                        ) {
                            Icon(Icons.Filled.Bookmark, null, Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (state.saved) "已儲存" else "儲存")
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (vm.hasDeepSeekKey()) {
                        TextButton(onClick = { vm.generateLyricsAi() }, enabled = !state.loading) {
                            Icon(
                                Icons.Filled.AutoAwesome, null, Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "AI 推測歌詞（可能不準）",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }
                    if (state.saved) {
                        TextButton(onClick = { vm.deleteLyrics() }) {
                            Icon(
                                Icons.Filled.Delete, null, Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "刪除歌詞",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}
