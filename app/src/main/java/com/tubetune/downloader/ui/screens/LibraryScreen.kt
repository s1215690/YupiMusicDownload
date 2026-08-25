package com.tubetune.downloader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tubetune.downloader.AppViewModel
import com.tubetune.downloader.data.LibraryRepository
import com.tubetune.downloader.data.Track
import com.tubetune.downloader.ui.FolderManageDialog
import com.tubetune.downloader.ui.FolderPickerDialog
import com.tubetune.downloader.ui.formatBytes
import com.tubetune.downloader.ui.formatDuration

@Composable
fun LibraryScreen(vm: AppViewModel) {
    val libData by vm.library.data.collectAsState()
    val playerState by vm.player.state.collectAsState()

    var filter by remember { mutableStateOf("全部") }
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    var moveTrack by remember { mutableStateOf<Track?>(null) }
    var folderDialog by remember { mutableStateOf(false) }
    var manageFolders by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf<Track?>(null) }

    val filtered = if (filter == "全部") libData.tracks else libData.tracks.filter { it.folder == filter }
    val grouped: List<Pair<String, List<Track>>> = if (filter == "全部") {
        val order = listOf(LibraryRepository.DEFAULT_FOLDER) + libData.folders.map { it.name }
        order.mapNotNull { f ->
            val ts = libData.tracks.filter { it.folder == f }
            if (ts.isEmpty()) null else f to ts
        }
    } else emptyList()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("音樂庫", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    libData.tracks.size.toString() + " 首 · " + formatBytes(libData.tracks.sumOf { it.sizeBytes }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { folderDialog = true }) {
                Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("新資料夾")
            }
            TextButton(onClick = { manageFolders = true }) { Text("管理") }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = filter == "全部", onClick = { filter = "全部" }, label = { Text("全部") })
            libData.folders.forEach { f ->
                FilterChip(selected = filter == f.name, onClick = { filter = f.name }, label = { Text(f.name) })
            }
        }

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.LibraryMusic,
                        null,
                        Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("音樂庫是空的", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "下載的音樂會出現在這裡",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                if (filter == "全部") {
                    grouped.forEach { (folderName, tracks) ->
                        item(key = "header_" + folderName) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    folderName,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    tracks.size.toString() + " 首",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        items(tracks, key = { "t_" + it.videoId }) { t ->
                            LibraryRow(
                                track = t,
                                isCurrent = playerState?.track?.videoId == t.videoId,
                                isPlaying = playerState?.isPlaying == true,
                                onPlay = { vm.playTrack(t, filtered) },
                                onMenu = { menuTrack = t }
                            )
                        }
                    }
                } else {
                    items(filtered, key = { "t_" + it.videoId }) { t ->
                        LibraryRow(
                            track = t,
                            isCurrent = playerState?.track?.videoId == t.videoId,
                            isPlaying = playerState?.isPlaying == true,
                            onPlay = { vm.playTrack(t, filtered) },
                            onMenu = { menuTrack = t }
                        )
                    }
                }
            }
        }
    }

    menuTrack?.let { t ->
        Box {
            DropdownMenu(expanded = true, onDismissRequest = { menuTrack = null }) {
                DropdownMenuItem(
                    text = { Text("播放") },
                    onClick = { menuTrack = null; vm.playTrack(t, filtered) }
                )
                DropdownMenuItem(
                    text = { Text("移動到資料夾") },
                    onClick = { menuTrack = null; moveTrack = t }
                )
                DropdownMenuItem(
                    text = { Text("刪除") },
                    onClick = { menuTrack = null; deleteConfirm = t }
                )
            }
        }
    }

    moveTrack?.let { t ->
        FolderPickerDialog(
            folders = libData.folders,
            onCreate = { vm.library.addFolder(it) },
            onPick = { folder ->
                vm.library.moveTrack(t.videoId, folder)
                moveTrack = null
            },
            onDismiss = { moveTrack = null }
        )
    }

    if (folderDialog) {
        CreateFolderDialog(
            onConfirm = {
                vm.library.addFolder(it)
                folderDialog = false
            },
            onDismiss = { folderDialog = false }
        )
    }

    if (manageFolders) {
        FolderManageDialog(
            folders = libData.folders,
            onRename = { old, new -> if (new.isNotBlank()) vm.library.renameFolder(old, new) },
            onDelete = { vm.library.deleteFolder(it) },
            onDismiss = { manageFolders = false }
        )
    }

    deleteConfirm?.let { t ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteConfirm = null },
            title = { Text("刪除音樂？") },
            text = { Text(t.title + "\n將從音樂庫移除，手機上的檔案也會一併刪除。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteTrack(t)
                    deleteConfirm = null
                }) { Text("刪除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirm = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun CreateFolderDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增資料夾") },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("資料夾名稱，例如：華語流行") }
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) onConfirm(name.trim())
            }) { Text("建立") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun LibraryRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onMenu: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable(onClick = onPlay)
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    track.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isCurrent && isPlaying) {
                Icon(Icons.Filled.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(4.dp))
            }
            Text(
                formatDuration(track.durationSeconds),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = onMenu) { Icon(Icons.Filled.MoreVert, contentDescription = "更多") }
        }
    }
}
