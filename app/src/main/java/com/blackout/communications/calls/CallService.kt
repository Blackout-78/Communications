package com.blackout.communications.calls

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.blackout.communications.CommunicationsApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android connects to this service while Communications is the phone app.
 * It receives every call, rings for incoming calls and shows the call screens.
 */
class CallService : InCallService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var ringer: Ringer
    private var ringingFor: Call? = null

    override fun onCreate() {
        super.onCreate()
        ringer = Ringer(this)
        CallNotifications.createChannels(this)
        CallManager.service = this
        CallManager.onChanged = { update() }
    }

    override fun onDestroy() {
        ringer.stop()
        CallManager.onChanged = null
        if (CallManager.service == this) CallManager.service = null
        CallNotifications.cancel(this)
        scope.cancel()
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        CallManager.add(call)
        val number = call.details.handle?.schemeSpecificPart
        val app = application as CommunicationsApp
        scope.launch {
            val contact = app.contacts.findByNumber(number)
            if (number != null) CallManager.setCaller(number, contact)
            if (CallManager.stateOf(call) == Call.STATE_RINGING) {
                ringingFor = call
                ringer.start(contact?.contact?.ringtonePath)
            }
            update()
        }
        if (CallManager.stateOf(call) != Call.STATE_RINGING) {
            // Outgoing call: open the call screen straight away.
            startActivity(Intent(this, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override fun onCallRemoved(call: Call) {
        CallManager.remove(call)
        if (CallManager.snapshot.value.isEmpty()) {
            RecordingService.stop(this)
        }
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        CallManager.setAudio(audioState)
    }

    override fun onSilenceRinger() {
        ringer.stop()
    }

    /** Keeps the ringer and the notification in step with the calls. */
    private fun update() {
        val calls = CallManager.snapshot.value
        val ringing = calls.firstOrNull { it.isRinging }
        if (ringing == null || ringing.call != ringingFor) {
            ringer.stop()
            if (ringing == null) ringingFor = null
        }
        when {
            ringing != null -> CallNotifications.showIncoming(this, ringing)
            calls.isNotEmpty() -> CallNotifications.showOngoing(this, calls.firstOrNull { it.isActive } ?: calls.first())
            else -> CallNotifications.cancel(this)
        }
    }
}
