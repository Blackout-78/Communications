package com.blackout.communications.calls

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import com.blackout.communications.R

/** The incoming call alert and the "call in progress" notification. */
object CallNotifications {
    private const val CHANNEL_INCOMING = "incoming_calls"
    private const val CHANNEL_ONGOING = "ongoing_calls"
    const val ID = 1001

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_INCOMING, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null) // The app plays the ringtone itself.
                enableVibration(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, "Calls in progress", NotificationManager.IMPORTANCE_LOW),
        )
    }

    fun showIncoming(context: Context, info: CallInfo) {
        val open = PendingIntent.getActivity(
            context, 1, Intent(context, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val answer = PendingIntent.getActivity(
            context, 2,
            Intent(context, CallActivity::class.java).setAction(CallActivity.ACTION_ANSWER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val decline = PendingIntent.getBroadcast(
            context, 3, Intent(context, CallActionReceiver::class.java).setAction(CallActionReceiver.DECLINE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val person = Person.Builder().setName(info.name).setImportant(true).build()
        val n = NotificationCompat.Builder(context, CHANNEL_INCOMING)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(info.name)
            .setContentText("Incoming call")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setFullScreenIntent(open, true)
            .setContentIntent(open)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(person, decline, answer))
            .build()
        notify(context, n)
    }

    fun showOngoing(context: Context, info: CallInfo) {
        val open = PendingIntent.getActivity(
            context, 1, Intent(context, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val hangUp = PendingIntent.getBroadcast(
            context, 4, Intent(context, CallActionReceiver::class.java).setAction(CallActionReceiver.HANG_UP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(info.name)
            .setContentText(if (info.isDialing) "Calling…" else if (info.isHeld) "On hold" else "Call in progress")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Hang up", hangUp)
        if (info.connectTime > 0) builder.setWhen(info.connectTime).setUsesChronometer(true)
        notify(context, builder.build())
    }

    fun cancel(context: Context) = NotificationManagerCompat.from(context).cancel(ID)

    private fun notify(context: Context, n: android.app.Notification) {
        try {
            NotificationManagerCompat.from(context).notify(ID, n)
        } catch (e: SecurityException) {
            // Notification permission not granted. The call screen still opens from the app.
        }
    }
}

/** Handles Decline and Hang up buttons in notifications. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val calls = CallManager.snapshot.value
        when (intent.action) {
            DECLINE -> CallManager.ringing()?.let { CallManager.decline(it.call) }
            HANG_UP -> (calls.firstOrNull { it.isActive || it.isDialing } ?: calls.firstOrNull())?.let { CallManager.hangUp(it.call) }
        }
    }

    companion object {
        const val DECLINE = "com.blackout.communications.DECLINE"
        const val HANG_UP = "com.blackout.communications.HANG_UP"
    }
}
