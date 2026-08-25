package com.tubetune.downloader

import android.app.Application
import android.content.Intent
import com.tubetune.downloader.data.YtDownloader
import com.tubetune.downloader.playback.PlaybackService
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TubeTuneApp : Application() {

    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
        // 啟動背景播放服務（MediaSessionService；播放時自動轉 foreground）
        try {
            startService(Intent(this, PlaybackService::class.java))
        } catch (t: Throwable) {
        }
        try {
            val loc = Localization.fromLocalizationCode("zh-TW").orElse(Localization.DEFAULT)
            NewPipe.init(YtDownloader(), loc)
        } catch (t: Throwable) {
            try {
                NewPipe.init(YtDownloader(), Localization.DEFAULT)
            } catch (t2: Throwable) {
            }
        }
    }

    /** 全域崩潰處理：把堆疊寫入 crash.log，下次啟動顯示給使用者 */
    private fun installCrashHandler() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val f = File(filesDir, "crash.log")
                val sb = StringBuilder()
                sb.append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())).append('\n')
                sb.append(throwable.toString()).append('\n')
                throwable.stackTrace.take(40).forEach { sb.append("    at ").append(it).append('\n') }
                throwable.cause?.let { c ->
                    sb.append("Caused by: ").append(c).append('\n')
                    c.stackTrace.take(20).forEach { sb.append("    at ").append(it).append('\n') }
                }
                f.writeText(sb.toString())
            } catch (t: Throwable) {
            }
            prev?.uncaughtException(thread, throwable)
        }
    }
}
