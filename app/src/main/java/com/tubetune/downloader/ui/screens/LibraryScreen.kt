package com.tubetune.downloader.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tubetune.downloader.AppViewModel
import com.tubetune.downloader.data.LibraryRepository
import com.tubetune.downloader.data.PlaySource
import com.tubetune.downloader.data.Prefs
import com.tubetune.downloader.data.Track
import com.tubetune.downloader.ui.FolderManageDialog
import com.tubetune.downloader.ui.FolderPickerDialog
import com.tubetune.downloader.ui.formatBytes
import com.tubetune.downloader.ui.formatDuration

@Composable
fun LibraryScreen(vm: AppViewModel) {
    val libData by vm.library.data.collectAsState()
    val playerState by vm.player.state.collectAsState()
    val streamBusy by vm.streamBusy.collectAsState()
    val context = LocalContext.current

    var filter by remember { mutableStateOf("全部") }
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    var moveTrack by remember { mutableStateOf<Track?>(null) }
    var folderDialog by remember { mutableStateOf(false) }
    var manageFolders by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf<Track?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }

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
            if (selectionMode) {
                IconButton(onClick = { selectionMode = false; selected.clear() }) {
                    Icon(Icons.Filled.Close, contentDescription = "取消多選")
                }
            }
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

        if (selectionMode) {
            Surface(tonalElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "已選 " + selected.size + " 首",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall
                    )
                    TextButton(onClick = { selected.clear() }) { Text("全不選") }
                    Button(
                        onClick = { vm.downloadTracks(filtered.filter { selected.contains(it.videoId) }) },
                        enabled = selected.isNotEmpty()
                    ) { Text("下載到本地") }
                }
            }
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
                        "在「搜尋」頁按 ⬇ 把歌曲加入資料夾（串流），再決定要不要下載",
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
                            FolderHeader(
                                folderName = folderName,
                                tracks = tracks,
                                context = context,
                                onToggleSource = { vm.togglePlaySource(folderName) },
                                onDownloadAll = { vm.downloadFolder(folderName) }
                            )
                        }
                        items(tracks, key = { "t_" + it.videoId }) { t ->
                            LibraryRow(
                                track = t,
                                isCurrent = playerState?.track?.videoId == t.videoId,
                                isPlaying = playerState?.isPlaying == true,
                                selectionMode = selectionMode,
                                selected = selected.contains(t.videoId),
                                onClick = {
                                    if (selectionMode) {
                                        if (selected.contains(t.videoId)) selected.remove(t.videoId)
                                        else selected.add(t.videoId)
                                    } else {
                                        vm.playTrack(t, filtered)
                                    }
                                },
                                onLongClick = {
                                    if (!selectionMode) {
                                        selectionMode = true
                                        selected.clear()
                                        selected.add(t.videoId)
                                    }
                                },
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
                            selectionMode = selectionMode,
                            selected = selected.contains(t.videoId),
                            onClick = {
                                if (selectionMode) {
                                    if (selected.contains(t.videoId)) selected.remove(t.videoId)
                                    else selected.add(t.videoId)
                                } else {
                                    vm.playTrack(t, filtered)
                                }
                            },
                            onLongClick = {
                                if (!selectionMode) {
                                    selectionMode = true
                                    selected.clear()
                                    selected.add(t.videoId)
                                }
                            },
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
                if (!t.downloaded) {
                    DropdownMenuItem(
                        text = { Text("下載到本地", color = MaterialTheme.colorScheme.primary) },
                        onClick = { menuTrack = null; vm.downloadTracks(listOf(t)) }
                    )
                }
                DropdownMenuItem(
                    text = { Text("下載播放") },
                    onClick = { menuTrack = null; vm.playTrack(t, filtered, PlaySource.DOWNLOAD) }
                )
                DropdownMenuItem(
                    text = { Text("串流播放", color = MaterialTheme.colorScheme.primary) },
                    onClick = { menuTrack = null; vm.playTrack(t, filtered, PlaySource.STREAM) }
                )
                DropdownMenuItem(
                    text = { Text("移動到資料夾") },
                    onClick = { menuTrack = null; moveTrack = t }
                )
                DropdownMenuItem(
                    text = { Text(if (t.downloaded) "刪除" else "從資料夾移除") },
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
            title = { Text(if (t.downloaded) "刪除音樂？" else "從資料夾移除？") },
            text = {
                Text(
                    if (t.downloaded)
                        t.title + "\n將從音樂庫移除，手機上的檔案也會一併刪除。"
                    else
                        t.title + "\n將從資料夾移除（不會刪除任何檔案）。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteTrack(t)
                    deleteConfirm = null
                }) { Text(if (t.downloaded) "刪除" else "移除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirm = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun FolderHeader(
    folderName: String,
    tracks: List<Track>,
    context: android.content.Context,
    onToggleSource: () -> Unit,
    onDownloadAll: () -> Unit
) {
    val notDownloaded = tracks.count { it.uri.isBlank() }
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 2.dp),
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
            tracks.size.toString() + " 首" + if (notDownloaded > 0) "（" + notDownloaded + " 首串流）" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        if (notDownloaded > 0) {
            TextButton(onClick = onDownloadAll) { Text("全部下載", style = MaterialTheme.typography.labelSmall) }
        }
        FilterChip(
            selected = Prefs.playSourceForFolder(context, folderName) == PlaySource.STREAM,
            onClick = onToggleSource,
            label = {
                Text(
                    if (Prefs.playSourceForFolder(context, folderName) == PlaySource.STREAM) "串流播放" else "下載播放",
                    style = MaterialTheme.typography.labelSmall
                )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenu: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onClick() })
            }
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
            Spacer(Modifier.width(6.dp))
            Icon(
                if (track.uri.isNotBlank()) Icons.Filled.CheckCircle
                else Icons.Filled.Cloud,
                contentDescription = if (track.uri.isNotBlank()) "已下載" else "串流",
                modifier = Modifier.size(16.dp),
                tint = if (track.uri.isNotBlank()) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isCurrent && isPlaying) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(6.dp))
            Text(
                formatDuration(track.durationSeconds),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!selectionMode) {
                IconButton(onClick = onMenu) { Icon(Icons.Filled.MoreVert, contentDescription = "更多") }
            }
        }
    }
}
