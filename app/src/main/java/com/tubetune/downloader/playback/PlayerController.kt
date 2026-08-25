package com.tubetune.downloader.playback

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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

/**
 * 音樂庫播放控制器：操作 PlaybackService 的共用 ExoPlayer。
 * 對外維持 StateFlow 介面（UI 無需改動）；Media3 自動處理背景播放、
 * 通知列控制與藍牙控制。
 */
class PlayerController(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var ticker: Job? = null
    private var attached: ExoPlayer? = null

    private val _state = MutableStateFlow<PlayerState?>(null)
    val state: StateFlow<PlayerState?> = _state.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val _playMode = MutableStateFlow(PlayMode.REPEAT_ALL)
    val playMode: StateFlow<PlayMode> = _playMode.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) startTicker()
            update()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                ticker?.cancel()
                update()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            update()
        }

        override fun onPlayerError(error: PlaybackException) {
            update()
        }
    }

    private fun player(): ExoPlayer? {
        val p = PlaybackService.ensureStarted(context)
        if (p != null && attached !== p) {
            attached?.removeListener(playerListener)
            attached = p
            p.addListener(playerListener)
        }
        return p
    }

    fun play(track: Track, playlist: List<Track>) {
        playInternal(track, playlist, null)
    }

    /**
     * 串流播放：urls 為 videoId → YouTube 串流 URL 的對應表；
     * 解析失敗的曲目會自動回退到本地檔案。
     */
    fun playStreaming(track: Track, playlist: List<Track>, urls: Map<String, String>) {
        playInternal(track, playlist, urls)
    }

    private fun playInternal(track: Track, playlist: List<Track>, urls: Map<String, String>?) {
        retrofit(3) { p ->
            try {
                p.stop()
                p.clearMediaItems()
                _queue.value = playlist
                _playMode.value = Prefs.playModeForFolder(context, track.folder)
                val ordered = if (_playMode.value == PlayMode.SHUFFLE) {
                    listOf(track) + playlist.filter { it.videoId != track.videoId }.shuffled()
                } else {
                    playlist.ifEmpty { listOf(track) }
                }
                val startIdx = ordered.indexOfFirst { it.videoId == track.videoId }.coerceAtLeast(0)
                p.setMediaItems(ordered.map { it.toMediaItem(urls?.get(it.videoId)) }, startIdx, 0L)
                p.repeatMode = when (_playMode.value) {
                    PlayMode.REPEAT_ONE -> Player.REPEAT_MODE_ONE
                    PlayMode.REPEAT_ALL -> Player.REPEAT_MODE_ALL
                    else -> Player.REPEAT_MODE_OFF
                }
                p.shuffleModeEnabled = false
                p.prepare()
                p.play()
                _state.value = PlayerState(track, true, 0, (track.durationSeconds * 1000).coerceAtLeast(1L))
                startTicker()
            } catch (t: Throwable) {
                // 不讓播放設定失敗拖垮 UI：保留錯誤訊息但不崩潰
                android.util.Log.e("PlayerController", "play failed", t)
                _state.value = PlayerState(track, false, 0, (track.durationSeconds * 1000).coerceAtLeast(1L))
            }
        }
    }

    fun toggle() {
        val p = player() ?: return
        if (p.isPlaying) p.pause() else {
            if (p.playbackState == Player.STATE_ENDED) p.seekTo(0)
            p.play()
            startTicker()
        }
        update()
    }

    fun seekTo(ms: Long) {
        player()?.seekTo(ms)
        update()
    }

    fun next() {
        val p = player() ?: return
        if (p.hasNextMediaItem()) {
            p.seekToNextMediaItem()
            p.play()
        } else if (_playMode.value != PlayMode.REPEAT_ALL) {
            stop()
        } else {
            p.seekTo(0, 0L)
            p.play()
        }
    }

    fun prev() {
        val p = player() ?: return
        if (p.hasPreviousMediaItem()) {
            p.seekToPreviousMediaItem()
            p.play()
        } else {
            p.seekTo(0, 0L)
            p.play()
        }
        update()
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
        player()?.repeatMode = when (next) {
            PlayMode.REPEAT_ONE -> Player.REPEAT_MODE_ONE
            PlayMode.REPEAT_ALL -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        val folder = _state.value?.track?.folder
        if (folder != null) Prefs.setPlayModeForFolder(context, folder, next)
    }

    fun stop() {
        val p = player()
        if (p != null) {
            try {
                p.stop()
                p.clearMediaItems()
            } catch (t: Throwable) {}
        }
        ticker?.cancel()
        _state.value = null
    }

    private fun retrofit(times: Int, block: (ExoPlayer) -> Unit) {
        val p = player()
        if (p != null) {
            block(p)
        } else if (times > 0) {
            scope.launch {
                delay(200)
                retrofit(times - 1, block)
            }
        }
    }

    private fun update() {
        val p = player() ?: return
        val s = _state.value ?: return
        val pos = p.currentPosition.coerceAtLeast(0)
        val dur = p.duration.takeIf { it > 0 } ?: (s.track.durationSeconds * 1000).coerceAtLeast(1L)
        _state.value = s.copy(
            positionMs = pos,
            durationMs = dur,
            isPlaying = p.isPlaying
        )
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

    fun release() {
        stop()
        attached?.removeListener(playerListener)
        attached = null
    }

    private fun Track.toMediaItem(streamUri: String? = null): MediaItem = MediaItem.Builder()
        .setMediaId(videoId)
        .setUri(streamUri ?: uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .apply { if (thumbnailUrl.isNotBlank()) setArtworkUri(Uri.parse(thumbnailUrl)) }
                .setExtras(
                    bundleOf(
                        "videoId" to videoId,
                        "title" to title,
                        "artist" to artist,
                        "thumbnailUrl" to thumbnailUrl,
                        "durationSeconds" to durationSeconds,
                        "folder" to folder,
                        "fileName" to fileName,
                        "uri" to uri,
                        "sizeBytes" to sizeBytes,
                        "downloadedAt" to downloadedAt
                    )
                )
                .build()
        )
        .build()

    private fun MediaItem.toTrack(): Track {
        val e: Bundle = mediaMetadata.extras ?: Bundle()
        return Track(
            videoId = mediaId,
            title = e.getString("title") ?: (mediaMetadata.title?.toString() ?: ""),
            artist = e.getString("artist") ?: (mediaMetadata.artist?.toString() ?: ""),
            durationSeconds = e.getLong("durationSeconds", 0L),
            thumbnailUrl = e.getString("thumbnailUrl")
                ?: (mediaMetadata.artworkUri?.toString() ?: ""),
            fileName = e.getString("fileName") ?: "",
            uri = e.getString("uri") ?: localConfiguration?.uri?.toString() ?: "",
            folder = e.getString("folder") ?: "",
            sizeBytes = e.getLong("sizeBytes", 0L),
            downloadedAt = e.getLong("downloadedAt", 0L)
        )
    }
}
