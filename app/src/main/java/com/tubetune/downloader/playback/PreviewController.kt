package com.tubetune.downloader.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.tubetune.downloader.data.PreviewState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 搜尋結果的音訊串流預覽（與音樂庫共用 PlaybackService 的 ExoPlayer）。
 * ExoPlayer + 瀏覽器 UA 才能正常播放 googlevideo 的直連串流；
 * 使用 MediaPlayer 網路串流預覽會失敗。
 */
class PreviewController {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var ticker: Job? = null
    private var listening = false

    private val _state = MutableStateFlow<PreviewState?>(null)
    val state: StateFlow<PreviewState?> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) startTicker()
            update()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val s = _state.value ?: return
            when (playbackState) {
                Player.STATE_READY -> update()
                Player.STATE_ENDED -> {
                    ticker?.cancel()
                    _state.value = s.copy(isPlaying = false)
                }
                else -> {}
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            ticker?.cancel()
            val s = _state.value
            _state.value = s?.copy(isPlaying = false, error = errorMessage(error))
        }
    }

    private fun attach(p: ExoPlayer) {
        if (!listening) {
            p.addListener(listener)
            listening = true
        }
    }

    fun play(videoId: String, title: String, artist: String, thumbnail: String, url: String) {
        val p = PlaybackService.ensureStarted(PlaybackService.appContext ?: return) ?: run {
            retryPlay(videoId, title, artist, thumbnail, url, 5)
            return
        }
        attach(p)
        try {
            p.stop()
            p.clearMediaItems()
            p.shuffleModeEnabled = false
            p.repeatMode = Player.REPEAT_MODE_OFF
            p.setMediaItem(
                MediaItem.Builder()
                    .setMediaId(videoId)
                    .setUri(url)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(title)
                            .setArtist(artist)
                            .apply { if (thumbnail.isNotBlank()) setArtworkUri(Uri.parse(thumbnail)) }
                            .build()
                    )
                    .build()
            )
            p.prepare()
            p.play()
            _state.value = PreviewState(videoId, title, artist, thumbnail, true, 0, 0, null)
            startTicker()
        } catch (t: Throwable) {
            _state.value = PreviewState(videoId, title, artist, thumbnail, false, 0, 0, "預覽失敗：" + (t.message ?: t.javaClass.simpleName))
        }
    }

    fun fail(videoId: String, title: String, artist: String, thumbnail: String, message: String) {
        stop()
        _state.value = PreviewState(videoId, title, artist, thumbnail, false, 0, 0, message)
    }

    fun toggle() {
        val p = PlaybackService.playerHolder ?: return
        if (_state.value?.error != null) return
        if (p.isPlaying) p.pause() else {
            if (p.playbackState == Player.STATE_ENDED) p.seekTo(0)
            p.play()
            startTicker()
        }
        update()
    }

    fun seekTo(ms: Long) {
        PlaybackService.playerHolder?.seekTo(ms)
        update()
    }

    fun stop() {
        val p = PlaybackService.playerHolder
        if (p != null) {
            try {
                p.stop()
                p.clearMediaItems()
            } catch (t: Throwable) {}
        }
        ticker?.cancel()
        _state.value = null
    }

    private fun retryPlay(videoId: String, title: String, artist: String, thumbnail: String, url: String, times: Int) {
        scope.launch {
            delay(200)
            if (times > 0) play(videoId, title, artist, thumbnail, url, times - 1)
            else fail(videoId, title, artist, thumbnail, "預覽失敗：播放器尚未就緒")
        }
    }

    private fun play(videoId: String, title: String, artist: String, thumbnail: String, url: String, retry: Int) {
        val p = PlaybackService.ensureStarted(PlaybackService.appContext ?: return) ?: run {
            retryPlay(videoId, title, artist, thumbnail, url, retry)
            return
        }
        attach(p)
        try {
            p.stop()
            p.clearMediaItems()
            p.repeatMode = Player.REPEAT_MODE_OFF
            p.setMediaItem(
                MediaItem.Builder()
                    .setMediaId(videoId)
                    .setUri(url)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(title)
                            .setArtist(artist)
                            .apply { if (thumbnail.isNotBlank()) setArtworkUri(Uri.parse(thumbnail)) }
                            .build()
                    )
                    .build()
            )
            p.prepare()
            p.play()
            _state.value = PreviewState(videoId, title, artist, thumbnail, true, 0, 0, null)
            startTicker()
        } catch (t: Throwable) {
            _state.value = PreviewState(videoId, title, artist, thumbnail, false, 0, 0, "預覽失敗：" + (t.message ?: t.javaClass.simpleName))
        }
    }

    /** 回到 App 前景時：若播放器尚有預覽曲目但介面沒有狀態，補上 */
    fun resync() {
        val p = PlaybackService.playerHolder ?: return
        if (_state.value == null && p.mediaItemCount > 0) {
            p.currentMediaItem?.let { item ->
                _state.value = PreviewState(
                    videoId = item.mediaId,
                    title = item.mediaMetadata.title?.toString() ?: "",
                    artist = item.mediaMetadata.artist?.toString() ?: "",
                    thumbnailUrl = item.mediaMetadata.artworkUri?.toString() ?: "",
                    isPlaying = p.isPlaying,
                    positionMs = p.currentPosition.coerceAtLeast(0),
                    durationMs = p.duration.takeIf { it > 0 } ?: 0L,
                    error = null
                )
            }
        }
    }

    private fun update() {
        val p = PlaybackService.playerHolder ?: return
        val s = _state.value ?: return
        val pos = p.currentPosition.coerceAtLeast(0)
        val dur = p.duration.takeIf { it > 0 } ?: 0L
        _state.value = s.copy(positionMs = pos, durationMs = dur, isPlaying = p.isPlaying)
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (true) {
                update()
                delay(500)
            }
        }
    }

    private fun errorMessage(e: PlaybackException): String {
        val base = when {
            e.errorCodeName?.contains("IO") == true -> "網路錯誤，無法載入串流"
            e.errorCodeName?.contains("DRM") == true -> "受著作權保護，無法預覽"
            e.message?.contains("403", ignoreCase = true) == true -> "YouTube 拒絕連線（403）"
            e.message?.contains("429", ignoreCase = true) == true -> "YouTube 暫時限流，請稍後再試"
            else -> e.message ?: "未知錯誤"
        }
        return base.take(100)
    }

    /** ViewModel 銷毀時釋放 UI 資源；不停止播放（背景播放交給 service） */
    fun release() {
        ticker?.cancel()
        if (listening) {
            PlaybackService.playerHolder?.removeListener(listener)
            listening = false
        }
        scope.coroutineContext[Job]?.cancel()
    }
}
