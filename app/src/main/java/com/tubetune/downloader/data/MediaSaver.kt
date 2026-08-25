package com.tubetune.downloader.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream

object MediaSaver {

    fun saveAudio(
        context: Context,
        source: File,
        folder: String,
        title: String,
        artist: String,
        durationMs: Long,
        mime: String,
        extension: String
    ): String {
        val safeFolder = sanitize(folder.ifBlank { LibraryRepository.DEFAULT_FOLDER })
        val baseName = sanitize(title)
        val fileName = baseName + "." + extension
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, source, safeFolder, fileName, title, artist, durationMs, mime)
        } else {
            saveToPublicDir(context, source, safeFolder, fileName)
        }
    }

    private fun saveViaMediaStore(
        context: Context,
        source: File,
        safeFolder: String,
        fileName: String,
        title: String,
        artist: String,
        durationMs: Long,
        mime: String
    ): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, mime)
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/TubeTune/" + safeFolder)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("無法建立媒體檔案")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(source).use { it.copyTo(out) }
            } ?: throw IllegalStateException("無法寫入媒體檔案")
            val update = ContentValues().apply {
                put(MediaStore.Audio.Media.IS_PENDING, 0)
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.Audio.Media.ARTIST, artist)
                if (durationMs > 0) put(MediaStore.Audio.Media.DURATION, durationMs)
            }
            resolver.update(uri, update, null, null)
        } catch (t: Throwable) {
            try { resolver.delete(uri, null, null) } catch (t2: Throwable) {}
            throw t
        }
        return uri.toString()
    }

    private fun saveToPublicDir(context: Context, source: File, safeFolder: String, fileName: String): String {
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val dir = File(musicDir, "TubeTune/" + safeFolder)
        if (!dir.exists()) dir.mkdirs()
        val dest = File(dir, fileName)
        FileInputStream(source).use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        return Uri.fromFile(dest).toString()
    }

    fun deleteTrack(context: Context, uriString: String) {
        try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "content") {
                context.contentResolver.delete(uri, null, null)
            } else if (uri.scheme == "file") {
                val f = File(uri.path ?: return)
                if (f.exists()) f.delete()
            }
        } catch (t: Throwable) {}
    }

    fun sanitize(name: String): String {
        var s = name.replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1f]"), " ").trim()
        if (s.length > 80) s = s.take(80)
        if (s.isEmpty()) s = "audio"
        return s.trimEnd('.')
    }
}
