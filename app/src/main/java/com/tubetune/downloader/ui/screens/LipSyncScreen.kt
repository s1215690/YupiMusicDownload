package com.tubetune.downloader.ui.screens

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.video.AudioConfig
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.tubetune.downloader.data.Track
import com.tubetune.downloader.ui.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 對嘴錄影：播放剛剛錄好的（或任何）音檔，同時用前置鏡頭錄影。
 *
 * 影片連麥克風收音一齊錄（所以戴耳機效果最好：只收到你的人聲），
 * 儲存到 Movies/TubeTune，唔需要儲存權限（MediaStore）。
 */
@Composable
fun LipSyncScreen(track: Track, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        granted = result.values.all { it }
    }

    var recording by remember { mutableStateOf(false) }
    var elapsedSec by remember { mutableIntStateOf(0) }
    var savedUri by remember { mutableStateOf<Uri?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.VIDEO_CAPTURE)
        }
    }
    // 播放要對嘴的音檔（獨立 player，唔會影響音樂庫播放）
    val takePlayer = remember(track.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(track.uri)))
            prepare()
        }
    }
    var recordingRef by remember { mutableStateOf<Recording?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { recordingRef?.stop() }
            runCatching { takePlayer.release() }
            runCatching { controller.unbind() }
        }
    }

    LaunchedEffect(granted, previewView) {
        if (!granted) return@LaunchedEffect
        runCatching {
            // CameraX 要先等初始化完成，否則會報 "Camera not initialized"
            withContext(Dispatchers.IO) { ProcessCameraProvider.getInstance(context).get() }
            // 有前鏡頭就用前鏡頭（自拍對嘴），冇就用後置（例如部分模擬器／平板）
            controller.cameraSelector =
                if (controller.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
            controller.bindToLifecycle(lifecycleOwner)
            previewView.controller = controller
        }.onFailure {
            status = "相機啟動失敗：" + (it.message ?: "未知錯誤")
        }
    }

    // 錄影中計時
    LaunchedEffect(recording) {
        elapsedSec = 0
        while (recording) {
            delay(1000)
            elapsedSec += 1
            // 音檔播完就自動停止錄影
            if (takePlayer.duration > 0 && takePlayer.currentPosition >= takePlayer.duration - 150) {
                runCatching { recordingRef?.stop() }
            }
        }
    }

    fun startRecording() {
        status = null
        savedUri = null
        val name = "對嘴-" + track.title.take(24) + "-" + System.currentTimeMillis()
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$name.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/TubeTune")
        }
        val options = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(values).build()

        runCatching {
            recordingRef = controller.startRecording(
                options,
                AudioConfig.create(true),
                ContextCompat.getMainExecutor(context)
            ) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        recording = true
                        takePlayer.seekTo(0)
                        takePlayer.play()
                    }
                    is VideoRecordEvent.Finalize -> {
                        recording = false
                        runCatching { takePlayer.pause() }
                        if (event.hasError()) {
                            status = "錄影失敗（代碼 " + event.error + "）"
                        } else {
                            savedUri = event.outputResults.outputUri
                            status = "已儲存到 Movies/TubeTune"
                        }
                    }
                }
            }
        }.onFailure {
            status = "無法開始錄影：" + (it.message ?: "未知錯誤")
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        } else {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.Videocam, null, Modifier.size(52.dp), tint = Color.White)
                Spacer(Modifier.height(14.dp))
                Text(
                    "對嘴錄影需要相機同麥克風權限",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    permLauncher.launch(
                        arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                    )
                }) { Text("授予權限") }
            }
        }

        // 頂欄
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { runCatching { recordingRef?.stop() }; onClose() }) {
                Icon(Icons.Filled.Close, "關閉", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (recording) "錄影中 · " + formatDuration(elapsedSec.toLong())
                    else "對嘴錄影 · 戴耳機收音最乾淨",
                    color = if (recording) Color(0xFFFF6B6B) else Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            if (recording) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF3B30))
                )
                Spacer(Modifier.width(10.dp))
            }
        }

        // 底部控制
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            status?.let {
                Text(
                    it,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (recording) {
                            runCatching { recordingRef?.stop() }
                        } else {
                            if (!granted) {
                                permLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.CAMERA,
                                        Manifest.permission.RECORD_AUDIO
                                    )
                                )
                            } else startRecording()
                        }
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (recording) Color(0xFFFF3B30) else Color.White,
                        contentColor = if (recording) Color.White else Color.Black
                    ),
                    modifier = Modifier.size(74.dp).clip(CircleShape)
                ) {
                    Icon(
                        if (recording) Icons.Filled.Mic else Icons.Filled.Videocam,
                        contentDescription = if (recording) "停止錄影" else "開始錄影",
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            savedUri?.let { uri ->
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    uri
                                ).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            )
                        }
                    }) { Text("播放影片") }
                    Button(
                        onClick = { savedUri = null; status = null },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.18f))
                    ) { Text("再錄一次", color = Color.White) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "音樂會同時播放（用喇叭收音）；戴耳機會更乾淨",
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
