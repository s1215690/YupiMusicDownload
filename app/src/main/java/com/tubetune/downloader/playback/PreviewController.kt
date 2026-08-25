package com.tubetune.downloader.playback

import android.media.AudioAttributes
import android.media.MediaPlayer
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
 * 搜尋結果的音訊串流預覽播放器。
 * 與 PlayerController（本機音樂庫）分開：預覽播放的是 YouTube 串流，
 * 不需要排隊、循環功能，壞掉時只顯示錯誤。
 */
class PreviewController {

    private var player: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var ticker: Job? = null

    private val _state = MutableStateFlow<PreviewState?>(null)
    val state: StateFlow<PreviewState?> = _state.asStateFlow()

    fun play(videoId: String, title: String, artist: String, thumbnail: String, url: String) {
        stopInternal()
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mp.setDataSource(url)
            mp.setOnPreparedListener { p ->
                p.start()
                val s = _state.value
                if (s != null) {
                    _state.value = s.copy(
                        isPlaying = true,
                        durationMs = try { p.duration.toLong() } catch (t: Throwable) { 0L },
                        error = null
                    )
                }
                startTicker()
            }
            mp.setOnErrorListener { _, what, extra ->
                ticker?.cancel()
                val s = _state.value
                _state.value = s?.copy(isPlaying = false, error = "預覽失敗（$what/$extra）")
                true
            }
            mp.setOnCompletionListener {
                ticker?.cancel()
                val s = _state.value
                _state.value = s?.copy(isPlaying = false)
            }
            mp.prepareAsync()
            player = mp
            _state.value = PreviewState(videoId, title, artist, thumbnail, false, 0, 0, null)
        } catch (t: Throwable) {
            releasePlayer()
            _state.value = PreviewState(videoId, title, artist, thumbnail, false, 0, 0, "預覽失敗：" + (t.message ?: t.javaClass.simpleName))
        }
    }

    fun fail(videoId: String, title: String, artist: String, thumbnail: String, message: String) {
        stopInternal()
        _state.value = PreviewState(videoId, title, artist, thumbnail, false, 0, 0, message)
    }

    fun toggle() {
        val p = player ?: return
        if (_state.value?.error != null) return
        try {
            if (p.isPlaying) p.pause() else {
                p.start()
                startTicker()
            }
            updatePosition()
        } catch (t: Throwable) {}
    }

    fun seekTo(ms: Long) {
        try { player?.seekTo(ms.toInt()) } catch (t: Throwable) {}
        updatePosition()
    }

    fun stop() {
        stopInternal()
        _state.value = null
    }

    private fun updatePosition() {
        val s = _state.value ?: return
        val p = player ?: return
        val pos = try { p.currentPosition.toLong() } catch (t: Throwable) { 0L }
        val dur = try { if (p.duration > 0) p.duration.toLong() else s.durationMs } catch (t: Throwable) { s.durationMs }
        val playing = try { if (p.isPlaying) { startTicker(); true } else false } catch (t: Throwable) { false }
        _state.value = s.copy(positionMs = pos, durationMs = dur, isPlaying = playing)
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (true) {
                updatePosition()
                delay(500)
            }
        }
    }

    private fun stopInternal() {
        ticker?.cancel()
        ticker = null
        releasePlayer()
    }

    private fun releasePlayer() {
        try { player?.stop() } catch (t: Throwable) {}
        try { player?.release() } catch (t: Throwable) {}
        player = null
    }

    fun release() {
        stop()
        scope.coroutineContext[Job]?.cancel()
    }
}
