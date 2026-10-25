package com.tubetune.downloader.data

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import de.sciss.jump3r.mp3.BitStream
import de.sciss.jump3r.mp3.GainAnalysis
import de.sciss.jump3r.mp3.ID3Tag
import de.sciss.jump3r.mp3.Lame
import de.sciss.jump3r.mp3.LameGlobalFlags
import de.sciss.jump3r.mp3.MPEGMode
import de.sciss.jump3r.mp3.Presets
import de.sciss.jump3r.mp3.Quantize
import de.sciss.jump3r.mp3.QuantizePVT
import de.sciss.jump3r.mp3.Reservoir
import de.sciss.jump3r.mp3.Takehiro
import de.sciss.jump3r.mp3.VBRTag
import de.sciss.jump3r.mp3.Version
import de.sciss.jump3r.mpg.Common
import de.sciss.jump3r.mpg.Interface
import de.sciss.jump3r.mpg.MPGLib
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteOrder

/**
 * 把下載回來的音訊（m4a/AAC、webm/Opus…）轉成真正的 MP3。
 *
 * 解碼用 Android 內建的 MediaCodec（沒有額外原生庫、任何機型都能跑），
 * 編碼用純 Java 版 LAME（jump3r）——桌面實測約 90 倍實時，
 * 手機上一般 8～20 倍實時，一首 4 分鐘的歌大約十幾秒。
 *
 * 副檔名是真的 MP3（CBR + Xing/LAME 標頭），不是把 m4a 改名。
 */
object Mp3Encoder {

    /** 單次餵給 LAME 的樣本數（8 個 MP3 音框），夠大又不佔記憶體 */
    private const val CHUNK = 1152 * 8

    /** 轉檔上限：避免壞檔令流程永遠不結束 */
    private const val MAX_MS = 8 * 60 * 1000L

    /**
     * @return true = 成功寫出 [dst]；false = 失敗／被取消（呼叫方應刪掉 dst）
     */
    fun convert(
        src: File,
        dst: File,
        bitrateKbps: Int = 192,
        title: String = "",
        artist: String = "",
        album: String = "",
        shouldStop: () -> Boolean = { false },
        onProgress: (Int) -> Unit = {}
    ): Boolean {
        val started = System.currentTimeMillis()
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        var lame: Lame? = null
        var gfp: LameGlobalFlags? = null
        var sink: FileOutputStream? = null
        try {
            val ex = MediaExtractor()
            extractor = ex
            ex.setDataSource(src.absolutePath)
            var track = -1
            var fmt: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    track = i
                    fmt = f
                    break
                }
            }
            if (track < 0 || fmt == null) return false
            ex.selectTrack(track)
            val srcMime = fmt.getString(MediaFormat.KEY_MIME) ?: return false
            val durationUs =
                if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L

            val dec = MediaCodec.createDecoderByType(srcMime)
            codec = dec
            dec.configure(fmt, null, null, 0)
            dec.start()

            val fos = FileOutputStream(dst)
            sink = fos

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var channels = 2
            var sampleRate = 44100
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            var left = IntArray(0)
            var right = IntArray(0)
            var mp3Buf = ByteArray(0)

