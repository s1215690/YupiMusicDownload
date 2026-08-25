package com.tubetune.downloader.playback

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.tubetune.downloader.MainActivity
import com.tubetune.downloader.R

/**
 * 桌面小工具：迷你播放器（封面、曲名、上一首/播放/下一首）。
 * 播放狀態改變時由 PlaybackService 呼叫 WidgetProvider.update() 即時刷新。
 */
class WidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { render(context, appWidgetManager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val player = PlaybackService.playerHolder ?: return
        when (intent.action) {
            ACTION_TOGGLE -> if (player.isPlaying) player.pause() else player.play()
            ACTION_NEXT -> if (player.hasNextMediaItem()) player.seekToNextMediaItem()
            ACTION_PREV -> {
                if (player.hasPreviousMediaItem()) {
                    player.seekToPreviousMediaItem()
                } else {
                    player.seekTo(0, 0L)
                }
            }
        }
        WidgetProvider.update(context)
    }

    companion object {
        const val ACTION_TOGGLE = "com.tubetune.downloader.WIDGET_TOGGLE"
        const val ACTION_NEXT = "com.tubetune.downloader.WIDGET_NEXT"
        const val ACTION_PREV = "com.tubetune.downloader.WIDGET_PREV"

        /** 播放狀態變化時由 Service 呼叫 */
        fun update(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WidgetProvider::class.java))
            if (ids.isEmpty()) return
            ids.forEach { render(context, manager, it) }
        }

        private fun render(context: Context, manager: AppWidgetManager, id: Int) {
            val player = PlaybackService.playerHolder
            val views = RemoteViews(context.packageName, R.layout.widget_player)
            val item = player?.currentMediaItem
            val md = item?.mediaMetadata

            views.setTextViewText(R.id.widget_title, md?.title?.toString() ?: "")
            views.setTextViewText(R.id.widget_artist, md?.artist?.toString() ?: "--")
            if (md?.artworkUri != null) {
                views.setImageViewUri(R.id.widget_art, md.artworkUri)
            } else {
                views.setImageViewResource(R.id.widget_art, android.R.drawable.ic_media_play)
                views.setInt(R.id.widget_art, "setColorFilter", 0x33FFFFFF.toInt())
            }
            views.setInt(R.id.widget_prev, "setColorFilter", Color.WHITE)
            views.setInt(R.id.widget_next, "setColorFilter", Color.WHITE)

            views.setImageViewResource(
                R.id.widget_toggle,
                if (player?.isPlaying == true) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play
            )
            views.setInt(R.id.widget_toggle, "setColorFilter", Color.WHITE)

            // 控制按鈕 → WidgetProvider 的 broadcast
            views.setOnClickPendingIntent(
                R.id.widget_toggle,
                broadcast(context, 1, ACTION_TOGGLE)
            )
            views.setOnClickPendingIntent(
                R.id.widget_next,
                broadcast(context, 2, ACTION_NEXT)
            )
            views.setOnClickPendingIntent(
                R.id.widget_prev,
                broadcast(context, 3, ACTION_PREV)
            )
            // 點卡片本體 → 開啟 App
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    context, 10,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            manager.updateAppWidget(id, views)
        }

        private fun broadcast(context: Context, requestCode: Int, action: String): PendingIntent =
            PendingIntent.getBroadcast(
                context, requestCode,
                Intent(context, WidgetProvider::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}
