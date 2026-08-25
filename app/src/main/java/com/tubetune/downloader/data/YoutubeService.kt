package com.tubetune.downloader.data

import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

object YoutubeService {

    fun search(query: String, page: Page?): Pair<List<SearchResult>, Page?> {
        val service = ServiceList.YouTube
        val handler = service.searchQHFactory.fromQuery(query)
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
                    videoId = videoIdOf(si.url),
                    title = si.name.orEmpty(),
                    uploader = si.uploaderName.orEmpty(),
                    durationSeconds = si.duration,
                    thumbnailUrl = si.thumbnails.firstOrNull()?.url.orEmpty()
                )
            }
        }
        return results to next
    }

    fun streamInfo(videoId: String): StreamInfo =
        StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=" + videoId)

    fun pickAudio(info: StreamInfo, preferAac: Boolean): AudioStream? {
        val audios = info.audioStreams.filter { it.bitrate > 0 }
        if (audios.isEmpty()) return null
        val aac = audios.filter { it.format?.suffix == "m4a" }
        return if (preferAac && aac.isNotEmpty()) aac.maxByOrNull { it.bitrate }
        else audios.maxByOrNull { it.bitrate }
    }

    /** 預覽用音訊：優先 m4a（MediaPlayer 支援度佳），否則取最高 bitrate。 */
    fun previewAudioUrl(info: StreamInfo): String? {
        val audios = info.audioStreams.filter { it.bitrate > 0 }
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
