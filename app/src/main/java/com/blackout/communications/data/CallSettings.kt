package com.blackout.communications.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Who sees the "Message" button on the incoming call screen. */
enum class DeclineAudience(val label: String) {
    Unknown("Unknown numbers only"),
    Everyone("All callers"),
    Selected("Selected contacts only"),
    Off("Off"),
}

data class CallPrefs(
    val unknownToVoicemail: Boolean = false,
    val blockHidden: Boolean = false,
    val blockNotInContacts: Boolean = false,
    val declineAudience: DeclineAudience = DeclineAudience.Unknown,
    val declineSelected: Set<Long> = emptySet(),
    val quickReplies: List<String> = DEFAULT_REPLIES,
    val autoRecord: Boolean = false,
    val recordExempt: Set<Long> = emptySet(),
) {
    companion object {
        val DEFAULT_REPLIES = listOf("Please text me.", "Can't talk now. Call you later.", "Who is this?")
    }
}

/** Call settings, saved on the phone only. */
class CallSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("call_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<CallPrefs> = _state

    val current: CallPrefs get() = _state.value

    fun update(change: (CallPrefs) -> CallPrefs) {
        val next = change(_state.value)
        prefs.edit()
            .putBoolean("unknownToVoicemail", next.unknownToVoicemail)
            .putBoolean("blockHidden", next.blockHidden)
            .putBoolean("blockNotInContacts", next.blockNotInContacts)
            .putString("declineAudience", next.declineAudience.name)
            .putStringSet("declineSelected", next.declineSelected.map { it.toString() }.toSet())
            .putString("quickReplies", next.quickReplies.joinToString("\n"))
            .putBoolean("autoRecord", next.autoRecord)
            .putStringSet("recordExempt", next.recordExempt.map { it.toString() }.toSet())
            .apply()
        _state.value = next
    }

    private fun read(): CallPrefs = CallPrefs(
        unknownToVoicemail = prefs.getBoolean("unknownToVoicemail", false),
        blockHidden = prefs.getBoolean("blockHidden", false),
        blockNotInContacts = prefs.getBoolean("blockNotInContacts", false),
        declineAudience = runCatching { DeclineAudience.valueOf(prefs.getString("declineAudience", null) ?: "") }
            .getOrDefault(DeclineAudience.Unknown),
        declineSelected = prefs.getStringSet("declineSelected", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet(),
        quickReplies = prefs.getString("quickReplies", null)?.split("\n")?.filter { it.isNotBlank() }
            ?.ifEmpty { null } ?: CallPrefs.DEFAULT_REPLIES,
        autoRecord = prefs.getBoolean("autoRecord", false),
        recordExempt = prefs.getStringSet("recordExempt", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet(),
    )

    // Calls that were sent to voicemail, so the call history can label them.
    private val screened = context.getSharedPreferences("screened_calls", Context.MODE_PRIVATE)

    fun rememberSentToVoicemail(number: String, time: Long) {
        val list = screened.getString("list", "")!!.split("\n").filter { it.isNotBlank() }.takeLast(299)
        screened.edit().putString("list", (list + "$time|$number").joinToString("\n")).apply()
    }

    /** Pairs of (time, number) for calls sent to voicemail. */
    fun sentToVoicemail(): List<Pair<Long, String>> =
        screened.getString("list", "")!!.split("\n").mapNotNull { line ->
            val parts = line.split("|", limit = 2)
            val t = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            t to (parts.getOrNull(1) ?: "")
        }
}
