package com.blackout.communications.calls

import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import com.blackout.communications.data.ContactWithNumbers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A read-only picture of one call, for the screens. */
data class CallInfo(
    val call: Call,
    val state: Int,
    val number: String,
    val name: String,
    val photoPath: String?,
    val contactId: Long?,
    val connectTime: Long,
    val isConference: Boolean,
    val children: List<CallInfo>,
    val canHold: Boolean,
    val canMergeConference: Boolean,
    val canSwapConference: Boolean,
    val canSeparate: Boolean,
    val canDisconnectChild: Boolean,
) {
    val isRinging get() = state == Call.STATE_RINGING
    val isActive get() = state == Call.STATE_ACTIVE
    val isHeld get() = state == Call.STATE_HOLDING
    val isDialing get() = state == Call.STATE_DIALING || state == Call.STATE_CONNECTING || state == Call.STATE_SELECT_PHONE_ACCOUNT
}

/**
 * Holds the phone's current calls. The [CallService] feeds it; the call screens read it.
 */
object CallManager {
    private val calls = mutableListOf<Call>()
    private val callers = mutableMapOf<String, ContactWithNumbers?>()

    private val _snapshot = MutableStateFlow<List<CallInfo>>(emptyList())
    /** Top-level calls (conference participants are inside their conference). */
    val snapshot: StateFlow<List<CallInfo>> = _snapshot

    private val _audio = MutableStateFlow<CallAudioState?>(null)
    val audio: StateFlow<CallAudioState?> = _audio

    /** Number of the call being recorded, or null. */
    val recording = MutableStateFlow<String?>(null)

    var service: CallService? = null

    /** Lets the service react when a call changes (stop ringing, update notifications). */
    var onChanged: (() -> Unit)? = null

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = refresh()
        override fun onDetailsChanged(call: Call, details: Call.Details) = refresh()
        override fun onChildrenChanged(call: Call, children: MutableList<Call>) = refresh()
        override fun onParentChanged(call: Call, parent: Call?) = refresh()
        override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: MutableList<Call>) = refresh()
    }

    fun add(call: Call) {
        calls.add(call)
        call.registerCallback(callback)
        refresh()
    }

    fun remove(call: Call) {
        call.unregisterCallback(callback)
        calls.remove(call)
        refresh()
    }

    fun setCaller(number: String, contact: ContactWithNumbers?) {
        callers[Phone.matchKey(number)] = contact
        refresh()
    }

    fun setAudio(state: CallAudioState?) {
        _audio.value = state
    }

    fun refresh() {
        _snapshot.value = calls.filter { it.parent == null }.map { info(it) }
        onChanged?.invoke()
    }

    fun ringing(): CallInfo? = _snapshot.value.firstOrNull { it.isRinging }

    // ---- Actions ----

    fun answer(call: Call) = call.answer(VideoProfile.STATE_AUDIO_ONLY)
    fun decline(call: Call) = call.reject(false, null)
    fun declineWithMessage(call: Call, text: String) = call.reject(true, text)
    fun hangUp(call: Call) = call.disconnect()
    fun hold(call: Call) = call.hold()
    fun unhold(call: Call) = call.unhold()
    fun setMuted(muted: Boolean) = service?.setMuted(muted)
    fun setRoute(route: Int) = service?.setAudioRoute(route)

    /** Switches between the active and the held call. */
    fun swap() {
        val top = _snapshot.value
        val active = top.firstOrNull { it.isActive }
        val held = top.firstOrNull { it.isHeld }
        when {
            active != null && active.canSwapConference -> active.call.swapConference()
            held != null -> held.call.unhold() // Telecom holds the other call automatically.
            active != null -> active.call.hold()
        }
    }

    /** Joins the calls into a conference call. */
    fun merge() {
        val top = _snapshot.value
        val active = top.firstOrNull { it.isActive } ?: top.firstOrNull() ?: return
        if (active.canMergeConference) {
            active.call.mergeConference()
            return
        }
        val other = active.call.conferenceableCalls.firstOrNull()
            ?: top.firstOrNull { it.call != active.call }?.call
            ?: return
        active.call.conference(other)
    }

    fun playDtmf(call: Call, digit: Char) {
        call.playDtmfTone(digit)
        call.stopDtmfTone()
    }

    private fun info(call: Call): CallInfo {
        val details = call.details
        val number = details.handle?.schemeSpecificPart.orEmpty()
        val caller = callers[Phone.matchKey(number)]
        val caps = details.callCapabilities
        val conference = details.hasProperty(Call.Details.PROPERTY_CONFERENCE)
        return CallInfo(
            call = call,
            state = stateOf(call),
            number = number,
            name = when {
                conference -> "Conference call"
                caller != null -> caller.contact.let { c -> listOf(c.firstName, c.lastName).filter { it.isNotBlank() }.joinToString(" ") }
                    .ifBlank { number }
                number.isBlank() -> "Private number"
                else -> number
            },
            photoPath = caller?.contact?.photoPath,
            contactId = caller?.contact?.id,
            connectTime = details.connectTimeMillis,
            isConference = conference,
            children = call.children.map { info(it) },
            canHold = caps and Call.Details.CAPABILITY_HOLD != 0,
            canMergeConference = caps and Call.Details.CAPABILITY_MERGE_CONFERENCE != 0,
            canSwapConference = caps and Call.Details.CAPABILITY_SWAP_CONFERENCE != 0,
            canSeparate = caps and Call.Details.CAPABILITY_SEPARATE_FROM_CONFERENCE != 0,
            canDisconnectChild = caps and Call.Details.CAPABILITY_DISCONNECT_FROM_CONFERENCE != 0,
        )
    }

    @Suppress("DEPRECATION")
    fun stateOf(call: Call): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) call.details.state else call.state
}
