package com.blackout.communications.ui.chats

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blackout.communications.CommunicationsApp
import com.blackout.communications.calls.BlockedNumbers
import com.blackout.communications.calls.Phone
import com.blackout.communications.chats.ChatCache
import com.blackout.communications.chats.ChatMessage
import com.blackout.communications.chats.ChatsState
import com.blackout.communications.chats.MessageNotifications
import com.blackout.communications.chats.MessageStore
import com.blackout.communications.chats.Messenger
import com.blackout.communications.data.ContactWithNumbers
import com.blackout.communications.data.displayName
import com.blackout.communications.ui.components.Avatar
import com.blackout.communications.ui.contacts.formatNumber
import com.blackout.communications.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationScreen(
    threadId: Long,
    onBack: (() -> Unit)?,
    onOpenProfile: (Long) -> Unit,
    initialText: String = "",
) {
    val context = LocalContext.current
    val app = context.applicationContext as CommunicationsApp
    val scope = rememberCoroutineScope()
    val messages by remember(threadId) { Texting.observeMessages(context, threadId) }.collectAsState(initial = ChatCache.messages[threadId] ?: emptyList())
    val flags by app.chatPrefs.state.collectAsStateWithLifecycle()

    var addresses by remember { mutableStateOf(ChatCache.threads.firstOrNull { it.id == threadId }?.addresses.orEmpty()) }
    var contact by remember { mutableStateOf<ContactWithNumbers?>(null) }
    LaunchedEffect(threadId) {
        addresses = withContext(Dispatchers.IO) { MessageStore.addresses(context, threadId) }
        contact = if (addresses.size == 1) app.contacts.findByNumber(addresses.first()) else null
    }
    val address = addresses.singleOrNull()
    val title = contact?.contact?.displayName?.ifBlank { null } ?: addresses.joinToString(", ").ifBlank { "Conversation" }

    // While this conversation is open: no notifications for it, and its messages are marked read.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(threadId, lifecycle) {
        val watcher = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> ChatsState.openThread = threadId
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> if (ChatsState.openThread == threadId) ChatsState.openThread = null
                else -> {}
            }
        }
        lifecycle.addObserver(watcher)
        onDispose {
            lifecycle.removeObserver(watcher)
            if (ChatsState.openThread == threadId) ChatsState.openThread = null
        }
    }
    val incoming = messages.count { !it.outgoing }
    LaunchedEffect(threadId, incoming) {
        withContext(Dispatchers.IO) { MessageStore.markRead(context, threadId) }
        MessageNotifications.cancelUnlessBubble(context, threadId)
    }

    var text by rememberSaveable { mutableStateOf(initialText) }
    // Messages you just sent, shown at once until the phone's storage has them.
    val pending = remember { mutableStateListOf<ChatMessage>() }
    fun saved(p: ChatMessage) = messages.any { m -> m.outgoing && m.body == p.body && m.date >= p.date - 10_000 && m.images.size == p.images.size }
    val shown = messages + pending.filterNot { saved(it) }
    LaunchedEffect(messages) { pending.removeAll { saved(it) } }
    val attachments = remember { mutableStateListOf<Uri>() }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    var messageMenu by remember { mutableStateOf<ChatMessage?>(null) }
    var viewing by remember { mutableStateOf<Uri?>(null) }
    fun savePicture(uri: Uri) {
        scope.launch {
            val saved = com.blackout.communications.chats.SavePicture.save(context, uri)
            Toast.makeText(context, if (saved != null) "Saved to Pictures/Communications" else "Couldn't save this picture", Toast.LENGTH_SHORT).show()
        }
    }

    var playing by remember { mutableStateOf<Uri?>(null) }
    fun saveVideo(uri: Uri) {
        scope.launch {
            val saved = com.blackout.communications.chats.SaveVideo.save(context, uri)
            Toast.makeText(context, if (saved != null) "Saved to Movies/Communications" else "Couldn't save this video", Toast.LENGTH_SHORT).show()
        }
    }

    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris ->
        attachments.addAll(uris.take(5 - attachments.size))
    }
    val pickWallpaper = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { app.chatPrefs.setWallpaper(threadId, uri) }
    }

    Box(Modifier.fillMaxSize().background(AppColors.Background)) {
        // This conversation's own wallpaper, dimmed so messages stay readable.
        flags.wallpapers[threadId]?.let { path ->
            val bitmap by produceState<ImageBitmap?>(null, path, File(path).lastModified()) {
                value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
            }
            bitmap?.let {
                Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
            }
        }

        Column(Modifier.fillMaxSize().imePadding()) {
            // Header: back, picture, name, number, call, menu
            Row(Modifier.fillMaxWidth().background(AppColors.Background.copy(alpha = 0.92f)).padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Chats") }
                Avatar(
                    title, contact?.contact?.photoPath, 40.dp,
                    modifier = Modifier.padding(start = if (onBack == null) 12.dp else 0.dp).clip(CircleShape)
                        .clickable(enabled = contact != null) { contact?.let { onOpenProfile(it.contact.id) } },
                )
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (address != null && contact != null) Text(formatNumber(address), fontSize = 13.sp, color = AppColors.SubText)
                }
                if (address != null) IconButton(onClick = { Phone.call(context, address) }) { Icon(Icons.Outlined.Phone, "Call $title") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Wallpaper") }, onClick = { menu = false; pickWallpaper.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                        if (flags.wallpapers.containsKey(threadId)) {
                            DropdownMenuItem(text = { Text("Remove wallpaper") }, onClick = { menu = false; scope.launch { app.chatPrefs.setWallpaper(threadId, null) } })
                        }
                        DropdownMenuItem(text = { Text(if (threadId in flags.pinned) "Unpin" else "Pin to top") }, onClick = { menu = false; app.chatPrefs.togglePin(threadId) })
                        DropdownMenuItem(text = { Text(if (threadId in flags.muted) "Unmute" else "Mute") }, onClick = { menu = false; app.chatPrefs.toggleMute(threadId) })
                        if (address != null) DropdownMenuItem(text = { Text("Block") }, onClick = { menu = false; confirmBlock = true })
                        DropdownMenuItem(text = { Text("Delete conversation", color = AppColors.Missed) }, onClick = { menu = false; confirmDelete = true })
                    }
                }
            }
            HorizontalDivider(color = AppColors.Line)

            // Messages, newest at the bottom
            val items = remember(shown) { withDateBreaks(shown) }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                reverseLayout = true,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items.reversed(), key = { it.key }) { item ->
                    when (item) {
                        is Item.Day -> Text(item.label, color = AppColors.SubText, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        is Item.Msg -> MessageBubble(
                            item.m,
                            onImageClick = { viewing = it },
                            onVideoClick = { playing = it },
                            modifier = Modifier.combinedClickable(
                                onClick = {
                                    if (item.m.status == ChatMessage.Status.Failed && !item.m.isMms && address != null) {
                                        Messenger.retrySms(context, item.m.id, address, item.m.body)
                                    }
                                },
                                onLongClick = { messageMenu = item.m },
                            ),
                        )
                    }
                }
            }

            // Pictures waiting to be sent
            if (attachments.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    attachments.forEach { uri ->
                        Box {
                            UriImage(uri, Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)))
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(2.dp).size(22.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.7f))
                                    .clickable { attachments.remove(uri) },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Filled.Close, "Remove picture", tint = Color.White, modifier = Modifier.size(14.dp)) }
                        }
                    }
                }
            }

            // Message bar
            if (addresses.size > 1) {
                Text(
                    "Group conversations can be read here. Replying to a group isn't supported yet.",
                    color = AppColors.SubText, fontSize = 13.sp, modifier = Modifier.padding(16.dp),
                )
            } else {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(AppColors.Card)
                            .clickable { pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.AttachFile, "Attach pictures", tint = AppColors.SubText) }
                    Box(
                        Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(AppColors.Card).padding(horizontal = 16.dp, vertical = 11.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (text.isEmpty()) Text("Text message", color = AppColors.SubText, fontSize = 15.sp)
                        BasicTextField(
                            value = text, onValueChange = { text = it },
                            textStyle = TextStyle(color = AppColors.Text, fontSize = 15.sp),
                            cursorBrush = SolidColor(AppColors.Accent),
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    val canSend = address != null && (text.isNotBlank() || attachments.isNotEmpty())
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(if (canSend) AppColors.Accent else AppColors.Card)
                            .clickable(enabled = canSend) {
                                val to = address ?: return@clickable
                                pending += ChatMessage(
                                    id = -System.nanoTime(), isMms = attachments.isNotEmpty(), outgoing = true, body = text.trim(),
                                    date = System.currentTimeMillis(), status = ChatMessage.Status.Sending, images = attachments.toList(), from = null,
                                )
                                if (attachments.isNotEmpty()) Messenger.sendPictures(context, to, text.trim(), attachments.toList(), threadId)
                                else Messenger.sendText(context, to, text.trim(), threadId)
                                text = ""
                                attachments.clear()
                            },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color.White, modifier = Modifier.size(20.dp)) }
                }
            }
        }
    }

    messageMenu?.let { m ->
        AlertDialog(
            onDismissRequest = { messageMenu = null },
            title = { Text("Message") },
            text = {
                Column {
                    if (m.body.isNotBlank()) Text("Copy text", fontSize = 16.sp, modifier = Modifier.fillMaxWidth().clickable {
                        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("message", m.body))
                        messageMenu = null
                    }.padding(vertical = 12.dp))
                    if (m.images.isNotEmpty()) Text(if (m.images.size > 1) "Save ${m.images.size} pictures" else "Save picture", fontSize = 16.sp, modifier = Modifier.fillMaxWidth().clickable {
                        m.images.forEach { savePicture(it) }
                        messageMenu = null
                    }.padding(vertical = 12.dp))
                    if (m.videos.isNotEmpty()) Text(if (m.videos.size > 1) "Save ${m.videos.size} videos" else "Save video", fontSize = 16.sp, modifier = Modifier.fillMaxWidth().clickable {
                        m.videos.forEach { saveVideo(it) }
                        messageMenu = null
                    }.padding(vertical = 12.dp))
                    Text("Delete", fontSize = 16.sp, color = AppColors.Missed, modifier = Modifier.fillMaxWidth().clickable {
                        Messenger.scope.launch { MessageStore.deleteMessage(context, m) }
                        messageMenu = null
                    }.padding(vertical = 12.dp))
                }
            },
            confirmButton = { TextButton(onClick = { messageMenu = null }) { Text("Close") } },
        )
    }

    viewing?.let { uri ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { viewing = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                UriImage(uri, Modifier.fillMaxSize(), fit = true)
                val info by produceState("", uri) {
                    value = withContext(Dispatchers.IO) {
                        runCatching {
                            val src = ImageDecoder.createSource(context.contentResolver, uri)
                            var w = 0; var h = 0
                            ImageDecoder.decodeBitmap(src) { d, i, _ -> w = i.size.width; h = i.size.height; d.setTargetSize(1, 1) }
                            val kb = (context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L) / 1024
                            "${w}x${h} px, ${kb} KB"
                        }.getOrDefault("")
                    }
                }
                if (info.isNotEmpty()) Text(info, color = Color.White, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 8.dp, vertical = 4.dp))
                Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewing = null }) { Icon(Icons.Filled.Close, "Close", tint = Color.White) }
                    Box(Modifier.weight(1f))
                    TextButton(onClick = { savePicture(uri) }) {
                        Icon(Icons.Outlined.Download, null, tint = Color.White)
                        Text("Save", color = Color.White, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }

    playing?.let { uri ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { playing = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                androidx.compose.ui.viewinterop.AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        android.widget.VideoView(ctx).apply {
                            val controller = android.widget.MediaController(ctx)
                            controller.setAnchorView(this)
                            setMediaController(controller)
                            setVideoURI(uri)
                            setOnPreparedListener { start() }
                            setOnErrorListener { _, _, _ ->
                                Toast.makeText(ctx, "This video can't be played", Toast.LENGTH_SHORT).show()
                                true
                            }
                        }
                    },
                    onRelease = { it.stopPlayback() },
                )
                Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { playing = null }) { Icon(Icons.Filled.Close, "Close", tint = Color.White) }
                    Box(Modifier.weight(1f))
                    TextButton(onClick = { saveVideo(uri) }) {
                        Icon(Icons.Outlined.Download, null, tint = Color.White)
                        Text("Save", color = Color.White, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }

    if (confirmBlock && address != null) {
        com.blackout.communications.ui.components.BlockConfirmDialog(
            who = title,
            isContact = contact != null,
            onConfirm = {
                confirmBlock = false
                val ok = BlockedNumbers.block(context, address, "chat menu")
                Toast.makeText(context, if (ok) "Blocked. Their calls and texts will be stopped." else "Couldn't block this number", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { confirmBlock = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this conversation?") },
            text = { Text("This deletes every message in it from this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    Messenger.scope.launch { MessageStore.deleteThread(context, threadId); app.chatPrefs.forget(threadId) }
                    onBack?.invoke()
                }) { Text("Delete", color = AppColors.Missed) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private sealed interface Item {
    val key: String
    data class Day(val label: String) : Item { override val key get() = "d-$label" }
    data class Msg(val m: ChatMessage) : Item { override val key get() = (if (m.isMms) "mms-" else "sms-") + m.id }
}

private fun withDateBreaks(messages: List<ChatMessage>): List<Item> {
    val out = ArrayList<Item>()
    var lastDay = ""
    messages.forEach { m ->
        val day = dayLabel(m.date)
        if (day != lastDay) { out += Item.Day(day); lastDay = day }
        out += Item.Msg(m)
    }
    return out
}

private fun dayLabel(time: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = time }
    val now = Calendar.getInstance()
    if (c.get(Calendar.YEAR) == now.get(Calendar.YEAR) && c.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)) return "Today"
    now.add(Calendar.DAY_OF_YEAR, -1)
    if (c.get(Calendar.YEAR) == now.get(Calendar.YEAR) && c.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)) return "Yesterday"
    return SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(Date(time))
}

@Composable
private fun MessageBubble(m: ChatMessage, onImageClick: (Uri) -> Unit, onVideoClick: (Uri) -> Unit, modifier: Modifier) {
    val mine = m.outgoing
    val hasMedia = m.images.isNotEmpty() || m.videos.isNotEmpty()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            modifier
                .widthIn(max = 290.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (mine) 18.dp else 4.dp, bottomEnd = if (mine) 4.dp else 18.dp))
                .background(if (mine) AppColors.MyBubble else AppColors.TheirBubble)
                .padding(horizontal = if (hasMedia) 6.dp else 14.dp, vertical = if (hasMedia) 6.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            m.images.forEach { uri ->
                UriImage(uri, Modifier.widthIn(max = 260.dp).heightIn(max = 320.dp).clip(RoundedCornerShape(12.dp)).clickable { onImageClick(uri) })
            }
            m.videos.forEach { uri ->
                VideoThumb(uri, Modifier.widthIn(max = 260.dp).heightIn(max = 320.dp).clip(RoundedCornerShape(12.dp)).clickable { onVideoClick(uri) })
            }
            if (m.body.isNotBlank()) {
                val textColor = AppColors.onColor(if (mine) AppColors.MyBubble else AppColors.TheirBubble)
                Text(linkify(m.body, textColor), color = textColor, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(horizontal = if (hasMedia) 8.dp else 0.dp))
            }
            val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(m.date))
            val status = when (m.status) {
                ChatMessage.Status.Received -> ""
                ChatMessage.Status.Sending -> " · Sending…"
                ChatMessage.Status.Sent -> " · Sent"
                ChatMessage.Status.Delivered -> " · Delivered"
                ChatMessage.Status.Failed -> " · Not sent, tap to retry"
            }
            Text(
                time + status,
                fontSize = 11.sp,
                color = when {
                    m.status == ChatMessage.Status.Failed -> Color(0xFFFFD1D1)
                    else -> AppColors.onColor(if (mine) AppColors.MyBubble else AppColors.TheirBubble).copy(alpha = 0.75f)
                },
                modifier = Modifier.align(Alignment.End).padding(horizontal = if (hasMedia) 8.dp else 0.dp),
            )
        }
    }
}

/** A video's first frame with a play button on top. Tap to play. */
@Composable
private fun VideoThumb(uri: Uri, modifier: Modifier) {
    val context = LocalContext.current
    val frame by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val r = android.media.MediaMetadataRetriever()
                try {
                    r.setDataSource(context, uri)
                    r.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.asImageBitmap()
                } finally {
                    r.release()
                }
            }.getOrNull()
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val f = frame
        if (f != null) Image(f, "Video", contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        else Box(Modifier.size(160.dp).background(Color(0xFF2E2E33)))
        Box(Modifier.size(52.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PlayArrow, "Play video", tint = Color.White, modifier = Modifier.size(32.dp))
        }
    }
}

