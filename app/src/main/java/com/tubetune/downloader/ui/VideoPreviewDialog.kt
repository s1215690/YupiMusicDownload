package com.tubetune.downloader.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.tubetune.downloader.data.VideoPreview
import com.tubetune.downloader.playback.PlaybackService

/**
 * 影片預覽：ExoPlayer + PlayerView（帶瀏覽器 UA）。
 * 支援全屏模式：填滿整個螢幕並隱藏系統列；點控制列的 ⛶ 或右上角按鈕切換。
 */
@Composable
fun VideoPreviewDialog(preview: VideoPreview, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var fullscreen by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val p = ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    DefaultHttpDataSource.Factory()
                        .setUserAgent(PlaybackService.CHROME_UA)
                        .setAllowCrossProtocolRedirects(true)
                )
            )
            .build()
        p.setMediaItem(MediaItem.fromUri(Uri.parse(preview.url)))
        p.prepare()
        p.play()
        player = p
        onDispose {
            p.release()
            player = null
            // 關閉對話框時恢復系統列（防全屏狀態殘留）
            activity?.window?.let { w ->
                WindowCompat.getInsetsController(w, w.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // 全屏進出：隱藏/顯示系統狀態列與導航列
    DisposableEffect(fullscreen, activity) {
        val w = activity?.window ?: return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(w, w.decorView)
        if (fullscreen) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (fullscreen) controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        if (fullscreen) {
            // ---- 全屏模式：黑色背景，畫面填滿 ----
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            setFullscreenButtonClickListener {
                                fullscreen = false
                            }
                        }
                    }
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Color(0x88000000))
                        .padding(start = 16.dp, end = 4.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        preview.title,
                        Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall
                    )
                    IconButton(onClick = { fullscreen = false }) {
                        Icon(Icons.Filled.FullscreenExit, contentDescription = "退出全屏", tint = Color.White)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "關閉預覽", tint = Color.White)
                    }
                }
            }
        } else {
            // ---- 一般模式：卡片式 ----
            Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp), tonalElevation = 6.dp) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            preview.title,
                            Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall
                        )
                        IconButton(onClick = { fullscreen = true }) {
                            Icon(Icons.Filled.Fullscreen, contentDescription = "全屏")
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "關閉預覽")
                        }
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    this.player = player
                                    setFullscreenButtonClickListener {
                                        fullscreen = true
                                    }
                                }
                            }
                        )
                    }
                    Spacer(Modifier.size(8.dp))
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
