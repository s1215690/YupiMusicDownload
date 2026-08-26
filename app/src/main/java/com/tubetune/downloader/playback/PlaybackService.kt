package com.tubetune.downloader.playback

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import coil.ImageLoader
import coil.request.ImageRequest
import coil.target.ImageViewTarget
import com.tubetune.downloader.R
import com.tubetune.downloader.data.Prefs
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 全域播放服務（Media3 MediaSessionService）：
 * - 持有唯一的 ExoPlayer：音樂庫播放與音訊預覽共用
 * - 背景播放：播放時系統自動轉為 foreground service
 * - MediaSession 提供通知列控制（播放/暫停/上一首/下一首）與藍牙耳機控制
 * - 懸浮播放視窗（浮動迷你播放器）：可拖動到螢幕任何位置
 * - DefaultHttpDataSource 帶 Chrome UA：YouTube 串流需要瀏覽器 UA
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // ---- 懸浮視窗 ----
    private var overlay: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var lastArtUrl: String? = null
    private var tickerRunning = false
    private val imageLoader by lazy { ImageLoader(this) }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        val player = buildPlayer()
        mediaSession = MediaSession.Builder(this, player).build()
        playerHolder = player
        player.addListener(overlayTrigger)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        // 只有在「完全沒有媒體內容」時才停止 service；
        // 否則保留（例如使用者回到其他 App、稍後再回來繼續控制）
        if (player == null || player.mediaItemCount == 0 || player.playbackState == Player.STATE_IDLE) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        hideOverlay()
        playerHolder = null
        appContext = null
        val p = mediaSession?.player
        mediaSession?.player?.removeListener(overlayTrigger)
        mediaSession?.release()
        mediaSession = null
        try { p?.release() } catch (t: Throwable) {}
        super.onDestroy()
    }

    private fun buildPlayer(): ExoPlayer {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(CHROME_UA)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(30_000)
            .setReadTimeoutMs(30_000)
        return ExoPlayer.Builder(this)
            // DefaultDataSource 依 URI scheme 分派：
            // content://（本地音樂庫檔案）→ ContentDataSource
            // http(s)（串流/預覽）→ 帶 Chrome UA 的 DefaultHttpDataSource
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(DefaultDataSource.Factory(this, httpFactory))
            )
            // 播放時持有 WakeLock：防止 CPU 休眠導致本地播放放到一半卡住
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    // ---------- 懸浮播放視窗 ----------

    /** 播放狀態變化時呼叫：有曲目且設定開啟權限 → 顯示；無曲目 → 隱藏 */
    private val overlayTrigger = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            WidgetProvider.update(this@PlaybackService)
            if (mediaItem != null) maybeShowOverlay() else hideOverlay()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            WidgetProvider.update(this@PlaybackService)
            if (isPlaying) maybeShowOverlay()
            updateOverlay()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            WidgetProvider.update(this@PlaybackService)
            updateOverlay()
        }
    }

    private fun overlayEnabled(): Boolean =
        Prefs.floatingPlayer(this) && Settings.canDrawOverlays(this)

    private fun maybeShowOverlay() {
        if (overlay != null) return
        if (!overlayEnabled()) return
        val player = mediaSession?.player ?: return
        if (player.mediaItemCount == 0) return
        try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val view = LayoutInflater.from(this).inflate(R.layout.floating_player, null)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            params.x = Prefs.floatX(this)
            params.y = Prefs.floatY(this)

            setupOverlayView(view, player)
            wm.addView(view, params)
            overlay = view
            overlayParams = params
            lastArtUrl = null
            updateOverlay()
            startTicker()
        } catch (t: Throwable) {
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupOverlayView(view: View, player: Player) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false

        view.setOnTouchListener { v, ev ->
            val p = overlayParams ?: return@setOnTouchListener false
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    downY = ev.rawY
                    startX = p.x
                    startY = p.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downX
                    val dy = ev.rawY - downY
                    if (abs(dx) > 6 || abs(dy) > 6) dragging = true
                    if (dragging) {
                        // 拖曳中只改 translation（純渲染執行緒操作、無 WMS binder 呼叫）：
                        // 在觸控分派路徑內同步呼叫 updateViewLayout 是部分 ROM 上
                        // 輸入卡死/死當的已知誘因
                        v.translationX = dx
                        v.translationY = dy
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        v.translationX = 0f
                        v.translationY = 0f
                        // 點擊：開啟主畫面
                        try {
                            startActivity(
                                Intent(this, com.tubetune.downloader.MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (t: Throwable) {
                        }
                    } else {
                        // 放開時才一次提交視窗位置（單一 updateViewLayout）
                        p.x = startX + (ev.rawX - downX).roundToInt()
                        p.y = startY + (ev.rawY - downY).roundToInt()
                        v.translationX = 0f
                        v.translationY = 0f
                        (getSystemService(WINDOW_SERVICE) as WindowManager).updateViewLayout(v, p)
                        Prefs.setFloatPos(this, p.x, p.y)
                    }
                    dragging = false
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.translationX = 0f
                    v.translationY = 0f
                    dragging = false
                    false
                }
                else -> false
            }
        }

        view.findViewById<ImageButton>(R.id.float_prev).setOnClickListener {
            if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
            updateOverlay()
        }
        view.findViewById<ImageButton>(R.id.float_toggle).setOnClickListener {
            if (player.isPlaying) player.pause() else player.play()
            updateOverlay()
        }
        view.findViewById<ImageButton>(R.id.float_next).setOnClickListener {
            if (player.hasNextMediaItem()) player.seekToNextMediaItem()
            updateOverlay()
        }
        view.findViewById<ImageButton>(R.id.float_close).setOnClickListener {
            hideOverlay()
        }
    }

    private fun hideOverlay() {
        val v = overlay ?: return
        overlay = null
        overlayParams?.let { p -> Prefs.setFloatPos(this, p.x, p.y) }
        stopTicker()
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v)
        } catch (t: Throwable) {
        }
    }

    private fun updateOverlay() {
        val v = overlay ?: return
        val player = mediaSession?.player ?: return
        if (player.mediaItemCount == 0) {
            hideOverlay()
            return
        }
        val item = player.currentMediaItem ?: return
        val md: MediaMetadata = item.mediaMetadata

        v.findViewById<TextView>(R.id.float_title).text =
            md.title?.toString() ?: ""
        v.findViewById<TextView>(R.id.float_artist).text =
            md.artist?.toString() ?: ""

        val toggle = v.findViewById<ImageButton>(R.id.float_toggle)
        toggle.setImageResource(
            if (player.isPlaying) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
        toggle.setColorFilter(Color.WHITE)
        v.findViewById<ImageButton>(R.id.float_prev).setColorFilter(Color.WHITE)
        v.findViewById<ImageButton>(R.id.float_next).setColorFilter(Color.WHITE)
        v.findViewById<ImageButton>(R.id.float_close).setColorFilter(0xAAFFFFFF.toInt())

        val dur = player.duration.takeIf { it > 0 } ?: 1L
        v.findViewById<ProgressBar>(R.id.float_progress).progress =
            ((player.currentPosition.coerceAtLeast(0) * 1000) / dur).toInt().coerceIn(0, 1000)

        val art = md.artworkUri?.toString()
        if (art != null && art != lastArtUrl) {
            lastArtUrl = art
            val imageView = v.findViewById<ImageView>(R.id.float_art)
            imageLoader.enqueue(
                ImageRequest.Builder(this)
                    .data(art)
                    .target(ImageViewTarget(imageView))
                    .build()
            )
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            updateOverlay()
            if (overlay != null) {
                mainHandler.postDelayed(this, 500)
            } else {
                tickerRunning = false
            }
        }
    }

    private fun startTicker() {
        if (tickerRunning) return
        tickerRunning = true
        mainHandler.post(ticker)
    }

    private fun stopTicker() {
        mainHandler.removeCallbacks(ticker)
        tickerRunning = false
    }

    companion object {
        const val CHROME_UA =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        /** 進程內對 Player 的引用（service 與 UI 同進程，跨元件共用） */
        @Volatile
        var playerHolder: ExoPlayer? = null
            private set

        /** 進程內 application context（service 建立後註冊） */
        @Volatile
        var appContext: Context? = null
            private set

        /** 確保 service 已啟動並回傳 player（可能尚在初始化，可稍後重試） */
        fun ensureStarted(context: Context): ExoPlayer? {
            val p = playerHolder
            if (p != null) return p
            try {
                context.startService(Intent(context, PlaybackService::class.java))
            } catch (t: Throwable) {
            }
            return null
        }
    }
}
