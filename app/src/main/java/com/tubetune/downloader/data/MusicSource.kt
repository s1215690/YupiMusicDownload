package com.tubetune.downloader.data

/**
 * 音樂來源。NewPipeExtractor 內建支援多個站：
 * YouTube、SoundCloud、Bandcamp、PeerTube、media.ccc.de。
 * 這裡先開 YouTube 同 SoundCloud（音樂量最實用）。
 */
enum class MusicSource(val label: String) {
    YOUTUBE("YouTube"),

    /** SoundCloud：搜尋可用，但音源經常被 401 擋（新版 API 要 auth），先留給進階用戶 */
    SOUNDCLOUD("SoundCloud"),

    /** Bandcamp：獨立音樂，音源係直接 MP3，下載最穩定 */
    BANDCAMP("Bandcamp")
}
