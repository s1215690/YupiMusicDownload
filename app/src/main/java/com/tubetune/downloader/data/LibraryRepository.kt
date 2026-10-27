package com.tubetune.downloader.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class LibraryRepository(private val ctx: Context) {

    private val gson = Gson()
    private val file: File
        get() = File(ctx.filesDir, "library.json")

    private val _data = MutableStateFlow(load())
    val data: StateFlow<LibraryData> = _data.asStateFlow()

    private fun load(): LibraryData {
        val d = try {
            if (file.exists()) gson.fromJson(file.readText(), LibraryData::class.java)
                ?: LibraryData(emptyList(), emptyList())
            else LibraryData(emptyList(), emptyList())
        } catch (t: Throwable) {
            LibraryData(emptyList(), emptyList())
        }
        // 相容舊資料：曲目引用的資料夾若不在 folders 清單（例如 KTV 錄音），補建，否則「全部」檢視看不到
        val missing = d.tracks.map { it.folder }
            .filter { f -> f != DEFAULT_FOLDER }
            .distinct()
            .filter { f -> d.folders.none { it.name == f } }
        return if (missing.isEmpty()) d
        else d.copy(folders = d.folders + missing.map { Folder(it, System.currentTimeMillis()) })
    }

    private fun save() {
        val json = gson.toJson(_data.value)
        try { file.writeText(json) } catch (t: Throwable) {}
        // 有設定雲端備份資料夾就自動同步「清單」（唔含音檔），背景寫入
        try { CloudBackup.autoBackupAsync(ctx, json) } catch (t: Throwable) {}
    }

    /** 匯出整個音樂庫（JSON 字串）—— 給「設定 → 備份音樂庫」用 */
    fun exportJson(): String = gson.toJson(_data.value)

    /**
     * 由備份 JSON 還原整個音樂庫（連資料夾分類、未下載的串流曲目、AI 歌詞）。
     * @return 還原的曲目數（0 = 檔案格式不對或沒有曲目）
     */
    fun importJson(json: String): Int {
        val d = try { gson.fromJson(json, LibraryData::class.java) } catch (t: Throwable) { null } ?: return 0
        if (d.tracks.isEmpty()) return 0
        val extraFolders = d.tracks.map { it.folder }
            .filter { f -> f != DEFAULT_FOLDER }
            .distinct()
            .filter { f -> d.folders.none { it.name == f } }
        val restored = d.copy(
            folders = d.folders + extraFolders.map { Folder(it, System.currentTimeMillis()) }
        )
        _data.value = restored
        try { file.writeText(gson.toJson(restored)) } catch (t: Throwable) {}
        return restored.tracks.size
    }

    /** 儲存／清除某首歌的歌詞（連來源標籤） */
    fun setLyrics(videoId: String, text: String?, source: String? = null) {
        val d = _data.value
        if (d.tracks.none { it.videoId == videoId }) return
        _data.value = d.copy(tracks = d.tracks.map {
            if (it.videoId == videoId) it.copy(lyrics = text, lyricsSource = source) else it
        })
        save()
    }

    fun lyricsOf(videoId: String): String? = trackBy(videoId)?.lyrics

    fun addTrack(track: Track) {
        val d = _data.value
        if (d.tracks.any { it.videoId == track.videoId }) return
        // 曲目所在的資料夾若不存在就補建，否則在「全部」檢視與資料夾分頁都會隐身
        val folders = if (track.folder != DEFAULT_FOLDER && d.folders.none { it.name == track.folder })
            d.folders + Folder(track.folder, System.currentTimeMillis())
        else d.folders
        _data.value = d.copy(tracks = listOf(track) + d.tracks, folders = folders)
        save()
    }

    fun trackBy(videoId: String): Track? = _data.value.tracks.firstOrNull { it.videoId == videoId }

    /** 更新既有曲目（例如下載完成後補上本地檔案資訊） */
    fun updateTrack(track: Track) {
        val d = _data.value
        _data.value = d.copy(tracks = d.tracks.map { if (it.videoId == track.videoId) track else it })
        save()
    }

    fun removeTrack(videoId: String) {
        val d = _data.value
        _data.value = d.copy(tracks = d.tracks.filter { it.videoId != videoId })
        save()
    }

    fun moveTrack(videoId: String, folder: String) {
        val d = _data.value
        _data.value = d.copy(tracks = d.tracks.map { if (it.videoId == videoId) it.copy(folder = folder) else it })
        save()
    }

    fun addFolder(name: String) {
        val d = _data.value
        // 「未分類」是內建預設資料夾：不可新增/改名成它，否則「全部」檢視會出現兩個同名群組
        if (name.isBlank() || name.trim() == DEFAULT_FOLDER || d.folders.any { it.name == name }) return
        _data.value = d.copy(folders = d.folders + Folder(name.trim(), System.currentTimeMillis()))
        save()
    }

    fun renameFolder(old: String, new: String) {
        val d = _data.value
        if (new.isBlank() || new.trim() == DEFAULT_FOLDER || d.folders.any { it.name == new }) return
        _data.value = d.copy(
            folders = d.folders.map { if (it.name == old) it.copy(name = new.trim()) else it },
            tracks = d.tracks.map { if (it.folder == old) it.copy(folder = new.trim()) else it }
        )
        save()
    }

    fun deleteFolder(name: String) {
        if (name == DEFAULT_FOLDER) return
        val d = _data.value
        _data.value = d.copy(
            folders = d.folders.filter { it.name != name },
            tracks = d.tracks.map { if (it.folder == name) it.copy(folder = DEFAULT_FOLDER) else it }
        )
        save()
    }

    /**
     * 掃描手機上 Music/TubeTune 已存在的音訊檔，把缺失的索引補回音樂庫。
     *
     * 用途：重裝 App／清除資料／換機之後，檔案仍然在，但 library.json 已經沒有了，
     * 音樂庫會變成空的。這個方法用 MediaStore 把檔案找回來（含資料夾、時長、大小），
     * 已存在的曲目（同 uri 或同資料夾同檔名）不會重複加入。
     *
     * @return 實際新加入的曲目數
     */
    fun importLocalFiles(): Int {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.MIME_TYPE,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Audio.Media.RELATIVE_PATH
            else MediaStore.Audio.Media.DATA
        )
        val selection: String
        val args: Array<String>
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = MediaStore.Audio.Media.RELATIVE_PATH + " LIKE ?"
            args = arrayOf("Music/TubeTune/%")
        } else {
            selection = MediaStore.Audio.Media.DATA + " LIKE ?"
            args = arrayOf("%/Music/TubeTune/%")
        }

        val found = ArrayList<Track>()
        try {
            ctx.contentResolver.query(collection, projection, selection, args, null)?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val iName = c.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                val iTitle = c.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val iArtist = c.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val iDur = c.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val iSize = c.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val iAdded = c.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                val iPath = c.getColumnIndex(projection[8])
                while (c.moveToNext()) {
                    val mediaId = c.getLong(iId)
                    val name = if (iName >= 0) c.getString(iName).orEmpty() else ""
                    if (name.isBlank()) continue
                    val path = if (iPath >= 0) c.getString(iPath).orEmpty() else ""
                    // 只收 Music/TubeTune/<資料夾>/ 底下的檔案（備份資料夾唔會被收）
                    val rel = if (path.startsWith("/")) {
                        path.substringAfter("/Music/TubeTune/", "")
                    } else {
                        path.removePrefix("Music/TubeTune/")
                    }
                    val folder = rel.substringBefore('/', "").ifBlank { DEFAULT_FOLDER }
                    if (path.isNotBlank() && !path.contains("Music/TubeTune/")) continue
                    var title = if (iTitle >= 0) c.getString(iTitle).orEmpty() else ""
                    if (title.isBlank()) title = name.substringBeforeLast('.', name)
                    var artist = if (iArtist >= 0) c.getString(iArtist).orEmpty() else ""
                    if (artist.isBlank() || artist == "<unknown>") artist = "未知歌手"
                    val dur = if (iDur >= 0) c.getLong(iDur) else 0L
                    val size = if (iSize >= 0) c.getLong(iSize) else 0L
                    val added = if (iAdded >= 0) c.getLong(iAdded) * 1000L else 0L
                    found.add(
                        Track(
                            videoId = "local_" + mediaId,
                            title = title,
                            artist = artist,
                            durationSeconds = dur / 1000L,
                            thumbnailUrl = "",
                            fileName = name,
                            uri = ContentUris.withAppendedId(collection, mediaId).toString(),
                            folder = folder,
                            sizeBytes = size,
                            downloadedAt = added,
                            downloaded = true
                        )
                    )
                }
            }
        } catch (t: Throwable) {
            return 0
        }

        if (found.isEmpty()) return 0
        val d = _data.value
        val newTracks = ArrayList<Track>()
        val newFolders = ArrayList(d.folders)
        found.forEach { t ->
            val dup = d.tracks.any { it.uri == t.uri }
                || d.tracks.any { it.folder == t.folder && it.fileName == t.fileName }
                || newTracks.any { it.uri == t.uri || (it.folder == t.folder && it.fileName == t.fileName) }
            if (!dup) {
                newTracks.add(t)
                if (t.folder != DEFAULT_FOLDER && newFolders.none { it.name == t.folder }) {
                    newFolders.add(Folder(t.folder, System.currentTimeMillis()))
                }
            }
        }
        if (newTracks.isNotEmpty()) {
            _data.value = d.copy(tracks = d.tracks + newTracks, folders = newFolders)
            save()
        }
        return newTracks.size
    }

    companion object {
        const val DEFAULT_FOLDER = "未分類"

    }
}
