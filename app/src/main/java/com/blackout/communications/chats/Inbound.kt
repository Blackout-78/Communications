package com.blackout.communications.chats

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject

/*
 * Safety nets for incoming messages. Rule: a message that reaches this phone is never
 * lost silently. It is saved, or kept until it can be saved, and it always alerts you.
 * Everything here stays in the app's private storage.
 */

/** When the last text and picture message arrived. Shown in Settings > Message check. */
object RxLog {
    const val SMS = "sms"
    const val MMS_NOTICE = "mms_notice"
    const val MMS_SAVED = "mms_saved"

    private fun prefs(c: Context) = c.getSharedPreferences("rx_log", Context.MODE_PRIVATE)

    fun mark(c: Context, key: String) {
        prefs(c).edit().putLong(key, System.currentTimeMillis()).apply()
    }

    fun last(c: Context, key: String): Long = prefs(c).getLong(key, 0L)
}

/**
 * Texts that arrived but couldn't be saved yet (storage full, message database busy).
 * They wait here and are saved the next time a text arrives or the app opens.
 */
object PendingTexts {
    private fun prefs(c: Context) = c.getSharedPreferences("pending_texts", Context.MODE_PRIVATE)

    private fun load(c: Context): JSONArray =
        runCatching { JSONArray(prefs(c).getString("list", "[]")) }.getOrDefault(JSONArray())

    @Synchronized
    fun count(c: Context): Int = load(c).length()

    @Synchronized
    fun add(c: Context, address: String, body: String, sentAt: Long, subId: Int) {
        val list = load(c)
        list.put(JSONObject().put("a", address).put("b", body).put("s", sentAt).put("i", subId).put("t", System.currentTimeMillis()))
        prefs(c).edit().putString("list", list.toString()).commit()
    }

    /** Tries to save every waiting text. Whatever still fails stays waiting. */
    @Synchronized
    fun flush(c: Context) {
        val list = load(c)
        if (list.length() == 0) return
        val keep = JSONArray()
        for (i in 0 until list.length()) {
            val o = list.getJSONObject(i)
            val saved = MessageStore.saveIncomingSms(c, o.getString("a"), o.getString("b"), o.getLong("s"), o.getInt("i"), o.optLong("t", System.currentTimeMillis()))
            if (saved == null) keep.put(o)
        }
        prefs(c).edit().putString("list", keep.toString()).commit()
    }
}

/**
 * Picture messages the carrier has announced but that haven't downloaded yet.
 * A failed download is tried again after 1, 5, 15, 60 and 240 minutes, and again whenever
 * the app opens. If it still fails you get a notification instead of silence.
 */
object MmsInbox {
    private val DELAY_MIN = longArrayOf(1, 5, 15, 60, 240)
    private const val MAX_AGE_MS = 3L * 24 * 3600 * 1000 // carriers drop the message after a few days
    private const val MIN_GAP_MS = 120_000L

    private fun prefs(c: Context) = c.getSharedPreferences("mms_pending", Context.MODE_PRIVATE)

    private fun load(c: Context): JSONArray =
        runCatching { JSONArray(prefs(c).getString("list", "[]")) }.getOrDefault(JSONArray())

    private fun save(c: Context, list: JSONArray) {
        prefs(c).edit().putString("list", list.toString()).commit()
    }

    private fun indexOf(list: JSONArray, location: String): Int {
        for (i in 0 until list.length()) if (list.getJSONObject(i).getString("l") == location) return i
        return -1
    }

    @Synchronized
    fun count(c: Context): Int = load(c).length()

    /** A new "picture message waiting" notice from the carrier. */
    @Synchronized
    fun onNotice(c: Context, location: String, from: String?) {
        val list = load(c)
        if (indexOf(list, location) >= 0) return // the carrier sent the notice twice
        list.put(JSONObject().put("l", location).put("f", from ?: "").put("n", 0).put("a", 0L).put("t", System.currentTimeMillis()))
        save(c, list)
        attempt(c, location)
    }

    /** Tries the download now and books the next try first, so one failure never ends it. */
    @Synchronized
    fun attempt(c: Context, location: String) {
        val list = load(c)
        val i = indexOf(list, location)
        if (i < 0) return
        val o = list.getJSONObject(i)
        if (System.currentTimeMillis() - o.getLong("t") > MAX_AGE_MS) {
            list.remove(i)
            save(c, list)
            cancelAlarm(c, location)
            return
        }
        val n = o.getInt("n")
        if (n >= DELAY_MIN.size) {
            val who = o.getString("f").ifBlank { "someone" }
            MessageNotifications.alert(
                c, location.hashCode(), "Picture message not downloaded",
                "From $who. Check that mobile data is on, then open Communications to try again.",
            )
            return
        }
        o.put("n", n + 1).put("a", System.currentTimeMillis())
        save(c, list)
        scheduleAlarm(c, location, DELAY_MIN[n])
        start(c, location, o.getString("f"))
    }

