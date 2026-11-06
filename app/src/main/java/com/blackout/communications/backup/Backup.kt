package com.blackout.communications.backup

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.CallLog
import android.provider.DocumentsContract
import android.provider.Telephony
import android.util.Base64
import com.blackout.communications.CommunicationsApp
import com.blackout.communications.chats.MessageStore
import com.blackout.communications.data.Contact
import com.blackout.communications.data.PhoneNumber
import com.blackout.communications.data.displayName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Backup to plain files in a folder you choose on your phone:
 * contacts (.vcf), messages (.json) and call history (.json). Import reads them back.
 */
object Backup {
    private fun prefs(context: Context) = context.getSharedPreferences("backup", Context.MODE_PRIVATE)

    fun folder(context: Context): Uri? = prefs(context).getString("folder", null)?.let(Uri::parse)

    fun setFolder(context: Context, tree: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        prefs(context).edit().putString("folder", tree.toString()).apply()
    }

    fun folderName(context: Context): String? = folder(context)?.let { DocumentsContract.getTreeDocumentId(it).substringAfter(':').ifBlank { "Phone storage" } }

    data class Result(val contacts: Int, val messages: Int, val calls: Int, val error: String? = null)

    /** Writes all three backup files. */
    suspend fun exportAll(context: Context): Result = withContext(Dispatchers.IO) {
        val tree = folder(context) ?: return@withContext Result(0, 0, 0, "Choose a backup folder first.")
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
        try {
            val (vcf, contactCount) = contactsVcf(context)
            write(context, tree, "Communications-contacts-$stamp.vcf", "text/x-vcard", vcf.toByteArray())
            val (messages, messageCount) = messagesJson(context)
            write(context, tree, "Communications-messages-$stamp.json", "application/json", messages.toByteArray())
            val (calls, callCount) = callsJson(context)
            write(context, tree, "Communications-calls-$stamp.json", "application/json", calls.toByteArray())
            BackupReminder.markBackedUp(context)
            Result(contactCount, messageCount, callCount)
        } catch (e: Exception) {
            Result(0, 0, 0, "Backup failed: ${e.message ?: "couldn't write to the folder"}")
        }
    }

    private fun write(context: Context, tree: Uri, name: String, mime: String, bytes: ByteArray) {
        val dir = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val file = DocumentsContract.createDocument(context.contentResolver, dir, mime, name) ?: error("couldn't create $name")
        context.contentResolver.openOutputStream(file)!!.use { it.write(bytes) }
    }

    // ---------------------------------------------------------------- Contacts (vCard)

    private suspend fun contactsVcf(context: Context): Pair<String, Int> {
        val app = context.applicationContext as CommunicationsApp
        val all = app.database.contactDao().getAll()
        val sb = StringBuilder()
        all.forEach { c ->
            val k = c.contact
            sb.append("BEGIN:VCARD\r\nVERSION:3.0\r\n")
            sb.append("N:${esc(k.lastName)};${esc(k.firstName)};;;\r\n")
            sb.append("FN:${esc(k.displayName.ifBlank { c.numbers.firstOrNull()?.number ?: "" })}\r\n")
            c.numbers.forEach { n ->
                val type = when (n.label) { "Mobile" -> "CELL"; "Home" -> "HOME"; "Work" -> "WORK"; else -> "VOICE" }
                sb.append("TEL;TYPE=$type${if (n.isMain) ",PREF" else ""}:${n.number}\r\n")
            }
            if (k.groupName.isNotBlank()) sb.append("CATEGORIES:${esc(k.groupName)}\r\n")
            if (k.favorite) sb.append("X-COMMUNICATIONS-FAVORITE:1\r\n")
            k.speedDial?.let { sb.append("X-COMMUNICATIONS-SPEEDDIAL:$it\r\n") }
            k.photoPath?.let { File(it) }?.takeIf { it.exists() }?.let { f ->
                val b64 = Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
                fold("PHOTO;ENCODING=b;TYPE=JPEG:$b64", sb)
            }
            sb.append("END:VCARD\r\n")
        }
        return sb.toString() to all.size
    }

    private fun fold(line: String, sb: StringBuilder) {
        var i = 0
        while (i < line.length) {
            val end = minOf(line.length, i + if (i == 0) 75 else 74)
            if (i > 0) sb.append(' ')
            sb.append(line, i, end).append("\r\n")
            i = end
        }
    }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;").replace("\n", "\\n")
    private fun unesc(s: String) = s.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    private suspend fun importVcf(context: Context, text: String): Int {
        val app = context.applicationContext as CommunicationsApp
        val existing = app.database.contactDao().getAll()
        val lines = text.replace("\r\n", "\n").split("\n").fold(mutableListOf<String>()) { acc, line ->
            if ((line.startsWith(" ") || line.startsWith("\t")) && acc.isNotEmpty()) acc[acc.lastIndex] = acc.last() + line.substring(1) else acc += line
            acc
        }
        var added = 0
        var cur: MutableMap<String, Any>? = null
        val numbers = mutableListOf<PhoneNumber>()
        for (raw in lines) {
            val line = raw.trimEnd()
            if (line.equals("BEGIN:VCARD", true)) { cur = mutableMapOf(); numbers.clear(); continue }
            val card = cur ?: continue
            if (line.equals("END:VCARD", true)) {
                val first = card["first"] as? String ?: ""
                val last = card["last"] as? String ?: ""
                var fn = card["fn"] as? String ?: ""
                val (f, l) = if (first.isBlank() && last.isBlank() && fn.isNotBlank()) {
                    val parts = fn.trim().split(" ", limit = 2); parts[0] to parts.getOrElse(1) { "" }
                } else first to last
                if (fn.isBlank()) fn = "$f $l".trim()
                val dupe = existing.any { e ->
                    e.contact.displayName.equals(fn, true) && (numbers.isEmpty() || e.numbers.any { en -> numbers.any { n -> MessageStore.sameNumber(n.number, en.number) } })
                }
                if (!dupe && (fn.isNotBlank() || numbers.isNotEmpty())) {
                    val photo = (card["photo"] as? ByteArray)?.let { savePhoto(context, it) }
                    app.database.contactDao().save(
                        Contact(
                            firstName = f, lastName = l, groupName = card["group"] as? String ?: "",
                            favorite = card["fav"] == true, photoPath = photo,
                        ),
                        numbers.toList(),
                    )
                    added++
                }
                cur = null
                continue
            }
            val colon = line.indexOf(':')
            if (colon < 0) continue
            val head = line.substring(0, colon)
            val value = line.substring(colon + 1)
            val name = head.substringBefore(';').uppercase().substringAfter('.')
            val params = head.uppercase()
            when (name) {
                "N" -> { val p = value.split(";"); card["last"] = unesc(p.getOrElse(0) { "" }); card["first"] = unesc(p.getOrElse(1) { "" }) }
                "FN" -> card["fn"] = unesc(value)
                "TEL" -> {
                    val label = when { "CELL" in params || "MOBILE" in params -> "Mobile"; "HOME" in params -> "Home"; "WORK" in params -> "Work"; else -> "Other" }
                    numbers += PhoneNumber(label = label, number = value.trim(), isMain = "PREF" in params)
                }
                "CATEGORIES" -> card["group"] = unesc(value.split(",").first())
                "X-COMMUNICATIONS-FAVORITE" -> card["fav"] = value.trim() == "1"
                "PHOTO" -> if ("ENCODING=B" in params || "BASE64" in params) runCatching { card["photo"] = Base64.decode(value, Base64.DEFAULT) }
            }
        }
        return added
    }

    private fun savePhoto(context: Context, bytes: ByteArray): String? = runCatching {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val file = File(File(context.filesDir, "photos").apply { mkdirs() }, "${UUID.randomUUID()}.jpg")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        file.absolutePath
    }.getOrNull()

    // ---------------------------------------------------------------- Messages

    private fun messagesJson(context: Context): Pair<String, Int> {
        val items = JSONArray()
        val cr = context.contentResolver
        runCatching {
            cr.query(Telephony.Sms.CONTENT_URI, arrayOf("address", "body", "date", "type", "read"), null, null, "date ASC")?.use { c ->
                while (c.moveToNext()) items.put(JSONObject().apply {
                    put("kind", "sms"); put("address", c.getString(0) ?: ""); put("body", c.getString(1) ?: "")
                    put("date", c.getLong(2)); put("type", c.getInt(3)); put("read", c.getInt(4))
                })
            }
        }
        runCatching {
            MessageStore.threads(context).forEach { t ->
                val address = t.addresses.firstOrNull() ?: return@forEach
                MessageStore.messages(context, t.id).filter { it.isMms }.forEach { m ->
                    val images = JSONArray()
                    m.images.forEach { uri ->
                        runCatching { cr.openInputStream(uri)?.use { images.put(Base64.encodeToString(it.readBytes(), Base64.NO_WRAP)) } }
                    }
                    items.put(JSONObject().apply {
                        put("kind", "mms"); put("address", if (m.outgoing) address else (m.from ?: address)); put("body", m.body)
                        put("date", m.date); put("outgoing", m.outgoing); put("images", images)
                    })
                }
            }
        }
        return JSONObject().put("app", "Communications").put("kind", "messages").put("items", items).toString() to items.length()
    }

    private fun importMessages(context: Context, items: JSONArray): Int {
        val cr = context.contentResolver
        var added = 0
        for (i in 0 until items.length()) {
            val o = items.getJSONObject(i)
            val address = o.optString("address")
            if (address.isBlank()) continue
            val date = o.optLong("date")
            val body = o.optString("body")
            if (o.optString("kind") == "sms") {
                val exists = cr.query(Telephony.Sms.CONTENT_URI, arrayOf("_id"), "date = ? AND body = ?", arrayOf(date.toString(), body), null)?.use { it.count > 0 } ?: false
                if (exists) continue
                val v = ContentValues().apply {
                    put("address", address); put("body", body); put("date", date); put("type", o.optInt("type", 1))
                    put("read", o.optInt("read", 1)); put("seen", 1); put("thread_id", MessageStore.threadId(context, address))
                }
                if (runCatching { cr.insert(Telephony.Sms.CONTENT_URI, v) }.getOrNull() != null) added++
            } else {
                val outgoing = o.optBoolean("outgoing")
                val parts = mutableListOf<com.blackout.communications.chats.Pdu.Part>()
                val imgs = o.optJSONArray("images") ?: JSONArray()
                for (j in 0 until imgs.length()) parts += com.blackout.communications.chats.Pdu.Part("image/jpeg", Base64.decode(imgs.getString(j), Base64.DEFAULT), name = "image$j.jpg")
                if (body.isNotBlank()) parts += com.blackout.communications.chats.Pdu.Part("text/plain", body.toByteArray(), name = "text.txt")
                if (parts.isEmpty()) continue
                val box = if (outgoing) Telephony.Mms.MESSAGE_BOX_SENT else Telephony.Mms.MESSAGE_BOX_INBOX
                val row = MessageStore.saveMms(context, box, MessageStore.threadId(context, address), address, date / 1000, parts, null)
                if (row != null) {
                    cr.update(row, ContentValues().apply { put("read", 1); put("seen", 1) }, null, null)
                    added++
                }
            }
        }
        return added
    }

    // ---------------------------------------------------------------- Call history

    private fun callsJson(context: Context): Pair<String, Int> {
        val items = JSONArray()
        runCatching {
            context.contentResolver.query(CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.TYPE), null, null, "${CallLog.Calls.DATE} ASC")?.use { c ->
                while (c.moveToNext()) items.put(JSONObject().apply {
                    put("number", c.getString(0) ?: ""); put("date", c.getLong(1)); put("duration", c.getLong(2)); put("type", c.getInt(3))
                })
            }
        }
        return JSONObject().put("app", "Communications").put("kind", "calls").put("items", items).toString() to items.length()
    }

