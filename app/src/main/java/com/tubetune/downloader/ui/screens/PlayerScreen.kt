package com.tubetune.downloader.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.tubetune.downloader.AppViewModel
import com.tubetune.downloader.data.PlayMode
import com.tubetune.downloader.ui.Cover
import com.tubetune.downloader.ui.GradientCircleButton
import com.tubetune.downloader.ui.formatDuration
import com.tubetune.downloader.ui.theme.Gradients

@Composable
fun PlayerScreen(vm: AppViewModel) {
    val state by vm.player.state.collectAsState()
    val playMode by vm.player.playMode.collectAsState()
    val s = state ?: return
    val context = LocalContext.current

    val ktvRecording by vm.ktvRecording.collectAsState()
    val ktvElapsed by vm.ktvElapsed.collectAsState()
    val ktvError by vm.ktvError.collectAsState()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.startKtv()
        else vm.showKtvError("需要麥克風權限才能錄下你的歌聲")
    }

    fun startKtv() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) vm.startKtv() else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }
    val pos = if (dragging) dragValue.toLong() else s.positionMs
    val maxMs = s.durationMs.coerceAtLeast(1L)

    Box(
        Modifier
            .fillMaxSize()
            // 先鋪一層不透明底色：漸層頂部是半透明的，否則下面（音樂庫）會透出來
            .background(MaterialTheme.colorScheme.background)
            .background(Gradients.hero())
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp)) {
            // ---- 頂欄 ----
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.closePlayerScreen() }) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown, contentDescription = "收合",
                        Modifier.size(30.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "正在播放",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        s.track.folder,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (ktvRecording) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.16f))
                            .clickable { vm.stopKtv() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(MaterialTheme.colorScheme.error, CircleShape)
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            formatDuration(ktvElapsed.toLong()),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(7.dp))
                        Icon(
                            Icons.Filled.Stop, contentDescription = "停止錄音",
                            Modifier.size(15.dp), tint = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    IconButton(onClick = { startKtv() }) {
                        Icon(
                            Icons.Filled.Mic, contentDescription = "KTV 錄音",
                            Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            ktvError?.let { msg ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        msg,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { vm.clearKtvError() }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Close, contentDescription = "關閉",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.weight(0.6f))

            // ---- 封面 ----
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .fillMaxWidth(0.8f)
                        .aspectRatio(1f)
                        .shadow(26.dp, RoundedCornerShape(28.dp))
                ) {
                    Cover(s.track.thumbnailUrl, Modifier.fillMaxSize(), corner = 28)
                    if (s.track.thumbnailUrl.isNotBlank()) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(28.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.18f))
                                    )
                                )
                        )
                    }
                }
            }

            Spacer(Modifier.weight(0.7f))

            // ---- 曲目資訊 ----
            Text(
                s.track.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text(
                s.track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(18.dp))

            // ---- 進度 ----
            Slider(
                value = pos.toFloat().coerceIn(0f, maxMs.toFloat()),
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = {
                    dragging = false
                    vm.player.seekTo(dragValue.toLong())
                },
                valueRange = 0f..maxMs.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                )
            )
            Row(Modifier.fillMaxWidth()) {
                Text(
                    formatDuration(pos / 1000),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Text(
                    formatDuration(s.durationMs / 1000),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(6.dp))

            // ---- 控制列 ----
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.player.cyclePlayMode() }) {
                    when (playMode) {
                        PlayMode.SHUFFLE -> Icon(
                            Icons.Filled.Shuffle, contentDescription = "隨機播放",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        PlayMode.REPEAT_ONE -> Icon(
                            Icons.Filled.RepeatOne, contentDescription = "單曲重複",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        PlayMode.REPEAT_ALL -> Icon(
                            Icons.Filled.Repeat, contentDescription = "循環播放",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        PlayMode.NORMAL -> Icon(
                            Icons.Filled.Repeat, contentDescription = "順序播放",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
                IconButton(onClick = { vm.player.prev() }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Filled.SkipPrevious, contentDescription = "上一首",
                        Modifier.size(34.dp)
                    )
                }
                GradientCircleButton(70, 38, if (s.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "播放/暫停") {
                    vm.player.toggle()
                }
                IconButton(onClick = { vm.player.next() }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Filled.SkipNext, contentDescription = "下一首",
                        Modifier.size(34.dp)
                    )
                }
                IconButton(onClick = {
                    vm.player.stop()
                    vm.closePlayerScreen()
                }) {
                    Icon(
                        Icons.Filled.Close, contentDescription = "停止",
                        Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
        }
    }
}
