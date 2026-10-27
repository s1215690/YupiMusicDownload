package com.tubetune.downloader.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tubetune.downloader.AppViewModel
import com.tubetune.downloader.data.DownloadStatus
import com.tubetune.downloader.data.LibraryRepository
import com.tubetune.downloader.data.PlaySource
import com.tubetune.downloader.data.Prefs
import com.tubetune.downloader.data.Track
import com.tubetune.downloader.ui.Cover
import com.tubetune.downloader.ui.EmptyState
import com.tubetune.downloader.ui.FolderManageDialog
import com.tubetune.downloader.ui.FolderPickerDialog
import com.tubetune.downloader.ui.GradientCircleButton
import com.tubetune.downloader.ui.cardBorder
import com.tubetune.downloader.ui.formatBytes
import com.tubetune.downloader.ui.formatDuration
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ACTIVE = setOf(
    DownloadStatus.QUEUED, DownloadStatus.RESOLVING, DownloadStatus.DOWNLOADING,
    DownloadStatus.CONVERTING, DownloadStatus.SAVING
)

@Composable
fun LibraryScreen(vm: AppViewModel) {
    val libData by vm.library.data.collectAsState()
    val playerState by vm.player.state.collectAsState()
    val downloads by vm.downloads.items.collectAsState()
    val startupMsg by vm.startupMsg.collectAsState()
    val context = LocalContext.current

    var folder by remember { mutableStateOf<String?>(null) }
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    var moveTrack by remember { mutableStateOf<Track?>(null) }
    var folderDialog by remember { mutableStateOf(false) }
    var manageFolders by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf<Track?>(null) }
    var bulkDelete by remember { mutableStateOf<List<Track>>(emptyList()) }
    var selectionMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    var scanMsg by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val audioPermission = if (Build.VERSION.SDK_INT >= 33)
        Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scanning = true
            scope.launch {
                val n = withContext(Dispatchers.IO) { vm.library.importLocalFiles() }
                scanning = false
                scanMsg = if (n > 0) "已加入 " + n + " 首歌曲" else "沒有找到新歌曲"
            }
        } else {
            scanMsg = "需要「音樂和音訊」權限才能掃描本機歌曲"
        }
    }
    // 掃描手機上 Music/TubeTune 既有檔案，重建索引（重裝 App 後把歌救回來）
    val requestScan: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(context, audioPermission) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            scanning = true
            scope.launch {
                val n = withContext(Dispatchers.IO) { vm.library.importLocalFiles() }
                scanning = false
                scanMsg = if (n > 0) "已加入 " + n + " 首歌曲" else "沒有找到新歌曲"
            }
        } else {
            permissionLauncher.launch(audioPermission)
        }
    }

    val defaultHasTracks = libData.tracks.any { it.folder == LibraryRepository.DEFAULT_FOLDER }
    val folderNames = buildList {
        if (defaultHasTracks) add(LibraryRepository.DEFAULT_FOLDER)
        libData.folders.forEach { if (it.name != LibraryRepository.DEFAULT_FOLDER) add(it.name) }
    }
    // 目前選的資料夾被刪除／改名 → 回到總覽
    LaunchedEffect(folder, folderNames) {
        if (folder != null && folder != LibraryRepository.DEFAULT_FOLDER &&
            libData.folders.none { it.name == folder }
        ) folder = null
    }

    val tracksOf = { name: String -> libData.tracks.filter { it.folder == name } }
    // 多選動作的對象：全部時＝所有資料夾的曲目；否則＝當前資料夾
    val visibleTracks = if (folder == null) folderNames.flatMap { tracksOf(it) } else tracksOf(folder!!)
    val selectedTracks = visibleTracks.filter { selected.contains(it.videoId) }
    val activeIds = downloads.filter { it.status in ACTIVE }.map { it.videoId }.toSet()

    Column(Modifier.fillMaxSize()) {
        // ---- 頂部標題 ----
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (folder != null) {
                IconButton(onClick = { folder = null }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "返回", Modifier.size(26.dp))
                }
                Spacer(Modifier.width(2.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    folder ?: "音樂庫",
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val statsTracks = if (folder != null) tracksOf(folder!!) else libData.tracks
                Text(
                    statsTracks.size.toString() + " 首 · " +
                        formatBytes(statsTracks.sumOf { it.sizeBytes }) + " · " +
                        statsTracks.count { it.uri.isNotBlank() } + " 首已下載",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { requestScan() }, enabled = !scanning) {
                if (scanning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.Refresh, contentDescription = "掃描本機歌曲", Modifier.size(21.dp))
            }
            IconButton(onClick = { folderDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "新資料夾", Modifier.size(22.dp))
            }
            IconButton(onClick = { manageFolders = true }) {
                Icon(Icons.Filled.Tune, contentDescription = "管理資料夾", Modifier.size(21.dp))
            }
        }

        // ---- 資料夾分頁膠囊 ----
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FolderPill("全部", folder == null) {
                folder = null
                selectionMode = false
                selected.clear()
            }
            folderNames.forEach { name ->
                FolderPill(name, folder == name) {
                    folder = name
                    selectionMode = false
                    selected.clear()
                }
            }
        }

        (scanMsg ?: startupMsg)?.let { msg ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .clickable { scanMsg = null; vm.clearStartupMsg() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    msg,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    Icons.Filled.Close, contentDescription = "關閉", Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (selectionMode) {
            Surface(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = cardBorder()
            ) {
                // 一定要包 Column：Surface 嘅 content 係 Box，兩個 Row 直接放會疊埋一齊
                Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "已選 " + selected.size + " 首",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall
                    )
                    TextButton(onClick = {
                        if (visibleTracks.isNotEmpty() && selected.size >= visibleTracks.size) {
                            selected.clear()
                        } else {
                            selected.clear()
                            selected.addAll(visibleTracks.map { it.videoId })
                        }
                    }) {
                        Text(if (visibleTracks.isNotEmpty() && selected.size >= visibleTracks.size) "全不選" else "全選")
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            vm.downloadTracks(selectedTracks)
                            selectionMode = false
                            selected.clear()
                        },
                        enabled = selected.isNotEmpty(),
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Icon(Icons.Filled.Download, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("下載")
                    }
                    OutlinedButton(
                        onClick = { shareTracks(context, selectedTracks) },
                        enabled = selectedTracks.any { it.uri.startsWith("content://") },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Icon(Icons.Filled.Share, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("分享")
                    }
                    OutlinedButton(
                        onClick = { bulkDelete = selectedTracks },
                        enabled = selected.isNotEmpty(),
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Filled.Delete, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("刪除")
                    }
                }
                }
            }
        }

        Box(Modifier.weight(1f)) {
            when {
                libData.tracks.isEmpty() && folderNames.isEmpty() -> EmptyState(
                    Icons.Filled.LibraryMusic,
                    "音樂庫是空的",
                    "如果手機 Music/TubeTune 內已有歌曲（例如重裝過 App），按下面掃描即可找回"
                ) {
                    Button(onClick = { requestScan() }, shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("掃描手機上已下載的歌曲")
                    }
                }

                folder == null -> FolderOverview(
                    folderNames = folderNames,
                    tracksOf = tracksOf,
                    activeIds = activeIds,
                    onOpen = { folder = it; selectionMode = false; selected.clear() },
                    onDownloadAll = { name ->
                        vm.downloadFolder(name)
                    }
                )

                else -> FolderTracks(
                    tracks = tracksOf(folder!!),
                    currentId = playerState?.track?.videoId,
                    isPlaying = playerState?.isPlaying == true,
                    selectionMode = selectionMode,
                    selected = selected,
                    activeIds = activeIds,
                    onToggleSelect = { id ->
                        if (selected.contains(id)) selected.remove(id) else selected.add(id)
                    },
                    onClickTrack = { t ->
                        if (selectionMode) {
                            if (selected.contains(t.videoId)) selected.remove(t.videoId) else selected.add(t.videoId)
                        } else vm.playTrack(t, tracksOf(folder!!))
                    },
                    onLongClickTrack = { t ->
                        if (!selectionMode) {
                            selectionMode = true
                            selected.clear()
                            selected.add(t.videoId)
                        }
                    },
                    onMenu = { menuTrack = it },
                    onDownloadAll = { vm.downloadFolder(folder!!) },
                    onDownloadOne = { vm.downloadTracks(listOf(it)) },
                    onToggleSource = { vm.togglePlaySource(folder!!) },
                    onSelectAll = {
                        selectionMode = true
                        selected.clear()
                        selected.addAll(tracksOf(folder!!).map { it.videoId })
                    },
                    sourceIsStream = Prefs.playSourceForFolder(context, folder!!) == PlaySource.STREAM
                )
            }
        }
    }

    // ---- 曲目選單 ----
    menuTrack?.let { t ->
        Box {
            DropdownMenu(expanded = true, onDismissRequest = { menuTrack = null }) {
                if (!t.downloaded) {
                    DropdownMenuItem(
                        text = { Text("下載到本地", color = MaterialTheme.colorScheme.primary) },
                        leadingIcon = { Icon(Icons.Filled.Download, null) },
                        onClick = { menuTrack = null; vm.downloadTracks(listOf(t)) }
                    )
                }
                DropdownMenuItem(
                    text = { Text("下載播放") },
                    leadingIcon = { Icon(Icons.Filled.PlayArrow, null) },
                    onClick = { menuTrack = null; vm.playTrack(t, tracksOf(t.folder), PlaySource.DOWNLOAD) }
                )
                DropdownMenuItem(
                    text = { Text("串流播放") },
                    leadingIcon = { Icon(Icons.Filled.CloudDownload, null) },
                    onClick = { menuTrack = null; vm.playTrack(t, tracksOf(t.folder), PlaySource.STREAM) }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            if (t.lyrics.isNullOrBlank()) "歌詞" else "歌詞（已儲存）",
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.Lyrics, null) },
                    onClick = { menuTrack = null; vm.openLyrics(t) }
                )
                DropdownMenuItem(
                    text = { Text("移動到資料夾") },
                    leadingIcon = { Icon(Icons.Filled.Folder, null) },
                    onClick = { menuTrack = null; moveTrack = t }
                )
                DropdownMenuItem(
                    text = { Text(if (t.downloaded) "刪除" else "從資料夾移除") },
                    leadingIcon = { Icon(Icons.Filled.Delete, null) },
                    onClick = { menuTrack = null; deleteConfirm = t }
                )
            }
        }
    }

    moveTrack?.let { t ->
        FolderPickerDialog(
            folders = libData.folders,
            onCreate = { vm.library.addFolder(it) },
            onPick = { f ->
                vm.library.moveTrack(t.videoId, f)
                moveTrack = null
            },
            onDismiss = { moveTrack = null },
            title = "移動到資料夾"
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
            shape = MaterialTheme.shapes.large,
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

    // ---- 批量刪除（全選／多選後） ----
    if (bulkDelete.isNotEmpty()) {
        val withFiles = bulkDelete.count { it.uri.startsWith("content://") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { bulkDelete = emptyList() },
            title = { Text("刪除 " + bulkDelete.size + " 首？") },
            text = {
                Text(
                    if (withFiles > 0)
                        "其中 " + withFiles + " 首已下載，手機上的檔案會一併刪除；其餘只會從資料夾移除。"
                    else
                        "這些歌還未下載，只會從資料夾移除，不會刪除任何檔案。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteTracks(bulkDelete)
                    selectionMode = false
                    selected.clear()
                    bulkDelete = emptyList()
                }) { Text("刪除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { bulkDelete = emptyList() }) { Text("取消") }
            }
        )
    }
}

/** 分享已下載的歌曲（MediaStore content:// URI 授權給其他 App 讀取） */
private fun shareTracks(context: android.content.Context, tracks: List<Track>) {
    val uris = tracks
        .filter { it.uri.startsWith("content://") }
        .map { android.net.Uri.parse(it.uri) }
    if (uris.isEmpty()) {
        android.widget.Toast.makeText(context, "選中的歌未下載，沒有檔案可分享", android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
        type = "audio/*"
        putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, ArrayList(uris))
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(android.content.Intent.createChooser(intent, "分享歌曲")) }
        .onFailure {
            android.widget.Toast.makeText(context, "沒有 App 可以接收分享", android.widget.Toast.LENGTH_SHORT).show()
        }
}

/* ---------------- 資料夾總覽（卡片格） ---------------- */

@Composable
private fun FolderOverview(
    folderNames: List<String>,
    tracksOf: (String) -> List<Track>,
    activeIds: Set<String>,
    onOpen: (String) -> Unit,
    onDownloadAll: (String) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
        item {
            Text(
                "我的資料夾",
                Modifier.padding(start = 20.dp, top = 6.dp, bottom = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(folderNames.chunked(2), key = { it.joinToString("|") }) { pair ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                pair.forEach { name ->
                    val tracks = tracksOf(name)
                    FolderCard(
                        name = name,
                        tracks = tracks,
                        modifier = Modifier.weight(1f),
                        active = tracks.any { it.videoId in activeIds },
                        onOpen = { onOpen(name) },
                        onDownloadAll = { onDownloadAll(name) }
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FolderCard(
    name: String,
    tracks: List<Track>,
    modifier: Modifier = Modifier,
    active: Boolean,
    onOpen: () -> Unit,
    onDownloadAll: () -> Unit
) {
    val missing = tracks.count { it.uri.isBlank() }
    val cover = tracks.firstOrNull { it.thumbnailUrl.isNotBlank() }?.thumbnailUrl.orEmpty()
    Surface(
        modifier = modifier.clip(RoundedCornerShape(22.dp)).clickable { onOpen() },
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = cardBorder()
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(1.15f)) {
                if (cover.isNotBlank()) {
                    Cover(cover, Modifier.fillMaxSize(), corner = 0)
                } else {
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
                                )
                            )
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Folder, null, Modifier.size(34.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }
                // 底部漸黑，令白色文字清楚
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                            )
                        )
                )
                // 下載整個資料夾（未下載的）
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (missing > 0) MaterialTheme.colorScheme.primary
                            else Color.Black.copy(alpha = 0.35f)
                        )
                        .clickable(enabled = missing > 0) { onDownloadAll() },
                    contentAlignment = Alignment.Center
                ) {
                    if (active) {
                        CircularProgressIndicator(
                            Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White
                        )
                    } else {
                        Icon(
                            if (missing > 0) Icons.Filled.Download else Icons.Filled.Check,
                            contentDescription = if (missing > 0) "下載全部" else "已全部下載",
                            Modifier.size(19.dp),
                            tint = if (missing > 0) MaterialTheme.colorScheme.onPrimary else Color.White
                        )
                    }
                }
                if (missing > 0) {
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            missing.toString() + " 首待下載",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    tracks.size.toString() + " 首 · " + formatBytes(tracks.sumOf { it.sizeBytes }),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/* ---------------- 單一資料夾：動作列 + 曲目 ---------------- */

@Composable
private fun FolderTracks(
    tracks: List<Track>,
    currentId: String?,
    isPlaying: Boolean,
    selectionMode: Boolean,
    selected: List<String>,
    activeIds: Set<String>,
    onToggleSelect: (String) -> Unit,
    onClickTrack: (Track) -> Unit,
    onLongClickTrack: (Track) -> Unit,
    onMenu: (Track) -> Unit,
    onDownloadAll: () -> Unit,
    onDownloadOne: (Track) -> Unit,
    onToggleSource: () -> Unit,
    onSelectAll: () -> Unit,
    sourceIsStream: Boolean
) {
    val missing = tracks.count { it.uri.isBlank() }
    val fmt = Prefs.format(androidx.compose.ui.platform.LocalContext.current)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp)
    ) {
        item(key = "action") {
            Surface(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = cardBorder()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (missing > 0) missing.toString() + " 首尚未下載" else "已全部下載",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (missing > 0) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                tracks.size.toString() + " 首 · " + (tracks.size - missing) + " 首已下載 · " +
                                    formatBytes(tracks.sumOf { it.sizeBytes }),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        if (tracks.isNotEmpty()) {
                            // 多選入口：一次過全選再分享／刪除（唔需要逐首長按）
                            IconButton(
                                onClick = onSelectAll,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface)
                            ) {
                                Icon(
                                    Icons.Filled.Checklist, "全選／多選",
                                    Modifier.size(21.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            GradientCircleButton(46, 24, Icons.Filled.PlayArrow, "播放整個資料夾") {
                                onClickTrack(tracks.first())
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onDownloadAll,
                            enabled = missing > 0,
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Filled.Download, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (missing > 0) "下載全部（" + missing + " 首）" else "已全部下載",
                                maxLines = 1
                            )
                        }
                        OutlinedButton(
                            onClick = onToggleSource,
                            modifier = Modifier.height(46.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(
                                if (sourceIsStream) Icons.Filled.CloudDownload else Icons.Filled.Download,
                                null, Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (sourceIsStream) "串流" else "本地", maxLines = 1)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "下載格式：" + (if (fmt == "mp3") "MP3 · " + Prefs.mp3Bitrate(androidx.compose.ui.platform.LocalContext.current) + " kbps" else "M4A（原始）") + "　可在「設定」更改",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        if (tracks.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "這個資料夾還沒有歌曲",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            item { Spacer(Modifier.height(6.dp)) }
            items(tracks, key = { it.videoId }) { t ->
                TrackRow(
                    track = t,
                    isCurrent = currentId == t.videoId,
                    isPlaying = isPlaying && currentId == t.videoId,
                    selectionMode = selectionMode,
                    selected = selected.contains(t.videoId),
                    busy = t.videoId in activeIds,
                    onClick = { onClickTrack(t) },
                    onLongClick = { onLongClickTrack(t) },
                    onMenu = { onMenu(t) },
                    onDownload = { onDownloadOne(t) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenu: () -> Unit,
    onDownload: () -> Unit
) {
    val bg by animateFloatAsState(if (isCurrent) 1f else 0f, label = "row")
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f * bg)
                else Color.Transparent
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
            Spacer(Modifier.width(2.dp))
        }
        Box {
            Cover(track.thumbnailUrl, Modifier.size(48.dp), corner = 13)
            if (isCurrent && isPlaying) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color.Black.copy(alpha = 0.42f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.GraphicEq, null, Modifier.size(20.dp), tint = Color.White)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
            )
            Spacer(Modifier.height(1.dp))
            Text(
                track.artist + " · " + formatDuration(track.durationSeconds),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!selectionMode) {
            if (busy) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                }
            } else if (track.uri.isBlank()) {
                IconButton(onClick = onDownload, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Filled.Download, contentDescription = "下載",
                        Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Icon(
                    Icons.Filled.Check, contentDescription = "已下載",
                    Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onMenu, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.MoreVert, contentDescription = "更多",
                    Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FolderPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) null else cardBorder(),
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onClick() }
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun CreateFolderDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text("新增資料夾", style = MaterialTheme.typography.titleLarge) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
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
