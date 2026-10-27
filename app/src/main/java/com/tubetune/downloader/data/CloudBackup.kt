package com.tubetune.downloader.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * 雲端備份：只上傳**清單**（library.json：資料夾、曲目、歌詞、未下載清單），
 * **唔會**上載任何音檔。
 *
 * 做法：用 SAF 由用戶揀一次資料夾（可以係 Google Drive／OneDrive／本機），
 * 取得 persistable 權限後，之後每次音樂庫有變動就自動覆寫 `tubetune-backup.json`，
 * 唔需要 OAuth client、唔需要 Google Cloud 專案。
 */
object CloudBackup {

    const val FILE_NAME = "tubetune-backup.json"
    private const val MIME = "application/json"

    /** 記住用戶揀嘅資料夾（並保留跨重啟權限） */
    fun choose(context: Context, treeUri: Uri): Boolean {
        return try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            Prefs.setBackupTreeUri(context, treeUri.toString())
            true
        } catch (t: Throwable) {
            // 有些 provider 唔支援 persistable，退而求其次記住 URI
            Prefs.setBackupTreeUri(context, treeUri.toString())
            false
        }
    }

    fun clear(context: Context) {
        Prefs.setBackupTreeUri(context, null)
        Prefs.setBackupLastAt(context, 0L)
    }

    fun folderName(context: Context): String? {
        val uriStr = Prefs.backupTreeUri(context) ?: return null
        return runCatching {
            DocumentFile.fromTreeUri(context, Uri.parse(uriStr))?.name
        }.getOrNull() ?: uriStr.substringAfterLast('/')
    }

    fun isEnabled(context: Context): Boolean = Prefs.backupTreeUri(context) != null

    /** 立即寫入備份（會覆寫同名檔案） */
    fun writeNow(context: Context, json: String): Result<Long> {
        val uriStr = Prefs.backupTreeUri(context) ?: return Result.failure(
            IllegalStateException("未選擇備份資料夾")
        )
        return try {
            val dir = DocumentFile.fromTreeUri(context, Uri.parse(uriStr))
                ?: return Result.failure(IllegalStateException("讀取備份資料夾失敗"))
            if (!dir.canWrite()) {
                return Result.failure(IllegalStateException("備份資料夾只有讀取權限，請重新選擇"))
            }
            val file = dir.findFile(FILE_NAME)
                ?: dir.createFile(MIME, FILE_NAME)
                ?: return Result.failure(IllegalStateException("無法建立備份檔案"))
            val out = context.contentResolver.openOutputStream(file.uri, "wt")
                ?: return Result.failure(IllegalStateException("無法寫入備份檔案"))
            out.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            val now = System.currentTimeMillis()
            Prefs.setBackupLastAt(context, now)
            Result.success(now)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /** 自動備份節流：最少相隔 20 秒，避免每改一次歌單就寫一次雲端 */
    private var lastAutoAt = 0L
    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()

    /**
     * 背景寫入（唔可以阻塞主 thread：Drive／OneDrive provider 寫檔可能幾百 ms）。
     * 同一時間只有一條 thread，避免兩個寫入互相覆蓋。
     */
    fun autoBackupAsync(context: Context, json: String) {
        if (!isEnabled(context)) return
        val now = System.currentTimeMillis()
        if (now - lastAutoAt < 20_000L) return
        lastAutoAt = now
        val app = context.applicationContext
        executor.execute { runCatching { writeNow(app, json) } }
    }
}
