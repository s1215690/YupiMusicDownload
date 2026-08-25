package com.tubetune.downloader.playback

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import com.tubetune.downloader.data.PlayMode
import com.tubetune.downloader.data.PlayerState
import com.tubetune.downloader.data.Prefs
import com.tubetune.downloader.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerController(private val context: Context) {

    private var player: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var ticker: Job? = null

    private val _state = MutableStateFlow<PlayerState?>(null)
    val state: StateFlow<PlayerState?> = _state.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val _playMode = MutableStateFlow(PlayMode.REPEAT_ALL)
    val playMode: StateFlow<PlayMode> = _playMode.asStateFlow()

    /** 實際播放順序（隨機模式時打亂，當前曲目放最前面） */
    private var order: List<Track> = emptyList()

    fun play(track: Track, playlist: List<Track>) {
        try {
            stopInternal()
            _queue.value = playlist
            _playMode.value = Prefs.playModeForFolder(context, track.folder)
            order = if (_playMode.value == PlayMode.SHUFFLE) {
                listOf(track) + playlist.filter { it.videoId != track.videoId }.shuffled()
            } else {
                playlist.ifEmpty { listOf(track) }
            }
            val mp = MediaPlayer()
            mp.setDataSource(context, Uri.parse(track.uri))
            mp.isLooping = _playMode.value == PlayMode.REPEAT_ONE
            mp.setOnPreparedListener { p ->
                p.start()
                startTicker()
            }
            mp.setOnCompletionListener { next() }
            mp.prepareAsync()
            player = mp
            _state.value = PlayerState(track, false, 0, track.durationSeconds * 1000)
        } catch (t: Throwable) {
            stop()
        }
    }

    fun toggle() {
        val p = player ?: return
        try {
            if (p.isPlaying) {
                p.pause()
            } else {
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

    fun next() {
        val s = _state.value ?: return
        if (order.isEmpty()) return
        val idx = order.indexOfFirst { it.videoId == s.track.videoId }
        val n = when {
            idx < 0 -> order[0]
            idx == order.size - 1 ->
                if (_playMode.value == PlayMode.REPEAT_ALL) order[0] else null
            else -> order[idx + 1]
        }
        if (n != null) play(n, _queue.value) else stop()
    }

    fun prev() {
        val s = _state.value ?: return
        if (order.isEmpty()) return
        val idx = order.indexOfFirst { it.videoId == s.track.videoId }
        val p = if (idx > 0) order[idx - 1] else order[0]
        play(p, _queue.value)
    }

    /** 切換播放模式：順序 → 循環 → 隨機 → 單曲重複 → 順序 */
    fun cyclePlayMode() {
        val next = when (_playMode.value) {
            PlayMode.NORMAL -> PlayMode.REPEAT_ALL
            PlayMode.REPEAT_ALL -> PlayMode.SHUFFLE
            PlayMode.SHUFFLE -> PlayMode.REPEAT_ONE
            PlayMode.REPEAT_ONE -> PlayMode.NORMAL
        }
        _playMode.value = next
        try { player?.isLooping = next == PlayMode.REPEAT_ONE } catch (t: Throwable) {}
        val folder = _state.value?.track?.folder
        if (folder != null) Prefs.setPlayModeForFolder(context, folder, next)
    }

    fun stop() {
        stopInternal()
        _state.value = null
    }

    private fun stopInternal() {
        ticker?.cancel()
        ticker = null
        try { player?.stop() } catch (t: Throwable) {}
        try { player?.release() } catch (t: Throwable) {}
        player = null
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (true) {
                updatePosition()
                delay(500)
            }
        }
    }

    private fun updatePosition() {
        val s = _state.value ?: return
        val p = player ?: return
        val pos = try { p.currentPosition.toLong() } catch (t: Throwable) { 0L }
        val dur = try { if (p.duration > 0) p.duration.toLong() else s.durationMs } catch (t: Throwable) { s.durationMs }
        val playing = try { p.isPlaying } catch (t: Throwable) { false }
        _state.value = s.copy(positionMs = pos, durationMs = dur, isPlaying = playing)
    }

    fun release() {
        stop()
    }
}
