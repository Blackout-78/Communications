package com.blackout.communications.ui.calls

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blackout.communications.calls.BlockedNumbers
import com.blackout.communications.calls.Phone
import com.blackout.communications.data.title
import com.blackout.communications.ui.components.Avatar
import com.blackout.communications.ui.components.SquareSearchBar
import com.blackout.communications.ui.contacts.formatNumber
import com.blackout.communications.ui.theme.AppColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Permissions asked for once Communications is the phone app. */
fun callPermissions(): Array<String> = buildList {
    add(Manifest.permission.CALL_PHONE)
    add(Manifest.permission.READ_CALL_LOG)
    add(Manifest.permission.WRITE_CALL_LOG)
    add(Manifest.permission.READ_PHONE_STATE)
    add(Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

@Composable
fun CallsScreen(
    vm: CallsViewModel,
    onOpenDialpad: () -> Unit,
    onOpenProfile: (Long) -> Unit,
    onAddToContacts: (String) -> Unit,
    onMessage: (String) -> Unit,
    onOpenCallSettings: () -> Unit,
    onOpenBlocked: () -> Unit,
    onOpenRecordings: () -> Unit,
) {
    val context = LocalContext.current
    val entries by vm.entries.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val canRead by vm.canRead.collectAsStateWithLifecycle()
    var isDefault by remember { mutableStateOf(Phone.isDefaultPhoneApp(context)) }
    var menu by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refresh() }
    val role = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = Phone.isDefaultPhoneApp(context)
        if (isDefault) permissions.launch(callPermissions())
    }
    LifecycleResumeEffect(Unit) {
        isDefault = Phone.isDefaultPhoneApp(context)
        vm.refresh()
        onPauseOrDispose { }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SquareSearchBar(
                query = query,
                onQueryChange = { vm.query.value = it },
                placeholder = "Search call history",
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
            ) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More options", tint = AppColors.SubText) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Call settings") }, onClick = { menu = false; onOpenCallSettings() })
                        DropdownMenuItem(text = { Text("Blocked numbers") }, onClick = { menu = false; onOpenBlocked() })
                        DropdownMenuItem(text = { Text("Recordings") }, onClick = { menu = false; onOpenRecordings() })
                        DropdownMenuItem(text = { Text("Clear call history") }, enabled = canRead, onClick = { menu = false; confirmClear = true })
                    }
                }
            }

            when {
                !isDefault -> SetupCard(
                    title = "Make Communications your phone app",
                    body = "Then it can ring for calls, show who's calling, keep your call history, block numbers and record calls. You can switch back any time in your phone's settings.",
                    button = "Set as phone app",
                ) {
                    val intent = Phone.requestPhoneRoleIntent(context)
                    if (intent != null) role.launch(intent) else Toast.makeText(context, "Not available on this phone", Toast.LENGTH_SHORT).show()
                }
                !canRead -> SetupCard(
                    title = "Allow call permissions",
                    body = "Communications needs these to show your call history, place calls and record calls. Nothing leaves your phone.",
                    button = "Allow",
                ) { permissions.launch(callPermissions()) }
                entries.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(if (query.isBlank()) "No calls yet." else "No calls match.", color = AppColors.SubText, fontSize = 16.sp)
                }
                else -> CallList(
                    entries = entries,
                    modifier = Modifier.weight(1f),
                    onOpenProfile = onOpenProfile,
                    onAddToContacts = onAddToContacts,
                    onMessage = onMessage,
                    onDelete = { vm.delete(it) },
                )
            }
        }

        FloatingActionButton(
            onClick = onOpenDialpad,
            shape = CircleShape,
            containerColor = AppColors.Accent,
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 16.dp),
        ) { Icon(Icons.Filled.Dialpad, "Open dialpad") }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear call history?") },
            text = { Text("This deletes every call from the history on this phone.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; vm.clearAll() }) { Text("Clear", color = AppColors.Missed) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SetupCard(title: String, body: String, button: String, onClick: () -> Unit) {
    Column(
        Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AppColors.Card).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(body, color = AppColors.SubText, fontSize = 14.sp)
        Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent)) { Text(button, color = Color.White) }
    }
}