    /** Called when a download finished. True if it was still waiting (so this copy is the one to save). */
    @Synchronized
    fun done(c: Context, location: String): Boolean {
        val list = load(c)
        val i = indexOf(list, location)
        cancelAlarm(c, location)
        if (i < 0) return false
        list.remove(i)
        save(c, list)
        return true
    }

    /** Opening the app tries waiting picture messages again, unless one just started. */
    @Synchronized
    fun retryAll(c: Context) {
        val list = load(c)
        val now = System.currentTimeMillis()
        val due = ArrayList<String>()
        for (i in 0 until list.length()) {
            val o = list.getJSONObject(i)
            if (o.getInt("n") >= DELAY_MIN.size) o.put("n", 0) // gave up earlier; start over
            if (now - o.getLong("a") >= MIN_GAP_MS) due += o.getString("l")
        }
        save(c, list)
        due.forEach { attempt(c, it) }
    }

    private fun start(c: Context, location: String, from: String) {
        val file = MmsFileProvider.newFile(c)
        val uri = MmsFileProvider.uriFor(c, file)
        Messenger.grantMms(c, uri)
        val done = PendingIntent.getBroadcast(
            c, location.hashCode(),
            Intent(c, MmsDownloadedReceiver::class.java).putExtra("file", file.name).putExtra("from", from).putExtra("location", location),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        // Ask for the full-size message: raise the phone's own size cap so big videos aren't refused.
        val limits = android.os.Bundle().apply {
            putInt(android.telephony.SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE, 50 * 1024 * 1024)
            // Tell the carrier this phone can show full-size pictures (default is 640x480).
            putInt(android.telephony.SmsManager.MMS_CONFIG_MAX_IMAGE_WIDTH, 8192)
            putInt(android.telephony.SmsManager.MMS_CONFIG_MAX_IMAGE_HEIGHT, 8192)
        }
        runCatching { Messenger.smsManager(c).downloadMultimediaMessage(c, location, uri, limits, done) }
    }

    private fun alarmIntent(c: Context, location: String): PendingIntent = PendingIntent.getBroadcast(
        c, location.hashCode() + 7,
        Intent(c, MmsRetryReceiver::class.java).putExtra("location", location),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun scheduleAlarm(c: Context, location: String, minutes: Long) {
        runCatching {
            c.getSystemService(AlarmManager::class.java)
                .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + minutes * 60_000, alarmIntent(c, location))
        }
    }

    private fun cancelAlarm(c: Context, location: String) {
        runCatching { c.getSystemService(AlarmManager::class.java).cancel(alarmIntent(c, location)) }
    }
}

/** Wakes up to try a picture-message download again. */
class MmsRetryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        MmsInbox.attempt(context, intent.getStringExtra("location") ?: return)
    }
}

/**
 * What happened to the last text or picture message you sent, and the last picture-message
 * download problem, with Android's error codes. Shown in Settings > Message check.
 */
object TxLog {
    private fun prefs(c: Context) = c.getSharedPreferences("tx_log", Context.MODE_PRIVATE)

    /** status: "Sent", "Delivered", "Not delivered" or "Failed". */
    fun send(c: Context, status: String, detail: String = "") {
        prefs(c).edit().putLong("send_t", System.currentTimeMillis()).putString("send_s", status).putString("send_d", detail).apply()
    }

    fun downloadProblem(c: Context, detail: String) {
        prefs(c).edit().putLong("dl_t", System.currentTimeMillis()).putString("dl_d", detail).apply()
    }

    /** Time, status and detail of the last send, or null. */
    fun lastSend(c: Context): Triple<Long, String, String>? {
        val t = prefs(c).getLong("send_t", 0L)
        if (t == 0L) return null
        return Triple(t, prefs(c).getString("send_s", "") ?: "", prefs(c).getString("send_d", "") ?: "")
    }

    fun lastDownloadProblem(c: Context): Pair<Long, String>? {
        val t = prefs(c).getLong("dl_t", 0L)
        if (t == 0L) return null
        return t to (prefs(c).getString("dl_d", "") ?: "")
    }

    fun smsError(code: Int, extra: Int): String {
        val name = when (code) {
            1 -> "generic failure"
            2 -> "radio off (airplane mode?)"
            3 -> "bad message"
            4 -> "no service"
            5 -> "sending limit reached"
            6 -> "fixed dialing blocked it"
            7 -> "short code not allowed"
            8 -> "short code blocked"
            else -> "error"
        }
        return "$name (code $code${if (extra >= 0) ", carrier code $extra" else ""})"
    }

    fun mmsError(code: Int, http: Int): String {
        val name = when (code) {
            1 -> "unspecified error"
            2 -> "invalid APN"
            3 -> "can't connect to the picture-message server"
            4 -> "server refused (HTTP)"
            5 -> "network read/write error"
            6 -> "please retry"
            7 -> "picture-message settings problem"
            8 -> "mobile data is off or unavailable"
            else -> "error"
        }
        return "$name (code $code${if (http > 0) ", HTTP $http" else ""})"
    }
}
