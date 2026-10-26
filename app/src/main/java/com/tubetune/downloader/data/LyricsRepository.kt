package com.tubetune.downloader.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 歌詞來源：**真實歌詞庫優先**，AI 只做使用者明確按下嘅後備。
 *
 *   1. LRCLIB（lrclib.net，開放歌詞庫；歐美歌最齊，有逐句時間軸）
 *   2. 酷狗歌詞庫（krcs.kugou.com；華語／粵語歌最齊）
 *   3. DeepSeek AI —— 只有使用者按「AI 推測」才會用，UI 一定標明「可能不準」
 *
 * 歌詞存入 Track.lyrics + Track.lyricsSource，跟音樂庫一齊備份。
 */
class LyricsRepository(private val context: Context) {

    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val ua = "TubeTune/1.4 (Android; +https://github.com/s1215690/YupiMusicDownload)"

    /** 一首歌詞 + 來源標籤（顯示給用戶看） */
    data class Lyric(val text: String, val source: String)

    // ---------------- 對外 API ----------------

    /** 由真歌詞庫搵：LRCLIB → 酷狗。搵唔到就失敗（唔會作） */
    suspend fun fetchOnline(track: Track): Result<Lyric> = withContext(Dispatchers.IO) {
        val title = cleanTitle(track.title)
        val artist = cleanArtist(track.artist)

        runCatching { lrclib(title, artist, track.durationSeconds) }.getOrNull()?.let {
            return@withContext Result.success(it)
        }
        runCatching { kugou(title, artist) }.getOrNull()?.let {
            return@withContext Result.success(it)
        }
        Result.failure(IllegalStateException("兩個歌詞庫都搵唔到（可試「AI 推測」，但可能不準）"))
    }

