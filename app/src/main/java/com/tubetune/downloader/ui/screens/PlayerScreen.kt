package com.tubetune.downloader.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.tubetune.downloader.AppViewModel
import com.tubetune.downloader.data.PlayMode
import com.tubetune.downloader.ui.formatDuration

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
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.closePlayerScreen() }) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "收合", Modifier.size(32.dp))
                }
                Spacer(Modifier.size(8.dp))
                Column {
                    Text(
                        "正在播放",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        s.track.folder,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))
                if (ktvRecording) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .background(MaterialTheme.colorScheme.error, CircleShape)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            formatDuration(ktvElapsed.toLong()),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(onClick = { vm.stopKtv() }) {
                            Icon(Icons.Filled.Stop, contentDescription = "停止錄音", Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("停止")
                        }
                    }
                } else {
                    IconButton(onClick = { startKtv() }) {
                        Icon(Icons.Filled.Mic, contentDescription = "KTV 錄音")
                    }
                }
            }
            ktvError?.let { msg ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { vm.clearKtvError() }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "關閉",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = s.track.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(20.dp))
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                s.track.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                s.track.artist,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            Slider(
                value = if (dragging) dragValue else pos.toFloat(),
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = {
                    dragging = false
                    vm.player.seekTo(dragValue.toLong())
                },
                valueRange = 0f..maxMs.toFloat()
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatDuration(pos / 1000), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
                Text(formatDuration(s.durationMs / 1000), style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.player.cyclePlayMode() }) {
                    when (playMode) {
                        PlayMode.SHUFFLE -> Icon(
                            Icons.Filled.Shuffle,
                            contentDescription = "隨機播放",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        PlayMode.REPEAT_ONE -> Icon(
                            Icons.Filled.RepeatOne,
                            contentDescription = "單曲重複",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        PlayMode.REPEAT_ALL -> Icon(
                            Icons.Filled.Repeat,
                            contentDescription = "循環播放",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        PlayMode.NORMAL -> Icon(
                            Icons.Filled.Repeat,
                            contentDescription = "順序播放",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                FilledIconButton(onClick = { vm.player.prev() }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "上一首")
                }
                FilledIconButton(onClick = { vm.player.toggle() }, modifier = Modifier.size(72.dp)) {
                    Icon(
                        if (s.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (s.isPlaying) "暫停" else "播放",
                        Modifier.size(40.dp)
                    )
                }
                FilledIconButton(onClick = { vm.player.next() }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "下一首")
                }
                IconButton(onClick = {
                    vm.player.stop()
                    vm.closePlayerScreen()
                }) {
                    Icon(Icons.Filled.Close, contentDescription = "停止")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
