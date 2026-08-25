package com.tubetune.downloader.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tubetune.downloader.AppViewModel
import com.tubetune.downloader.data.PreviewState
import com.tubetune.downloader.data.SearchResult
import com.tubetune.downloader.ui.FolderPickerDialog
import com.tubetune.downloader.ui.VideoPreviewDialog
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

    // 離開搜尋頁時停止音訊預覽
    DisposableEffect(Unit) {
        onDispose { vm.stopPreview() }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                IconButton(onClick = {
                    selectionMode = false
                    selected.clear()
                }) { Icon(Icons.Filled.Close, contentDescription = "取消多選") }
                Spacer(Modifier.width(4.dp))
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("搜尋 YouTube 音樂…") },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search(query) }),
                trailingIcon = {
                    IconButton(onClick = { vm.search(query) }) {
                        Icon(Icons.Filled.Search, contentDescription = "搜尋")
                    }
                }
            )
        }

        if (selectionMode && selected.isNotEmpty()) {
            Surface(tonalElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "已選 " + selected.size + " 首",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall
                    )
                    TextButton(onClick = { selected.clear() }) { Text("全不選") }
                    Button(onClick = {
                        pending = results.filter { selected.contains(it.videoId) }
                        showFolderPicker = true
                    }) { Text("加入資料夾") }
                }
            }
        }

        Box(Modifier.weight(1f)) {
            when {
                searching -> Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("搜尋中…")
                }
                error != null -> Column(Modifier.padding(24.dp)) {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "提示：若 YouTube 暫時限制連線，請稍後再試。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.MusicNote,
                            null,
                            Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("輸入歌名或歌手開始搜尋", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "點封面/標題影片預覽、點 ▶ 串流播放、點 ⬇ 加入資料夾、長按多選",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
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
                                    // 點封面/標題 → 影片預覽
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
                            onDownload = {
                                pending = listOf(r)
                                showFolderPicker = true
                            }
                        )
                    }
                    if (hasMore) {
                        item(key = "load_more") {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                OutlinedButton(onClick = { vm.loadMore() }) { Text("載入更多") }
                            }
                        }
                    }
                }
            }
        }

        // 音訊預覽列
        if (previewState != null) {
            PreviewBar(vm, previewState!!, onVideo = {
                val s = previewState!!
                vm.previewVideo(SearchResult(s.videoId, s.title, s.artist, 0, s.thumbnailUrl))
            })
        }
    }

    // 影片預覽對話框
    val vp = videoPreview
    if (vp != null) {
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
            }
        )
    }
}

@Composable
private fun PreviewBar(vm: AppViewModel, s: PreviewState, onVideo: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 3.dp) {
        Column {
            LinearProgressIndicator(
                progress = {
                    if (s.durationMs > 0 && s.error == null)
                        (s.positionMs.toFloat() / s.durationMs.toFloat()).coerceIn(0f, 1f)
                    else 0f
                },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                trackColor = Color.Transparent
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = s.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        s.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (s.error != null) {
                        Text(
                            s.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            s.artist,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (s.error == null) {
                    IconButton(onClick = { vm.preview.toggle() }) {
                        Icon(
                            if (s.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "播放/暫停"
                        )
                    }
                    IconButton(onClick = onVideo) {
                        Icon(Icons.Filled.Videocam, contentDescription = "切換影片預覽")
                    }
                }
                IconButton(onClick = { vm.stopPreview() }) { Icon(Icons.Filled.Close, contentDescription = "停止預覽") }
            }
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
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onClick() })
            }
            AsyncImage(
                model = r.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.size(width = 110.dp, height = 62.dp).clip(RoundedCornerShape(10.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    r.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        r.uploader,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        formatDuration(r.durationSeconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!selectionMode) {
                IconButton(onClick = onPlay, enabled = !isResolving) {
                    if (isResolving) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.Filled.PlayCircle,
                            contentDescription = "音訊串流",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(onClick = onDownload) {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = "下載",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
