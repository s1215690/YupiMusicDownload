package com.tubetune.downloader.data

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class LibraryRepository(private val context: Context) {

    private val gson = Gson()
    private val file: File
        get() = File(context.filesDir, "library.json")

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
        try { file.writeText(gson.toJson(_data.value)) } catch (t: Throwable) {}
    }

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
        if (name.isBlank() || d.folders.any { it.name == name }) return
        _data.value = d.copy(folders = d.folders + Folder(name.trim(), System.currentTimeMillis()))
        save()
    }

    fun renameFolder(old: String, new: String) {
        val d = _data.value
        if (new.isBlank() || d.folders.any { it.name == new }) return
        _data.value = d.copy(
            folders = d.folders.map { if (it.name == old) it.copy(name = new.trim()) else it },
            tracks = d.tracks.map { if (it.folder == old) it.copy(folder = new.trim()) else it }
        )
        save()
    }

    fun deleteFolder(name: String) {
        val d = _data.value
        _data.value = d.copy(
            folders = d.folders.filter { it.name != name },
            tracks = d.tracks.map { if (it.folder == name) it.copy(folder = DEFAULT_FOLDER) else it }
        )
        save()
    }

    companion object {
        const val DEFAULT_FOLDER = "未分類"
    }
}