            while (!outputDone) {
                if (shouldStop()) return false
                if (System.currentTimeMillis() - started > MAX_MS) return false

                if (!inputDone) {
                    val inIdx = dec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val ib = dec.getInputBuffer(inIdx)
                        val size = if (ib == null) -1 else ex.readSampleData(ib, 0)
                        if (size < 0) {
                            dec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            dec.queueInputBuffer(inIdx, 0, size, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }

                when (val outIdx = dec.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val of = dec.outputFormat
                        sampleRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        if (of.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            pcmEncoding = of.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                        val pair = initLame(sampleRate, if (channels >= 2) 2 else 1, bitrateKbps)
                        lame = pair.first
                        gfp = pair.second
                        left = IntArray(CHUNK)
                        right = IntArray(CHUNK)
                        mp3Buf = ByteArray(CHUNK * 2 + 8192)
                    }

                    MediaCodec.INFO_TRY_AGAIN_LATER, MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit

                    else -> if (outIdx >= 0) {
                        val ob = dec.getOutputBuffer(outIdx)
                        val l = lame
                        val g = gfp
                        if (ob != null && info.size > 0 && l != null && g != null) {
                            val slice = ob.duplicate()
                            slice.position(info.offset)
                            slice.limit(info.offset + info.size)
                            val pcm = slice.slice().order(ByteOrder.LITTLE_ENDIAN)
                            encodePcm(pcm, channels, pcmEncoding, l, g, left, right, mp3Buf) { buf, len ->
                                fos.write(buf, 0, len)
                            }
                        }
                        dec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        if (durationUs > 0) {
                            val pct = (info.presentationTimeUs * 100L / durationUs).toInt().coerceIn(0, 99)
                            onProgress(pct)
                        }
                    }
                }
            }

            val l = lame
            val g = gfp
            if (l != null && g != null) {
                val flushBuf = ByteArray(CHUNK * 2 + 8192)
                val n = l.lame_encode_flush(g, flushBuf, 0, flushBuf.size)
                if (n > 0) fos.write(flushBuf, 0, n)
                l.lame_close(g)
            }
            fos.flush()
            fos.close()
            sink = null
            if (dst.length() <= 1024L) return false

            onProgress(100)
            if (title.isNotBlank() || artist.isNotBlank() || album.isNotBlank()) {
                runCatching { prependId3(dst, title, artist, album) }
            }
            return true
        } catch (t: Throwable) {
            return false
        } finally {
            runCatching { sink?.close() }
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor?.release() }
        }
    }

    // ---- PCM → MP3 ----

    private fun encodePcm(
        pcm: java.nio.ByteBuffer,
        channels: Int,
        pcmEncoding: Int,
        lame: Lame,
        gfp: LameGlobalFlags,
        left: IntArray,
        right: IntArray,
        mp3Buf: ByteArray,
        write: (ByteArray, Int) -> Unit
    ) {
        val ch = if (channels > 0) channels else 1
        if (pcmEncoding != AudioFormat.ENCODING_PCM_FLOAT) {
            // 16-bit PCM：用 ShortBuffer 順序讀（比逐個 getShort(index) 快很多）
            val shorts = pcm.asShortBuffer()
            val frames = shorts.remaining() / ch
            var pos = 0
            while (pos < frames) {
                val n = minOf(left.size, frames - pos)
                if (ch == 1) {
                    for (i in 0 until n) {
                        val v = shorts.get().toInt()
                        left[i] = v
                        right[i] = v
                    }
                } else {
                    for (i in 0 until n) {
                        left[i] = shorts.get().toInt()
                        right[i] = shorts.get().toInt()
                        var c = 2
                        while (c < ch) { // 多聲道只取前 2 聲道
                            shorts.get()
                            c++
                        }
                    }
                }
                val written = lame.lame_encode_buffer_int(gfp, left, right, n, mp3Buf, 0, mp3Buf.size)
                if (written > 0) write(mp3Buf, written)
                pos += n
            }
            return
        }
        // float PCM（少見，多數解碼器輸出 16-bit）
        val frameBytes = 4 * ch
        val frames = pcm.remaining() / frameBytes
        var pos = 0
        while (pos < frames) {
            val n = minOf(left.size, frames - pos)
            for (i in 0 until n) {
                val base = (pos + i) * frameBytes
                if (ch >= 2) {
                    left[i] = floatAt(pcm, base)
                    right[i] = floatAt(pcm, base + 4)
                } else {
                    val v = floatAt(pcm, base)
                    left[i] = v
                    right[i] = v
                }
            }
            val written = lame.lame_encode_buffer_int(gfp, left, right, n, mp3Buf, 0, mp3Buf.size)
            if (written > 0) write(mp3Buf, written)
            pos += n
        }
    }

    /** float 樣本 → LAME 期望的 ±32768 整數 */
    private fun floatAt(pcm: java.nio.ByteBuffer, offset: Int): Int =
        (pcm.getFloat(offset) * 32768f).toInt().coerceIn(-32768, 32767)

    // ---- LAME 初始化（jump3r 需要手動接好各模組，否則 NPE）----

    private fun initLame(sampleRate: Int, channels: Int, bitrateKbps: Int): Pair<Lame, LameGlobalFlags> {
        val lame = Lame()
        val ga = GainAnalysis()
        val bs = BitStream()
        val preset = Presets()
        val qupvt = QuantizePVT()
        val qu = Quantize()
        val vbrTag = VBRTag()
        val ver = Version()
        val id3 = ID3Tag()
        val mpg = MPGLib()
        val intf = Interface()
        val common = Common()
        val rv = Reservoir()
        val tak = Takehiro()

        lame.setModules(ga, bs, preset, qupvt, qu, vbrTag, ver, id3, mpg)
        bs.setModules(ga, mpg, ver, vbrTag)
        id3.setModules(bs, ver)
        preset.setModules(lame)
        qu.setModules(bs, rv, qupvt, tak)
        qupvt.setModules(tak, rv, lame.enc.psy)
        rv.setModules(bs)
        tak.setModules(qupvt)
        vbrTag.setModules(lame, bs, ver)
        mpg.setModules(intf, common)
        intf.setModules(vbrTag, common)

        val gfp = lame.lame_init()
        gfp.in_samplerate = sampleRate
        gfp.num_channels = channels
        gfp.brate = bitrateKbps.coerceIn(32, 320)
        gfp.mode = if (channels == 1) MPEGMode.MONO else MPEGMode.JOINT_STEREO
        gfp.quality = 5          // 0 最慢最好、9 最快；5 是速度／品質平衡
        gfp.bWriteVbrTag = true  // 寫 Xing 標頭，播放器才能顯示正確長度
        gfp.write_id3tag_automatic = false // jump3r 自動寫 ID3 會 NPE，改成自己補
        val r = lame.lame_init_params(gfp)
        if (r < 0) throw IllegalStateException("MP3 編碼器初始化失敗 code=" + r)
        return lame to gfp
    }

    // ---- ID3v2.3 標籤（讓其他播放器／車機看到歌名歌手）----

    private fun prependId3(file: File, title: String, artist: String, album: String) {
        val frames = ArrayList<ByteArray>()
        if (title.isNotBlank()) frames.add(textFrame("TIT2", title))
        if (artist.isNotBlank()) frames.add(textFrame("TPE1", artist))
        if (album.isNotBlank()) frames.add(textFrame("TALB", album))
        if (frames.isEmpty()) return
        val bodySize = frames.sumOf { it.size }
        val header = ByteArray(10)
        header[0] = 'I'.code.toByte()
        header[1] = 'D'.code.toByte()
        header[2] = '3'.code.toByte()
        header[3] = 3
        header[4] = 0
        header[5] = 0
        // 同步安全整數（每個 byte 只用 7 bit）
        header[6] = ((bodySize shr 21) and 0x7F).toByte()
        header[7] = ((bodySize shr 14) and 0x7F).toByte()
        header[8] = ((bodySize shr 7) and 0x7F).toByte()
        header[9] = (bodySize and 0x7F).toByte()

        val tmp = File(file.parentFile, file.name + ".tag")
        FileOutputStream(tmp).use { out ->
            out.write(header)
            frames.forEach { out.write(it) }
            file.inputStream().use { it.copyTo(out, 128 * 1024) }
        }
        if (!tmp.renameTo(file)) {
            file.outputStream().use { o -> tmp.inputStream().use { it.copyTo(o) } }
            tmp.delete()
        }
    }

    /** ID3v2.3 文字框：UTF-16 + BOM，中文不會亂碼 */
    private fun textFrame(id: String, value: String): ByteArray {
        val text = value.toByteArray(Charsets.UTF_16LE)
        val payload = ByteArray(3 + text.size)
        payload[0] = 1 // encoding = UTF-16 with BOM
        payload[1] = 0xFF.toByte()
        payload[2] = 0xFE.toByte()
        System.arraycopy(text, 0, payload, 3, text.size)
        val frame = ByteArray(10 + payload.size)
        id.toByteArray(Charsets.US_ASCII).copyInto(frame, 0)
        val size = payload.size
        // ID3v2.3 的 frame size 是一般 big-endian（不是同步安全）
        frame[4] = ((size shr 24) and 0xFF).toByte()
        frame[5] = ((size shr 16) and 0xFF).toByte()
        frame[6] = ((size shr 8) and 0xFF).toByte()
        frame[7] = (size and 0xFF).toByte()
        System.arraycopy(payload, 0, frame, 10, payload.size)
        return frame
    }
}
