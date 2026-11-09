package com.blackout.communications.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackout.communications.calls.CallBook
import com.blackout.communications.ui.theme.AppColors

/**
 * The last calls and how each ended, with Android's reason code, plus what call screening decided.
 * A call that fails also shows a notification. Copy report puts all of this on the clipboard.
 */
@Composable
fun CallCheckDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val entries = CallBook.recent(context, 15)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Call check") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (entries.isEmpty()) {
                    Text("No calls recorded yet. Calls from now on will show here.", fontSize = 14.sp, color = AppColors.SubText)
                }
                entries.forEach { e ->
                    Text(
                        CallBook.line(e),
                        fontSize = 12.sp,
                        color = if (e.problem) AppColors.Missed else AppColors.Text,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                Text(
                    "\"Screening\" lines show why a call rang, was blocked or went to voicemail. If a call from someone never appears here at all, it was stopped before reaching this app (Android's blocked list, or the carrier).",
                    fontSize = 12.sp,
                    color = AppColors.SubText,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = { TextButton(onClick = { copyReport(context, CallBook.report(context)) }) { Text("Copy report") } },
    )
}
