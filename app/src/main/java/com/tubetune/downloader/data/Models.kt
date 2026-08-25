package com.tubetune.downloader.data

data class Track(
    val videoId: String,
    val title: String,
    val artist: String,
    val durationSeconds: Long,
    val thumbnailUrl: String,
    val fileName: String,
    val uri: String,          // 空字串 = 尚未下載（純串流曲目）
    val folder: String,
    val sizeBytes: Long,
    val downloadedAt: Long,
    val downloaded: Boolean = false
)

data class Folder(val name: String, val createdAt: Long)

data class SearchResult(
    val videoId: String,
    val title: String,
    val uploader: String,
    val durationSeconds: Long,
    val thumbnailUrl: String
)

data class DownloadItem(
    val id: String,
    val videoId: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val folder: String,
    var status: DownloadStatus = DownloadStatus.QUEUED,
    var progress: Int = 0,
    var error: String? = null
)

enum class DownloadStatus { QUEUED, RESOLVING, DOWNLOADING, SAVING, DONE, FAILED, CANCELLED }

data class LibraryData(val tracks: List<Track>, val folders: List<Folder>)

data class PlayerState(
    val track: Track,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long
)

/** 播放模式：順序 / 循環 / 隨機 / 單曲重複（每個資料夾各自記憶） */
enum class PlayMode { NORMAL, REPEAT_ALL, SHUFFLE, REPEAT_ONE }

/** 播放來源：下載的本地檔案 / YouTube 串流（每個資料夾各自記憶） */
enum class PlaySource { DOWNLOAD, STREAM }

data class PreviewState(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val error: String? = null
)

data class VideoPreview(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val url: String
)
