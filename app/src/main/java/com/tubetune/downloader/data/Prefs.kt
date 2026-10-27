package com.tubetune.downloader.data

import android.content.Context

object Prefs {
    private const val NAME = "tubetune"

    fun quality(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("quality", "aac") ?: "aac"

    fun setQuality(context: Context, value: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putString("quality", value).apply()
    }

    /** 下載格式：mp3（轉檔，通用）/ m4a（原始 AAC） */
    fun format(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("format", "mp3") ?: "mp3"

    fun setFormat(context: Context, value: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putString("format", value).apply()
    }

    /** MP3 位元率（kbps）：128 / 192 / 320 */
    fun mp3Bitrate(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt("mp3_bitrate", 192)

    fun setMp3Bitrate(context: Context, value: Int) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putInt("mp3_bitrate", value).apply()
    }

    /** 有無自動問過「音樂和音訊」權限（避免每次開 App 都彈） */
    fun askedAudioPerm(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("asked_audio", false)

    fun setAskedAudioPerm(context: Context, value: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putBoolean("asked_audio", value).apply()
    }

    /** 錄音音量（dB）：0 / 6 / 12 / 18，預設 +12（藍牙外放時用麥克風收環境聲，通常需要放大） */
    fun ktvGainDb(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt("ktv_gain_db", 12)

    fun setKtvGainDb(context: Context, db: Int) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putInt("ktv_gain_db", db.coerceIn(0, 30)).apply()
    }

    /** DeepSeek API Key（只存本機，不會上傳） */
    fun deepSeekKey(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("deepseek_key", "") ?: ""

    fun setDeepSeekKey(context: Context, value: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("deepseek_key", value.trim()).apply()
    }

    fun deepSeekModel(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString("deepseek_model", "deepseek-chat") ?: "deepseek-chat"

    fun setDeepSeekModel(context: Context, value: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("deepseek_model", value.trim()).apply()
    }

    fun theme(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("theme", "system") ?: "system"

    fun setTheme(context: Context, value: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putString("theme", value).apply()
    }

    /** 每個資料夾各自的播放模式（預設循環） */
    fun playModeForFolder(context: Context, folder: String): PlayMode = runCatching {
        PlayMode.valueOf(
            context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
                .getString("mode_" + folder, PlayMode.REPEAT_ALL.name) ?: PlayMode.REPEAT_ALL.name
        )
    }.getOrDefault(PlayMode.REPEAT_ALL)

    fun setPlayModeForFolder(context: Context, folder: String, mode: PlayMode) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString("mode_" + folder, mode.name).apply()
    }

    /** 每個資料夾的播放來源（預設下載播放） */
    fun playSourceForFolder(context: Context, folder: String): PlaySource = runCatching {
        PlaySource.valueOf(
            context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
                .getString("src_" + folder, PlaySource.DOWNLOAD.name) ?: PlaySource.DOWNLOAD.name
        )
    }.getOrDefault(PlaySource.DOWNLOAD)

    fun setPlaySourceForFolder(context: Context, folder: String, source: PlaySource) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString("src_" + folder, source.name).apply()
    }

    /** 懸浮播放視窗開關 */
    fun floatingPlayer(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("floating", false)

    fun setFloatingPlayer(context: Context, enabled: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putBoolean("floating", enabled).apply()
    }

    /** 懸浮視窗位置 */
    fun floatX(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt("float_x", 48)

    fun floatY(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt("float_y", 480)

    fun setFloatPos(context: Context, x: Int, y: Int) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putInt("float_x", x).putInt("float_y", y).apply()
    }
}
