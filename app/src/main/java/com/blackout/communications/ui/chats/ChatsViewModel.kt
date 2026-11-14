package com.blackout.communications.ui.chats

import android.Manifest
import android.app.Application
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blackout.communications.CommunicationsApp
import com.blackout.communications.calls.Phone
import com.blackout.communications.chats.ChatCache
import com.blackout.communications.chats.ChatMessage
import com.blackout.communications.chats.ChatThread
import com.blackout.communications.chats.MessageStore
import com.blackout.communications.data.ContactWithNumbers
import com.blackout.communications.data.displayName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ThreadRow(
    val thread: ChatThread,
    val title: String,
    val contact: ContactWithNumbers?,
    val pinned: Boolean,
    val muted: Boolean,
)

object Texting {
    val MESSAGES_URI: Uri = Uri.parse("content://mms-sms/")

    fun isDefaultTextingApp(context: Context): Boolean = Telephony.Sms.getDefaultSmsPackage(context) == context.packageName

    fun requestRoleIntent(context: Context): Intent? =
        context.getSystemService(RoleManager::class.java)?.takeIf { it.isRoleAvailable(RoleManager.ROLE_SMS) }
            ?.createRequestRoleIntent(RoleManager.ROLE_SMS)

    fun permissions(): Array<String> = buildList {
        add(Manifest.permission.READ_SMS)
        add(Manifest.permission.SEND_SMS)
        add(Manifest.permission.RECEIVE_SMS)
        add(Manifest.permission.RECEIVE_MMS)
        add(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    fun canRead(context: Context) = Phone.has(context, Manifest.permission.READ_SMS)

    /** A conversation's messages, refreshed whenever the message storage changes. */
    fun observeMessages(context: Context, threadId: Long): Flow<List<ChatMessage>> = callbackFlow {
        val ticks = Channel<Unit>(Channel.CONFLATED)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                if (MessageStore.saving.get() > 0) return // a picture/video is still arriving
                ticks.trySend(Unit)
            }
        }
        ChatCache.messages[threadId]?.let { send(it) } // show what we had, right away
        context.contentResolver.registerContentObserver(MESSAGES_URI, true, observer)
        ticks.trySend(Unit)
        val job = launch(Dispatchers.IO) {
            for (t in ticks) {
                kotlinx.coroutines.delay(80) // one reload for a burst of changes
                send(MessageStore.messages(context, threadId))
            }
        }
        awaitClose {
            context.contentResolver.unregisterContentObserver(observer)
            job.cancel()
        }
    }.flowOn(Dispatchers.IO)
}

class ChatsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CommunicationsApp
    private val threads = MutableStateFlow(ChatCache.threads)
    val groupFilter = MutableStateFlow<String?>(null)
    val ready = MutableStateFlow(false)

    private val contacts = app.contacts.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val rows: StateFlow<List<ThreadRow>> = combine(threads, contacts, app.chatPrefs.state, groupFilter) { list, people, flags, group ->
        val byKey = HashMap<String, ContactWithNumbers>()
        people.forEach { c -> c.numbers.forEach { n -> byKey[Phone.matchKey(n.number)] = c } }
        list.map { t ->
            val matched = t.addresses.map { byKey[Phone.matchKey(it)] }
            val title = t.addresses.mapIndexed { i, a -> matched[i]?.contact?.displayName?.ifBlank { null } ?: a }.joinToString(", ")
            ThreadRow(t, title.ifBlank { "Unknown" }, matched.singleOrNull(), t.id in flags.pinned, t.id in flags.muted)
        }.filter { r ->
            group == null || r.contact?.contact?.groupName?.equals(group, ignoreCase = true) == true
        }.sortedWith(compareByDescending<ThreadRow> { it.pinned }.thenByDescending { it.thread.date })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val handler = Handler(Looper.getMainLooper())
    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            if (MessageStore.saving.get() > 0) return // a picture/video is still arriving
            // One refresh for a burst of changes (sending a text changes storage several times).
            handler.removeCallbacksAndMessages(REFRESH)
            handler.postAtTime({ refresh() }, REFRESH, android.os.SystemClock.uptimeMillis() + 150)
        }
    }
    private companion object { val REFRESH = Any() }
    private var observing = false

    init { refresh() }

    fun refresh() {
        val ctx = getApplication<Application>()
        val ok = Texting.isDefaultTextingApp(ctx) && Texting.canRead(ctx)
        ready.value = ok
        if (!ok) return
        if (!observing) {
            runCatching { ctx.contentResolver.registerContentObserver(Texting.MESSAGES_URI, true, observer) }
            observing = true
        }
        viewModelScope.launch { threads.value = withContext(Dispatchers.IO) { MessageStore.threads(ctx) } }
    }

    fun togglePin(id: Long) = app.chatPrefs.togglePin(id)
    fun toggleMute(id: Long) = app.chatPrefs.toggleMute(id)

    /** Deletes several conversations at once. */
    fun deleteMany(ids: Collection<Long>) = viewModelScope.launch(Dispatchers.IO) {
        ids.forEach { id ->
            MessageStore.deleteThread(getApplication(), id)
            app.chatPrefs.forget(id)
        }
        refresh()
    }

    fun delete(id: Long) = viewModelScope.launch(Dispatchers.IO) {
        MessageStore.deleteThread(getApplication(), id)
        app.chatPrefs.forget(id)
        refresh()
    }

    override fun onCleared() {
        if (observing) getApplication<Application>().contentResolver.unregisterContentObserver(observer)
        super.onCleared()
    }
}
