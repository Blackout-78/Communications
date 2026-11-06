package com.blackout.communications.ui.calls

import android.Manifest
import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blackout.communications.CommunicationsApp
import com.blackout.communications.calls.Phone
import com.blackout.communications.data.ContactWithNumbers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

enum class CallType(val label: String) { Missed("Missed"), Incoming("Incoming"), Outgoing("Outgoing"), Voicemail("Sent to voicemail") }

data class CallEntry(
    val id: Long,
    val number: String,
    val date: Long,
    val durationSec: Long,
    val type: CallType,
    val contact: ContactWithNumbers?,
)

class CallsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CommunicationsApp
    private val raw = MutableStateFlow<List<RawCall>>(emptyList())
    val query = MutableStateFlow("")

    /** False until the app may read the call history. */
    val canRead = MutableStateFlow(false)

    private val contacts = app.contacts.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val entries: StateFlow<List<CallEntry>> = combine(raw, contacts, query) { calls, people, q ->
        val byKey = HashMap<String, ContactWithNumbers>()
        people.forEach { c -> c.numbers.forEach { n -> byKey[Phone.matchKey(n.number)] = c } }
        val needle = q.trim().lowercase()
        val digits = q.filter { it.isDigit() }
        calls.map { r -> CallEntry(r.id, r.number, r.date, r.duration, r.type, byKey[Phone.matchKey(r.number)]) }
            .filter { e ->
                needle.isEmpty() ||
                    (e.contact?.let { c -> "${c.contact.firstName} ${c.contact.lastName}".lowercase().contains(needle) } == true) ||
                    (digits.isNotEmpty() && e.number.filter(Char::isDigit).contains(digits))
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh()
    }
    private var observing = false

    init {
        refresh()
    }

    fun refresh() {
        val ctx = getApplication<Application>()
        val allowed = Phone.has(ctx, Manifest.permission.READ_CALL_LOG)
        canRead.value = allowed
        if (!allowed) return
        if (!observing) {
            runCatching { ctx.contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer) }
            observing = true
        }
        viewModelScope.launch { raw.value = withContext(Dispatchers.IO) { load() } }
    }

    fun delete(id: Long) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            getApplication<Application>().contentResolver.delete(CallLog.Calls.CONTENT_URI, "${CallLog.Calls._ID} = ?", arrayOf(id.toString()))
        }
    }

    fun clearAll() = viewModelScope.launch(Dispatchers.IO) {
        runCatching { getApplication<Application>().contentResolver.delete(CallLog.Calls.CONTENT_URI, null, null) }
    }

    private class RawCall(val id: Long, val number: String, val date: Long, val duration: Long, val type: CallType)

    private fun load(): List<RawCall> {
        val ctx = getApplication<Application>()
        val sentToVoicemail = app.callSettings.sentToVoicemail()
        val out = ArrayList<RawCall>()
        runCatching {
            ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.TYPE),
                null, null, "${CallLog.Calls.DATE} DESC",
            )?.use { c ->
                while (c.moveToNext() && out.size < 500) {
                    val number = c.getString(1).orEmpty()
                    val date = c.getLong(2)
                    val type = when (c.getInt(4)) {
                        CallLog.Calls.INCOMING_TYPE, CallLog.Calls.ANSWERED_EXTERNALLY_TYPE -> CallType.Incoming
                        CallLog.Calls.OUTGOING_TYPE -> CallType.Outgoing
                        CallLog.Calls.MISSED_TYPE -> CallType.Missed
                        CallLog.Calls.REJECTED_TYPE ->
                            if (sentToVoicemail.any { (t, n) -> abs(t - date) < 60_000 && Phone.matchKey(n) == Phone.matchKey(number) }) {
                                CallType.Voicemail
                            } else {
                                CallType.Missed
                            }
                        else -> null // Blocked calls and voicemail entries stay hidden.
                    } ?: continue
                    out.add(RawCall(c.getLong(0), number, date, c.getLong(3), type))
                }
            }
        }
        return out
    }

    override fun onCleared() {
        if (observing) getApplication<Application>().contentResolver.unregisterContentObserver(observer)
        super.onCleared()
    }
}