    /** AI 後備：DeepSeek 按歌名推測（UI 必須標明可能不準） */
    suspend fun fetchAi(track: Track): Result<Lyric> = withContext(Dispatchers.IO) {
        val key = Prefs.deepSeekKey(context).trim()
        if (key.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("未設定 DeepSeek API Key（設定 → AI 歌詞）")
            )
        }
        val userPrompt = buildString {
            append("歌曲：").append(track.title).append('\n')
            append("歌手：").append(track.artist).append('\n')
            append("請提供這首歌的歌詞。")
        }
        val body = JsonObject().apply {
            addProperty("model", Prefs.deepSeekModel(context))
            addProperty("temperature", 0.2)
            addProperty("max_tokens", 2400)
            addProperty("stream", false)
            add("messages", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "system")
                    addProperty("content", SYSTEM_PROMPT)
                })
                add(JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", userPrompt)
                })
            })
        }
        val req = Request.Builder()
            .url(ENDPOINT)
            .addHeader("Authorization", "Bearer " + key)
            .addHeader("Content-Type", "application/json")
            .post(gson.toJson(body).toRequestBody("application/json".toMediaType()))
            .build()
        try {
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = when (resp.code) {
                        401 -> "API Key 無效或已撤銷"
                        402 -> "DeepSeek 餘額不足，請充值後再試"
                        429 -> "請求太頻繁，請等一陣再試"
                        else -> "DeepSeek 回應 " + resp.code + "：" + raw.take(140)
                    }
                    return@withContext Result.failure(IllegalStateException(msg))
                }
                val content = runCatching {
                    gson.fromJson(raw, JsonObject::class.java).getAsJsonArray("choices")
                        .firstOrNull()?.asJsonObject
                        ?.getAsJsonObject("message")?.get("content")?.asString
                }.getOrNull()
                if (content.isNullOrBlank()) {
                    Result.failure(IllegalStateException("DeepSeek 沒有回傳內容"))
                } else {
                    Result.success(Lyric(stripFence(content), "AI 推測（可能不準）"))
                }
            }
        } catch (t: Throwable) {
            Result.failure(IllegalStateException("連線失敗：" + (t.message ?: t.javaClass.simpleName)))
        }
    }

    fun hasKey(): Boolean = Prefs.deepSeekKey(context).isNotBlank()

    companion object {
        private const val ENDPOINT = "https://api.deepseek.com/chat/completions"
        private const val SYSTEM_PROMPT =
            "你係歌詞整理助手。你只輸出歌詞正文：每行一句，段落之間留一個空行，用繁體中文。" +
                "如果唔確定係邊一首歌，第一行寫「AI 推測（可能不準確）」；完全查唔到就只輸出「找不到歌詞」。"
    }

    // ---------------- LRCLIB ----------------

    private fun lrclib(title: String, artist: String, durationSec: Long): Lyric? {
        if (title.isBlank()) return null
        val query = if (artist.isNotBlank()) "$title $artist" else title
        val body = httpGet("https://lrclib.net/api/search?q=" + enc(query), retries = 2) ?: return null
        val arr = runCatching { gson.fromJson(body, JsonArray::class.java) }.getOrNull() ?: return null
        if (arr.size() == 0) return null

        var best: JsonObject? = null
        var bestScore = -1.0
        for (el in arr) {
            val o = el as? JsonObject ?: continue
            val t = o.get("trackName")?.asString.orEmpty()
            val a = o.get("artistName")?.asString.orEmpty()
            val dur = o.get("duration")?.asDouble ?: 0.0
            var score = similarity(title, t) * 3
            if (artist.isNotBlank() && similarity(artist, a) > 0.45) score += 2
            if (durationSec > 5 && dur > 5) {
                val diff = kotlin.math.abs(dur - durationSec)
                score += when {
                    diff <= 5 -> 2.0
                    diff <= 15 -> 0.5
                    else -> -2.0
                }
            }
            if (o.get("instrumental")?.asBoolean == true) score -= 3
            if (o.get("plainLyrics").orEmptyText().isBlank() &&
                o.get("syncedLyrics").orEmptyText().isBlank()
            ) score -= 3
            if (score > bestScore) {
                bestScore = score
                best = o
            }
        }
        val o = best ?: return null
        if (bestScore < 2.0) return null
        val plain = o.get("plainLyrics").orEmptyText()
        val synced = o.get("syncedLyrics").orEmptyText()
        val text = if (plain.isNotBlank()) plain else lrcToPlain(synced)
        if (text.isBlank()) return null
        return Lyric(toTraditional(text), "LRCLIB")
    }

    // ---------------- 酷狗歌詞 ----------------

    private fun kugou(title: String, artist: String): Lyric? {
        if (title.isBlank()) return null
        val keyword = if (artist.isNotBlank()) "$title $artist" else title
        val body = httpGet("https://krcs.kugou.com/search?ver=1&man=yes&client=mobi&keyword=" + enc(keyword))
            ?: return null
        val root = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull() ?: return null
        val cands = root.getAsJsonArray("candidates") ?: return null
        if (cands.size() == 0) return null

        var best: JsonObject? = null
        var bestScore = -1.0
        for (el in cands) {
            val o = el as? JsonObject ?: continue
            val song = o.get("song")?.asString.orEmpty()
            val singer = o.get("singer")?.asString.orEmpty()
            var score = similarity(title, song) * 3
            if (artist.isNotBlank() && similarity(artist, singer) > 0.4) score += 2
            if (o.get("krctype")?.asInt == 2) score += 0.5 // 官方推薦歌詞
            if (score > bestScore) {
                bestScore = score
                best = o
            }
        }
        val cand = best ?: return null
        if (bestScore < 2.0) return null
        val id = cand.get("id")?.asString ?: return null
        val accesskey = cand.get("accesskey")?.asString ?: return null
        val dlUrl = "https://lyrics.kugou.com/download?ver=1&client=pc&id=" + enc(id) +
            "&accesskey=" + enc(accesskey) + "&fmt=lrc&charset=utf8"
        val dl = httpGet(dlUrl) ?: return null
        val dlJson = runCatching { gson.fromJson(dl, JsonObject::class.java) }.getOrNull() ?: return null
        val content = dlJson.get("content")?.asString ?: return null
        val lrc = runCatching {
            String(android.util.Base64.decode(content, android.util.Base64.DEFAULT), Charsets.UTF_8)
        }.getOrNull() ?: return null
        val text = lrcToPlain(lrc)
        if (text.isBlank()) return null
        return Lyric(toTraditional(text), "酷狗歌詞")
    }

    // ---------------- 工具 ----------------

    private fun httpGet(url: String, retries: Int = 1): String? {
        var attempt = 0
        while (attempt <= retries) {
            attempt++
            try {
                val req = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", ua)
                    .addHeader("Accept", "application/json")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) return resp.body?.string()
                    // 503／429 = 對方繁忙，等一陣再試；其他錯誤直接失敗
                    if (resp.code != 503 && resp.code != 429) return null
                }
            } catch (t: Throwable) {
                if (attempt > retries) return null
            }
            try {
                Thread.sleep(900L * attempt)
            } catch (e: InterruptedException) {
            }
        }
        return null
    }

    /** LRC（[mm:ss.xx] 歌詞）→ 純文字；順手去掉「詞：」「曲：」等製作名單 */
    private fun lrcToPlain(lrc: String): String {
        if (lrc.isBlank()) return ""
        val lines = lrc.lines().map { line ->
            line.replace(Regex("^\\s*(\\[[^\\]]*\\]\\s*)+"), "").trim()
        }.filter { it.isNotEmpty() && !it.startsWith("[") }
            .filterNot { it.matches(Regex("^[\\p{Punct}\\s]*$")) }
        val credits = Regex("^(作詞|作曲|編曲|監製|製作人|製作|詞|曲|編|OP|SP|混音|母帶|吉他|貝斯|鼓|和音|發行|出品|錄音)[：:]")
        val kept = lines.filterNot { credits.containsMatchIn(it) }
        return (if (kept.isEmpty()) lines else kept).joinToString("\n").trim()
    }

    /** 簡體 → 繁體（Android 內建 ICU），失敗就原樣返回 */
    private fun toTraditional(text: String): String = runCatching {
        android.icu.text.Transliterator.getInstance("Simplified-Traditional").transliterate(text)
    }.getOrDefault(text)

    private fun stripFence(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("```")) {
            s = s.removePrefix("```")
            val nl = s.indexOf('\n')
            if (nl >= 0) s = s.substring(nl + 1)
            s = s.removeSuffix("```").trim()
        }
        return s
    }

    /** 由 YouTube 標題抽出歌名：「張敬軒《放棄的界限》[Official MV]」→「放棄的界限」 */
    private fun cleanTitle(raw: String): String {
        val s0 = raw.trim()
        Regex("《([^》]{1,40})》").find(s0)?.let { return it.groupValues[1].trim() }
        Regex("[「『]([^」』]{1,40})[」』]").find(s0)?.let { return it.groupValues[1].trim() }
        var s = s0.replace(Regex("[\\(\\[（【][^\\)\\]）】]*[\\)\\]）】]"), " ")
        val noise = listOf(
            "Official Music Video", "Official MV", "Official Video", "Official Audio", "Official",
            "Music Video", "M/V", "MV", "高音質", "動態歌詞", "純音樂", "歌詞版", "完整版", "現場版",
            "Lyrics Video", "Lyrics", "Audio", "4K", "HD", "Live", "Chinese Ver", "Remastered"
        )
        noise.forEach { s = s.replace(it, " ", ignoreCase = true) }
        s = s.replace(Regex("(?i)\\b(feat|ft)\\.?\\s+.*$"), " ")
        if (s.contains(" - ")) s = s.substringAfterLast(" - ")
        s = s.substringBefore("|")
        return s.replace(Regex("\\s+"), " ").trim().trim('-', '–', '—').trim()
    }

    private fun cleanArtist(raw: String): String {
        val s = raw.trim()
        return if (s.isBlank() || s == "未知歌手" || s == "<unknown>") "" else s
    }

    /** 粗略字串相似度（0..1），用於揀最匹配的搜尋結果 */
    private fun similarity(a: String, b: String): Double {
        val x = norm(a)
        val y = norm(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        if (x == y) return 1.0
        if (x.contains(y) || y.contains(x)) return 0.75
        val setX = x.toSet()
        val setY = y.toSet()
        val inter = setX.intersect(setY).size.toDouble()
        val union = setX.union(setY).size.toDouble()
        return if (union == 0.0) 0.0 else inter / union
    }

    private fun norm(s: String): String =
        s.lowercase().replace(Regex("[\\s\\p{Punct}，。！？、：；「」『』（）《》]+"), "")

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun JsonElement?.orEmptyText(): String =
        if (this == null || this.isJsonNull) "" else runCatching { this.asString }.getOrDefault("")
}
