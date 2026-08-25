package com.tubetune.downloader

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tubetune.downloader.data.DownloadManager
import com.tubetune.downloader.data.LibraryRepository
import com.tubetune.downloader.data.MediaSaver
import com.tubetune.downloader.data.SearchResult
import com.tubetune.downloader.data.Track
import com.tubetune.downloader.data.VideoPreview
import com.tubetune.downloader.data.YoutubeService
import com.tubetune.downloader.playback.PlayerController
import com.tubetune.downloader.playback.PreviewController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.stream.StreamInfo

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val library = LibraryRepository(app)
    val downloads = DownloadManager(app, library)
    val player = PlayerController(app)
    val preview = PreviewController()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private var nextPage: Page? = null
    private var lastQuery = ""

    private val _openPlayer = MutableStateFlow(false)
    val openPlayer: StateFlow<Boolean> = _openPlayer.asStateFlow()

    fun openPlayerScreen() { _openPlayer.value = true }
    fun closePlayerScreen() { _openPlayer.value = false }

    fun search(query: String) {
        if (query.isBlank()) return
        lastQuery = query.trim()
        _searching.value = true
        _searchError.value = null
        viewModelScope.launch {
            try {
                val (results, page) = withContext(Dispatchers.IO) { YoutubeService.search(lastQuery, null) }
                nextPage = page
                _hasMore.value = page != null
                _searchResults.value = results
                if (results.isEmpty()) _searchError.value = "沒有找到結果"
            } catch (t: Throwable) {
                _searchError.value = friendly(t)
            } finally {
                _searching.value = false
            }
        }
    }

    fun loadMore() {
        val page = nextPage ?: return
        if (_searching.value) return
        _searching.value = true
        viewModelScope.launch {
            try {
                val (results, p) = withContext(Dispatchers.IO) { YoutubeService.search(lastQuery, page) }
                nextPage = p
                _hasMore.value = p != null
                _searchResults.value = _searchResults.value + results
            } catch (t: Throwable) {
                _searchError.value = friendly(t)
            } finally {
                _searching.value = false
            }
        }
    }

    fun downloadSelected(items: List<SearchResult>, folder: String) {
        downloads.enqueue(items, folder)
    }

    fun playTrack(track: Track, playlist: List<Track>) {
        player.play(track, playlist)
        openPlayerScreen()
    }

    // ---- 預覽 ----

    private val _previewBusy = MutableStateFlow<String?>(null)
    val previewBusy: StateFlow<String?> = _previewBusy.asStateFlow()

    /** 影片預覽對話框（null = 關閉） */
    private val _videoPreview = MutableStateFlow<VideoPreview?>(null)
    val videoPreview: StateFlow<VideoPreview?> = _videoPreview.asStateFlow()

    /** 解析過的 StreamInfo 快取：音訊↔影片預覽切換時不用重新解析 */
    private val infoCache = LinkedHashMap<String, StreamInfo>(16, 0.75f, true)

    private fun cachedStreamInfo(id: String): StreamInfo = synchronized(infoCache) {
        infoCache[id] ?: YoutubeService.streamInfo(id).also {
            infoCache[id] = it
            while (infoCache.size > 40) infoCache.remove(infoCache.keys.first())
        }
    }

    fun previewAudio(result: SearchResult) {
        if (_previewBusy.value != null) return
        _previewBusy.value = result.videoId
        viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) { cachedStreamInfo(result.videoId) }
                val url = withContext(Dispatchers.IO) { YoutubeService.previewAudioUrl(info) }
                if (url == null) {
                    preview.fail(result.videoId, result.title, result.uploader, result.thumbnailUrl, "找不到可預覽的音訊串流")
                } else {
                    preview.play(result.videoId, result.title, result.uploader, result.thumbnailUrl, url)
                }
            } catch (t: Throwable) {
                preview.fail(result.videoId, result.title, result.uploader, result.thumbnailUrl, friendly(t))
            } finally {
                _previewBusy.value = null
            }
        }
    }

    fun previewVideo(result: SearchResult) {
        if (_previewBusy.value != null) return
        _previewBusy.value = result.videoId
        viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) { cachedStreamInfo(result.videoId) }
                val url = withContext(Dispatchers.IO) { YoutubeService.previewVideoUrl(info) }
                if (url == null) {
                    preview.fail(result.videoId, result.title, result.uploader, result.thumbnailUrl, "找不到可預覽的影片串流")
                } else {
                    preview.stop()
                    _videoPreview.value = VideoPreview(result.videoId, result.title, result.thumbnailUrl, url)
                }
            } catch (t: Throwable) {
                preview.fail(result.videoId, result.title, result.uploader, result.thumbnailUrl, friendly(t))
            } finally {
                _previewBusy.value = null
            }
        }
    }

    fun closeVideoPreview() {
        _videoPreview.value = null
    }

    fun stopPreview() {
        preview.stop()
        closeVideoPreview()
    }

    fun deleteTrack(track: Track) {
        MediaSaver.deleteTrack(getApplication(), track.uri)
        library.removeTrack(track.videoId)
        if (player.state.value?.track?.videoId == track.videoId) player.stop()
    }

    fun handleSharedText(intent: Intent?) {
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val id = YoutubeService.videoIdOf(text)
        if (id.isBlank() || id.length != 11) return
        viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) { YoutubeService.streamInfo(id) }
                downloads.enqueueSingle(
                    id,
                    info.name ?: "YouTube 音樂",
                    info.uploaderName ?: "",
                    info.thumbnails.firstOrNull()?.url ?: "",
                    info.duration,
                    LibraryRepository.DEFAULT_FOLDER
                )
            } catch (t: Throwable) {
                _searchError.value = friendly(t)
            }
        }
    }

    fun friendly(t: Throwable): String {
        val msg = t.message ?: t.javaClass.simpleName
        return when {
            msg.contains("ContentNotAvailable") -> "影片無法播放（地區限制或已下架）"
            msg.contains("age", ignoreCase = true) -> "影片受限（年齡限制）"
            msg.contains("Found no streams") -> "找不到音訊串流"
            msg.contains("Sign in to confirm") -> "YouTube 要求驗證，請稍後再試"
            else -> msg.take(140)
        }
    }
}
