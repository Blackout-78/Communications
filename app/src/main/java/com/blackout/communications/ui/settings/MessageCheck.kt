package com.blackout.communications.ui.settings

import android.Manifest
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.blackout.communications.calls.Phone
import com.blackout.communications.chats.MmsInbox
import com.blackout.communications.chats.PendingTexts
import com.blackout.communications.chats.RxLog
import com.blackout.communications.ui.chats.Texting
import com.blackout.communications.ui.theme.AppColors
import java.text.DateFormat
import java.util.Date

/**
 * Shows, in plain words, whether texts can reach this phone and when the last ones arrived.
 * "Last text received" tells you whether a missing message ever got here at all.
 */
@Composable
fun MessageCheckDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val isDefault = Texting.isDefaultTextingApp(context)
    val allowed = Texting.canRead(context) && Phone.has(context, Manifest.permission.RECEIVE_SMS)
    val notifications = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val waitingTexts = PendingTexts.count(context)
    val waitingPictures = MmsInbox.count(context)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Message check") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Line("Texting app", isDefault, if (isDefault) "Communications" else "Another app gets your texts. Fix it on the Chats tab.")
                Line("Permissions", allowed, if (allowed) "Allowed" else "Missing. Fix it on the Chats tab.")
                Line("Notifications", notifications, if (notifications) "On" else "Off, so new texts would be silent.")
                Line("Texts waiting to be saved", waitingTexts == 0, waitingTexts.toString())
                Line("Picture messages downloading", waitingPictures == 0, waitingPictures.toString())
                Text("Last arrivals", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
                Info("Text", whenText(context, RxLog.SMS))
                Info("Picture message notice", whenText(context, RxLog.MMS_NOTICE))
                Info("Picture message saved", whenText(context, RxLog.MMS_SAVED))
                Text(
                    "If a friend says they sent something and the times above don't show it, it never reached this phone as a text. " +
                        "The usual cause is chat messages: turn off \"Chat features\" (RCS) in Google Messages, and if this number was ever on an iPhone, " +
                        "turn iMessage off for it at selfsolve.apple.com/deregister-imessage. Then ask them to send again.",
                    fontSize = 13.sp,
                    color = AppColors.SubText,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun whenText(context: Context, key: String): String {
    val t = RxLog.last(context, key)
    return if (t == 0L) "Never" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(t))
}

@Composable
private fun Line(label: String, ok: Boolean, detail: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(if (ok) "OK" else "FIX", color = if (ok) AppColors.Accent else AppColors.Missed, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(end = 10.dp))
        Column {
            Text(label, fontSize = 14.sp)
            Text(detail, fontSize = 12.sp, color = AppColors.SubText)
        }
    }
}

@Composable
private fun Info(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, fontSize = 13.sp, modifier = Modifier.padding(end = 8.dp))
        Text(value, fontSize = 13.sp, color = AppColors.SubText)
    }
}
