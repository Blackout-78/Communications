package com.blackout.communications.chats

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.TelephonyManager

/**
 * Sends "decline with message" replies from the incoming call screen.
 * Android requires every texting app to have this.
 */
class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == TelephonyManager.ACTION_RESPOND_VIA_MESSAGE) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            val address = intent.data?.schemeSpecificPart?.substringBefore('?')
            if (!text.isNullOrBlank() && !address.isNullOrBlank()) {
                address.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }.forEach { Messenger.sendText(this, it, text) }
            }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
