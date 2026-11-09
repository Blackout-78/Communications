package com.blackout.communications.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.blackout.communications.ui.theme.AppColors

/** Blocking stops all calls and texts, so it always asks first. */
@Composable
fun BlockConfirmDialog(who: String, isContact: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Block $who?") },
        text = {
            Text(
                if (isContact) "$who is in your contacts. Their calls and texts will be stopped until you unblock them."
                else "Their calls and texts will be stopped until you unblock them.",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Block", color = AppColors.Missed) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
