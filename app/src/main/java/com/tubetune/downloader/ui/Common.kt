package com.tubetune.downloader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tubetune.downloader.AppViewModel
import com.tubetune.downloader.data.Folder
import com.tubetune.downloader.data.LibraryRepository

fun formatDuration(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, sec) else String.format("%d:%02d", m, sec)
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes >= gb -> String.format("%.1f GB", bytes / gb)
        bytes >= mb -> String.format("%.1f MB", bytes / mb)
        else -> String.format("%.0f KB", bytes / kb)
    }
}

@Composable
fun MiniPlayer(vm: AppViewModel) {
    val state by vm.player.state.collectAsState()
    val s = state ?: return
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 3.dp) {
        Column {
            LinearProgressIndicator(
                progress = { if (s.durationMs > 0) (s.positionMs.toFloat() / s.durationMs.toFloat()).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                trackColor = Color.Transparent
            )
            Row(
                Modifier.fillMaxWidth().clickable { vm.openPlayerScreen() }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(s.track.thumbnailUrl, null, Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    Text(s.track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { vm.player.toggle() }) {
                    Icon(if (s.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "播放/暫停")
                }
                IconButton(onClick = { vm.player.stop() }) { Icon(Icons.Filled.Close, contentDescription = "停止") }
            }
        }
    }
}

@Composable
fun FolderPickerDialog(
    folders: List<Folder>,
    onCreate: (String) -> Unit,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("選擇要存入的資料夾") },
        text = {
            Column {
                FolderRow(LibraryRepository.DEFAULT_FOLDER, onPick)
                folders.forEach { f -> FolderRow(f.name, onPick) }
                if (showCreate) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("新資料夾名稱") }
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            val n = newName.trim()
                            if (n.isNotBlank()) {
                                onCreate(n)
                                onPick(n)
                            }
                        }) { Text("建立") }
                    }
                } else {
                    TextButton(onClick = { showCreate = true }) {
                        Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("新增資料夾")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun FolderRow(name: String, onPick: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onPick(name) }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(name)
    }
}

@Composable
fun FolderManageDialog(
    folders: List<Folder>,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var editing by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理資料夾") },
        text = {
            Column {
                folders.forEach { f ->
                    if (editing == f.name) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = editText,
                                onValueChange = { editText = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            TextButton(onClick = {
                                onRename(f.name, editText.trim())
                                editing = null
                            }) { Text("確定") }
                            IconButton(onClick = { editing = null }) { Icon(Icons.Filled.Close, null) }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(f.name, Modifier.weight(1f))
                            IconButton(onClick = { editing = f.name; editText = f.name }) { Icon(Icons.Filled.Edit, "重新命名") }
                            IconButton(onClick = { onDelete(f.name) }) {
                                Icon(Icons.Filled.Delete, "刪除", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                if (folders.isEmpty()) {
                    Text("還沒有自訂資料夾", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("關閉") } }
    )
}