/** Shows a picture from a content address, shrunk to screen size. */
@Composable
fun UriImage(uri: Uri, modifier: Modifier, fit: Boolean = false) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
                    val s = minOf(1f, (if (fit) 4096f else 900f) / maxOf(info.size.width, info.size.height))
                    d.setTargetSize((info.size.width * s).toInt().coerceAtLeast(1), (info.size.height * s).toInt().coerceAtLeast(1))
                }.asImageBitmap()
            }.getOrNull()
        }
    }
    val b = bitmap
    if (b != null) Image(b, "Picture", contentScale = if (fit) ContentScale.Fit else ContentScale.Crop, filterQuality = if (fit) androidx.compose.ui.graphics.FilterQuality.High else androidx.compose.ui.graphics.FilterQuality.Low, modifier = modifier)
    else Box(modifier.size(160.dp).background(Color(0xFF2E2E33)))
}

/**
 * Turns web addresses in a message into real links. Tapping one opens your default web browser.
 * (The browser goes online, not Communications.)
 */
private fun linkify(text: String, color: Color): androidx.compose.ui.text.AnnotatedString {
    val matcher = android.util.Patterns.WEB_URL.matcher(text)
    return androidx.compose.ui.text.buildAnnotatedString {
        var last = 0
        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            val raw = text.substring(start, end)
            // Skip things like "3.5" or "e.g" that only look like addresses.
            if (!raw.contains('.') || raw.first().isDigit() && !raw.contains('/')) continue
            append(text.substring(last, start))
            val url = if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) raw else "https://$raw"
            withLink(
                androidx.compose.ui.text.LinkAnnotation.Url(
                    url,
                    androidx.compose.ui.text.TextLinkStyles(
                        androidx.compose.ui.text.SpanStyle(color = color, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
                    ),
                ),
            ) { append(raw) }
            last = end
        }
        append(text.substring(last))
    }
}
