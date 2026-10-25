package com.tubetune.downloader.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * 下載／轉檔期間的前景服務。
 *
 * 只做一件事：在下載佇列非空時把行程鎖在前景，令 MP3 轉檔（每首幾十秒）
 * 不會因為切去其他 App 被系統回收。實際下載邏輯仍然在 DownloadManager。
 */
class DownloadService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: "準備中…"
        try {
            startForeground(NOTIF_ID, buildNotification(this, text))
        } catch (t: Throwable) {
        }
        running = true
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "tubetune_download"
        private const val NOTIF_ID = 4201
        private const val EXTRA_TEXT = "text"

        @Volatile
        var running = false
            private set

        /** 更新（或啟動）前景通知 */
        fun show(context: Context, text: String) {
            val i = Intent(context, DownloadService::class.java).putExtra(EXTRA_TEXT, text)
            try {
                if (running) context.startService(i)
                else context.startForegroundService(i)
            } catch (t: Throwable) {
            }
        }

        fun stop(context: Context) {
            if (!running) return
            try {
                context.stopService(Intent(context, DownloadService::class.java))
            } catch (t: Throwable) {
            }
            running = false
        }

        private fun buildNotification(context: Context, text: String): Notification {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                nm.getNotificationChannel(CHANNEL) == null
            ) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "下載與轉檔", NotificationManager.IMPORTANCE_LOW)
                )
            }
            return NotificationCompat.Builder(context, CHANNEL)
                .setContentTitle("TubeTune")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }
    }
}
