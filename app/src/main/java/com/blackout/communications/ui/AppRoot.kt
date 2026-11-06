package com.blackout.communications.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blackout.communications.ui.calls.BlockedNumbersScreen
import com.blackout.communications.ui.calls.CallSettingsScreen
import com.blackout.communications.ui.calls.CallsScreen
import com.blackout.communications.ui.calls.CallsViewModel
import com.blackout.communications.ui.calls.ContactHistoryScreen
import com.blackout.communications.ui.calls.DialpadScreen
import com.blackout.communications.ui.calls.RecordingsScreen
import com.blackout.communications.ui.calls.SpeedDialScreen
import com.blackout.communications.ui.calls.openMessage
import com.blackout.communications.ui.contacts.ContactsScreen
import com.blackout.communications.ui.contacts.ContactsViewModel
import com.blackout.communications.ui.contacts.EditContactScreen
import com.blackout.communications.ui.contacts.ProfileScreen
import com.blackout.communications.ui.placeholder.ComingSoon
import com.blackout.communications.ui.theme.AppColors

/** Bottom tabs, in the order from the design: Contacts, Chats, Calls, Settings. */
enum class Tab(val label: String, val icon: ImageVector) {
    Contacts("Contacts", Icons.Outlined.Person),
    Chats("Chats", Icons.Outlined.ChatBubbleOutline),
    Calls("Calls", Icons.Outlined.Phone),
    Settings("Settings", Icons.Outlined.Settings),
}

/** Screens that open on top of the tabs. */
sealed interface Screen {
    data class Profile(val id: Long) : Screen
    data class Edit(val id: Long?, val number: String? = null) : Screen
    data class Dialpad(val number: String = "") : Screen
    data class ContactHistory(val id: Long) : Screen
    data object CallSettings : Screen
    data object SpeedDial : Screen
    data object Blocked : Screen
    data object Recordings : Screen
}

/**
 * @param dialRequest a number (or "") to open on the dialpad, from a tel: link or "Add call". Null when none.
 */
@Composable
fun AppRoot(dialRequest: String?, onDialHandled: () -> Unit) {
    val contactsVm: ContactsViewModel = viewModel()
    val callsVm: CallsViewModel = viewModel()
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(Tab.Contacts) }
    val stack = remember { mutableStateListOf<Screen>() }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }

    LaunchedEffect(dialRequest) {
        if (dialRequest != null) {
            stack.clear()
            tab = Tab.Calls
            stack.add(Screen.Dialpad(dialRequest))
            onDialHandled()
        }
    }

    BackHandler(enabled = stack.isNotEmpty()) { pop() }

    Surface(color = AppColors.Background, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            when (val top = stack.lastOrNull()) {
                is Screen.Profile -> ProfileScreen(
                    vm = contactsVm,
                    contactId = top.id,
                    onBack = { pop() },
                    onEdit = { stack.add(Screen.Edit(top.id)) },
                    onOpenHistory = { stack.add(Screen.ContactHistory(top.id)) },
                )
                is Screen.Edit -> EditContactScreen(
                    vm = contactsVm,
                    contactId = top.id,
                    prefillNumber = top.number,
                    onDone = { savedId ->
                        pop()
                        // A newly added contact opens on its profile.
                        if (top.id == null && savedId != null) stack.add(Screen.Profile(savedId))
                    },
                )
                is Screen.Dialpad -> DialpadScreen(contactsVm = contactsVm, initial = top.number, onBack = { pop() })
                is Screen.ContactHistory -> ContactHistoryScreen(
                    callsVm = callsVm, contactsVm = contactsVm, contactId = top.id,
                    onBack = { pop() }, onOpenProfile = { stack.add(Screen.Profile(it)) },
                )
                Screen.CallSettings -> CallSettingsScreen(
                    contactsVm = contactsVm,
                    onBack = { pop() },
                    onOpenSpeedDial = { stack.add(Screen.SpeedDial) },
                    onOpenBlocked = { stack.add(Screen.Blocked) },
                    onOpenRecordings = { stack.add(Screen.Recordings) },
                )
                Screen.SpeedDial -> SpeedDialScreen(contactsVm = contactsVm, onBack = { pop() })
                Screen.Blocked -> BlockedNumbersScreen(onBack = { pop() })
                Screen.Recordings -> RecordingsScreen(onBack = { pop() })
                null -> Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        when (tab) {
                            Tab.Contacts -> ContactsScreen(
                                vm = contactsVm,
                                onOpenProfile = { stack.add(Screen.Profile(it)) },
                                onAddContact = { stack.add(Screen.Edit(null)) },
                                onOpenDialpad = { stack.add(Screen.Dialpad()) },
                            )
                            Tab.Chats -> ComingSoon(
                                Tab.Chats.icon, "Chats", "Coming in Stage 3",
                                "Text messages, picture messages and chat bubbles over other apps.",
                            )
                            Tab.Calls -> CallsScreen(
                                vm = callsVm,
                                onOpenDialpad = { stack.add(Screen.Dialpad()) },
                                onOpenProfile = { stack.add(Screen.Profile(it)) },
                                onAddToContacts = { stack.add(Screen.Edit(null, it)) },
                                onMessage = { openMessage(context, it) },
                                onOpenCallSettings = { stack.add(Screen.CallSettings) },
                                onOpenBlocked = { stack.add(Screen.Blocked) },
                                onOpenRecordings = { stack.add(Screen.Recordings) },
                            )
                            Tab.Settings -> ComingSoon(
                                Tab.Settings.icon, "Settings", "Coming in Stage 4",
                                "Themes, colors, background pictures, app icon color, and backup with reminders.",
                            )
                        }
                    }
                    TabBar(selected = tab, onSelect = { tab = it })
                }
            }
        }
    }
}

/** Dark rounded bar with an icon and a label for each tab. */
@Composable
private fun TabBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(AppColors.Card)
            .padding(vertical = 6.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { t ->
            val active = t == selected
            val color = if (active) AppColors.Accent else AppColors.SubText
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .widthIn(min = 64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Tab) { onSelect(t) }
                    .semantics { this.selected = active }
                    .padding(vertical = 6.dp),
            ) {
                Icon(t.icon, contentDescription = null, tint = color)
                Text(t.label, color = color, fontSize = 11.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium)
            }
        }
    }
}
