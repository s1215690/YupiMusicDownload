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

    /** 最後一次播放錯誤（UI 可以顯示「重試」） */
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /** 緩衝中：ExoPlayer 緩衝時 isPlaying 會變 false，UI 唔應該當成「已暫停」 */
    private val _buffering = MutableStateFlow(false)
    val buffering: StateFlow<Boolean> = _buffering.asStateFlow()

    // 記住上次播咩，方便系統回收 service／出錯之後原地恢復
    private var lastTrack: Track? = null
    private var lastPlaylist: List<Track> = emptyList()
    private var lastUrls: Map<String, String>? = null

    /** 串流卡住 watchdog：playWhenReady 但一直緩衝唔前進 */
    private var stallSince = 0L
    private var watchdog: Job? = null

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val _playMode = MutableStateFlow(PlayMode.REPEAT_ALL)
    val playMode: StateFlow<PlayMode> = _playMode.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                startTicker()
                stallSince = 0L
            }
            update()
        }

        /** 記錄「為咩停」：焦點被搶 / 使用者按暫停 / 出錯 —— 方便日後查真實原因 */
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            android.util.Log.i("TubeTune", "playWhenReady=" + playWhenReady + " reason=" + reason)
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) {
                android.util.Log.i("TubeTune", "→ 音訊焦點被其他 App 取走（例如播片／通話）")
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _buffering.value = playbackState == Player.STATE_BUFFERING
            if (playbackState == Player.STATE_ENDED) {
                ticker?.cancel()
                update()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            stallSince = 0L
            update()
        }

        override fun onPlayerError(error: PlaybackException) {
            // 保住錯誤資訊：串流連結過期／403／斷網都會嚟到呢度，
            // 之前只係 update()，所以 UI 睇落「暫停咗」但完全冇提示、亦播唔返。
            android.util.Log.e(
                "TubeTune", "播放錯誤 " + error.errorCodeName + "：" + error.message
            )
            _lastError.value = error.errorCodeName + "：" + (error.message ?: "").take(160)
            _buffering.value = false
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
        lastTrack = track
        lastPlaylist = playlist
        lastUrls = urls
        _lastError.value = null
        retrofit(15) { p ->
            try {
                p.stop()
                p.clearMediaItems()
                // 播放範圍鎖定「點下去那首歌所在的資料夾」：
                // 就算清單是「全部」跨資料夾的，也只播同一資料夾的歌
                val folder = track.folder
                val scoped = playlist.filter { it.folder == folder }
                    .ifEmpty { playlist.filter { it.videoId == track.videoId } }
                    .ifEmpty { listOf(track) }
                _queue.value = scoped
                _playMode.value = Prefs.playModeForFolder(context, folder)
                val ordered = if (_playMode.value == PlayMode.SHUFFLE) {
                    listOf(track) + scoped.filter { it.videoId != track.videoId }.shuffled()
                } else {
                    scoped
                }
                // 既拿不到串流 URL、又沒有本地檔案的曲目，硬放進佇列會在播到它時直接報錯；
                // 這裡先剔除（點播的那首歌已優先解析，正常不會被剔除）。
                val queue = if (urls == null) ordered else {
                    ordered.filter { !urls[it.videoId].isNullOrBlank() || it.uri.isNotBlank() }
                        .ifEmpty { ordered }
                }
                val startIdx = queue.indexOfFirst { it.videoId == track.videoId }.coerceAtLeast(0)
                p.setMediaItems(queue.map { it.toMediaItem(urls?.get(it.videoId)) }, startIdx, 0L)
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
                startWatchdog()
            } catch (t: Throwable) {
                // 不讓播放設定失敗拖垮 UI：保留錯誤訊息但不崩潰
                android.util.Log.e("PlayerController", "play failed", t)
                _state.value = PlayerState(track, false, 0, (track.durationSeconds * 1000).coerceAtLeast(1L))
            }
        }
    }

    /**
     * 播放／暫停。
     *
     * 這裡修的是「突然停咗之後按 ▶ 完全冇反應」：
     *  - 串流出錯（403／連結過期／斷網）之後 ExoPlayer 會回到 STATE_IDLE，
     *    單純 play() 只會設定 playWhenReady，唔會重新 prepare → 睇落完全冇反應。
     *  - 系統回收咗 PlaybackService 之後 player() 會回 null，舊寫法直接 return。
     *  - 播到最後一首時是 STATE_ENDED，要 seekTo(0) 才播得返。
     */
    fun toggle() {
        val p = player()
        if (p == null) {
            // service 被系統回收：等它重建後用記住的清單原地恢復
            android.util.Log.i("TubeTune", "toggle：player 未就緒，嘗試由快取恢復")
            resumeFromCache()
            return
        }
        if (p.isPlaying) {
            p.pause()
            update()
            return
        }
        when (p.playbackState) {
            Player.STATE_IDLE -> {
                if (p.mediaItemCount > 0) {
                    // 重新 prepare：串流出錯後靠這一步才播得返
                    p.prepare()
                    p.play()
                } else {
                    resumeFromCache()
                }
            }
            Player.STATE_ENDED -> {
                p.seekTo(0)
                p.play()
            }
            else -> p.play()
        }
        _lastError.value = null
        startTicker()
        startWatchdog()
        update()
    }

    /** 由最後一次播放的資料重建佇列（service 被回收／player 已釋放時用） */
    private fun resumeFromCache() {
        val t = lastTrack ?: _state.value?.track ?: return
        val q = lastPlaylist.ifEmpty { _queue.value }
        if (q.isEmpty()) return
        if (q.none { it.videoId == t.videoId }) return
        playInternal(t, q, lastUrls)
    }

    /** 串流卡死 watchdog：playWhenReady 但連續緩衝超過 20 秒 → 強制重新 prepare 一次 */
    private fun startWatchdog() {
        if (watchdog?.isActive == true) return
        watchdog = scope.launch {
            while (true) {
                delay(3000)
                val p = attached ?: continue
                val stuck = p.playWhenReady && !p.isPlaying && p.playbackState != Player.STATE_ENDED
                if (!stuck) {
                    stallSince = 0L
                    continue
                }
                if (stallSince == 0L) stallSince = System.currentTimeMillis()
                else if (System.currentTimeMillis() - stallSince > 20_000L) {
                    stallSince = 0L
                    android.util.Log.w("TubeTune", "播放卡住 20 秒，重新 prepare")
                    try {
                        if (p.playbackState == Player.STATE_IDLE) p.prepare() else p.prepare()
                        p.play()
                    } catch (t: Throwable) {
                    }
                    _lastError.value = "播放卡住，已自動重試"
                }
            }
        }
    }

    fun seekTo(ms: Long) {
        player()?.seekTo(ms)
        update()
    }

    /**
     * 下一首：依「資料夾播放順序」切到下一首歌。
     * 與 repeatMode 解耦 —— 即使是單曲重複(重複)也能手動切下一首；
     * 循環(循環)模式會在資料夾尾端回到第一首，順序(順序)播到最後則停止。
     */
    fun next() {
        val p = player() ?: return
        if (p.mediaItemCount <= 0) return
        applySkip(+1)
    }

    /**
     * 上一首：依「資料夾播放順序」切到上一首歌。
     * 與 repeatMode 解耦 —— 即使是單曲重複(重複)也能手動切上一首；
     * 循環(循環)模式會在資料夾頭端回到最後一首，順序(順序)播到第一首則重新播放。
     */
    fun prev() {
        val p = player() ?: return
        if (p.mediaItemCount <= 0) return
        applySkip(-1)
    }

    /**
     * 依資料夾播放順序 [delta](+1 下一首 / -1 上一首) 切換曲目。
     * 委派給 [skipTrack]：直接以「播放順序 index」用 seekTo(index, 0) 跳轉，
     * 不用 seekToNextMediaItem()/seekToPreviousMediaItem()（那兩支在 REPEAT_MODE_ONE 會鎖死成重複現曲）。
     */
    private fun applySkip(delta: Int) {
        val p = player() ?: return
        if (p.mediaItemCount <= 0) return
        if (!skipTrack(p, delta)) stop() // 順序模式播到資料夾最後一首 → 停止
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

    /** 回到 App 前景時呼叫：確保 service 存活、介面與播放器狀態同步 */
    fun resync() {
        val p = player()
        if (p == null) {
            PlaybackService.ensureStarted(context)
            return
        }
        if (p.mediaItemCount > 0 && _state.value == null) {
            p.currentMediaItem?.let { item ->
                _state.value = PlayerState(
                    track = item.toTrack(),
                    isPlaying = p.isPlaying,
                    positionMs = p.currentPosition.coerceAtLeast(0),
                    durationMs = p.duration.takeIf { it > 0 } ?: 0L
                )
                if (p.isPlaying) startTicker()
            }
        }
        update()
    }

    private fun retrofit(times: Int, block: (ExoPlayer) -> Unit) {
        val p = player()
        if (p != null) {
            block(p)
        } else if (times > 0) {
            scope.launch {
                delay(250)
                retrofit(times - 1, block)
            }
        }
    }

    private fun update() {
        val p = player() ?: return
        val s = _state.value ?: return
        val pos = p.currentPosition.coerceAtLeast(0)
        val dur = p.duration.takeIf { it > 0 } ?: (s.track.durationSeconds * 1000).coerceAtLeast(1L)
        // 關鍵：ExoPlayer 換咗 media item（按下一首／自動續播）之後，state 內的 track
        // 之前一直停留在「最初點播那首」，所以歌名／封面／歌詞全部唔會更新。
        // 這裡每次都以播放器目前的 mediaId 為準，由佇列取回完整 Track（連歌詞）。
        val curId = p.currentMediaItem?.mediaId
        val track = if (curId != null && curId != s.track.videoId) {
            _queue.value.firstOrNull { it.videoId == curId }
                ?: p.currentMediaItem?.toTrack()
                ?: s.track
        } else {
            s.track
        }
        _state.value = PlayerState(
            track = track,
            isPlaying = p.isPlaying,
            positionMs = pos,
            durationMs = dur
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

    /**
     * ViewModel 銷毀時釋放 UI 資源（listener、ticker）。
     * 注意：**不停止播放** — 背景播放交給 PlaybackService 繼續；
     * 之前在這裡 stop() 會把退出 App 後的背景音樂一起停掉（看起來像卡住）。
     */
    fun release() {
        ticker?.cancel()
        ticker = null
        watchdog?.cancel()
        watchdog = null
        attached?.removeListener(playerListener)
        attached = null
        scope.coroutineContext[Job]?.cancel()
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
