package com.tubetune.downloader.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tubetune.downloader.AppViewModel
import com.tubetune.downloader.data.PreviewState
import com.tubetune.downloader.data.SearchResult
import com.tubetune.downloader.ui.Cover
import com.tubetune.downloader.ui.EmptyState
import com.tubetune.downloader.ui.FolderPickerDialog
import com.tubetune.downloader.ui.VideoPreviewDialog
import com.tubetune.downloader.ui.cardBorder
import com.tubetune.downloader.ui.formatDuration

@Composable
fun SearchScreen(vm: AppViewModel) {
    var query by remember { mutableStateOf("") }
    var selectionMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    var pending: List<SearchResult> by remember { mutableStateOf(emptyList()) }
    var showFolderPicker by remember { mutableStateOf(false) }

    val results by vm.searchResults.collectAsState()
    val searching by vm.searching.collectAsState()
    val error by vm.searchError.collectAsState()
    val hasMore by vm.hasMore.collectAsState()
    val libData by vm.library.data.collectAsState()
    val previewState by vm.preview.state.collectAsState()
    val previewBusy by vm.previewBusy.collectAsState()
    val videoPreview by vm.videoPreview.collectAsState()

    DisposableEffect(Unit) {
        onDispose { vm.stopPreview() }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                IconButton(onClick = { selectionMode = false; selected.clear() }) {
                    Icon(Icons.Filled.Close, contentDescription = "取消多選", Modifier.size(20.dp))
                }
                Spacer(Modifier.width(4.dp))
            }
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("搜尋歌曲、歌手…") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search, null, Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }, modifier = Modifier.size(34.dp)) {
                                Icon(
                                    Icons.Filled.Close, contentDescription = "清除", Modifier.size(17.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (searching) {
                            Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            }
                        } else {
                            IconButton(onClick = { vm.search(query) }, modifier = Modifier.size(34.dp)) {
                                Icon(
                                    Icons.Filled.Search, contentDescription = "搜尋", Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search(query) })
            )
        }

        if (selectionMode && selected.isNotEmpty()) {
            Surface(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = cardBorder()
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "已選 " + selected.size + " 首",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall
                    )
                    TextButton(onClick = { selected.clear() }) { Text("全不選") }
                    Button(
                        onClick = {
                            pending = results.filter { selected.contains(it.videoId) }
                            showFolderPicker = true
                        },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.Add, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("加入資料夾")
                    }
                }
            }
        }

        Box(Modifier.weight(1f)) {
            when {
                error != null && results.isEmpty() -> Column(Modifier.padding(24.dp)) {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "提示：若 YouTube 暫時限制連線，請稍後再試。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                results.isEmpty() && !searching -> EmptyState(
                    Icons.Filled.MusicNote,
                    "輸入歌名或歌手開始搜尋",
                    "點封面看影片預覽 · ▶ 試聽 · ＋ 加入資料夾，長按可多選"
                )

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(results, key = { it.videoId }) { r ->
                        val isSelected = selected.contains(r.videoId)
                        SearchRow(
                            r = r,
                            selectionMode = selectionMode,
                            selected = isSelected,
                            isResolving = previewBusy == r.videoId,
                            onClick = {
                                if (selectionMode) {
                                    if (isSelected) selected.remove(r.videoId) else selected.add(r.videoId)
                                } else {
                                    vm.previewVideo(r)
                                }
                            },
                            onLongClick = {
                                if (!selectionMode) {
                                    selectionMode = true
                                    selected.clear()
                                    selected.add(r.videoId)
                                }
                            },
                            onPlay = { vm.previewAudio(r) },
                            onAdd = {
                                pending = listOf(r)
                                showFolderPicker = true
                            }
                        )
                    }
                    if (hasMore) {
                        item(key = "load_more") {
                            Box(
                                Modifier.fillMaxWidth().padding(18.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                OutlinedButton(
                                    onClick = { vm.loadMore() },
                                    shape = RoundedCornerShape(16.dp)
                                ) { Text("載入更多") }
                            }
                        }
                    }
                }
            }
        }

        previewState?.let { s ->
            PreviewBar(vm, s) {
                vm.previewVideo(SearchResult(s.videoId, s.title, s.artist, 0, s.thumbnailUrl))
            }
        }
    }

    videoPreview?.let { vp ->
        VideoPreviewDialog(vp) { vm.closeVideoPreview() }
    }

    if (showFolderPicker) {
        FolderPickerDialog(
            folders = libData.folders,
            onCreate = { vm.library.addFolder(it) },
            onPick = { folder ->
                showFolderPicker = false
                vm.addToFolder(pending, folder)
                pending = emptyList()
                selectionMode = false
                selected.clear()
            },
            onDismiss = {
                showFolderPicker = false
                pending = emptyList()
            },
            title = "加入資料夾"
        )
    }
}

@Composable
private fun PreviewBar(vm: AppViewModel, s: PreviewState, onVideo: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = cardBorder(),
        shadowElevation = 10.dp
    ) {
        Column(Modifier.clip(RoundedCornerShape(22.dp))) {
            Row(
                Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Cover(s.thumbnailUrl, Modifier.size(42.dp), corner = 12)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        s.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        s.error ?: s.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (s.error != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (s.error == null) {
                    IconButton(onClick = { vm.preview.toggle() }) {
                        Icon(
                            if (s.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "播放/暫停",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onVideo, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Filled.Videocam, contentDescription = "切換影片預覽",
                            Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = { vm.stopPreview() }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Close, contentDescription = "停止預覽",
                        Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            LinearProgressIndicator(
                progress = {
                    if (s.durationMs > 0 && s.error == null)
                        (s.positionMs.toFloat() / s.durationMs.toFloat()).coerceIn(0f, 1f)
                    else 0f
                },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                trackColor = Color.Transparent
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchRow(
    r: SearchResult,
    selectionMode: Boolean,
    selected: Boolean,
    isResolving: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlay: () -> Unit,
    onAdd: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
            Spacer(Modifier.width(2.dp))
        }
        Cover(r.thumbnailUrl, Modifier.size(width = 96.dp, height = 54.dp), corner = 13)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                r.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                r.uploader + " · " + formatDuration(r.durationSeconds),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!selectionMode) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    .clickable(enabled = !isResolving) { onPlay() },
                contentAlignment = Alignment.Center
            ) {
                if (isResolving) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.Filled.PlayArrow, contentDescription = "試聽",
                        Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onAdd, modifier = Modifier.size(38.dp)) {
                Icon(
                    Icons.Filled.Add, contentDescription = "加入資料夾",
                    Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
