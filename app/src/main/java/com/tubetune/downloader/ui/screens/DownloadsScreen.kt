package com.tubetune.downloader.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tubetune.downloader.AppViewModel
import com.tubetune.downloader.data.DownloadItem
import com.tubetune.downloader.data.DownloadStatus
import com.tubetune.downloader.ui.Cover
import com.tubetune.downloader.ui.EmptyState
import com.tubetune.downloader.ui.StatusPill
import com.tubetune.downloader.ui.cardBorder

@Composable
fun DownloadsScreen(vm: AppViewModel) {
    val items by vm.downloads.items.collectAsState()
    val active by vm.downloads.activeCount.collectAsState()
    val running = items.filter { it.status in ACTIVE_STATES }
    val done = items.count { it.status == DownloadStatus.DONE }
    val failed = items.count { it.status == DownloadStatus.FAILED }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("下載", style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (items.isEmpty()) "還沒有任務"
                    else "進行中 " + running.size + " · 完成 " + done + (if (failed > 0) " · 失敗 " + failed else ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (items.isNotEmpty()) {
                TextButton(onClick = { vm.downloads.clearFinished() }) { Text("清除完成") }
            }
        }

        if (active > 0) {
            val cur = running.firstOrNull()
            Surface(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        (if (cur?.status == DownloadStatus.CONVERTING) "轉檔 MP3…" else "下載中…") +
                            " " + (cur?.progress ?: 0) + "%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { ((cur?.progress ?: 0) / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)),
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                }
            }
        }

        if (items.isEmpty()) {
            EmptyState(
                Icons.Filled.CloudDownload,
                "還沒有下載任務",
                "到「音樂庫」按資料夾的「下載全部」，或長按歌曲多選下載"
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    DownloadRow(
                        item = item,
                        onRemove = { vm.downloads.removeItem(item.id) },
                        onCancel = { vm.downloads.cancel(item.id) }
                    )
                }
            }
        }
    }
}

private val ACTIVE_STATES = setOf(
    DownloadStatus.QUEUED, DownloadStatus.RESOLVING, DownloadStatus.DOWNLOADING,
    DownloadStatus.CONVERTING, DownloadStatus.SAVING
)

@Composable
private fun DownloadRow(item: DownloadItem, onRemove: () -> Unit, onCancel: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = cardBorder()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Cover(item.thumbnailUrl, Modifier.size(46.dp), corner = 13)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusPill(statusText(item), statusColor(item))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            item.folder,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (item.status in ACTIVE_STATES) {
                    IconButton(onClick = onCancel) {
                        Icon(
                            Icons.Filled.Cancel, contentDescription = "取消",
                            Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    IconButton(onClick = onRemove) {
                        Icon(
                            if (item.status == DownloadStatus.DONE) Icons.Filled.Check else Icons.Filled.Close,
                            contentDescription = "移除",
                            Modifier.size(19.dp),
                            tint = if (item.status == DownloadStatus.DONE)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.CONVERTING) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { (item.progress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)),
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                )
            }
            if (item.status == DownloadStatus.SAVING || item.status == DownloadStatus.RESOLVING) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)),
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                )
            }
            item.error?.let { err ->
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.status == DownloadStatus.FAILED) {
                        Icon(
                            Icons.Filled.ErrorOutline, null, Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(
                        err,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.status == DownloadStatus.FAILED)
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun statusText(item: DownloadItem): String = when (item.status) {
    DownloadStatus.QUEUED -> "排隊中"
    DownloadStatus.RESOLVING -> "解析中"
    DownloadStatus.DOWNLOADING -> "下載 " + item.progress + "%"
    DownloadStatus.CONVERTING -> "轉檔 MP3 " + item.progress + "%"
    DownloadStatus.SAVING -> "儲存中"
    DownloadStatus.DONE -> "已完成"
    DownloadStatus.FAILED -> "失敗"
    DownloadStatus.CANCELLED -> "已取消"
}

@Composable
private fun statusColor(item: DownloadItem): Color = when (item.status) {
    DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
    DownloadStatus.DONE -> MaterialTheme.colorScheme.primary
    DownloadStatus.CANCELLED -> MaterialTheme.colorScheme.outline
    DownloadStatus.CONVERTING -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
