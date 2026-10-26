package com.tubetune.downloader.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * AI 歌詞：用 DeepSeek 的 chat completions，按「歌名 + 歌手」產生歌詞。
 *
 * - API Key 由使用者在「設定 → AI 歌詞」自己貼上，只存喺本機 SharedPreferences
 *   （**唔會**寫入 Music/ 的索引鏡像，避免其他 App 讀得到）
 * - 產生結果由 LibraryRepository 寫入 Track.lyrics，跟圖書館索引一齊鏡像到 Music/TubeTune/，
 *   所以重裝 App 之後歌詞仲喺度
 * - AI 生成嘅歌詞可能同原曲有出入，UI 要標明
 */
class LyricsRepository(private val context: Context) {

    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(150, TimeUnit.SECONDS) // 生成整首歌詞要啲時間
        .build()

    companion object {
        private const val ENDPOINT = "https://api.deepseek.com/chat/completions"

        private const val SYSTEM_PROMPT =
            "你係歌詞整理助手。你只輸出歌詞正文：每行一句，段落之間留一個空行，用繁體中文。" +
                "如果唔確定係邊一首歌，第一行寫「AI 推測（可能不準確）」，之後輸出你最有把握嘅版本；" +
                "如果完全查唔到，只輸出「找不到歌詞」。"
    }

    fun hasKey(): Boolean = Prefs.deepSeekKey(context).isNotBlank()

    /** 呼叫 DeepSeek 取得歌詞（純文字） */
    suspend fun fetch(track: Track): Result<String> = withContext(Dispatchers.IO) {
        val key = Prefs.deepSeekKey(context).trim()
        if (key.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("未設定 DeepSeek API Key，請到「設定 → AI 歌詞」貼上")
            )
        }

        val userPrompt = buildString {
            append("歌曲：").append(track.title).append('\n')
            append("歌手：").append(track.artist).append('\n')
            if (track.folder.isNotBlank()) append("（資料夾：").append(track.folder).append("）\n")
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

        val request = Request.Builder()
            .url(ENDPOINT)
            .addHeader("Authorization", "Bearer " + key)
            .addHeader("Content-Type", "application/json")
            .post(gson.toJson(body).toRequestBody("application/json".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = when (resp.code) {
                        401 -> "API Key 無效或已撤銷"
                        402 -> "DeepSeek 餘額不足，請充值後再試"
                        422 -> "請求參數有問題：" + raw.take(120)
                        429 -> "請求太頻繁，請等一陣再試"
                        else -> "DeepSeek 回應 " + resp.code + "：" + raw.take(160)
                    }
                    return@withContext Result.failure(IllegalStateException(msg))
                }
                val json = runCatching { gson.fromJson(raw, JsonObject::class.java) }.getOrNull()
                val content = json?.getAsJsonArray("choices")
                    ?.firstOrNull()?.asJsonObject
                    ?.getAsJsonObject("message")
                    ?.get("content")?.asString
                if (content.isNullOrBlank()) {
                    return@withContext Result.failure(IllegalStateException("DeepSeek 沒有回傳內容"))
                }
                val text = clean(content)
                if (text.isBlank()) {
                    Result.failure(IllegalStateException("DeepSeek 回傳空白內容"))
                } else {
                    Result.success(text)
                }
            }
        } catch (t: Throwable) {
            Result.failure(
                IllegalStateException("連線失敗：" + (t.message ?: t.javaClass.simpleName))
            )
        }
    }

    /** 去掉 markdown 圍欄、開頭的「歌詞：」之類，保留正文 */
    private fun clean(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("```")) {
            s = s.removePrefix("```")
            val nl = s.indexOf('\n')
            if (nl >= 0) s = s.substring(nl + 1)
            s = s.removeSuffix("```").trim()
        }
        val lines = s.lines().toMutableList()
        while (lines.isNotEmpty()) {
            val head = lines.first().trim()
            if (head.isEmpty() && lines.size > 1) lines.removeAt(0)
            else if (head.startsWith("歌詞") && head.length <= 12) lines.removeAt(0)
            else break
        }
        return lines.joinToString("\n").trim()
    }
}
