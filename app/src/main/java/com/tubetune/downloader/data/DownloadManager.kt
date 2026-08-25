package com.tubetune.downloader.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class DownloadManager(
    private val context: Context,
    private val library: LibraryRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 下載用 OkHttp：
     * - 強制 HTTP/1.1：YouTube 的限速是「每條連線」計的，HTTP/2 會把所有
     *   分段請求塞進同一條 TCP 連線而一起吃限速；HTTP/1.1 每段獨立連線，
     *   每條各拿一份頻寬。yt-dlp 的 -N 也是這個原理。
     * - 提高同主機並行數到 16（預設只有 5，會卡住 8 路分段）。
     */
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
        .build()
        .newBuilder()
        .dispatcher(okhttp3.Dispatcher().apply {
            maxRequests = 16
            maxRequestsPerHost = 16
        })
        .build()

    /** 進行中的 HTTP 呼叫（一個任務可能有多條分段連線），供取消使用 */
    private val calls = ConcurrentHashMap<String, CopyOnWriteArrayList<Call>>()

    private val _items = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()

    private val _activeCount = MutableStateFlow(0)
    val activeCount: StateFlow<Int> = _activeCount.asStateFlow()

    private var busy = false

    fun enqueue(results: List<SearchResult>, folder: String) {
        if (results.isEmpty()) return
        val targetFolder = folder.ifBlank { LibraryRepository.DEFAULT_FOLDER }
        val newItems = results.map { r ->
            DownloadItem(
                id = UUID.randomUUID().toString(),
                videoId = r.videoId,
                title = r.title,
                uploader = r.uploader,
                thumbnailUrl = r.thumbnailUrl,
                durationSeconds = r.durationSeconds,
                folder = targetFolder
            )
        }
        _items.value = newItems + _items.value
        pump()
    }

    fun enqueueSingle(
        videoId: String,
        title: String,
        uploader: String,
        thumbnail: String,
        duration: Long,
        folder: String
    ) {
        enqueue(listOf(SearchResult(videoId, title, uploader, duration, thumbnail)), folder)
    }

    private fun pump() {
        if (busy) return
        val next = _items.value.lastOrNull { it.status == DownloadStatus.QUEUED }
        if (next == null) {
            _activeCount.value = 0
            return
        }
        busy = true
        _activeCount.value = 1
        scope.launch {
            process(next)
            busy = false
            pump()
        }
    }

    private fun update(item: DownloadItem) {
        _items.value = _items.value.map { if (it.id == item.id) item else it }
    }

    private fun isCancelled(id: String): Boolean =
        _items.value.firstOrNull { it.id == id }?.status == DownloadStatus.CANCELLED

    /** 取消下載任務：進行中的立即中斷，等待中的直接取消。 */
    fun cancel(id: String) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        when (item.status) {
            DownloadStatus.QUEUED,
            DownloadStatus.RESOLVING,
            DownloadStatus.DOWNLOADING,
            DownloadStatus.SAVING -> {
                update(item.copy(status = DownloadStatus.CANCELLED))
                calls.remove(id)?.forEach { it.cancel() }
            }
            else -> {}
        }
    }

    private suspend fun process(item: DownloadItem) {
        val tmp = File(context.cacheDir, "download_" + item.id + ".tmp")
        try {
            if (isCancelled(item.id)) return
            update(item.copy(status = DownloadStatus.RESOLVING))
            val info = withContext(Dispatchers.IO) { YoutubeService.streamInfo(item.videoId) }
            if (isCancelled(item.id)) return
            val audio = YoutubeService.pickAudio(info, Prefs.quality(context) == "aac")
                ?: throw IllegalStateException("找不到可下載的音訊串流")
            val suffix = audio.format?.suffix.orEmpty().ifBlank { "m4a" }
            val mime = audio.format?.mimeType.orEmpty().ifBlank { "audio/mp4" }
            val url = audio.content ?: throw IllegalStateException("音訊串流沒有網址")

            update(item.copy(status = DownloadStatus.DOWNLOADING, progress = 0))
            val total = probeSize(url)
            if (total >= MIN_PARALLEL_SIZE) {
                downloadParallel(url, tmp, total, item)
            } else {
                downloadSequential(url, tmp, item)
            }
            if (isCancelled(item.id)) {
                tmp.delete()
                return
            }
            if (tmp.length() == 0L) throw IllegalStateException("下載的檔案是空的")

            update(item.copy(status = DownloadStatus.SAVING, progress = 100))
            val sizeBytes = tmp.length()
            val savedUri = withContext(Dispatchers.IO) {
                MediaSaver.saveAudio(
                    context, tmp, item.folder, item.title, item.uploader,
                    item.durationSeconds * 1000, mime, suffix
                )
            }
            tmp.delete()
            library.addTrack(
                Track(
                    videoId = item.videoId,
                    title = item.title,
                    artist = item.uploader,
                    durationSeconds = item.durationSeconds,
                    thumbnailUrl = item.thumbnailUrl,
                    fileName = MediaSaver.sanitize(item.title) + "." + suffix,
                    uri = savedUri,
                    folder = item.folder.ifBlank { LibraryRepository.DEFAULT_FOLDER },
                    sizeBytes = sizeBytes,
                    downloadedAt = System.currentTimeMillis()
                )
            )
            update(item.copy(status = DownloadStatus.DONE, progress = 100))
        } catch (t: Throwable) {
            if (isCancelled(item.id)) {
                tmp.delete()
                return
            }
            update(item.copy(status = DownloadStatus.FAILED, error = friendlyError(t)))
        }
    }

    fun clearFinished() {
        _items.value = _items.value.filter {
            it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.RESOLVING ||
                it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.SAVING
        }
    }

    // ---- 下載核心：小檔案單連線、大檔案 Range 分段並行（YouTube 單連線會限速） ----

    /** 探測檔案大小：HEAD 有時不給 Content-Length，改用 Range 0-0 讀 Content-Range */
    private fun probeSize(url: String): Long = try {
        val req = Request.Builder().url(url).get()
            .header("User-Agent", UA)
            .header("Accept", "*/*")
            .header("Accept-Encoding", "identity")
            .header("Range", "bytes=0-0")
            .build()
        client.newCall(req).execute().use { r ->
            if (r.code == 206) {
                val cr = r.header("Content-Range")
                if (cr != null && cr.contains("/")) {
                    cr.substringAfterLast("/").trim().toLongOrNull() ?: -1L
                } else r.body?.contentLength()?.takeIf { it > 0 } ?: -1L
            } else -1L
        }
    } catch (t: Throwable) {
        -1L
    }

    /** 單連線下載（fallback / 小檔案） */
    private fun downloadSequential(url: String, tmp: File, item: DownloadItem) {
        val request = Request.Builder().url(url)
            .header("User-Agent", UA)
            .header("Accept", "*/*")
            .header("Accept-Encoding", "identity")
            .build()
        val call = client.newCall(request)
        registerCall(item.id, call)
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("下載失敗 HTTP " + resp.code)
                val body = resp.body ?: throw IllegalStateException("空的回應")
                val total = body.contentLength()
                var read = 0L
                tmp.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(128 * 1024)
                        var n = input.read(buf)
                        while (n > 0) {
                            if (isCancelled(item.id)) break
                            out.write(buf, 0, n)
                            read += n
                            publishProgress(item, read, total)
                            n = input.read(buf)
                        }
                    }
                }
            }
        } finally {
            unregisterCall(item.id, call)
        }
    }

    /** 分段並行下載：[0, CHUNK-1]、[CHUNK, 2*CHUNK-1]… 多條連線同時抓不同區段 */
    private suspend fun downloadParallel(url: String, tmp: File, total: Long, item: DownloadItem) {
        val parts = minOf(MAX_PARTS, maxOf(1L, (total + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt())
        val cursor = AtomicLong(0L)
        val downloaded = AtomicLong(0L)
        RandomAccessFile(tmp, "rw").use { raf ->
            raf.setLength(total)
            coroutineScope {
                (0 until parts).map { _ ->
                    async(Dispatchers.IO) {
                        while (true) {
                            if (isCancelled(item.id)) return@async
                            val start = cursor.getAndAdd(CHUNK_SIZE)
                            if (start >= total) return@async
                            val end = minOf(start + CHUNK_SIZE - 1, total - 1)
                            val req = Request.Builder().url(url)
                                .header("User-Agent", UA)
                                .header("Accept", "*/*")
                                .header("Accept-Encoding", "identity")
                                .header("Range", "bytes=$start-$end")
                                .build()
                            val call = client.newCall(req)
                            registerCall(item.id, call)
                            try {
                                call.execute().use { resp ->
                                    if (resp.code != 206) throw IllegalStateException("下載失敗 HTTP " + resp.code)
                                    val body = resp.body ?: throw IllegalStateException("空的回應")
                                    var written = 0L
                                    body.byteStream().use { input ->
                                        val buf = ByteArray(128 * 1024)
                                        var n = input.read(buf)
                                        while (n > 0) {
                                            if (isCancelled(item.id)) break
                                            synchronized(raf) {
                                                raf.seek(start + written)
                                                raf.write(buf, 0, n)
                                            }
                                            written += n
                                            n = input.read(buf)
                                        }
                                    }
                                    downloaded.addAndGet(written)
                                    publishProgress(item, downloaded.get(), total)
                                }
                            } finally {
                                unregisterCall(item.id, call)
                            }
                        }
                    }
                }.awaitAll()
            }
        }
    }

    private fun registerCall(id: String, call: Call) {
        calls.computeIfAbsent(id) { CopyOnWriteArrayList() }.add(call)
    }

    private fun unregisterCall(id: String, call: Call) {
        calls[id]?.remove(call)
    }

    private fun publishProgress(item: DownloadItem, read: Long, total: Long) {
        val pct = if (total > 0) ((read * 100) / total).toInt() else -1
        update(item.copy(status = DownloadStatus.DOWNLOADING, progress = if (pct in 0..100) pct else 0))
    }

    fun removeItem(id: String) {
        _items.value = _items.value.filter { it.id != id }
    }

    private fun friendlyError(t: Throwable): String {
        val msg = t.message ?: t.javaClass.simpleName
        return when {
            msg.contains("ContentNotAvailable") -> "影片無法播放（地區限制或已下架）"
            msg.contains("age", ignoreCase = true) -> "影片受限（年齡限制）"
            msg.contains("HTTP 403") || msg.contains("HTTP 429") -> "YouTube 暫時拒絕連線，請稍後再試"
            msg.contains("找不到可下載") -> msg
            msg.contains("HTTP") -> msg
            else -> msg.take(120)
        }
    }

    companion object {
        const val UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        /** 分段大小：1MB（越細越能讓 8 路都吃到小檔案） */
        private const val CHUNK_SIZE = 1L * 1024 * 1024

        /** 並行連線數上限（YouTube 每連線限速，多連線各拿一份頻寬） */
        private const val MAX_PARTS = 8

        /** 檔案至少多大才用分段並行（更小的檔案單連線即可） */
        private const val MIN_PARALLEL_SIZE = 1L * 1024 * 1024
    }
}
