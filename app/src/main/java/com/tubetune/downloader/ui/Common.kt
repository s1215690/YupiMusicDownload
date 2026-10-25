package com.tubetune.downloader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tubetune.downloader.AppViewModel
import com.tubetune.downloader.data.Folder
import com.tubetune.downloader.data.LibraryRepository
import com.tubetune.downloader.ui.theme.Gradients

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

/** 圓角封面：沒有縮圖時用品牌漸層 + 音符佔位 */
@Composable
fun Cover(
    url: String,
    modifier: Modifier = Modifier,
    corner: Int = 14
) {
    val shape = RoundedCornerShape(corner.dp)
    Box(modifier.clip(shape)) {
        Box(Modifier.matchParentSize().background(Gradients.soft(0.35f)))
        if (url.isNotBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

/** 小節標題 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        action?.invoke()
    }
}

/** 圓形主按鈕（漸層底） */
@Composable
fun GradientCircleButton(
    size: Int,
    iconSize: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Gradients.accent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize.dp),
            tint = Color.White
        )
    }
}

/** 底部迷你播放器：浮動卡片 */
@Composable
fun MiniPlayer(vm: AppViewModel) {
    val state by vm.player.state.collectAsState()
    val s = state ?: return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        ),
        shadowElevation = 10.dp
    ) {
        Column(
            Modifier
                .clip(RoundedCornerShape(22.dp))
                .clickable { vm.openPlayerScreen() }
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Cover(s.track.thumbnailUrl, Modifier.size(42.dp), corner = 12)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        s.track.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        s.track.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { vm.player.toggle() }) {
                    Icon(
                        if (s.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "播放/暫停",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { vm.player.stop() }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "停止",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            LinearProgressIndicator(
                progress = {
                    if (s.durationMs > 0) (s.positionMs.toFloat() / s.durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                trackColor = Color.Transparent
            )
        }
    }
}

@Composable
fun FolderPickerDialog(
    folders: List<Folder>,
    onCreate: (String) -> Unit,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = "選擇資料夾"
) {
    var newName by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FolderOption(LibraryRepository.DEFAULT_FOLDER, onPick)
                folders.forEach { f -> FolderOption(f.name, onPick) }
                Spacer(Modifier.height(6.dp))
                if (showCreate) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
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
                        Spacer(Modifier.width(6.dp))
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
private fun FolderOption(name: String, onPick: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onPick(name) }
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Folder,
                null,
                Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(name, style = MaterialTheme.typography.bodyLarge)
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
        shape = MaterialTheme.shapes.large,
        title = { Text("管理資料夾", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                folders.forEach { f ->
                    if (editing == f.name) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = editText,
                                onValueChange = { editText = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp)
                            )
                            TextButton(onClick = {
                                onRename(f.name, editText.trim())
                                editing = null
                            }) { Text("確定") }
                            IconButton(onClick = { editing = null }) { Icon(Icons.Filled.Close, null) }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(f.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            IconButton(onClick = { editing = f.name; editText = f.name }) {
                                Icon(
                                    Icons.Filled.Edit, "重新命名",
                                    Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onDelete(f.name) }) {
                                Icon(
                                    Icons.Filled.Delete, "刪除",
                                    Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
                if (folders.isEmpty()) {
                    Text(
                        "還沒有自訂資料夾",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("關閉") } }
    )
}

/**
 * 三指點選手勢：三指在 250ms 內同時落下、未大幅移動即全部抬起 → 觸發 [onTap]。
 * 不消費事件，不影響其他按鈕/捲動操作。
 */
fun Modifier.threeFingerTap(onTap: () -> Unit): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        val downs = HashMap<PointerId, Long>()
        val startPos = HashMap<PointerId, Offset>()
        var triple = false
        while (true) {
            val event = awaitPointerEvent()
            for (c in event.changes) {
                if (c.pressed && !c.previousPressed) {
                    downs[c.id] = c.uptimeMillis
                    startPos[c.id] = c.position
                    downs.entries.removeAll { it.value < c.uptimeMillis - 200L }
                    if (downs.size >= 3) {
                        val t = downs.values
                        if (t.max() - t.min() < 250L) triple = true
                    }
                } else if (!c.pressed) {
                    downs.remove(c.id)
                    startPos.remove(c.id)
                }
            }
            if (triple) {
                if (event.changes.any { c ->
                        startPos[c.id]?.let { (c.position - it).getDistance() > 40f } == true
                    }) {
                    triple = false // 移太多，不算點選
                } else if (downs.isEmpty()) {
                    triple = false
                    onTap()
                }
            }
        }
    }
}

/** 卡片底用的細邊框 */
@Composable
fun cardBorder() = androidx.compose.foundation.BorderStroke(
    1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
)

/** 狀態膠囊 */
@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** 空狀態 */
@Composable
fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    hint: String
) {
    Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(78.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 40.dp)
            )
        }
    }
}

/** 細分隔線 */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    )
}
