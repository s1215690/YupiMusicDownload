package com.tubetune.downloader.ui.screens

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import coil.compose.AsyncImage
import com.tubetune.downloader.AppViewModel
import com.tubetune.downloader.data.DownloadItem
import com.tubetune.downloader.data.DownloadStatus

@Composable
fun DownloadsScreen(vm: AppViewModel) {
    val items by vm.downloads.items.collectAsState()
    val active by vm.downloads.activeCount.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "下載任務",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (items.isNotEmpty()) {
                TextButton(onClick = { vm.downloads.clearFinished() }) { Text("清除已完成") }
            }
        }
        if (active > 0) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            Text(
                "正在下載…",
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.CloudDownload,
                        null,
                        Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("還沒有下載任務", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "到「搜尋」頁長按多首歌曲即可批量下載",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
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

@Composable
private fun DownloadRow(item: DownloadItem, onRemove: () -> Unit, onCancel: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = item.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        statusText(item),
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor(item)
                    )
                }
                if (item.status == DownloadStatus.FAILED ||
                    item.status == DownloadStatus.DONE ||
                    item.status == DownloadStatus.CANCELLED
                ) {
                    IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = "移除") }
                } else {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Cancel, contentDescription = "取消下載", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            when (item.status) {
                DownloadStatus.DOWNLOADING -> LinearProgressIndicator(
                    progress = { item.progress / 100f },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                DownloadStatus.SAVING -> LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                else -> {}
            }
            if (item.error != null) {
                Text(
                    item.error ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun statusText(item: DownloadItem): String = when (item.status) {
    DownloadStatus.QUEUED -> "排隊中 · " + item.folder
    DownloadStatus.RESOLVING -> "解析影片中…"
    DownloadStatus.DOWNLOADING -> "下載中 " + item.progress + "%"
    DownloadStatus.SAVING -> "儲存到音樂庫…"
    DownloadStatus.DONE -> "已完成 · " + item.folder
    DownloadStatus.FAILED -> "失敗"
    DownloadStatus.CANCELLED -> "已取消"
}

@Composable
private fun statusColor(item: DownloadItem): Color = when (item.status) {
    DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
    DownloadStatus.DONE -> MaterialTheme.colorScheme.primary
    DownloadStatus.CANCELLED -> MaterialTheme.colorScheme.outline
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
