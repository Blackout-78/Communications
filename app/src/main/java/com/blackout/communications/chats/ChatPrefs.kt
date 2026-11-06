package com.blackout.communications.chats

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

data class ChatFlags(val pinned: Set<Long> = emptySet(), val muted: Set<Long> = emptySet(), val wallpapers: Map<Long, String> = emptyMap())

/** Pinned and muted conversations, and each conversation's wallpaper. Saved on the phone only. */
class ChatPrefs(private val context: Context) {
    private val prefs = context.getSharedPreferences("chat_prefs", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<ChatFlags> = _state

    private fun read() = ChatFlags(
        pinned = prefs.getStringSet("pinned", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet(),
        muted = prefs.getStringSet("muted", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet(),
        wallpapers = prefs.all.filterKeys { it.startsWith("wall_") }
            .mapNotNull { (k, v) -> k.removePrefix("wall_").toLongOrNull()?.let { id -> (v as? String)?.let { id to it } } }.toMap(),
    )

    private fun save(f: ChatFlags) {
        val e = prefs.edit()
        e.putStringSet("pinned", f.pinned.map { it.toString() }.toSet())
        e.putStringSet("muted", f.muted.map { it.toString() }.toSet())
        prefs.all.keys.filter { it.startsWith("wall_") }.forEach { e.remove(it) }
        f.wallpapers.forEach { (id, path) -> e.putString("wall_$id", path) }
        e.apply()
        _state.value = f
    }

    fun togglePin(id: Long) = save(_state.value.let { it.copy(pinned = if (id in it.pinned) it.pinned - id else it.pinned + id) })
    fun toggleMute(id: Long) = save(_state.value.let { it.copy(muted = if (id in it.muted) it.muted - id else it.muted + id) })
    fun isMuted(id: Long) = id in _state.value.muted

    suspend fun setWallpaper(id: Long, uri: Uri?) {
        val dir = File(context.filesDir, "wallpapers").apply { mkdirs() }
        val file = File(dir, "thread_$id.jpg")
        if (uri == null) {
            file.delete()
            save(_state.value.let { it.copy(wallpapers = it.wallpapers - id) })
            return
        }
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
                    val s = minOf(1f, 1440f / maxOf(info.size.width, info.size.height))
                    d.setTargetSize((info.size.width * s).toInt().coerceAtLeast(1), (info.size.height * s).toInt().coerceAtLeast(1))
                    d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            }.isSuccess
        }
        if (ok) save(_state.value.let { it.copy(wallpapers = it.wallpapers + (id to file.absolutePath)) })
    }

    fun forget(id: Long) {
        File(context.filesDir, "wallpapers/thread_$id.jpg").delete()
        save(_state.value.let { ChatFlags(it.pinned - id, it.muted - id, it.wallpapers - id) })
    }
}
