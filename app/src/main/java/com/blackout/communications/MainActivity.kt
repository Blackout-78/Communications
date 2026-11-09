package com.blackout.communications

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.blackout.communications.calls.BlockedNumbers
import com.blackout.communications.chats.Messenger
import com.blackout.communications.chats.MessageNotifications
import kotlinx.coroutines.launch
import com.blackout.communications.settings.AppIcon
import com.blackout.communications.ui.AppRoot
import com.blackout.communications.ui.theme.ThemeState
import com.blackout.communications.ui.theme.CommunicationsTheme

/** Something another app, a notification or a call asked to open. */
sealed interface LaunchRequest {
    data class Dial(val number: String) : LaunchRequest
    data class Thread(val id: Long) : LaunchRequest
    data class Compose(val address: String, val text: String) : LaunchRequest
    data class Contact(val id: Long) : LaunchRequest
    data object Backup : LaunchRequest
    /** Opened from the Contacts, Messages or Call Log icon. */
    data class OpenTab(val tab: String) : LaunchRequest
}

class MainActivity : ComponentActivity() {
    private val request = mutableStateOf<LaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        ThemeState.appearance.let { AppIcon.applyAfterUpdate(this, it.iconStyle, it.iconColor, it.iconColor2) }
        setContent {
            CommunicationsTheme {
                AppRoot(request = request.value, onRequestHandled = { request.value = null })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val app = application as CommunicationsApp
        Messenger.scope.launch { runCatching { BlockedNumbers.clearBlockedContacts(app) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        val data = intent.data
        val scheme = data?.scheme
        val alias = intent.component?.className.orEmpty()
        request.value = when (intent.action) {
            Intent.ACTION_MAIN -> when {
                alias.contains(".OpenContacts") -> LaunchRequest.OpenTab("Contacts")
                alias.contains(".OpenChats") -> LaunchRequest.OpenTab("Chats")
                alias.contains(".OpenCalls") -> LaunchRequest.OpenTab("Calls")
                else -> null
            }
            ACTION_ADD_CALL -> LaunchRequest.Dial("")
            ACTION_OPEN_BACKUP -> LaunchRequest.Backup
            ACTION_OPEN_TAB -> intent.getStringExtra(EXTRA_TAB)?.let { LaunchRequest.OpenTab(it) }
            ACTION_OPEN_THREAD -> intent.getLongExtra(MessageNotifications.EXTRA_THREAD, -1).takeIf { it >= 0 }?.let { LaunchRequest.Thread(it) }
            ACTION_OPEN_CONTACT -> intent.getLongExtra(EXTRA_CONTACT, -1).takeIf { it >= 0 }?.let { LaunchRequest.Contact(it) }
            Intent.ACTION_DIAL -> LaunchRequest.Dial(if (scheme == "tel") data.schemeSpecificPart.orEmpty() else "")
            Intent.ACTION_VIEW, Intent.ACTION_SENDTO, Intent.ACTION_SEND -> when (scheme) {
                "tel" -> LaunchRequest.Dial(data.schemeSpecificPart.orEmpty())
                "sms", "smsto", "mms", "mmsto" -> {
                    val address = data.schemeSpecificPart.orEmpty().substringBefore('?').split(',', ';').first().trim()
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.getStringExtra("sms_body") ?: ""
                    if (address.isNotBlank()) LaunchRequest.Compose(address, text) else null
                }
                else -> null
            }
            else -> null
        } ?: request.value
    }

    companion object {
        const val ACTION_ADD_CALL = "com.blackout.communications.ADD_CALL"
        const val ACTION_OPEN_THREAD = "com.blackout.communications.OPEN_THREAD"
        const val ACTION_OPEN_CONTACT = "com.blackout.communications.OPEN_CONTACT"
        const val EXTRA_CONTACT = "contact_id"
        const val ACTION_OPEN_TAB = "com.blackout.communications.OPEN_TAB"
        const val EXTRA_TAB = "tab"
        const val ACTION_OPEN_BACKUP = "com.blackout.communications.OPEN_BACKUP"
    }
}
