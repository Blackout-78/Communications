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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import com.blackout.communications.LaunchRequest
import com.blackout.communications.chats.MessageStore
import com.blackout.communications.ui.chats.ChatsScreen
import com.blackout.communications.ui.chats.ChatsViewModel
import com.blackout.communications.ui.chats.ConversationScreen
import com.blackout.communications.ui.chats.NewMessageScreen
import com.blackout.communications.ui.chats.Texting
import com.blackout.communications.ui.contacts.ContactsScreen
import com.blackout.communications.ui.contacts.ContactsViewModel
import com.blackout.communications.ui.contacts.EditContactScreen
import com.blackout.communications.ui.contacts.ProfileScreen
import com.blackout.communications.ui.settings.SettingsScreen
import com.blackout.communications.settings.AppIcon
import com.blackout.communications.ui.theme.ThemeState
import com.blackout.communications.data.StartTab
import com.blackout.communications.CommunicationsApp
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
    data class Conversation(val threadId: Long, val text: String = "") : Screen
    data object NewMessage : Screen
}

/**
 * @param request something to open: the dialpad, a conversation or a contact. Null when none.
 */
@Composable
fun AppRoot(request: LaunchRequest?, onRequestHandled: () -> Unit) {
    val contactsVm: ContactsViewModel = viewModel()
    val callsVm: CallsViewModel = viewModel()
    val chatsVm: ChatsViewModel = viewModel()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val app = context.applicationContext as CommunicationsApp
    var tab by rememberSaveable {
        val a = ThemeState.appearance
        val start = when (a.startTab) {
            StartTab.Contacts -> Tab.Contacts
            StartTab.Chats -> Tab.Chats
            StartTab.Calls -> Tab.Calls
            StartTab.LastUsed -> Tab.entries.firstOrNull { it.label == a.lastTab } ?: Tab.Contacts
        }
        mutableStateOf(start)
    }
    var backupFocus by remember { mutableStateOf(false) }
    // Each tab can have its own colors; remember the tab for "Last used".
    LaunchedEffect(tab) {
        ThemeState.activeTab = tab.label
        if (ThemeState.appearance.lastTab != tab.label) app.updateAppearance { it.copy(lastTab = tab.label) }
    }
    val stack = remember { mutableStateListOf<Screen>() }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }

    /** Opens the conversation with a number inside Communications, or the current texting app until it's set up. */
    fun openConversation(address: String, text: String = "") {
        if (!Texting.isDefaultTextingApp(context)) {
            openMessage(context, address)
            return
        }
        scope.launch {
            val id = withContext(Dispatchers.IO) { MessageStore.threadId(context, address) }
            stack.add(Screen.Conversation(id, text))
        }
    }

    LaunchedEffect(request) {
        when (val r = request ?: return@LaunchedEffect) {
            is LaunchRequest.Dial -> { stack.clear(); tab = Tab.Calls; stack.add(Screen.Dialpad(r.number)) }
            is LaunchRequest.Thread -> { stack.clear(); tab = Tab.Chats; stack.add(Screen.Conversation(r.id)) }
            is LaunchRequest.Compose -> { stack.clear(); tab = Tab.Chats; openConversation(r.address, r.text) }
            is LaunchRequest.Contact -> { stack.clear(); tab = Tab.Contacts; stack.add(Screen.Profile(r.id)) }
            LaunchRequest.Backup -> { stack.clear(); tab = Tab.Settings; backupFocus = true }
            is LaunchRequest.OpenTab -> { stack.clear(); tab = Tab.entries.firstOrNull { it.label == r.tab } ?: tab }
        }
        onRequestHandled()
    }

    BackHandler(enabled = stack.isNotEmpty()) { pop() }

    val contactsList by contactsVm.listState.collectAsStateWithLifecycle()
    val contactsGroups = contactsList.groups

    val picture = ThemeState.appearance.pictureFor(tab.label)
    Surface(color = AppColors.Background, modifier = Modifier.fillMaxSize()) {
        // Background picture for the whole app or this tab, dimmed so text stays readable.
        if (picture != null) {
            val bitmap by produceState<ImageBitmap?>(null, picture) {
                value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(picture)?.asImageBitmap() }
            }
            bitmap?.let {
                Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(AppColors.Background.copy(alpha = ThemeState.appearance.dim)))
            }
        }
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            when (val top = stack.lastOrNull()) {
                is Screen.Profile -> ProfileScreen(
                    vm = contactsVm,
                    contactId = top.id,
                    onMessage = { openConversation(it) },
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
                is Screen.Conversation -> ConversationScreen(
                    threadId = top.threadId,
                    onBack = { pop() },
                    onOpenProfile = { stack.add(Screen.Profile(it)) },
                    initialText = top.text,
                )
                Screen.NewMessage -> NewMessageScreen(
                    contactsVm = contactsVm,
                    onBack = { pop() },
                    onPick = { address -> pop(); openConversation(address) },
                )
                null -> Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        when (tab) {
                            Tab.Contacts -> ContactsScreen(
                                vm = contactsVm,
                                onOpenProfile = { stack.add(Screen.Profile(it)) },
                                onAddContact = { stack.add(Screen.Edit(null)) },
                                onOpenDialpad = { stack.add(Screen.Dialpad()) },
                                onOpenBackup = { tab = Tab.Settings; backupFocus = true },
                            )
                            Tab.Chats -> ChatsScreen(
                                vm = chatsVm,
                                groups = contactsGroups,
                                onOpenThread = { stack.add(Screen.Conversation(it)) },
                                onNewMessage = { stack.add(Screen.NewMessage) },
                            )
                            Tab.Calls -> CallsScreen(
                                vm = callsVm,
                                onOpenDialpad = { stack.add(Screen.Dialpad()) },
                                onOpenProfile = { stack.add(Screen.Profile(it)) },
                                onAddToContacts = { stack.add(Screen.Edit(null, it)) },
                                onMessage = { openConversation(it) },
                                onOpenCallSettings = { stack.add(Screen.CallSettings) },
                                onOpenBlocked = { stack.add(Screen.Blocked) },
                                onOpenRecordings = { stack.add(Screen.Recordings) },
                            )
                            Tab.Settings -> SettingsScreen(
                                onOpenCallSettings = { stack.add(Screen.CallSettings) },
                                onOpenBlocked = { stack.add(Screen.Blocked) },
                                onOpenSpeedDial = { stack.add(Screen.SpeedDial) },
                                scrollToBackup = backupFocus,
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
