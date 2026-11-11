package com.blackout.communications.chats

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Copies a picture from a message into Pictures/Communications, where the Gallery app sees it. */
object SavePicture {
    suspend fun save(context: Context, source: Uri): Uri? = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        val type = runCatching { cr.getType(source) }.getOrNull()?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
        val ext = when (type) { "image/png" -> "png"; "image/gif" -> "gif"; "image/webp" -> "webp"; else -> "jpg" }
        val name = "Communications_" + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date()) + "_${(source.lastPathSegment ?: "").takeLast(6)}.$ext"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, type)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Communications")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val target = runCatching { cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) }.getOrNull() ?: return@withContext null
        val ok = runCatching {
            cr.openInputStream(source)!!.use { input -> cr.openOutputStream(target)!!.use { input.copyTo(it) } }
        }.isSuccess
        if (!ok) {
            runCatching { cr.delete(target, null, null) }
            return@withContext null
        }
        cr.update(target, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        target
    }
}

/** Copies a video from a message into Movies/Communications, where the Gallery app sees it. */
object SaveVideo {
    suspend fun save(context: Context, source: Uri): Uri? = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        val type = runCatching { cr.getType(source) }.getOrNull()?.takeIf { it.startsWith("video/") } ?: "video/mp4"
        val ext = when (type) { "video/3gpp" -> "3gp"; "video/webm" -> "webm"; "video/quicktime" -> "mov"; else -> "mp4" }
        val name = "Communications_" + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date()) + "_${(source.lastPathSegment ?: "").takeLast(6)}.$ext"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, type)
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Communications")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val target = runCatching { cr.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) }.getOrNull() ?: return@withContext null
        val ok = runCatching {
            cr.openInputStream(source)!!.use { input -> cr.openOutputStream(target)!!.use { input.copyTo(it) } }
        }.isSuccess
        if (!ok) {
            runCatching { cr.delete(target, null, null) }
            return@withContext null
        }
        cr.update(target, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
        target
    }
}
