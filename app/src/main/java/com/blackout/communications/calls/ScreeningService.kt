package com.blackout.communications.calls

import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.TelecomManager
import com.blackout.communications.CommunicationsApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Checks each incoming call before it rings:
 * - hidden numbers and numbers not in contacts can be blocked (no ring, hidden from history)
 * - unknown numbers can be sent straight to voicemail (no ring, shown as "Sent to voicemail")
 * Numbers on the blocked list are already stopped by Android itself.
 */
class ScreeningService : CallScreeningService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onScreenCall(details: Call.Details) {
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(details, CallResponse.Builder().build())
            return
        }
        val app = application as CommunicationsApp
        val prefs = app.callSettings.current
        val number = details.handle?.schemeSpecificPart.orEmpty()
        val hidden = number.isBlank() || details.handlePresentation != TelecomManager.PRESENTATION_ALLOWED

        scope.launch {
            val known = !hidden && app.contacts.findByNumber(number) != null
            val response = when {
                hidden && prefs.blockHidden -> block()
                !known && prefs.blockNotInContacts -> block()
                !known && prefs.unknownToVoicemail -> {
                    app.callSettings.rememberSentToVoicemail(number, System.currentTimeMillis())
                    CallResponse.Builder()
                        .setDisallowCall(true)
                        .setRejectCall(true) // Rejected calls go to your carrier voicemail.
                        .setSkipCallLog(false)
                        .setSkipNotification(true)
                        .build()
                }
                else -> CallResponse.Builder().build()
            }
            respondToCall(details, response)
        }
    }

    private fun block() = CallResponse.Builder()
        .setDisallowCall(true)
        .setRejectCall(true)
        .setSkipCallLog(true)
        .setSkipNotification(true)
        .build()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
