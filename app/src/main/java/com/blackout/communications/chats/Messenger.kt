package com.blackout.communications.chats

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.widget.Toast
import com.blackout.communications.CommunicationsApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** Sends texts and picture messages through your carrier, and saves them in the phone's message storage. */
object Messenger {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Suppress("DEPRECATION")
    fun smsManager(context: Context): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(SmsManager::class.java) else SmsManager.getDefault()

    /** Lets the phone's own MMS service read and write our picture-message files. */
    fun grantMms(context: Context, uri: Uri) {
        for (pkg in listOf("com.android.phone", "com.android.mms.service")) {
            runCatching { context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        }
    }

    fun sendText(context: Context, address: String, body: String, threadId: Long? = null) {
        val app = context.applicationContext
        scope.launch {
            val thread = threadId ?: MessageStore.threadId(app, address)
            val row = MessageStore.saveOutgoingSms(app, address, body, thread) ?: return@launch
            sendSmsRow(app, row, address, body)
        }
    }

    /** Tries an SMS again from its saved row. */
    fun retrySms(context: Context, rowId: Long, address: String, body: String) {
        val app = context.applicationContext
        scope.launch {
            val row = Uri.parse("content://sms/$rowId")
            MessageStore.setSmsType(app, row, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
            sendSmsRow(app, row, address, body)
        }
    }

    private fun sendSmsRow(context: Context, row: Uri, address: String, body: String) {
        val sms = smsManager(context)
        val parts = sms.divideMessage(body)
        val code = row.lastPathSegment?.toIntOrNull() ?: 0
        val sent = ArrayList<PendingIntent>()
        val delivered = ArrayList<PendingIntent>()
        parts.forEachIndexed { i, _ ->
            val last = i == parts.size - 1
            sent += PendingIntent.getBroadcast(
                context, code * 10 + i,
                Intent(context, SmsStatusReceiver::class.java).setAction(SmsStatusReceiver.SENT).setData(row).putExtra("last", last),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            delivered += PendingIntent.getBroadcast(
                context, code * 10 + i + 5,
                Intent(context, SmsStatusReceiver::class.java).setAction(SmsStatusReceiver.DELIVERED).setData(row),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
        }
        try {
            if (parts.size > 1) sms.sendMultipartTextMessage(address, null, parts, sent, delivered)
            else sms.sendTextMessage(address, null, body, sent.first(), delivered.first())
        } catch (e: Exception) {
            MessageStore.setSmsType(context, row, Telephony.Sms.MESSAGE_TYPE_FAILED)
        }
    }

    /** Sends a picture message (pictures plus optional text) to one person. */
    fun sendPictures(context: Context, address: String, text: String, images: List<Uri>, threadId: Long? = null) {
        val app = context.applicationContext
        scope.launch {
            val parts = ArrayList<Pdu.Part>()
            images.forEachIndexed { i, uri -> compress(app, uri)?.let { parts += Pdu.Part("image/jpeg", it, name = "image$i.jpg") } }
            if (text.isNotBlank()) parts += Pdu.Part("text/plain", text.toByteArray(Charsets.UTF_8), name = "text.txt")
            if (parts.isEmpty()) return@launch
            val thread = threadId ?: MessageStore.threadId(app, address)
            val tid = UUID.randomUUID().toString().replace("-", "").take(16)
            val row = MessageStore.saveMms(app, Telephony.Mms.MESSAGE_BOX_OUTBOX, thread, address, System.currentTimeMillis() / 1000, parts, tid)
            val file = MmsFileProvider.newFile(app)
            file.writeBytes(Pdu.sendRequest(address, tid, parts))
            grantMms(app, MmsFileProvider.uriFor(app, file))
            val sentIntent = PendingIntent.getBroadcast(
                app, (row?.lastPathSegment?.toIntOrNull() ?: 0) + 100_000,
                Intent(app, MmsSentReceiver::class.java).setData(row).putExtra("file", file.name),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            try {
                smsManager(app).sendMultimediaMessage(app, MmsFileProvider.uriFor(app, file), null, null, sentIntent)
            } catch (e: Exception) {
                row?.let { MessageStore.setMmsBox(app, it, Telephony.Mms.MESSAGE_BOX_FAILED) }
                file.delete()
            }
        }
    }

    /** Shrinks a picture so carriers accept it (about 300 KB or less). */
    private fun compress(context: Context, uri: Uri): ByteArray? = runCatching {
        var maxSide = 1280
        var result: ByteArray
        do {
            val side = maxSide
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
                val s = minOf(1f, side.toFloat() / maxOf(info.size.width, info.size.height))
                d.setTargetSize((info.size.width * s).toInt().coerceAtLeast(1), (info.size.height * s).toInt().coerceAtLeast(1))
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            result = out.toByteArray()
            maxSide = (maxSide * 0.75).toInt()
        } while (result.size > 300_000 && maxSide > 320)
        result
    }.getOrNull()
}

/** Updates a sent text to "Sent", "Delivered" or "Failed". */
class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val row = intent.data ?: return
        when (intent.action) {
            SENT -> if (resultCode == Activity.RESULT_OK) {
                if (intent.getBooleanExtra("last", true)) {
                    MessageStore.setSmsType(context, row, Telephony.Sms.MESSAGE_TYPE_SENT)
                    TxLog.send(context, "Sent")
                }
            } else {
                MessageStore.setSmsType(context, row, Telephony.Sms.MESSAGE_TYPE_FAILED)
                val why = TxLog.smsError(resultCode, intent.getIntExtra("errorCode", -1))
                TxLog.send(context, "Failed", why)
                Toast.makeText(context, "A text didn't send: $why. Tap it to try again.", Toast.LENGTH_LONG).show()
            }
            DELIVERED -> {
                val pdu = intent.getByteArrayExtra("pdu") ?: return
                val msg = SmsMessage.createFromPdu(pdu, intent.getStringExtra("format")) ?: return
                val status = msg.status
                when {
                    status == 0 -> {
                        MessageStore.setSmsStatus(context, row, Telephony.Sms.STATUS_COMPLETE)
                        TxLog.send(context, "Delivered")
                    }
                    status >= 0x40 -> {
                        MessageStore.setSmsStatus(context, row, Telephony.Sms.STATUS_FAILED)
                        TxLog.send(context, "Not delivered", "carrier status $status")
                    }
                }
            }
        }
    }

    companion object {
        const val SENT = "com.blackout.communications.SMS_SENT"
        const val DELIVERED = "com.blackout.communications.SMS_DELIVERED"
    }
}

class MmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringExtra("file")?.let { File(MmsFileProvider.dir(context), it).delete() }
        val row = intent.data ?: return
        MessageStore.setMmsBox(
            context, row,
            if (resultCode == Activity.RESULT_OK) Telephony.Mms.MESSAGE_BOX_SENT else Telephony.Mms.MESSAGE_BOX_FAILED,
        )
        if (resultCode == Activity.RESULT_OK) {
            TxLog.send(context, "Sent", "picture message")
        } else {
            val why = TxLog.mmsError(resultCode, intent.getIntExtra("android.telephony.extra.MMS_HTTP_STATUS", 0))
            TxLog.send(context, "Failed", "picture message: $why")
            Toast.makeText(context, "A picture message didn't send: $why.", Toast.LENGTH_LONG).show()
        }
    }
}

/** Receives texts while Communications is the texting app. Blocked numbers never reach this point. */
class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return
        val address = messages[0].displayOriginatingAddress ?: messages[0].originatingAddress ?: "Unknown"
        val body = messages.joinToString("") { it.displayMessageBody ?: it.messageBody ?: "" }
        val sentAt = messages[0].timestampMillis
        val subId = intent.getIntExtra("subscription", -1)
        val app = context.applicationContext as CommunicationsApp
        val pending = goAsync()
        Messenger.scope.launch {
            try {
                val thread = runCatching { MessageStore.threadId(app, address) }.getOrNull()
                val saved = MessageStore.saveIncomingSms(app, address, body, sentAt, subId, thread = thread)
                // Never lose a text: if it can't be saved now, keep it until it can.
                if (saved == null) runCatching { PendingTexts.add(app, address, body, sentAt, subId) }
                RxLog.mark(app, RxLog.SMS)
                // Alert first, with the text we already have; nothing slow runs before this.
                MessageNotifications.showFor(app, thread, address, body, textOnly = saved != null)
                runCatching { PendingTexts.flush(app) } // older texts that were waiting
            } finally {
                pending.finish()
            }
        }
    }
}

