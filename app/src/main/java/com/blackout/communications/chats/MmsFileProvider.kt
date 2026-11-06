package com.blackout.communications.chats

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID

/**
 * Hands picture-message files to the phone's own MMS service (which does the carrier transfer)
 * without giving any other app access. Files live in the app's private cache.
 */
class MmsFileProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val name = uri.lastPathSegment ?: throw FileNotFoundException()
        if (!name.matches(Regex("[A-Za-z0-9-]+\\.pdu"))) throw FileNotFoundException()
        val file = File(dir(context!!), name)
        val flags = ParcelFileDescriptor.parseMode(mode)
        return ParcelFileDescriptor.open(file, if (mode.contains('w')) flags or ParcelFileDescriptor.MODE_CREATE else flags)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String = "application/octet-stream"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        fun dir(context: Context): File = File(context.cacheDir, "mms").apply { mkdirs() }
        fun newFile(context: Context): File = File(dir(context), "${UUID.randomUUID()}.pdu")
        fun uriFor(context: Context, file: File): Uri = Uri.parse("content://${context.packageName}.mms/${file.name}")
    }
}
