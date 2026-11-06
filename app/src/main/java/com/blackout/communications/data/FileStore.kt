package com.blackout.communications.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * Copies pictures and songs the user picks into the app's private folders,
 * so they keep working if the original file is moved or deleted.
 */
class FileStore(private val context: Context) {

    private val photoDir get() = File(context.filesDir, "photos").apply { mkdirs() }
    private val ringtoneDir get() = File(context.filesDir, "ringtones").apply { mkdirs() }

    /** Saves a picked picture as a 512 px JPEG and returns its path. */
    suspend fun savePhoto(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val size = info.size
                val scale = minOf(1f, MAX_PHOTO_PX.toFloat() / maxOf(size.width, size.height))
                decoder.setTargetSize((size.width * scale).toInt().coerceAtLeast(1), (size.height * scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val file = File(photoDir, "${UUID.randomUUID()}.jpg")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            file.absolutePath
        }.getOrNull()
    }

    fun deletePhoto(path: String?) {
        if (path == null) return
        val file = File(path)
        if (file.parentFile?.canonicalPath == photoDir.canonicalPath) file.delete()
    }

    /**
     * Copies a picked song into the ringtones folder. Files are named by their content,
     * so the same song picked for two contacts is stored once.
     * Returns the stored path and the song's original name.
     */
    suspend fun saveRingtone(uri: Uri): Pair<String, String>? = withContext(Dispatchers.IO) {
        runCatching {
            val name = displayName(uri) ?: "Ringtone"
            val extension = name.substringAfterLast('.', "").lowercase().takeIf { it.length in 1..5 } ?: "audio"
            val temp = File(ringtoneDir, "incoming-${UUID.randomUUID()}.tmp")
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri)!!.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }.take(32)
            val target = File(ringtoneDir, "$hash.$extension")
            if (target.exists()) temp.delete() else temp.renameTo(target)
            target.absolutePath to name
        }.getOrNull()
    }

    /** Deletes stored songs that no contact uses any more. */
    fun deleteUnusedRingtones(inUse: Collection<String>) {
        val keep = inUse.map { File(it).canonicalPath }.toSet()
        ringtoneDir.listFiles()?.forEach { file ->
            if (file.canonicalPath !in keep) file.delete()
        }
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private companion object {
        const val MAX_PHOTO_PX = 512
    }
}
