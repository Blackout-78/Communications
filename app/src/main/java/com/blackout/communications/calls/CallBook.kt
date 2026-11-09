package com.blackout.communications.calls

import android.content.Context
import android.os.Build
import com.blackout.communications.BuildConfigInfo
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

/**
 * A private record of what happened to each call: how it ended (with Android's own reason code)
 * and what the call screening decided. Stays on the phone. Settings > Call check shows it, and
 * its Copy report button gives text you can paste to whoever is helping you.
 */
object CallBook {
    private const val MAX = 60

    class Entry(val time: Long, val kind: String, val number: String, val result: String, val code: Int, val reason: String, val seconds: Long, val problem: Boolean)

    private fun prefs(c: Context) = c.getSharedPreferences("call_book", Context.MODE_PRIVATE)

    private fun load(c: Context): JSONArray =
        runCatching { JSONArray(prefs(c).getString("list", "[]")) }.getOrDefault(JSONArray())

    /** kind: "in" or "out" for how a call ended, "screen" for what screening decided. */
    @Synchronized
    fun add(c: Context, kind: String, number: String, result: String, code: Int = -1, reason: String = "", seconds: Long = 0, problem: Boolean = false) {
        val list = load(c)
        list.put(
            JSONObject().put("t", System.currentTimeMillis()).put("k", kind).put("n", number).put("r", result)
                .put("c", code).put("w", reason).put("s", seconds).put("p", problem),
        )
        val kept = JSONArray()
        for (i in maxOf(0, list.length() - MAX) until list.length()) kept.put(list.get(i))
        prefs(c).edit().putString("list", kept.toString()).apply()
    }

    @Synchronized
    fun recent(c: Context, count: Int): List<Entry> {
        val list = load(c)
        val out = ArrayList<Entry>()
        for (i in list.length() - 1 downTo maxOf(0, list.length() - count)) {
            val o = list.getJSONObject(i)
            out += Entry(o.getLong("t"), o.getString("k"), o.getString("n"), o.getString("r"), o.getInt("c"), o.optString("w"), o.optLong("s"), o.optBoolean("p"))
        }
        return out
    }

    /** Android's name for why a call ended. */
    fun causeName(code: Int): String = when (code) {
        0 -> "UNKNOWN"
        1 -> "ERROR"
        2 -> "LOCAL (you hung up)"
        3 -> "REMOTE (they hung up)"
        4 -> "CANCELED"
        5 -> "MISSED"
        6 -> "REJECTED"
        7 -> "BUSY"
        8 -> "RESTRICTED"
        9 -> "OTHER"
        10 -> "CONNECTION_MANAGER_NOT_SUPPORTED"
        11 -> "ANSWERED_ELSEWHERE"
        12 -> "CALL_PULLED"
        else -> "CODE $code"
    }

    /** Only the last 4 digits, so a copied report doesn't spread whole phone numbers. */
    fun mask(number: String): String = if (number.length > 4) "***" + number.takeLast(4) else number.ifBlank { "hidden" }

    fun whenText(time: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(time))

    fun line(e: Entry): String = buildString {
        append(whenText(e.time)).append("  ")
        append(when (e.kind) { "in" -> "Incoming"; "out" -> "Outgoing"; else -> "Screening" })
        append("  ").append(mask(e.number)).append("  ").append(e.result)
        if (e.code >= 0 && e.kind != "screen") append("  [code ${e.code}]")
        if (e.reason.isNotBlank()) append("  reason: ").append(e.reason)
        if (e.seconds > 0) append("  ${e.seconds}s")
        if (e.problem) append("  PROBLEM")
    }

    fun report(c: Context): String = buildString {
        appendLine("Communications call report (app ${BuildConfigInfo.versionName(c)}, Android ${Build.VERSION.RELEASE})")
        recent(c, 25).forEach { appendLine(line(it)) }
    }
}