/** Receives the notice that a picture message is waiting, then asks the phone to download it. */
class MmsWapPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        val data = intent.getByteArrayExtra("data") ?: return
        val notice = Pdu.parse(data)
        if (notice != null && notice.type != Pdu.TYPE_NOTIFICATION_IND) return // delivery and read reports
        val location = notice?.contentLocation ?: Pdu.findLocation(data)
        if (location == null) {
            MessageNotifications.alert(context, data.contentHashCode(), "Picture message", "A picture message arrived but couldn't be read.")
            return
        }
        RxLog.mark(context, RxLog.MMS_NOTICE)
        MmsInbox.onNotice(context, location, notice?.from)
    }
}

/** Saves a downloaded picture message and shows a notification. A failed download is retried by MmsInbox. */
class MmsDownloadedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val file = File(MmsFileProvider.dir(context), intent.getStringExtra("file") ?: return)
        if (resultCode != Activity.RESULT_OK || !file.exists()) {
            file.delete()
            // MmsInbox keeps trying; the reason is kept so Message check can show it.
            TxLog.downloadProblem(context, TxLog.mmsError(resultCode, intent.getIntExtra("android.telephony.extra.MMS_HTTP_STATUS", 0)))
            return
        }
        val location = intent.getStringExtra("location")
        val noticeFrom = intent.getStringExtra("from")?.takeIf { it.isNotBlank() }
        val app = context.applicationContext as CommunicationsApp
        val pending = goAsync()
        Messenger.scope.launch {
            var from = noticeFrom ?: "Unknown"
            try {
                val bytes = file.readBytes()
                file.delete()
                // A second copy of the same message (carrier resent the notice) is dropped.
                if (location != null && !MmsInbox.done(app, location)) return@launch
                val msg = Pdu.parse(bytes)
                from = msg?.from?.takeIf { it.isNotBlank() } ?: from
                val all = msg?.parts.orEmpty()
                val shown = all.filter { it.isText || it.isImage || it.isVideo }
                val hidden = all.count { !it.isText && !it.isImage && !it.isVideo && !it.contentType.startsWith("application/smil") }
                // Something this app can't show still leaves a visible line, never an empty message.
                val note = when {
                    msg == null -> "[Picture message couldn't be read]"
                    hidden > 0 -> "[$hidden attachment(s) this app can't show]"
                    shown.isEmpty() -> "[Empty picture message]"
                    else -> null
                }
                val parts = shown + listOfNotNull(note?.let { Pdu.Part("text/plain", it.toByteArray(Charsets.UTF_8)) })
                val thread = MessageStore.threadId(app, from)
                MessageStore.saveMms(
                    app, Telephony.Mms.MESSAGE_BOX_INBOX, thread, from,
                    msg?.dateSec ?: (System.currentTimeMillis() / 1000), parts, msg?.transactionId,
                )
                RxLog.mark(app, RxLog.MMS_SAVED)
                val text = parts.firstOrNull { it.isText }?.text() ?: if (parts.any { it.isVideo }) "Video message" else "Picture message"
                MessageNotifications.showFor(app, thread, from, text)
            } catch (e: Exception) {
                MessageNotifications.alert(app, from.hashCode(), "Picture message", "A picture message from $from arrived but couldn't be saved.")
            } finally {
                pending.finish()
            }
        }
    }
}