/** Call history grouped by day, with a line between each call. Shared with a contact's own call history. */
@Composable
fun CallList(
    entries: List<CallEntry>,
    modifier: Modifier = Modifier,
    onOpenProfile: (Long) -> Unit,
    onAddToContacts: (String) -> Unit,
    onMessage: (String) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val grouped = remember(entries) { entries.groupBy { dayLabel(it.date) } }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 8.dp, bottom = 100.dp)) {
        grouped.forEach { (day, list) ->
            item(key = "h-$day") {
                Text(day, color = AppColors.SubText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
            }
            items(list, key = { it.id }) { e ->
                CallRow(e, onOpenProfile, onAddToContacts, onMessage, onDelete)
            }
        }
    }
}

fun typeColor(type: CallType): Color = when (type) {
    CallType.Missed -> AppColors.Missed
    CallType.Incoming -> AppColors.Incoming
    CallType.Outgoing -> AppColors.Outgoing
    CallType.Voicemail -> AppColors.Voicemail
}

@Composable
private fun CallRow(
    e: CallEntry,
    onOpenProfile: (Long) -> Unit,
    onAddToContacts: (String) -> Unit,
    onMessage: (String) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val name = e.contact?.title ?: e.number.ifBlank { "Private number" }.let { if (it == e.number) formatNumber(it) else it }
    val color = typeColor(e.type)
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(e.date))
    val duration = if (e.durationSec > 0 && e.type != CallType.Missed) " • ${formatDuration(e.durationSec)}" else ""
    val country = Phone.country(e.number)

    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Tap the picture to open the profile. The rest of the row does nothing.
            Avatar(
                name = name,
                photoPath = e.contact?.contact?.photoPath,
                size = 44.dp,
                modifier = Modifier.clip(CircleShape)
                    .clickable(enabled = e.contact != null) { e.contact?.let { onOpenProfile(it.contact.id) } }
                    .semantics { contentDescription = "Open $name profile" },
            )
            Column(Modifier.weight(1f)) {
                Text(name, color = if (e.type == CallType.Missed) AppColors.Missed else AppColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${e.type.label} • $time$duration", color = if (e.type == CallType.Missed) AppColors.Missed else AppColors.SubText, fontSize = 13.sp)
                if (country != null) Text(country, color = AppColors.SubText, fontSize = 12.sp)
            }
            // Call-back phone in the call type's color. Missed calls get the red phone facing down.
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF18181B))
                    .clickable(enabled = e.number.isNotBlank()) { Phone.call(context, e.number) }
                    .semantics { contentDescription = "Call back $name" },
                contentAlignment = Alignment.Center,
            ) { Icon(if (e.type == CallType.Missed) Icons.Filled.CallEnd else Icons.Filled.Call, null, tint = color, modifier = Modifier.size(22.dp)) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More options for $name", tint = AppColors.SubText) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (e.number.isNotBlank()) {
                        DropdownMenuItem(text = { Text("Message") }, onClick = { menu = false; onMessage(e.number) })
                        if (e.contact == null) DropdownMenuItem(text = { Text("Add to contacts") }, onClick = { menu = false; onAddToContacts(e.number) })
                        DropdownMenuItem(text = { Text("Block") }, onClick = {
                            menu = false
                            val ok = BlockedNumbers.block(context, e.number)
                            Toast.makeText(context, if (ok) "Blocked" else "Make Communications your phone app to block numbers", Toast.LENGTH_SHORT).show()
                        })
                    }
                    DropdownMenuItem(text = { Text("Delete from history") }, onClick = { menu = false; onDelete(e.id) })
                }
            }
        }
        HorizontalDivider(color = AppColors.Line)
    }
}

fun formatDuration(sec: Long): String = when {
    sec >= 3600 -> "${sec / 3600}h ${(sec / 60) % 60}m"
    sec >= 60 -> "${sec / 60}m ${sec % 60}s"
    else -> "${sec}s"
}

private fun dayLabel(time: Long): String {
    val day = Calendar.getInstance().apply { timeInMillis = time }
    val today = Calendar.getInstance()
    fun same(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (same(day, today)) return "Today"
    today.add(Calendar.DAY_OF_YEAR, -1)
    if (same(day, today)) return "Yesterday"
    val pattern = if (day.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)) "MMM d" else "MMM d, yyyy"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(time))
}

/** Opens the current texting app until Stage 3 adds Chats. */
fun openMessage(context: android.content.Context, number: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
