package com.tubetune.downloader.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.tubetune.downloader.MainActivity
import com.tubetune.downloader.data.MediaSaver
import com.tubetune.downloader.data.Prefs
import com.tubetune.downloader.data.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * KTV 錄音服務：
 * - 只錄手機本體麥克風（即使連著藍牙，mic 也鎖定本體 mic）
 * - VOICE_RECOGNITION 音源、關閉 AEC/NS，外放的音樂不會被回聲消除掉
 * - 停止後把單聲道 PCM 轉成 WAV 存入音樂庫「KTV 錄音」資料夾
 */
class KtvService : Service() {

    private var micRecord: AudioRecord? = null
    private var micThread: Thread? = null
    @Volatile private var recording = false
    private var micFile: File? = null
    private var startAt = 0L

    /** 這次錄音的歌曲名（開始錄音時由 UI 傳入；沒有就退回時間戳命名） */
    private var songTitle = ""
    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            if (!recording) return
            elapsedSec.value = ((System.currentTimeMillis() - startAt) / 1000).toInt()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "KTV 錄音", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                songTitle = intent.getStringExtra(EXTRA_TITLE)?.trim().orEmpty()
                val notif = buildNotification(
                    if (songTitle.isNotEmpty()) "正在錄：" + songTitle else "KTV 錄音中"
                )
                if (Build.VERSION.SDK_INT >= 30) {
                    ServiceCompat.startForeground(
                        this, NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    )
                } else {
                    startForeground(NOTIF_ID, notif)
                }
                begin()
            }
            ACTION_STOP -> {
                // 轉 WAV＋儲存可能數秒，主執行緒跑會導致長錄音 ANR → 背景執行緒
                thread(name = "ktv-stop") { stopRecording() }
            }
        }
        return START_NOT_STICKY
    }

    private fun begin() {
        try {
            error.value = null
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(8192)

            // 優先用 UNPROCESSED（完全不經 AEC/NS/AGC，最適合收「藍牙音響外放 + 人聲」），
            // 裝置唔支援就退回 VOICE_RECOGNITION
            val wantUnprocessed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
            var src = if (wantUnprocessed) MediaRecorder.AudioSource.UNPROCESSED
            else MediaRecorder.AudioSource.VOICE_RECOGNITION
            micRecord = AudioRecord(
                src, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, minBuf * 4
            )
            if (micRecord!!.state != AudioRecord.STATE_INITIALIZED) {
                Log.w(TAG, "begin: UNPROCESSED 不可用（state=${micRecord!!.state}），改用 VOICE_RECOGNITION")
                runCatching { micRecord!!.release() }
                src = MediaRecorder.AudioSource.VOICE_RECOGNITION
                micRecord = AudioRecord(
                    src, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, minBuf * 4
                )
            }
            Log.i(TAG, "begin: audioSource=$src (UNPROCESSED=${MediaRecorder.AudioSource.UNPROCESSED})")
            // 保險：若裝置在麥克風上掛了 AEC/NS，關閉它們
            try {
                android.media.audiofx.AcousticEchoCanceler.create(micRecord!!.audioSessionId)?.enabled = false
                android.media.audiofx.NoiseSuppressor.create(micRecord!!.audioSessionId)?.enabled = false
            } catch (t: Throwable) {}
            // 強制使用手機本體麥克風：即使連著藍牙，人聲也走本體 mic（不搶用藍牙 mic）
            // 本 SDK stub 的 getDevices/setPreferredDevice 簽名（AudioDeviceInfo）與真機（AudioDevice）
            // 不一致，用反射相容兩種框架，避免真實裝置上 NoSuchMethodError
            try {
                val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val devices = am.javaClass.getMethod("getDevices", Int::class.javaPrimitiveType)
                    .invoke(am, 1 /* AudioManager.GET_DEVICES_INPUTS */) as Array<Any>
                val builtin = devices.firstOrNull { d ->
                    runCatching { d.javaClass.getMethod("getType").invoke(d) as Int }.getOrNull() ==
                        (runCatching { d.javaClass.getField("TYPE_BUILTIN_MIC").getInt(null) }.getOrNull() ?: 14)
                }
                if (builtin != null) {
                    val setter = micRecord!!.javaClass.methods.first {
                        it.name == "setPreferredDevice" &&
                            it.parameterTypes.size == 1 && it.parameterTypes[0].isInstance(builtin)
                    }
                    setter.invoke(micRecord!!, builtin)
                    Log.i(TAG, "begin: mic pinned to builtin mic (${builtin.javaClass.simpleName})")
                } else {
                    Log.w(TAG, "begin: builtin mic not found among ${devices.size} input devices")
                }
            } catch (t: Throwable) {
                Log.w(TAG, "begin: pin builtin mic failed", t)
            }
            Log.i(TAG, "begin: mic state=${micRecord!!.state}, minBuf=$minBuf")

            micFile = File(cacheDir, "ktv_mic_${System.currentTimeMillis()}.pcm")
            recording = true
            startAt = System.currentTimeMillis()
            elapsedSec.value = 0

            val vRec = micRecord!!
            val vFile = micFile!!
            micThread = thread(name = "ktv-mic") { writeLoop(vRec, vFile) }
            isRecording.value = true
            handler.post(tick)
        } catch (t: Throwable) {
            cleanupRecords()
            isRecording.value = false
            error.value = "無法開始錄音：" + (t.message ?: t.javaClass.simpleName)
            stopSelf()
        }
    }

    private fun writeLoop(rec: AudioRecord, file: File) {
        try {
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "writeLoop(${file.name}): not initialized, state=${rec.state}")
                return
            }
            rec.startRecording()
            Log.i(TAG, "writeLoop(${file.name}): started")
            val buf = ShortArray(4096)
            // 邊錄邊寫磁碟：長錄音不會把整段 PCM 堆在 RAM 上
            java.io.FileOutputStream(file).use { out ->
                while (recording) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n > 0) {
                        val bytes = ByteArray(n * 2)
                        for (i in 0 until n) {
                            bytes[i * 2] = (buf[i].toInt() and 0xFF).toByte()
                            bytes[i * 2 + 1] = ((buf[i].toInt() shr 8) and 0xFF).toByte()
                        }
                        out.write(bytes)
                    } else if (n < 0) {
                        break // 讀取錯誤，避免空轉
                    }
                }
            }
            Log.i(TAG, "writeLoop(${file.name}): done, ${file.length()} bytes")
            try { rec.stop() } catch (t: Throwable) {}
            try { rec.release() } catch (t: Throwable) {}
        } catch (t: Throwable) {
            Log.e(TAG, "writeLoop(${file.name}) crashed", t)
        }
    }

    private val stopLock = Any()

    private fun stopRecording() {
        synchronized(stopLock) {
            if (!recording) { stopSelf(); return }
            recording = false
        }
        micThread?.join(3000)
        micThread = null
        handler.removeCallbacks(tick)
        isRecording.value = false

        try {
            val vSize = micFile?.let { if (it.exists()) it.length() else -1L } ?: -1L
            Log.i(TAG, "stop: mic=$vSize B")
            val wav = micToWav()
            micFile?.delete()
            if (wav != null && wav.length() > 44) {
                // 檔名／曲名以歌曲名為主；沒有歌曲資訊（理論上不會）時退回「KTV 月日-時間」
                val title = if (songTitle.isNotEmpty()) songTitle else {
                    val stamp = SimpleDateFormat("MMdd-HHmm", Locale.US).format(Date(startAt))
                    "KTV " + stamp
                }
                val size = wav.length()
                val uri = MediaSaver.saveAudio(
                    this, wav, KTV_FOLDER, title, "KTV 錄音",
                    elapsedSec.value * 1000L, "audio/wav", "wav"
                )
                wav.delete()
                Log.i(TAG, "stop: saved $title -> $uri")
                val track = Track(
                    videoId = "ktv_" + startAt,
                    title = title,
                    artist = "KTV 錄音",
                    durationSeconds = elapsedSec.value.toLong(),
                    thumbnailUrl = "",
                    fileName = MediaSaver.sanitize(title) + ".wav",
                    uri = uri,
                    folder = KTV_FOLDER,
                    sizeBytes = size,
                    downloadedAt = System.currentTimeMillis(),
                    downloaded = true
                )
                savedTrack.value = track
            } else {
                Log.w(TAG, "stop: no wav produced (mic=$vSize B)")
                error.value = "錄音內容是空的"
            }
        } catch (t: Throwable) {
            Log.w(TAG, "stop: save failed", t)
            error.value = "儲存失敗：" + (t.message ?: t.javaClass.simpleName)
        }
        stopSelf()
    }

    /** 離線轉檔：單聲道 PCM → WAV（單聲道），分塊處理避免吃爆記憶體 */
    private fun micToWav(): File? {
        val vF = micFile ?: return null
        if (!vF.exists()) return null
        val out = File(cacheDir, "ktv_mix_${System.currentTimeMillis()}.wav")
        val micLen = vF.length()            // bytes；單聲道 = frames * 2
        val totalFrames = micLen / 2
        if (totalFrames == 0L) return null

        // ---- 第一輪：量原檔峰值，決定要補多少增益 ----
        // 藍牙音響外放 + 手機麥克風收環境聲，原始電平通常好低（-30dBFS 左右），
        // 單靠固定倍數唔夠 → 除了使用者設定的增益，再自動補到目標峰值（最多再 +18dB）
        var peak = 0
        var sumSq = 0.0
        var counted = 0L
        runCatching {
            java.io.DataInputStream(vF.inputStream().buffered()).use { input ->
                val bytes = ByteArray(CHUNK_BYTES)
                while (true) {
                    val n = input.read(bytes)
                    if (n <= 0) break
                    var i = 0
                    while (i + 1 < n) {
                        val s = ((bytes[i + 1].toInt() shl 8) or (bytes[i].toInt() and 0xFF)).toShort().toInt()
                        val a = kotlin.math.abs(s)
                        if (a > peak) peak = a
                        sumSq += (s.toDouble() * s)
                        counted++
                        i += 2
                    }
                }
            }
        }
        val userGain = Math.pow(10.0, Prefs.ktvGainDb(this) / 20.0)
        var autoGain = 1.0
        if (peak > 0) {
            val afterUser = peak * userGain
            val target = 0.85 * 32767.0
            if (afterUser < target) autoGain = (target / afterUser).coerceAtMost(8.0) // 上限 +18dB
        }
        val totalGain = userGain * autoGain
        val rmsDb = if (counted > 0) 20.0 * Math.log10(
            (Math.sqrt(sumSq / counted) / 32768.0).coerceAtLeast(1e-9)
        ) else -120.0
        Log.i(
            TAG,
            "micToWav: peak=" + peak + " rmsDb=" + String.format("%.1f", rmsDb) +
                " userGain=" + String.format("%.2f", userGain) +
                " autoGain=" + String.format("%.2f", autoGain) +
                " total=" + String.format("%.2f", totalGain) + " frames=" + totalFrames
        )

        val dataBytes = totalFrames * 2     // 單聲道 16bit
        java.io.RandomAccessFile(out, "rw").use { raf ->
            // WAV header
            val header = ByteArray(44)
            fun putInt(off: Int, v: Int) {
                header[off] = (v and 0xFF).toByte(); header[off + 1] = ((v shr 8) and 0xFF).toByte()
                header[off + 2] = ((v shr 16) and 0xFF).toByte(); header[off + 3] = ((v shr 24) and 0xFF).toByte()
            }
            fun putShort(off: Int, v: Int) {
                header[off] = (v and 0xFF).toByte(); header[off + 1] = ((v shr 8) and 0xFF).toByte()
            }
            "RIFF".toByteArray().copyInto(header, 0)
            putInt(4, (36 + dataBytes).toInt())
            "WAVE".toByteArray().copyInto(header, 8)
            "fmt ".toByteArray().copyInto(header, 12)
            putInt(16, 16); putShort(20, 1); putShort(22, 1)
            putInt(24, SAMPLE_RATE); putInt(28, SAMPLE_RATE * 2)
            putShort(32, 2); putShort(34, 16)
            "data".toByteArray().copyInto(header, 36)
            putInt(40, dataBytes.toInt())
            raf.write(header, 0, 44)

            val vIn = java.io.DataInputStream(vF.inputStream().buffered())
            val CHUNK = 4096
            val vBytes = ByteArray(CHUNK * 2)
            val vShorts = ShortArray(CHUNK)
            val outBytes = ByteArray(CHUNK * 2)
            var written = 0L
            while (true) {
                val nV = vIn.read(vBytes).let { if (it < 0) 0 else it }
                if (nV <= 0) break
                val framesV = nV / 2
                java.nio.ByteBuffer.wrap(vBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(vShorts, 0, framesV)
                var o = 0
                for (i in 0 until framesV) {
                    val v = applyGain(vShorts[i].toInt(), totalGain)
                    outBytes[o++] = (v and 0xFF).toByte()
                    outBytes[o++] = ((v shr 8) and 0xFF).toByte()
                }
                raf.write(outBytes, 0, o)
                written += o
            }
            // 回填實際 data 大小
            putInt(4, (36 + written).toInt()); putInt(40, written.toInt())
            raf.seek(0); raf.write(header, 0, 44)
        }
        return out
    }

    /**
     * 套用增益 + 軟限幅：0.8 全幅以下線性放大（唔失真），超過就壓縮，
     * 避免大聲唱歌時爆音（硬 clip 會拆聲）。
     */
    private fun applyGain(sample: Int, gain: Double): Int {
        val x = sample * gain
        val knee = 0.8 * 32767
        if (x > knee) {
            val over = (x - knee) / (32767 - knee)
            return (knee + (32767 - knee) * Math.tanh(over)).toInt().coerceAtMost(32767)
        }
        if (x < -knee) {
            val over = (-x - knee) / (32767 - knee)
            return (-(knee + (32767 - knee) * Math.tanh(over))).toInt().coerceAtLeast(-32768)
        }
        return x.toInt()
    }

    private fun cleanupRecords() {
        try { micRecord?.stop() } catch (t: Throwable) {}
        try { micRecord?.release() } catch (t: Throwable) {}
        micRecord = null
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, KtvService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("KTV 錄音")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "停止錄音", stop)
            .build()
    }

    override fun onDestroy() {
        recording = false
        handler.removeCallbacks(tick)
        cleanupRecords()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "KtvService"
        private const val CHANNEL = "ktv"
        private const val NOTIF_ID = 2001
        const val ACTION_START = "com.tubetune.downloader.KTV_START"
        const val ACTION_STOP = "com.tubetune.downloader.KTV_STOP"
        const val EXTRA_TITLE = "ktv_song_title"
        const val KTV_FOLDER = "KTV 錄音"
        private const val SAMPLE_RATE = 48000
        /** 第一輪量峰值時的讀取塊大小（bytes） */
        private const val CHUNK_BYTES = 64 * 1024

        val isRecording = MutableStateFlow(false)
        val elapsedSec = MutableStateFlow(0)
        val error = MutableStateFlow<String?>(null)
        val savedTrack = MutableStateFlow<Track?>(null)

        fun start(context: Context, songTitle: String) {
            val i = Intent(context, KtvService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_TITLE, songTitle)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
        }

        fun stopRecording(context: Context) {
            context.startService(
                Intent(context, KtvService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
