package com.blackout.communications.ui.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackout.communications.BuildConfigInfo
import com.blackout.communications.CommunicationsApp
import com.blackout.communications.backup.Backup
import com.blackout.communications.backup.BackupReminder
import com.blackout.communications.data.Appearance
import com.blackout.communications.data.ColorSlot
import com.blackout.communications.data.IconStyle
import com.blackout.communications.data.Palette
import com.blackout.communications.data.StartTab
import com.blackout.communications.data.TextSize
import com.blackout.communications.data.ThemeMode
import com.blackout.communications.settings.AppIcon
import com.blackout.communications.settings.IconGlyph
import com.blackout.communications.ui.components.ColorPickerDialog
import com.blackout.communications.ui.components.InfoCard
import com.blackout.communications.ui.components.Swatch
import com.blackout.communications.ui.theme.AppColors
import com.blackout.communications.ui.theme.ThemeState
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val TabNames = listOf("Contacts", "Chats", "Calls", "Settings")

@Composable
fun SettingsScreen(
    onOpenCallSettings: () -> Unit,
    onOpenBlocked: () -> Unit,
    onOpenSpeedDial: () -> Unit,
    scrollToBackup: Boolean = false,
) {
    val context = LocalContext.current
    val app = context.applicationContext as CommunicationsApp
    val scope = rememberCoroutineScope()
    val a = ThemeState.appearance
    fun update(change: (Appearance) -> Appearance) = app.updateAppearance(change)

    var themeDialog by remember { mutableStateOf(false) }
    var sizeDialog by remember { mutableStateOf(false) }
    var startDialog by remember { mutableStateOf(false) }
    var checkDialog by remember { mutableStateOf(false) }
    var callCheckDialog by remember { mutableStateOf(false) }
    var editTab by remember { mutableStateOf("Contacts") }
    var pickSlot by remember { mutableStateOf<ColorSlot?>(null) }
    var pickIcon by remember { mutableStateOf(0) } // 1 = main color, 2 = second color
    var pictureFor by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var lastBackup by remember { mutableStateOf(BackupReminder.lastBackup(context)) }
    var folderName by remember { mutableStateOf(Backup.folderName(context)) }

    val pickPicture = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val key = pictureFor ?: return@rememberLauncherForActivityResult
        if (uri != null) scope.launch {
            val path = app.fileStore.saveBackground(uri, "${key}_${UUID.randomUUID()}")
            if (path != null) update { cur ->
                cur.pictures[key]?.let { File(it).delete() }
                cur.copy(pictures = cur.pictures + (key to path))
            }
        }
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) { Backup.setFolder(context, tree); folderName = Backup.folderName(context) }
    }
    val pickImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { file ->
        if (file != null) scope.launch {
            busy = true
            val msg = Backup.import(context, file)
            busy = false
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    val scroll = rememberScrollState()
    // Opened from the backup reminder: jump to the Backup section.
    LaunchedEffect(scrollToBackup, scroll.maxValue) {
        if (scrollToBackup && scroll.maxValue > 0) scroll.animateScrollTo((scroll.maxValue * 0.8f).toInt())
    }
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 12.dp)) {
        Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 12.dp))

        // ------------------------------------------------ 1. Appearance
        Section("Appearance") {
            Row1("Theme", a.theme.label) { themeDialog = true }
            Divider()
            CheckRow("Use same colors for all tabs", a.sameForAllTabs) { v -> update { it.copy(sameForAllTabs = v) } }
            if (!a.sameForAllTabs) {
                Text("Editing colors for:", fontSize = 13.sp, color = AppColors.SubText, modifier = Modifier.padding(start = 16.dp, top = 6.dp))
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TabNames.forEach { t -> SmallChip(t, editTab == t) { editTab = t } }
                }
            }
            val palette = if (a.sameForAllTabs) a.palette else a.tabPalettes[editTab] ?: a.palette
            ColorSlot.entries.forEach { slot ->
                Divider()
                val c = palette.colors[slot]?.let { Color(it) } ?: themeColor(slot)
                Row(Modifier.fillMaxWidth().clickable { pickSlot = slot }.heightIn(min = 52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(slot.label, fontSize = 15.sp)
                        if (palette.colors[slot] == null) Text("Theme color", fontSize = 12.sp, color = AppColors.SubText)
                    }
                    Swatch(c)
                }
            }
            Divider()
            Text("Background picture", fontSize = 15.sp, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
            RadioRow("One picture for the whole app", !a.picturePerTab) { update { it.copy(picturePerTab = false) } }
            RadioRow("A different picture for each tab", a.picturePerTab) { update { it.copy(picturePerTab = true) } }
            (if (a.picturePerTab) TabNames else listOf("all")).forEach { key ->
                val label = if (key == "all") "Picture" else "$key picture"
                val has = a.pictures[key] != null
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { pictureFor = key; pickPicture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text(if (has) "Change" else "Choose", color = AppColors.Accent)
                    }
                    if (has) TextButton(onClick = {
                        update { cur -> cur.pictures[key]?.let { File(it).delete() }; cur.copy(pictures = cur.pictures - key) }
                    }) { Text("Remove", color = AppColors.SubText) }
                }
            }
            Text("Dim the picture so text stays readable", fontSize = 13.sp, color = AppColors.SubText, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
            Slider(
                value = a.dim, onValueChange = { v -> update { it.copy(dim = v) } }, valueRange = 0f..0.85f,
                colors = SliderDefaults.colors(thumbColor = AppColors.Accent, activeTrackColor = AppColors.Accent),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Divider()
            Row1("Text size", a.textSize.label) { sizeDialog = true }
            Divider()
            Row(Modifier.fillMaxWidth().clickable {
                update { it.copy(palette = Palette(), tabPalettes = emptyMap()) }
                Toast.makeText(context, "Colors reset", Toast.LENGTH_SHORT).show()
            }.heightIn(min = 52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Reset to default colors", color = AppColors.Accent, fontSize = 15.sp)
            }
        }

        // ------------------------------------------------ 2. App icon
        Section("App icon") {
            // Live preview of your icon, as it will look on the home screen.
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val preview = remember(a.iconStyle, a.iconColor, a.iconColor2) {
                    AppIcon.render(context, IconGlyph.Main, a.iconStyle, a.iconColor, a.iconColor2, 288).asImageBitmap()
                }
                Image(preview, "Your app icon", modifier = Modifier.size(84.dp).clip(CircleShape))
                Column(Modifier.padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Style ${a.iconStyle.name} · ${a.iconStyle.label}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(a.iconStyle.description, fontSize = 13.sp, color = AppColors.SubText)
                }
            }
            Divider()
            Text("Style", fontSize = 15.sp, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconStyle.entries.forEach { st ->
                    val img = remember(st, a.iconColor, a.iconColor2) {
                        AppIcon.render(context, IconGlyph.Main, st, a.iconColor, a.iconColor2, 168).asImageBitmap()
                    }
                    val selected = st == a.iconStyle
                    Column(
                        Modifier.clip(RoundedCornerShape(12.dp))
                            .background(if (selected) AppColors.Accent.copy(alpha = 0.18f) else Color.Transparent)
                            .clickable { setIcon(context, app, a.copy(iconStyle = st)) }
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Image(img, "Style ${st.name}", modifier = Modifier.size(56.dp).clip(CircleShape))
                        Text("${st.name} · ${st.label}", fontSize = 12.sp, color = if (selected) AppColors.Accent else AppColors.SubText, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            Divider()
            Row(Modifier.fillMaxWidth().clickable { pickIcon = 1 }.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Main color", fontSize = 15.sp)
                    Text("Color wheel", fontSize = 12.sp, color = AppColors.SubText)
                }
                Swatch(Color(a.iconColor))
            }
            Divider()
            Row(Modifier.fillMaxWidth().clickable { pickIcon = 2 }.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Second color", fontSize = 15.sp)
                    Text("Color wheel", fontSize = 12.sp, color = AppColors.SubText)
                }
                Swatch(Color(a.iconColor2))
            }
            Divider()
            Row1("Add icon to home screen", null, "Uses your exact colors. The app drawer uses the closest of 357 ready-made designs.") {
                if (!AppIcon.pinOrUpdateShortcut(context, IconGlyph.Main, a.iconStyle, a.iconColor, a.iconColor2, ask = true)) {
                    Toast.makeText(context, "Your home screen doesn't allow adding shortcuts", Toast.LENGTH_SHORT).show()
                }
            }
            Divider()
            Row(Modifier.fillMaxWidth().clickable {
                setIcon(context, app, a.copy(iconStyle = IconStyle.A, iconColor = AppIcon.DEFAULT_MAIN, iconColor2 = AppIcon.DEFAULT_SECOND))
            }.heightIn(min = 52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Reset to original icon", color = AppColors.Accent, fontSize = 15.sp)
            }
        }

        // ------------------------------------------------ 3. General
        Section("General") { Row1("Default tab on launch", a.startTab.label) { startDialog = true } }

        // ------------------------------------------------ 4. Calls
        Section("Calls") {
            Row1("Call settings", null, "Unknown callers, decline with message, recording", onOpenCallSettings)
            Divider()
            Row1("Speed dial", null, "Hold a number on the dialpad to call", onOpenSpeedDial)
            Divider()
            Row1("Call check", null, "Recent calls, error codes, why a call was blocked") { callCheckDialog = true }
        }

        // ------------------------------------------------ 5. Chats
        Section("Chats") {
            Row1("Chat bubbles", null, "Turn on \"All conversations can bubble\" in your phone's settings") {
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_BUBBLE_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                } else {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
                runCatching { context.startActivity(intent) }
            }
            Divider()
            Row1("Message check", null, "Is everything set so texts reach you?") { checkDialog = true }
        }

        // ------------------------------------------------ 6. Blocked numbers
        Section("Blocked numbers") { Row1("Blocked list", null, "Blocks both calls and texts", onOpenBlocked) }

        // ------------------------------------------------ 7. Backup
        Section("Backup") {
            Row1("Backup folder", folderName ?: "Not chosen") { pickFolder.launch(null) }
            Divider()
            Row1(if (busy) "Working…" else "Back up now", null, "Contacts (.vcf), messages and call history") {
                if (busy) return@Row1
                if (Backup.folder(context) == null) { pickFolder.launch(null); return@Row1 }
                scope.launch {
                    busy = true
                    val r = Backup.exportAll(context)
                    busy = false
                    lastBackup = BackupReminder.lastBackup(context)
                    Toast.makeText(
                        context,
                        r.error ?: "Backed up ${r.contacts} contacts, ${r.messages} messages and ${r.calls} calls.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
            Divider()
            Row1("Import from a backup file", null, "Adds only what's missing") { pickImport.launch(arrayOf("*/*")) }
            Divider()
            Text(
                (if (lastBackup > 0) "Last backup: " + SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(lastBackup)) else "No backup yet") +
                    ". You'll get a reminder every 2 weeks.",
                fontSize = 13.sp, color = AppColors.SubText, modifier = Modifier.padding(16.dp),
            )
        }

        // ------------------------------------------------ 8. About
        Section("About") {
            Text(com.blackout.communications.Version.full, fontSize = 15.sp, modifier = Modifier.padding(start = 16.dp, top = 14.dp))
            Text(
                "This app has no internet permission. No data leaves your phone.",
                fontSize = 14.sp, color = AppColors.SubText, modifier = Modifier.padding(16.dp),
            )
        }
        Spacer(Modifier.height(32.dp))
    }

    // ------------------------------------------------ Dialogs
    if (checkDialog) MessageCheckDialog { checkDialog = false }
    if (callCheckDialog) CallCheckDialog { callCheckDialog = false }
    if (themeDialog) ChoiceDialog("Theme", ThemeMode.entries.map { it.label }, a.theme.ordinal, { themeDialog = false }) { i ->
        update { it.copy(theme = ThemeMode.entries[i]) }; themeDialog = false
    }
    if (sizeDialog) ChoiceDialog("Text size", TextSize.entries.map { it.label }, a.textSize.ordinal, { sizeDialog = false }) { i ->
        update { it.copy(textSize = TextSize.entries[i]) }; sizeDialog = false
    }
    if (startDialog) ChoiceDialog("Default tab on launch", StartTab.entries.map { it.label }, a.startTab.ordinal, { startDialog = false }) { i ->
        update { it.copy(startTab = StartTab.entries[i]) }; startDialog = false
    }
    pickSlot?.let { slot ->
        val current = (if (a.sameForAllTabs) a.palette else a.tabPalettes[editTab] ?: a.palette).colors[slot]
        fun set(argb: Int?) = update { cur ->
            if (cur.sameForAllTabs) cur.copy(palette = cur.palette.with(slot, argb))
            else cur.copy(tabPalettes = cur.tabPalettes + (editTab to (cur.tabPalettes[editTab] ?: cur.palette).with(slot, argb)))
        }
        ColorPickerDialog(
            title = slot.label,
            initial = current ?: themeColor(slot).toArgbInt(),
            onPick = { set(it); pickSlot = null },
            onDismiss = { pickSlot = null },
            resetLabel = "Use theme color",
            onReset = { set(null); pickSlot = null },
        )
    }
    if (pickIcon != 0) {
        val main = pickIcon == 1
        ColorPickerDialog(
            title = if (main) "Icon main color" else "Icon second color",
            initial = if (main) a.iconColor else a.iconColor2,
            onPick = { color ->
                pickIcon = 0
                setIcon(context, app, if (main) a.copy(iconColor = color) else a.copy(iconColor2 = color))
                Toast.makeText(context, "Icon updated. Your home screen may take a moment to refresh.", Toast.LENGTH_LONG).show()
            },
            onDismiss = { pickIcon = 0 },
            resetLabel = "Original",
            onReset = {
                pickIcon = 0
                setIcon(context, app, if (main) a.copy(iconColor = AppIcon.DEFAULT_MAIN) else a.copy(iconColor2 = AppIcon.DEFAULT_SECOND))
            },
        )
    }
}

/** Saves the icon choice and updates the app drawer icon and home-screen shortcut. */
private fun setIcon(context: android.content.Context, app: CommunicationsApp, next: Appearance) {
    app.updateAppearance { it.copy(iconStyle = next.iconStyle, iconColor = next.iconColor, iconColor2 = next.iconColor2) }
    AppIcon.applyDrawerIcons(context, next.iconStyle, next.iconColor, next.iconColor2)
    AppIcon.updatePinnedShortcuts(context, next.iconStyle, next.iconColor, next.iconColor2)
}

private fun themeColor(slot: ColorSlot): Color = when (slot) {
    ColorSlot.Accent -> AppColors.Accent
    ColorSlot.Background -> AppColors.Background
    ColorSlot.Text -> AppColors.Text
    ColorSlot.MyBubble -> AppColors.MyBubble
    ColorSlot.TheirBubble -> AppColors.TheirBubble
}

private fun Color.toArgbInt(): Int = android.graphics.Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(title.uppercase(), color = AppColors.SubText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp, modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 6.dp))
    InfoCard { content() }
}

@Composable
private fun Divider() = HorizontalDivider(color = AppColors.Line)

@Composable
private fun Row1(label: String, value: String?, sub: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp)
            if (sub != null) Text(sub, fontSize = 12.sp, color = AppColors.SubText)
        }
        if (value != null) Text(value, fontSize = 14.sp, color = AppColors.SubText)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = AppColors.SubText)
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange, colors = CheckboxDefaults.colors(checkedColor = AppColors.Accent))
        Text(label, fontSize = 15.sp)
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = AppColors.Accent))
        Text(label, fontSize = 14.sp)
    }
}

@Composable
private fun SmallChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 13.sp,
        color = if (selected) AppColors.onColor(AppColors.Accent) else AppColors.Text,
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (selected) AppColors.Accent else AppColors.Background)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun ChoiceDialog(title: String, options: List<String>, selected: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { i, o ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(i) }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = i == selected, onClick = { onPick(i) }, colors = RadioButtonDefaults.colors(selectedColor = AppColors.Accent))
                        Text(o, fontSize = 16.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
