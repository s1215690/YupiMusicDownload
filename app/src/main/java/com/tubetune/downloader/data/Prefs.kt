package com.tubetune.downloader.data

import android.content.Context

object Prefs {
    private const val NAME = "tubetune"

    fun quality(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("quality", "aac") ?: "aac"

    fun setQuality(context: Context, value: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putString("quality", value).apply()
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
}
