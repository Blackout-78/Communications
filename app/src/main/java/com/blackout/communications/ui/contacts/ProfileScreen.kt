package com.blackout.communications.ui.contacts

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blackout.communications.calls.BlockedNumbers
import com.blackout.communications.calls.Phone
import com.blackout.communications.data.mainNumber
import com.blackout.communications.data.title
import com.blackout.communications.ui.components.Avatar
import com.blackout.communications.ui.components.InfoCard
import com.blackout.communications.ui.theme.AppColors

@Composable
fun ProfileScreen(
    vm: ContactsViewModel,
    contactId: Long,
    onMessage: (String) -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val context = LocalContext.current
    val data by remember(contactId) { vm.contact(contactId) }.collectAsState(initial = null)
    val owners by vm.speedDialOwners.collectAsStateWithLifecycle()
    val previewing by RingtonePreview.playing.collectAsStateWithLifecycle()
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { RingtonePreview.stop() } }
    val c = data ?: return

    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    var ringtoneDialog by remember { mutableStateOf(false) }
    var speedDialDialog by remember { mutableStateOf(false) }
    // Changes when blocking changes, so the Block row redraws.
    var blockVersion by remember { mutableStateOf(0) }
    val blocked = remember(c.numbers, blockVersion) {
        c.numbers.isNotEmpty() && c.numbers.all { BlockedNumbers.isBlocked(context, it.number) }
    }

    val pickSong = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.setRingtone(contactId, uri)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Top bar: back, edit, favorite star, more
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Contacts") }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "Edit contact") }
            IconButton(onClick = { vm.setFavorite(contactId, !c.contact.favorite) }) {
                if (c.contact.favorite) Icon(Icons.Filled.Star, "Remove from favorites", tint = AppColors.Favorite)
                else Icon(Icons.Outlined.StarOutline, "Add to favorites")
            }
            androidx.compose.foundation.layout.Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More options") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Delete contact", color = AppColors.Missed) }, onClick = { menu = false; confirmDelete = true })
                }
            }
        }

        // Picture, name, group
        Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(c.title, c.contact.photoPath, 140.dp)
            Text(c.title, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
            if (c.contact.groupName.isNotBlank()) Text(c.contact.groupName, color = AppColors.SubText, fontSize = 14.sp)
        }

        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Big Call and Message buttons
            val main = c.mainNumber
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton("Call", Icons.Outlined.Phone, primary = true, enabled = main != null, modifier = Modifier.weight(1f)) {
                    main?.let { dial(context, it.number) }
                }
                BigButton("Message", Icons.Outlined.ChatBubbleOutline, primary = false, enabled = main != null, modifier = Modifier.weight(1f)) {
                    main?.let { onMessage(it.number) }
                }
            }

            // Every number, each with its own message and call buttons
            if (c.numbers.isNotEmpty()) {
                InfoCard {
                    c.numbers.sortedByDescending { it.isMain }.forEachIndexed { i, n ->
                        if (i > 0) HorizontalDivider(color = AppColors.Line)
                        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(formatNumber(n.number), fontSize = 16.sp)
                                Text(if (n.isMain) "${n.label} · Main" else n.label, color = AppColors.SubText, fontSize = 13.sp)
                            }
                            IconButton(onClick = { onMessage(n.number) }) { Icon(Icons.Outlined.ChatBubbleOutline, "Message ${n.number}", tint = AppColors.AccentInk) }
                            IconButton(onClick = { dial(context, n.number) }) { Icon(Icons.Outlined.Phone, "Call ${n.number}", tint = AppColors.AccentInk) }
                        }
                    }
                }
            }

            // Ringtone, speed dial, group, favorite
            InfoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        ProfileRow(Icons.Outlined.MusicNote, "Ringtone", c.contact.ringtoneName?.let { "$it · saved" } ?: "Default ringtone", chevron = c.contact.ringtonePath == null) { ringtoneDialog = true }
                    }
                    if (c.contact.ringtonePath != null) {
                        IconButton(onClick = { RingtonePreview.toggle(context, c.contact.ringtonePath) }) {
                            Icon(if (previewing) Icons.Filled.Stop else Icons.Filled.PlayArrow, if (previewing) "Stop" else "Play ringtone", tint = AppColors.AccentInk)
                        }
                    }
                }
                if (c.contact.ringtonePath != null && !Phone.isDefaultPhoneApp(context)) {
                    Text(
                        "This ringtone plays only when Communications is your phone app. Set it in the Calls tab.",
                        color = AppColors.Missed, fontSize = 12.sp, modifier = Modifier.padding(start = 50.dp, end = 16.dp, bottom = 10.dp),
                    )
                }
                HorizontalDivider(color = AppColors.Line)
                val key = c.contact.speedDial
                ProfileRow(Icons.Outlined.Dialpad, "Speed dial", if (key != null) "Hold $key on the dialpad to call" else "Not set", value = key?.toString(), chevron = true) { speedDialDialog = true }
                HorizontalDivider(color = AppColors.Line)
                ProfileRow(Icons.Outlined.Group, "Group", null, value = c.contact.groupName.ifBlank { "None" }, chevron = true, onClick = onEdit)
                HorizontalDivider(color = AppColors.Line)
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.StarOutline, null, tint = AppColors.SubText, modifier = Modifier.size(20.dp))
                    Text("Favorite", fontSize = 15.sp, modifier = Modifier.padding(start = 14.dp).weight(1f))
                    Switch(
                        checked = c.contact.favorite,
                        onCheckedChange = { vm.setFavorite(contactId, it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = AppColors.Accent, checkedThumbColor = Color.White),
                    )
                }
            }

            InfoCard {
                ProfileRow(Icons.Outlined.History, "Call history", null, chevron = true, onClick = onOpenHistory)
                HorizontalDivider(color = AppColors.Line)
                ProfileRow(
                    Icons.Outlined.Block,
                    if (blocked) "Unblock this contact" else "Block this contact",
                    if (blocked) "Calls and texts from them are stopped" else "Stops calls and texts from all their numbers",
                    color = AppColors.Missed,
                    enabled = c.numbers.isNotEmpty(),
                ) {
                    if (!BlockedNumbers.canUse(context)) {
                        Toast.makeText(context, "Make Communications your phone app (Calls tab) to block numbers", Toast.LENGTH_LONG).show()
                    } else {
                        if (blocked) {
                            c.numbers.forEach { n -> BlockedNumbers.unblock(context, n.number) }
                            blockVersion++
                        } else {
                            confirmBlock = true
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmBlock) {
        com.blackout.communications.ui.components.BlockConfirmDialog(
            who = c.title,
            isContact = true,
            onConfirm = {
                confirmBlock = false
                c.numbers.forEach { n -> BlockedNumbers.block(context, n.number, "contact profile") }
                blockVersion++
            },
            onDismiss = { confirmBlock = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${c.title}?") },
            text = { Text("This removes the contact and their picture from this phone. It can't be undone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(contactId); onBack() }) { Text("Delete", color = AppColors.Missed) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    if (ringtoneDialog) {
        AlertDialog(
            onDismissRequest = { ringtoneDialog = false },
            title = { Text("Ringtone") },
            text = { Text("Pick a song from your phone. The app keeps its own copy, so the ringtone still works if the original file is moved or deleted.") },
            confirmButton = { TextButton(onClick = { ringtoneDialog = false; pickSong.launch(arrayOf("audio/*")) }) { Text("Pick a song") } },
            dismissButton = {
                TextButton(onClick = { ringtoneDialog = false; vm.setRingtone(contactId, null) }) { Text("Use default") }
            },
        )
    }

    if (speedDialDialog) {
        AlertDialog(
            onDismissRequest = { speedDialDialog = false },
            title = { Text("Speed dial key") },
            text = {
                Column {
                    Text("Hold this key on the dialpad to call ${c.title}. Key 1 is voicemail.", color = AppColors.SubText, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    (2..9).forEach { key ->
                        val owner = owners[key]
                        val ownerText = when {
                            owner == null -> "Empty"
                            owner.contact.id == contactId -> "This contact"
                            else -> "${owner.title} (will be replaced)"
                        }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .clickable { vm.setSpeedDial(contactId, key); speedDialDialog = false }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("$key", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.size(width = 32.dp, height = 24.dp))
                            Text(ownerText, color = if (owner?.contact?.id == contactId) AppColors.Accent else AppColors.SubText)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { speedDialDialog = false }) { Text("Close") } },
            dismissButton = {
                if (c.contact.speedDial != null) {
                    TextButton(onClick = { vm.setSpeedDial(contactId, null); speedDialDialog = false }) { Text("Remove") }
                }
            },
        )
    }
}

@Composable
private fun BigButton(label: String, icon: ImageVector, primary: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (primary) AppColors.Accent else AppColors.Card)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val tint = if (primary) Color.White else AppColors.Text
        Icon(icon, null, tint = tint)
        Text(label, color = tint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ProfileRow(
    icon: ImageVector,
    label: String,
    sub: String?,
    value: String? = null,
    chevron: Boolean = false,
    color: Color = AppColors.Text,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (color == AppColors.Text) AppColors.SubText else color, modifier = Modifier.size(20.dp))
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(label, color = color, fontSize = 15.sp)
            if (sub != null) Text(sub, color = AppColors.SubText, fontSize = 13.sp)
        }
        if (value != null) Text(value, color = AppColors.SubText, fontSize = 14.sp)
        if (chevron) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = AppColors.SubText)
    }
}

/** Calls go through Communications once it's the phone app. Texts open the current texting app until Stage 3. */
private fun dial(context: Context, number: String) = Phone.call(context, number)

private fun message(context: Context, number: String) =
    launch(context, Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)))

private fun launch(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app found to do that", Toast.LENGTH_SHORT).show()
    }
}

/** Plays a contact's saved ringtone so you can check it, for up to 15 seconds. */
object RingtonePreview {
    val playing = kotlinx.coroutines.flow.MutableStateFlow(false)
    private var player: android.media.MediaPlayer? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    fun toggle(context: Context, path: String?) {
        if (player != null) { stop(); return }
        val file = path?.let { java.io.File(it) }
        if (file == null || !file.exists()) {
            Toast.makeText(context, "The saved ringtone is missing. Pick the song again.", Toast.LENGTH_LONG).show()
            return
        }
        player = runCatching {
            android.media.MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { stop() }
                prepare()
                start()
            }
        }.getOrNull()
        if (player == null) {
            Toast.makeText(context, "This file can't be played. Try another song.", Toast.LENGTH_LONG).show()
            return
        }
        playing.value = true
        handler.postDelayed({ stop() }, 15_000)
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        player?.runCatching { stop(); release() }
        player = null
        playing.value = false
    }
}
