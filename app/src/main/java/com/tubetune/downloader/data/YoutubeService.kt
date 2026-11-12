package com.tubetune.downloader.data

import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

object YoutubeService {

    /** SoundCloud 曲目用前綴存 URL（videoId 欄位通用）："sc:https://soundcloud.com/..." */
    const val SC_PREFIX = "sc:"
    const val BC_PREFIX = "bc:"

    fun isSoundCloud(videoId: String): Boolean = videoId.startsWith(SC_PREFIX)

    fun isBandcamp(videoId: String): Boolean = videoId.startsWith(BC_PREFIX)

    /** 由存起來的 id 砌返原本網頁連結（備份／複製連結用） */
    fun webUrlOf(videoId: String): String = when {
        videoId.isBlank() -> ""
        isSoundCloud(videoId) -> videoId.removePrefix(SC_PREFIX)
        isBandcamp(videoId) -> videoId.removePrefix(BC_PREFIX)
        else -> "https://www.youtube.com/watch?v=" + videoId
    }

    private fun serviceOf(source: MusicSource) = when (source) {
        MusicSource.YOUTUBE -> ServiceList.YouTube
        MusicSource.SOUNDCLOUD -> ServiceList.SoundCloud
        MusicSource.BANDCAMP -> ServiceList.Bandcamp
    }

    fun search(
        query: String,
        page: Page?,
        source: MusicSource = MusicSource.YOUTUBE
    ): Pair<List<SearchResult>, Page?> {
        val service = serviceOf(source)
        // Bandcamp 預設搜尋會回「專輯」(AlbumInfoItem)，我們只要單曲 → 指定 tracks 過濾
        val handler = if (source == MusicSource.BANDCAMP)
            service.searchQHFactory.fromQuery(query, listOf("tracks"), "")
        else service.searchQHFactory.fromQuery(query)
        val items: List<InfoItem>
        val next: Page?
        if (page == null) {
            val info = SearchInfo.getInfo(service, handler)
            items = info.relatedItems
            next = info.nextPage
        } else {
            val p = SearchInfo.getMoreItems(service, handler, page)
            items = p.items
            next = p.nextPage
        }
        val results = items.mapNotNull { item ->
            (item as? StreamInfoItem)?.let { si ->
                SearchResult(
                    // SoundCloud 直接用完整網址當 id（它的 id 規則同 YouTube 唔同）
                    videoId = when (source) {
                        MusicSource.SOUNDCLOUD -> SC_PREFIX + si.url
                        MusicSource.BANDCAMP -> BC_PREFIX + si.url
                        MusicSource.YOUTUBE -> videoIdOf(si.url)
                    },
                    title = si.name.orEmpty(),
                    uploader = si.uploaderName.orEmpty(),
                    durationSeconds = si.duration,
                    thumbnailUrl = si.thumbnails.firstOrNull()?.url.orEmpty()
                )
            }
        }
        return results to next
    }

    fun streamInfo(videoId: String): StreamInfo = when {
        isSoundCloud(videoId) ->
            StreamInfo.getInfo(ServiceList.SoundCloud, videoId.removePrefix(SC_PREFIX))
        isBandcamp(videoId) ->
            StreamInfo.getInfo(ServiceList.Bandcamp, videoId.removePrefix(BC_PREFIX))
        else -> StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=" + videoId)
    }

    fun pickAudio(info: StreamInfo, preferAac: Boolean): AudioStream? {
        // 注意：唔可以只收 bitrate > 0 —— SoundCloud 回傳的 m4a 串流 bitrate 係 0，
        // 舊版過濾會令佢變成「找不到可下載的音訊串流」。
        val audios = info.audioStreams.filter { it.isUrl }
        if (audios.isEmpty()) return null
        val aac = audios.filter { it.format?.suffix == "m4a" }
        return if (preferAac && aac.isNotEmpty()) aac.maxByOrNull { it.bitrate }
        else audios.maxByOrNull { it.bitrate }
    }

    /** 預覽用音訊：優先 m4a（MediaPlayer 支援度佳），否則取最高 bitrate。 */
    fun previewAudioUrl(info: StreamInfo): String? {
        val audios = info.audioStreams.filter { it.isUrl }
        if (audios.isEmpty()) return null
        val aac = audios.filter { it.format?.suffix == "m4a" }
        val picked = (aac.ifEmpty { audios }).maxByOrNull { it.bitrate } ?: return null
        return previewUrlOf(picked)
    }

    /** 預覽用影片：取解析度最低的 mp4（含音訊）串流，預覽較省流量。 */
    fun previewVideoUrl(info: StreamInfo): String? {
        val muxed = info.videoStreams.filter { !it.isVideoOnly && it.format?.suffix == "mp4" }
        if (muxed.isEmpty()) return null
        return previewUrlOf(muxed.minByOrNull { it.height.coerceAtLeast(0) } ?: return null)
    }

    private fun previewUrlOf(stream: org.schabi.newpipe.extractor.stream.Stream): String? =
        stream.content?.takeIf { it.isNotBlank() } ?: stream.url

    fun videoIdOf(url: String): String {
        val m1 = Regex("(?:v=|youtu\\.be/|shorts/|live/)([A-Za-z0-9_-]{11})").find(url)
        if (m1 != null) return m1.groupValues[1]
        val m2 = Regex("^([A-Za-z0-9_-]{11})\$").find(url.trim())
        if (m2 != null) return m2.groupValues[1]
        return url
    }
}
