package com.blackout.communications.chats

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import com.blackout.communications.calls.Phone

/** One message in a conversation (text or picture). */
data class ChatMessage(
    val id: Long,
    val isMms: Boolean,
    val outgoing: Boolean,
    val body: String,
    val date: Long,
    val status: Status,
    val images: List<Uri>,
    val from: String?,
) {
    enum class Status { Received, Sending, Sent, Delivered, Failed }
}

/** One conversation in the Chats list. */
data class ChatThread(
    val id: Long,
    val addresses: List<String>,
    val snippet: String,
    val date: Long,
    val unread: Int,
)

/**
 * Reads and writes the phone's message storage. As the texting app, Communications must
 * save every message it receives or sends there itself.
 */
object MessageStore {
    private val CONVERSATIONS: Uri = Uri.parse("content://mms-sms/conversations?simple=true")
    private val CANONICAL: Uri = Uri.parse("content://mms-sms/canonical-addresses")
    private val MMS_PART: Uri = Uri.parse("content://mms/part")

    fun threadId(context: Context, address: String): Long =
        Telephony.Threads.getOrCreateThreadId(context, address)

    fun threadId(context: Context, addresses: Set<String>): Long =
        Telephony.Threads.getOrCreateThreadId(context, addresses)

    // ---------------------------------------------------------------- Reading

