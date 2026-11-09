package com.blackout.communications.ui.calls

import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Voicemail
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blackout.communications.CommunicationsApp
import com.blackout.communications.calls.BlockedNumbers
import com.blackout.communications.calls.RecordingService
import com.blackout.communications.data.ContactWithNumbers
import com.blackout.communications.data.DeclineAudience
import com.blackout.communications.data.mainNumber
import com.blackout.communications.data.title
import com.blackout.communications.ui.components.Avatar
import com.blackout.communications.ui.components.InfoCard
import com.blackout.communications.ui.contacts.ContactsViewModel
import com.blackout.communications.ui.contacts.formatNumber
import com.blackout.communications.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------- Shared pieces

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text.uppercase(), color = AppColors.SubText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
}

@Composable
private fun ToggleRow(label: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp)
            if (sub != null) Text(sub, color = AppColors.SubText, fontSize = 13.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = AppColors.Accent, checkedThumbColor = Color.White))
    }
}

@Composable
private fun LinkRow(label: String, value: String?, sub: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp)
            if (sub != null) Text(sub, color = AppColors.SubText, fontSize = 13.sp)
        }
        if (value != null) Text(value, color = AppColors.SubText, fontSize = 14.sp)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = AppColors.SubText)
    }
}

/** Pick several contacts with checkboxes. */
@Composable
fun ContactPickerDialog(
    title: String,
    contacts: List<ContactWithNumbers>,
    selected: Set<Long>,
    onDone: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (contacts.isEmpty()) {
                Text("No contacts yet.", color = AppColors.SubText)
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(contacts.sortedBy { it.title.lowercase() }, key = { it.contact.id }) { c ->
                        val id = c.contact.id
                        Row(
                            Modifier.fillMaxWidth().clickable { picked = if (id in picked) picked - id else picked + id }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = id in picked, onCheckedChange = { picked = if (it) picked + id else picked - id }, colors = CheckboxDefaults.colors(checkedColor = AppColors.Accent))
                            Avatar(c.title, c.contact.photoPath, 32.dp)
                            Text(c.title, modifier = Modifier.padding(start = 10.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(picked) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------- Call settings

@Composable
fun CallSettingsScreen(contactsVm: ContactsViewModel, onBack: () -> Unit, onOpenSpeedDial: () -> Unit, onOpenBlocked: () -> Unit, onOpenRecordings: () -> Unit) {
    val context = LocalContext.current
    val settings = (context.applicationContext as CommunicationsApp).callSettings
    val prefs by settings.state.collectAsStateWithLifecycle()
    val list by contactsVm.listState.collectAsStateWithLifecycle()
    val all = list.favorites + list.others
    val owners by contactsVm.speedDialOwners.collectAsStateWithLifecycle()
    var pickSelected by remember { mutableStateOf(false) }
    var pickExempt by remember { mutableStateOf(false) }
    var editReply by remember { mutableStateOf<Int?>(null) } // index, or -1 for a new reply

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Call settings", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Speed dial")
            InfoCard { LinkRow("Speed dial", "${owners.size} of 8 set", "Hold a number on the dialpad to call", onOpenSpeedDial) }

            SectionTitle("Unknown callers")
            InfoCard {
                ToggleRow("Never block my contacts", "Anyone saved in Contacts always rings and can always text you. A leftover block on a contact's number is removed, unless you blocked them here on purpose.", prefs.protectContacts) { v -> settings.update { it.copy(protectContacts = v) } }
                HorizontalDivider(color = AppColors.Line)
                ToggleRow("Send unknown numbers to voicemail", "They don't ring. They show in call history as \"Sent to voicemail\".", prefs.unknownToVoicemail) { v -> settings.update { it.copy(unknownToVoicemail = v) } }
                HorizontalDivider(color = AppColors.Line)
                ToggleRow("Block numbers not in contacts", "They don't ring and are hidden from call history.", prefs.blockNotInContacts) { v -> settings.update { it.copy(blockNotInContacts = v) } }
                HorizontalDivider(color = AppColors.Line)
                ToggleRow("Block hidden numbers", "Private and unknown caller ID.", prefs.blockHidden) { v -> settings.update { it.copy(blockHidden = v) } }
                HorizontalDivider(color = AppColors.Line)
                LinkRow("Blocked numbers", null, "Blocks both calls and texts", onOpenBlocked)
            }

            SectionTitle("Decline with message")
            InfoCard {
                Text("Show the Message button for", color = AppColors.SubText, fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
                DeclineAudience.entries.forEach { a ->
                    Row(Modifier.fillMaxWidth().clickable { settings.update { it.copy(declineAudience = a) } }.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = prefs.declineAudience == a, onClick = { settings.update { it.copy(declineAudience = a) } }, colors = RadioButtonDefaults.colors(selectedColor = AppColors.Accent))
                        Text(a.label, fontSize = 15.sp)
                    }
                }
                if (prefs.declineAudience == DeclineAudience.Selected) {
                    HorizontalDivider(color = AppColors.Line)
                    LinkRow("Selected contacts", "${prefs.declineSelected.size}") { pickSelected = true }
                }
                HorizontalDivider(color = AppColors.Line)
                Text("Quick replies (the first one is the default)", color = AppColors.SubText, fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
                prefs.quickReplies.forEachIndexed { i, reply ->
                    Row(Modifier.fillMaxWidth().clickable { editReply = i }.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(reply, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
                        if (prefs.quickReplies.size > 1) {
                            IconButton(onClick = { settings.update { p -> p.copy(quickReplies = p.quickReplies.filterIndexed { j, _ -> j != i }) } }) {
                                Icon(Icons.Outlined.DeleteOutline, "Delete reply", tint = AppColors.SubText)
                            }
                        }
                    }
                }
                TextButton(onClick = { editReply = -1 }, modifier = Modifier.padding(start = 6.dp)) {
                    Icon(Icons.Filled.Add, null, tint = AppColors.Accent)
                    Text("Add reply", color = AppColors.Accent, modifier = Modifier.padding(start = 6.dp))
                }
            }

            SectionTitle("Recording")
            InfoCard {
                ToggleRow("Auto-record all calls", "Communications tests whether your phone allows recording both voices directly. If not, the other person is only recorded on speaker.", prefs.autoRecord) { v -> settings.update { it.copy(autoRecord = v) } }
                HorizontalDivider(color = AppColors.Line)
                ToggleRow(
                    "Turn on speaker when recording",
                    "Most phones only let apps record through the microphone, so the other person is only recorded on speaker.",
                    prefs.speakerWhenRecording,
                ) { v -> settings.update { it.copy(speakerWhenRecording = v) } }
                HorizontalDivider(color = AppColors.Line)
                LinkRow("Never record these contacts", "${prefs.recordExempt.size}") { pickExempt = true }
                HorizontalDivider(color = AppColors.Line)
                LinkRow("Recordings", null, "Saved in ${RecordingService.folder()}", onOpenRecordings)
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (pickSelected) {
        ContactPickerDialog("Show Message button for", all, prefs.declineSelected, onDone = { ids -> settings.update { it.copy(declineSelected = ids) }; pickSelected = false }, onDismiss = { pickSelected = false })
    }
    if (pickExempt) {
        ContactPickerDialog("Never record", all, prefs.recordExempt, onDone = { ids -> settings.update { it.copy(recordExempt = ids) }; pickExempt = false }, onDismiss = { pickExempt = false })
    }
    editReply?.let { index ->
        var text by remember(index) { mutableStateOf(if (index >= 0) prefs.quickReplies.getOrElse(index) { "" } else "") }
        AlertDialog(
            onDismissRequest = { editReply = null },
            title = { Text(if (index >= 0) "Edit reply" else "New reply") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, colors = fieldColors()) },
            confirmButton = {
                TextButton(onClick = {
                    val t = text.trim()
                    if (t.isNotEmpty()) settings.update { p ->
                        p.copy(quickReplies = if (index >= 0) p.quickReplies.mapIndexed { j, r -> if (j == index) t else r } else p.quickReplies + t)
                    }
                    editReply = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editReply = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AppColors.Accent,
    unfocusedBorderColor = AppColors.Line,
    cursorColor = AppColors.Accent,
)

// ---------------------------------------------------------------- Speed dial

@Composable
fun SpeedDialScreen(contactsVm: ContactsViewModel, onBack: () -> Unit) {
    val owners by contactsVm.speedDialOwners.collectAsStateWithLifecycle()
    val list by contactsVm.listState.collectAsStateWithLifecycle()
    val all = list.favorites + list.others
    var pickFor by remember { mutableStateOf<Int?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Speed dial", onBack)
        Text(
            "Hold a number on the dialpad for 1 second to call that contact. You can also set this from a contact's profile.",
            color = AppColors.SubText, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        InfoCard(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                KeyBadge(1)
                Icon(Icons.Outlined.Voicemail, null, tint = AppColors.SubText, modifier = Modifier.padding(horizontal = 12.dp))
                Column {
                    Text("Voicemail", fontSize = 16.sp)
                    Text("Set by your carrier, can't be changed", color = AppColors.SubText, fontSize = 13.sp)
                }
            }
            (2..9).forEach { key ->
                HorizontalDivider(color = AppColors.Line)
                val owner = owners[key]
                Row(
                    Modifier.fillMaxWidth().clickable { pickFor = key }.heightIn(min = 64.dp).padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KeyBadge(key)
                    Spacer(Modifier.width(12.dp))
                    if (owner != null) {
                        Avatar(owner.title, owner.contact.photoPath, 40.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(owner.title, fontSize = 16.sp)
                            owner.mainNumber?.let { Text("${it.label} · ${formatNumber(it.number)}", color = AppColors.SubText, fontSize = 13.sp) }
                        }
                        IconButton(onClick = { contactsVm.setSpeedDial(owner.contact.id, null) }) {
                            Icon(Icons.Filled.Close, "Remove ${owner.title} from speed dial $key", tint = AppColors.SubText)
                        }
                    } else {
                        Column(Modifier.weight(1f).padding(start = 4.dp)) {
                            Text("Not set", fontSize = 16.sp, color = AppColors.SubText)
                            Text("Tap to choose a contact", color = AppColors.SubText, fontSize = 13.sp)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = AppColors.SubText)
                    }
                }
            }
        }
    }

    pickFor?.let { key ->
        AlertDialog(
            onDismissRequest = { pickFor = null },
            title = { Text("Speed dial $key") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(all.filter { it.numbers.isNotEmpty() }.sortedBy { it.title.lowercase() }, key = { it.contact.id }) { c ->
                        Row(
                            Modifier.fillMaxWidth().clickable { contactsVm.setSpeedDial(c.contact.id, key); pickFor = null }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(c.title, c.contact.photoPath, 36.dp)
                            Text(c.title, modifier = Modifier.padding(start = 10.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickFor = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun KeyBadge(key: Int) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF2A2A2E)), contentAlignment = Alignment.Center) {
        Text("$key", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------- Blocked numbers

@Composable
fun BlockedNumbersScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var numbers by remember { mutableStateOf(BlockedNumbers.list(context)) }
    var newNumber by remember { mutableStateOf("") }
    val allowed = BlockedNumbers.canUse(context)

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Blocked numbers", onBack)
        Text(
            if (allowed) "Calls and texts from these numbers are stopped by your phone. You won't be notified."
            else "Make Communications your phone app (in the Calls tab) to block numbers.",
            color = AppColors.SubText, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp),
        )
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newNumber, onValueChange = { newNumber = it }, label = { Text("Number to block") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), colors = fieldColors(),
                modifier = Modifier.weight(1f), enabled = allowed,
            )
            TextButton(enabled = allowed && newNumber.isNotBlank(), onClick = {
                if (BlockedNumbers.block(context, newNumber.trim())) {
                    newNumber = ""
                    numbers = BlockedNumbers.list(context)
                } else {
                    Toast.makeText(context, "Couldn't block that number", Toast.LENGTH_SHORT).show()
                }
            }) { Text("Block") }
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(numbers, key = { it.first }) { (_, n) ->
                Column {
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(formatNumber(n), fontSize = 16.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { BlockedNumbers.unblock(context, n); numbers = BlockedNumbers.list(context) }) { Text("Unblock", color = AppColors.Accent) }
                    }
                    HorizontalDivider(color = AppColors.Line)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Recordings

private data class Recording(val uri: Uri, val name: String, val date: Long, val durationMs: Long)

@Composable
fun RecordingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<Recording>>(emptyList()) }
    var reload by remember { mutableStateOf(0) }
    var confirmDelete by remember { mutableStateOf<Recording?>(null) }

    LaunchedEffect(reload) {
        items = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.DURATION),
                    "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?", arrayOf("${RecordingService.folder()}%"),
                    "${MediaStore.Audio.Media.DATE_ADDED} DESC",
                )?.use { c ->
                    buildList {
                        while (c.moveToNext()) {
                            add(Recording(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, c.getLong(0)), c.getString(1), c.getLong(2) * 1000, c.getLong(3)))
                        }
                    }
                } ?: emptyList()
            }.getOrDefault(emptyList())
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Recordings", onBack)
        Text("Saved in ${RecordingService.folder()} on your phone.", color = AppColors.SubText, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp))
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No recordings yet.", color = AppColors.SubText) }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
                items(items, key = { it.uri.toString() }) { r ->
                    Column {
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.name.substringBeforeLast('.').substringBefore('_'), fontSize = 16.sp)
                                Text(
                                    SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(r.date)) + " · " + formatDuration(r.durationMs / 1000),
                                    color = AppColors.SubText, fontSize = 13.sp,
                                )
                            }
                            IconButton(onClick = {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(r.uri, "audio/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                            }) { Icon(Icons.Outlined.PlayArrow, "Play", tint = AppColors.Accent) }
                            IconButton(onClick = {
                                val send = Intent(Intent.ACTION_SEND).setType("audio/*").putExtra(Intent.EXTRA_STREAM, r.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                runCatching { context.startActivity(Intent.createChooser(send, "Share recording")) }
                            }) { Icon(Icons.Outlined.Share, "Share", tint = AppColors.SubText) }
                            IconButton(onClick = { confirmDelete = r }) { Icon(Icons.Outlined.DeleteOutline, "Delete", tint = AppColors.SubText) }
                        }
                        HorizontalDivider(color = AppColors.Line)
                    }
                }
            }
        }
    }

    confirmDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete recording?") },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { context.contentResolver.delete(r.uri, null, null) }
                    confirmDelete = null
                    reload++
                }) { Text("Delete", color = AppColors.Missed) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

// ---------------------------------------------------------------- One contact's call history

@Composable
fun ContactHistoryScreen(callsVm: CallsViewModel, contactsVm: ContactsViewModel, contactId: Long, onBack: () -> Unit, onOpenProfile: (Long) -> Unit) {
    val context = LocalContext.current
    val entries by callsVm.entries.collectAsStateWithLifecycle()
    val list by contactsVm.listState.collectAsStateWithLifecycle()
    val name = (list.favorites + list.others).firstOrNull { it.contact.id == contactId }?.title ?: "Call history"
    val mine = entries.filter { it.contact?.contact?.id == contactId }
    LaunchedEffect(Unit) { callsVm.query.value = "" }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(name, onBack)
        if (mine.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No calls with this contact.", color = AppColors.SubText) }
        } else {
            CallList(mine, onOpenProfile = onOpenProfile, onAddToContacts = {}, onMessage = { openMessage(context, it) }, onDelete = { callsVm.delete(it) })
        }
    }
}
