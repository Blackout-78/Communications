package com.blackout.communications.ui.chats

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blackout.communications.ui.components.Avatar
import com.blackout.communications.ui.theme.AppColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatsScreen(
    vm: ChatsViewModel,
    groups: List<String>,
    onOpenThread: (Long) -> Unit,
    onNewMessage: () -> Unit,
) {
    val context = LocalContext.current
    val rows by vm.rows.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val group by vm.groupFilter.collectAsStateWithLifecycle()
    var isDefault by remember { mutableStateOf(Texting.isDefaultTextingApp(context)) }
    var confirmDelete by remember { mutableStateOf<ThreadRow?>(null) }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refresh() }
    val role = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = Texting.isDefaultTextingApp(context)
        if (isDefault) permissions.launch(Texting.permissions())
    }
    LifecycleResumeEffect(Unit) {
        isDefault = Texting.isDefaultTextingApp(context)
        vm.refresh()
        onPauseOrDispose { }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Chats", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onNewMessage, enabled = ready) { Icon(Icons.Filled.Add, "New message", modifier = Modifier.size(26.dp)) }
        }

        when {
            !isDefault -> SetupCard(
                "Make Communications your texting app",
                "Then it can send and receive texts and picture messages, show chat bubbles over other apps, and stop texts from blocked numbers.",
                "Set as texting app",
            ) {
                val intent = Texting.requestRoleIntent(context)
                if (intent != null) role.launch(intent) else Toast.makeText(context, "Not available on this phone", Toast.LENGTH_SHORT).show()
            }
            !ready -> SetupCard("Allow message permissions", "Communications needs these to read and send your texts. Nothing leaves your phone except the texts you send.", "Allow") {
                permissions.launch(Texting.permissions())
            }
            else -> {
                // Filter chips: All, then your contact groups.
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("All", group == null) { vm.groupFilter.value = null }
                    groups.forEach { g -> Chip(g, group.equals(g, ignoreCase = true)) { vm.groupFilter.value = g } }
                }
                if (rows.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(if (group == null) "No messages yet.\nTap + to start one." else "No chats in $group.", color = AppColors.SubText, fontSize = 16.sp)
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                        items(rows, key = { it.thread.id }) { r ->
                            var menu by remember { mutableStateOf(false) }
                            Box {
                                ThreadRowView(r, Modifier.combinedClickable(onClick = { onOpenThread(r.thread.id) }, onLongClick = { menu = true }))
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text(if (r.pinned) "Unpin" else "Pin to top") }, onClick = { menu = false; vm.togglePin(r.thread.id) })
                                    DropdownMenuItem(text = { Text(if (r.muted) "Unmute" else "Mute") }, onClick = { menu = false; vm.toggleMute(r.thread.id) })
                                    DropdownMenuItem(text = { Text("Delete conversation", color = AppColors.Missed) }, onClick = { menu = false; confirmDelete = r })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete conversation with ${r.title}?") },
            text = { Text("This deletes every message in it from this phone.") },
            confirmButton = { TextButton(onClick = { vm.delete(r.thread.id); confirmDelete = null }) { Text("Delete", color = AppColors.Missed) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(36.dp).clip(RoundedCornerShape(18.dp))
            .background(if (selected) AppColors.Accent else AppColors.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (selected) Color.White else AppColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun ThreadRowView(r: ThreadRow, modifier: Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(r.title, r.contact?.contact?.photoPath, 52.dp)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(timeLabel(r.thread.date), fontSize = 12.sp, color = if (r.thread.unread > 0) AppColors.Accent else AppColors.SubText)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(r.thread.snippet, fontSize = 14.sp, color = AppColors.SubText, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (r.muted) Icon(Icons.Outlined.NotificationsOff, "Muted", tint = AppColors.SubText, modifier = Modifier.size(14.dp))
                    if (r.pinned) Icon(Icons.Outlined.PushPin, "Pinned", tint = AppColors.SubText, modifier = Modifier.size(14.dp))
                    if (r.thread.unread > 0) {
                        Box(
                            Modifier.widthIn(min = 20.dp).height(20.dp).clip(RoundedCornerShape(10.dp)).background(AppColors.Accent).padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("${r.thread.unread}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
        HorizontalDivider(color = AppColors.Line)
    }
}

@Composable
fun SetupCard(title: String, body: String, button: String, onClick: () -> Unit) {
    Column(
        Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AppColors.Card).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(body, color = AppColors.SubText, fontSize = 14.sp)
        Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent)) { Text(button, color = Color.White) }
    }
}

fun timeLabel(time: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = time }
    val now = Calendar.getInstance()
    val sameDay = c.get(Calendar.YEAR) == now.get(Calendar.YEAR) && c.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
    if (sameDay) return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(time))
    now.add(Calendar.DAY_OF_YEAR, -1)
    if (c.get(Calendar.YEAR) == now.get(Calendar.YEAR) && c.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)) return "Yesterday"
    return SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(time))
}
