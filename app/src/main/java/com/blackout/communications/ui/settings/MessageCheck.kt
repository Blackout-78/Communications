package com.blackout.communications.ui.settings

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import com.blackout.communications.calls.CallBook
import com.blackout.communications.calls.Phone
import com.blackout.communications.chats.MmsInbox
import com.blackout.communications.chats.PendingTexts
import com.blackout.communications.chats.RxLog
import com.blackout.communications.chats.TxLog
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
                Text("Last text you sent", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
                Text(sendLine(context), fontSize = 13.sp, color = AppColors.SubText)
                TxLog.lastDownloadProblem(context)?.let { (t, why) ->
                    Text("Last picture download problem", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
                    Text("${CallBook.whenText(t)}: $why", fontSize = 13.sp, color = AppColors.SubText)
                }
                Text("Picture download settings", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
                Text(mmsSettings(context), fontSize = 12.sp, color = AppColors.SubText)
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
        dismissButton = { TextButton(onClick = { copyReport(context, messageReport(context)) }) { Text("Copy report") } },
    )
}

/** "Delivered" means her phone confirmed it. "Sent" means the carrier took it; not every carrier or phone sends a delivery report. */
private fun sendLine(context: Context): String {
    val s = TxLog.lastSend(context) ?: return "Nothing sent yet"
    return "${s.second}${if (s.third.isNotBlank()) " (${s.third})" else ""}, ${CallBook.whenText(s.first)}"
}

/** The phone's own picture-message settings (what the carrier is told when a picture is downloaded). */
private fun mmsSettings(context: Context): String = runCatching {
    val b = com.blackout.communications.chats.Messenger.smsManager(context).carrierConfigValues
    listOf("userAgent", "uaProfUrl", "uaProfTagName", "maxImageWidth", "maxImageHeight", "maxMessageSize", "httpParams")
        .joinToString("\n") { "$it = ${b.get(it) ?: "(none)"}" }
}.getOrElse { "unavailable: ${it.message}" }

private fun messageReport(context: Context): String = buildString {
    appendLine("Picture download settings:\n${mmsSettings(context)}")
    appendLine("Communications message report (app ${com.blackout.communications.BuildConfigInfo.versionName(context)}, Android ${android.os.Build.VERSION.RELEASE})")
    appendLine("Texting app: ${Texting.isDefaultTextingApp(context)}; permissions: ${Texting.canRead(context)}; notifications: ${NotificationManagerCompat.from(context).areNotificationsEnabled()}")
    appendLine("Last text received: ${whenText(context, RxLog.SMS)}")
    appendLine("Last picture notice: ${whenText(context, RxLog.MMS_NOTICE)}; saved: ${whenText(context, RxLog.MMS_SAVED)}")
    appendLine("Last text sent: ${sendLine(context)}")
    TxLog.lastDownloadProblem(context)?.let { appendLine("Last picture download problem: ${CallBook.whenText(it.first)}: ${it.second}") }
    appendLine("Waiting texts: ${PendingTexts.count(context)}; picture messages downloading: ${MmsInbox.count(context)}")
}

internal fun copyReport(context: Context, text: String) {
    runCatching {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Communications report", text))
        Toast.makeText(context, "Report copied. Paste it where you need it.", Toast.LENGTH_SHORT).show()
    }
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