    fun threads(context: Context): List<ChatThread> {
        val cr = context.contentResolver
        val canonical = HashMap<Long, String>()
        runCatching {
            cr.query(CANONICAL, arrayOf("_id", "address"), null, null, null)?.use { c ->
                while (c.moveToNext()) canonical[c.getLong(0)] = c.getString(1).orEmpty()
            }
        }
        val unread = HashMap<Long, Int>()
        runCatching {
            cr.query(Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms.THREAD_ID), "${Telephony.Sms.READ} = 0 AND ${Telephony.Sms.TYPE} = 1", null, null)?.use { c ->
                while (c.moveToNext()) unread.merge(c.getLong(0), 1, Int::plus)
            }
            cr.query(Telephony.Mms.CONTENT_URI, arrayOf(Telephony.Mms.THREAD_ID), "${Telephony.Mms.READ} = 0 AND ${Telephony.Mms.MESSAGE_BOX} = 1", null, null)?.use { c ->
                while (c.moveToNext()) unread.merge(c.getLong(0), 1, Int::plus)
            }
        }
        val out = ArrayList<ChatThread>()
        runCatching {
            cr.query(CONVERSATIONS, arrayOf("_id", "date", "message_count", "recipient_ids", "snippet", "has_attachment"), null, null, "date DESC")?.use { c ->
                while (c.moveToNext()) {
                    if (c.getInt(2) == 0) continue // Empty conversations stay hidden.
                    val ids = c.getString(3).orEmpty().split(" ").mapNotNull { it.toLongOrNull() }
                    val addresses = ids.mapNotNull { canonical[it] }.filter { it.isNotBlank() }
                    val snippet = c.getString(4).orEmpty().ifBlank { if (c.getInt(5) != 0) "Photo" else "" }
                    out += ChatThread(c.getLong(0), addresses, snippet, c.getLong(1), unread[c.getLong(0)] ?: 0)
                }
            }
        }
        return out
    }

    fun messages(context: Context, threadId: Long): List<ChatMessage> {
        val cr = context.contentResolver
        val out = ArrayList<ChatMessage>()
        runCatching {
            cr.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.STATUS),
                "${Telephony.Sms.THREAD_ID} = ?", arrayOf(threadId.toString()), null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val type = c.getInt(4)
                    val status = when (type) {
                        Telephony.Sms.MESSAGE_TYPE_INBOX -> ChatMessage.Status.Received
                        Telephony.Sms.MESSAGE_TYPE_FAILED -> ChatMessage.Status.Failed
                        Telephony.Sms.MESSAGE_TYPE_OUTBOX, Telephony.Sms.MESSAGE_TYPE_QUEUED -> ChatMessage.Status.Sending
                        else -> when (c.getInt(5)) {
                            Telephony.Sms.STATUS_COMPLETE -> ChatMessage.Status.Delivered
                            Telephony.Sms.STATUS_FAILED -> ChatMessage.Status.Failed
                            else -> ChatMessage.Status.Sent
                        }
                    }
                    out += ChatMessage(c.getLong(0), false, type != Telephony.Sms.MESSAGE_TYPE_INBOX, c.getString(2).orEmpty(), c.getLong(3), status, emptyList(), c.getString(1))
                }
            }
        }
        runCatching {
            cr.query(
                Telephony.Mms.CONTENT_URI,
                arrayOf(Telephony.Mms._ID, Telephony.Mms.DATE, Telephony.Mms.MESSAGE_BOX),
                "${Telephony.Mms.THREAD_ID} = ?", arrayOf(threadId.toString()), null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val box = c.getInt(2)
                    val (text, images) = mmsParts(context, id)
                    val status = when (box) {
                        Telephony.Mms.MESSAGE_BOX_INBOX -> ChatMessage.Status.Received
                        Telephony.Mms.MESSAGE_BOX_OUTBOX -> ChatMessage.Status.Sending
                        Telephony.Mms.MESSAGE_BOX_FAILED -> ChatMessage.Status.Failed
                        else -> ChatMessage.Status.Sent
                    }
                    out += ChatMessage(id, true, box != Telephony.Mms.MESSAGE_BOX_INBOX, text, c.getLong(1) * 1000, status, images, mmsSender(context, id))
                }
            }
        }
        return out.sortedBy { it.date }
    }

    private fun mmsParts(context: Context, id: Long): Pair<String, List<Uri>> {
        val texts = ArrayList<String>()
        val images = ArrayList<Uri>()
        runCatching {
            context.contentResolver.query(MMS_PART, arrayOf("_id", "ct", "text"), "mid = ?", arrayOf(id.toString()), null)?.use { c ->
                while (c.moveToNext()) {
                    val ct = c.getString(1).orEmpty()
                    when {
                        ct == "text/plain" -> c.getString(2)?.let { texts += it }
                        ct.startsWith("image/") -> images += Uri.parse("content://mms/part/${c.getLong(0)}")
                    }
                }
            }
        }
        return texts.joinToString("\n") to images
    }

    private fun mmsSender(context: Context, id: Long): String? = runCatching {
        context.contentResolver.query(Uri.parse("content://mms/$id/addr"), arrayOf("address"), "type = 137", null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    // ---------------------------------------------------------------- Writing

    fun saveIncomingSms(context: Context, address: String, body: String, sentAt: Long, subId: Int): Uri? {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.DATE_SENT, sentAt)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            put(Telephony.Sms.SUBSCRIPTION_ID, subId)
            put(Telephony.Sms.THREAD_ID, threadId(context, address))
        }
        return runCatching { context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values) }.getOrNull()
    }

    fun saveOutgoingSms(context: Context, address: String, body: String, threadId: Long): Uri? {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
            put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_PENDING)
            put(Telephony.Sms.THREAD_ID, threadId)
        }
        return runCatching { context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values) }.getOrNull()
    }

    fun setSmsType(context: Context, uri: Uri, type: Int) {
        runCatching { context.contentResolver.update(uri, ContentValues().apply { put(Telephony.Sms.TYPE, type) }, null, null) }
    }

    fun setSmsStatus(context: Context, uri: Uri, status: Int) {
        runCatching { context.contentResolver.update(uri, ContentValues().apply { put(Telephony.Sms.STATUS, status) }, null, null) }
    }

    /** Saves a picture message. box = Telephony.Mms.MESSAGE_BOX_INBOX or _OUTBOX. Returns its row. */
    fun saveMms(context: Context, box: Int, threadId: Long, address: String, dateSec: Long, parts: List<Pdu.Part>, transactionId: String?): Uri? {
        val cr = context.contentResolver
        val values = ContentValues().apply {
            put(Telephony.Mms.THREAD_ID, threadId)
            put(Telephony.Mms.DATE, dateSec)
            put(Telephony.Mms.DATE_SENT, dateSec)
            put(Telephony.Mms.MESSAGE_BOX, box)
            put(Telephony.Mms.READ, if (box == Telephony.Mms.MESSAGE_BOX_INBOX) 0 else 1)
            put(Telephony.Mms.SEEN, if (box == Telephony.Mms.MESSAGE_BOX_INBOX) 0 else 1)
            put(Telephony.Mms.MESSAGE_TYPE, if (box == Telephony.Mms.MESSAGE_BOX_INBOX) Pdu.TYPE_RETRIEVE_CONF else Pdu.TYPE_SEND_REQ)
            put(Telephony.Mms.MMS_VERSION, 0x12)
            put(Telephony.Mms.CONTENT_TYPE, "application/vnd.wap.multipart.mixed")
            if (transactionId != null) put(Telephony.Mms.TRANSACTION_ID, transactionId)
            put(Telephony.Mms.TEXT_ONLY, if (parts.all { it.isText }) 1 else 0)
        }
        val row = runCatching { cr.insert(Telephony.Mms.CONTENT_URI, values) }.getOrNull() ?: return null
        val id = row.lastPathSegment ?: return row
        runCatching {
            val addr = ContentValues().apply {
                put("address", if (box == Telephony.Mms.MESSAGE_BOX_INBOX) address else "insert-address-token")
                put("type", 137) // from
                put("charset", 106)
            }
            cr.insert(Uri.parse("content://mms/$id/addr"), addr)
            if (box != Telephony.Mms.MESSAGE_BOX_INBOX) {
                cr.insert(Uri.parse("content://mms/$id/addr"), ContentValues().apply {
                    put("address", address); put("type", 151); put("charset", 106) // to
                })
            }
        }
        parts.forEachIndexed { i, part ->
            runCatching {
                val pv = ContentValues().apply {
                    put("mid", id)
                    put("ct", part.contentType.substringBefore(';'))
                    put("cl", part.name ?: "part$i")
                    put("seq", i)
                    if (part.isText) { put("chset", 106); put("text", part.text()) }
                }
                val partUri = cr.insert(Uri.parse("content://mms/$id/part"), pv)
                if (!part.isText && partUri != null) cr.openOutputStream(partUri)?.use { it.write(part.data) }
            }
        }
        return row
    }

    fun setMmsBox(context: Context, uri: Uri, box: Int) {
        runCatching { context.contentResolver.update(uri, ContentValues().apply { put(Telephony.Mms.MESSAGE_BOX, box) }, null, null) }
    }

    fun markRead(context: Context, threadId: Long) {
        val v = ContentValues().apply { put("read", 1); put("seen", 1) }
        runCatching { context.contentResolver.update(Telephony.Sms.CONTENT_URI, v, "${Telephony.Sms.THREAD_ID} = ? AND read = 0", arrayOf(threadId.toString())) }
        runCatching { context.contentResolver.update(Telephony.Mms.CONTENT_URI, v, "${Telephony.Mms.THREAD_ID} = ? AND read = 0", arrayOf(threadId.toString())) }
    }

    fun deleteThread(context: Context, threadId: Long) {
        runCatching { context.contentResolver.delete(Uri.parse("content://mms-sms/conversations/$threadId"), null, null) }
    }

    fun deleteMessage(context: Context, m: ChatMessage) {
        val uri = if (m.isMms) Uri.parse("content://mms/${m.id}") else Uri.parse("content://sms/${m.id}")
        runCatching { context.contentResolver.delete(uri, null, null) }
    }

    /** The other people in a conversation. */
    fun addresses(context: Context, threadId: Long): List<String> =
        threads(context).firstOrNull { it.id == threadId }?.addresses.orEmpty()

    fun sameNumber(a: String, b: String) = Phone.matchKey(a) == Phone.matchKey(b)
}
