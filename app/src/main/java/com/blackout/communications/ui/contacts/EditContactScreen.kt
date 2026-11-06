package com.blackout.communications.ui.contacts

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackout.communications.data.Contact
import com.blackout.communications.data.PhoneNumber
import com.blackout.communications.ui.components.Avatar
import com.blackout.communications.ui.theme.AppColors
import kotlinx.coroutines.flow.first

private val NumberLabels = listOf("Mobile", "Home", "Work", "Other")

private class NumberField(label: String, number: String, isMain: Boolean) {
    var label by mutableStateOf(label)
    var number by mutableStateOf(number)
    var isMain by mutableStateOf(isMain)
}

/** Add a new contact (contactId = null) or edit an existing one. */
@Composable
fun EditContactScreen(
    vm: ContactsViewModel,
    contactId: Long?,
    onDone: (savedId: Long?) -> Unit,
) {
    var loaded by remember { mutableStateOf(contactId == null) }
    var original by remember { mutableStateOf<Contact?>(null) }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("") }
    var favorite by remember { mutableStateOf(false) }
    var photoPath by remember { mutableStateOf<String?>(null) }
    val numbers = remember { mutableStateListOf(NumberField("Mobile", "", true)) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(contactId) {
        if (contactId != null) {
            val c = vm.contact(contactId).first() ?: return@LaunchedEffect
            original = c.contact
            firstName = c.contact.firstName
            lastName = c.contact.lastName
            group = c.contact.groupName
            favorite = c.contact.favorite
            photoPath = c.contact.photoPath
            numbers.clear()
            c.numbers.forEach { numbers.add(NumberField(it.label, it.number, it.isMain)) }
            if (numbers.isEmpty()) numbers.add(NumberField("Mobile", "", true))
            loaded = true
        }
    }

    // A picture picked in this screen but not saved is deleted when leaving without saving.
    fun cancel() {
        if (photoPath != original?.photoPath) vm.discardPhoto(photoPath)
        onDone(null)
    }
    BackHandler { cancel() }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            vm.pickPhoto(uri) { newPath ->
                if (newPath != null) {
                    if (photoPath != original?.photoPath) vm.discardPhoto(photoPath)
                    photoPath = newPath
                }
            }
        }
    }

    fun save() {
        val cleaned = numbers.filter { it.number.isNotBlank() }
        if (firstName.isBlank() && lastName.isBlank() && cleaned.isEmpty()) {
            error = "Add a name or a phone number."
            return
        }
        val base = original ?: Contact()
        val contact = base.copy(
            firstName = firstName.trim(),
            lastName = lastName.trim(),
            groupName = group.trim(),
            favorite = favorite,
            photoPath = photoPath,
        )
        val list = cleaned.map { PhoneNumber(label = it.label, number = it.number.trim(), isMain = it.isMain) }
        vm.save(contact, list, previousPhoto = original?.photoPath) { id -> onDone(id) }
    }

    if (!loaded) return

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { cancel() }) { Icon(Icons.Filled.Close, "Cancel") }
            Text(if (contactId == null) "New contact" else "Edit contact", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = { save() }) { Text("Save", color = AppColors.Accent, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Picture with a camera badge. Tap to pick from the phone's photos.
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.clip(CircleShape).clickable { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Avatar("${firstName}${lastName}".ifBlank { "#" }, photoPath, 120.dp)
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(36.dp).clip(CircleShape).background(AppColors.Accent),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.PhotoCamera, "Choose picture", tint = Color.White, modifier = Modifier.size(20.dp)) }
                }
                if (photoPath != null) {
                    TextButton(onClick = {
                        if (photoPath != original?.photoPath) vm.discardPhoto(photoPath)
                        photoPath = null
                    }) { Text("Remove picture", color = AppColors.SubText) }
                }
            }

            Field("First name", firstName, { firstName = it }, KeyboardType.Text, KeyboardCapitalization.Words)
            Field("Last name", lastName, { lastName = it }, KeyboardType.Text, KeyboardCapitalization.Words)

            Text("PHONE NUMBERS", color = AppColors.SubText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
            Text("The filled circle marks the main number. It shows in your contact list and is used by speed dial.", color = AppColors.SubText, fontSize = 13.sp)
            numbers.forEachIndexed { index, field ->
                NumberRow(
                    field = field,
                    canDelete = numbers.size > 1,
                    onMain = { numbers.forEach { it.isMain = false }; field.isMain = true },
                    onDelete = {
                        val wasMain = field.isMain
                        numbers.removeAt(index)
                        if (wasMain) numbers.firstOrNull()?.isMain = true
                    },
                )
            }
            TextButton(onClick = { numbers.add(NumberField("Mobile", "", numbers.isEmpty())) }) {
                Icon(Icons.Filled.Add, null, tint = AppColors.Accent)
                Spacer(Modifier.width(6.dp))
                Text("Add number", color = AppColors.Accent)
            }

            Field("Group (for example Family or Work)", group, { group = it }, KeyboardType.Text, KeyboardCapitalization.Words)

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { favorite = !favorite }) {
                Checkbox(checked = favorite, onCheckedChange = { favorite = it }, colors = CheckboxDefaults.colors(checkedColor = AppColors.Accent))
                Text("Favorite (pinned to the top of Contacts)")
            }

            error?.let { Text(it, color = AppColors.Missed) }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, type: KeyboardType, caps: KeyboardCapitalization = KeyboardCapitalization.None, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type, capitalization = caps),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AppColors.Accent,
            unfocusedBorderColor = AppColors.Line,
            focusedLabelColor = AppColors.Accent,
            cursorColor = AppColors.Accent,
        ),
        modifier = modifier,
    )
}

@Composable
private fun NumberRow(field: NumberField, canDelete: Boolean, onMain: () -> Unit, onDelete: () -> Unit) {
    var labelMenu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box {
            TextButton(onClick = { labelMenu = true }) { Text(field.label, color = AppColors.Text) }
            DropdownMenu(expanded = labelMenu, onDismissRequest = { labelMenu = false }) {
                NumberLabels.forEach { l -> DropdownMenuItem(text = { Text(l) }, onClick = { field.label = l; labelMenu = false }) }
            }
        }
        Field("Number", field.number, { field.number = it }, KeyboardType.Phone, modifier = Modifier.weight(1f))
        RadioButton(selected = field.isMain, onClick = onMain, colors = RadioButtonDefaults.colors(selectedColor = AppColors.Accent))
        if (canDelete) IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, "Remove number", tint = AppColors.SubText) }
    }
}
