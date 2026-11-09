package com.blackout.communications.ui.chats

import android.os.Build
import android.widget.Toast
import com.blackout.communications.calls.Phone
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.SelectAll
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
import com.blackout.communications.chats.MmsInbox
import com.blackout.communications.chats.Messenger
import com.blackout.communications.chats.PendingTexts
import kotlinx.coroutines.launch
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
    // Multi-select: long-press a chat to start, tap to add or remove more.
    var selected by remember { mutableStateOf(setOf<Long>()) }
    val selecting = selected.isNotEmpty()
    var confirmDelete by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = selecting) { selected = emptySet() }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refresh() }
    val role = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = Texting.isDefaultTextingApp(context)
        if (isDefault) permissions.launch(Texting.permissions())
    }
    var notificationsOn by remember { mutableStateOf(true) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsOn = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    LifecycleResumeEffect(Unit) {
        notificationsOn = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
        isDefault = Texting.isDefaultTextingApp(context)
        vm.refresh()
        // Opening Chats saves any text that was waiting and retries any picture message that didn't download.
        val appContext = context.applicationContext
        Messenger.scope.launch {
            runCatching { PendingTexts.flush(appContext) }
            runCatching { MmsInbox.retryAll(appContext) }
        }
        onPauseOrDispose { }
    }

    Column(Modifier.fillMaxSize()) {
        if (selecting) {
            val visible = rows.map { it.thread.id }
            val allPinned = rows.filter { it.thread.id in selected }.all { it.pinned }
            val allMuted = rows.filter { it.thread.id in selected }.all { it.muted }
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Filled.Close, "Cancel selection") }
                Text("${selected.size} selected", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = { selected = if (selected.containsAll(visible)) emptySet() else visible.toSet() }) {
                    Icon(Icons.Outlined.SelectAll, if (selected.containsAll(visible)) "Unselect all" else "Select all")
                }
                IconButton(onClick = {
                    rows.filter { it.thread.id in selected && it.pinned == allPinned }.forEach { vm.togglePin(it.thread.id) }
                    selected = emptySet()
                }) { Icon(Icons.Outlined.PushPin, if (allPinned) "Unpin" else "Pin") }
                IconButton(onClick = {
                    rows.filter { it.thread.id in selected && it.muted == allMuted }.forEach { vm.toggleMute(it.thread.id) }
                    selected = emptySet()
                }) { Icon(Icons.Outlined.NotificationsOff, if (allMuted) "Unmute" else "Mute") }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.DeleteOutline, "Delete selected", tint = AppColors.Missed) }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Chats", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onNewMessage, enabled = ready) { Icon(Icons.Filled.Add, "New message", modifier = Modifier.size(26.dp)) }
            }
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
                if (!notificationsOn) {
                    SetupCard(
                        "Turn on message notifications",
                        "So new texts make a sound, buzz, and show in your notification bar and on your lock screen.",
                        "Turn on",
                    ) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Phone.has(context, android.Manifest.permission.POST_NOTIFICATIONS)) {
                            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName),
                                )
                            }
                        }
                    }
                }
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
                            val id = r.thread.id
                            val isSelected = id in selected
                            ThreadRowView(
                                r,
                                isSelected,
                                Modifier
                                    .background(if (isSelected) AppColors.Accent.copy(alpha = 0.16f) else Color.Transparent)
                                    .combinedClickable(
                                        onClick = {
                                            if (selecting) {
                                                selected = if (isSelected) selected - id else selected + id
                                            } else {
                                                onOpenThread(id)
                                            }
                                        },
                                        onLongClick = { selected = if (isSelected) selected - id else selected + id },
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        val n = selected.size
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (n == 1) "Delete 1 conversation?" else "Delete $n conversations?") },
            text = { Text("This deletes every message in ${if (n == 1) "it" else "them"} from this phone. It can't be undone.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteMany(selected); selected = emptySet(); confirmDelete = false }) { Text("Delete", color = AppColors.Missed) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
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
private fun ThreadRowView(r: ThreadRow, isSelected: Boolean, modifier: Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box {
                Avatar(r.title, r.contact?.contact?.photoPath, 52.dp)
                if (isSelected) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(22.dp).clip(androidx.compose.foundation.shape.CircleShape).background(AppColors.Accent),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Check, "Selected", tint = Color.White, modifier = Modifier.size(14.dp)) }
                }
            }
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