    private fun importCalls(context: Context, items: JSONArray): Int {
        val cr = context.contentResolver
        var added = 0
        for (i in 0 until items.length()) {
            val o = items.getJSONObject(i)
            val date = o.optLong("date")
            val number = o.optString("number")
            val exists = runCatching {
                cr.query(CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls._ID), "${CallLog.Calls.DATE} = ? AND ${CallLog.Calls.NUMBER} = ?", arrayOf(date.toString(), number), null)?.use { it.count > 0 }
            }.getOrNull() ?: false
            if (exists) continue
            val v = ContentValues().apply {
                put(CallLog.Calls.NUMBER, number); put(CallLog.Calls.DATE, date); put(CallLog.Calls.DURATION, o.optLong("duration"))
                put(CallLog.Calls.TYPE, o.optInt("type", CallLog.Calls.INCOMING_TYPE)); put(CallLog.Calls.NEW, 0)
            }
            if (runCatching { cr.insert(CallLog.Calls.CONTENT_URI, v) }.getOrNull() != null) added++
        }
        return added
    }

    // ---------------------------------------------------------------- Import

    /** Reads a backup file and adds what's missing. Returns a sentence to show. */
    suspend fun import(context: Context, file: Uri): String = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(file)!!.use { input ->
                val out = ByteArrayOutputStream(); input.copyTo(out); out.toByteArray()
            }
            val text = String(bytes, Charsets.UTF_8)
            when {
                text.trimStart().startsWith("BEGIN:VCARD", ignoreCase = true) -> "Added ${importVcf(context, text)} contacts."
                text.trimStart().startsWith("{") -> {
                    val o = JSONObject(text)
                    val items = o.optJSONArray("items") ?: JSONArray()
                    when (o.optString("kind")) {
                        "messages" -> "Added ${importMessages(context, items)} messages."
                        "calls" -> "Added ${importCalls(context, items)} calls to the history."
                        else -> "That file isn't a Communications backup."
                    }
                }
                else -> "That file isn't a Communications backup."
            }
        } catch (e: SecurityException) {
            "Communications must be your phone and texting app to import messages and calls."
        } catch (e: Exception) {
            "Couldn't read that file."
        }
    }
}
