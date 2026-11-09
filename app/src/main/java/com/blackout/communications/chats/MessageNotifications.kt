package com.blackout.communications.chats

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.blackout.communications.CommunicationsApp
import com.blackout.communications.MainActivity
import com.blackout.communications.R
import com.blackout.communications.data.ContactWithNumbers
import com.blackout.communications.data.displayName
import com.blackout.communications.ui.theme.AppColors
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.launch

/**
 * Message notifications: reply from the notification, mark as read, and chat bubbles
 * that float over other apps (Android 11 and newer).
 */
object MessageNotifications {
    // Android keeps a channel's sound and vibration as first created, so changes need a new channel id.
    private const val CHANNEL = "messages_v2"
    private const val OLD_CHANNEL = "messages"
    const val EXTRA_THREAD = "thread_id"
    const val KEY_REPLY = "reply"

    /** New texts: sound, vibration, pop-up banner, and full message on the lock screen. */
    private fun channel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel(OLD_CHANNEL)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "New texts and picture messages"
                setSound(
                    android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                enableLights(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) setAllowBubbles(true)
            },
        )
    }

    /**
     * Alerts you to a new message. Uses the full notification (reply, bubble) when it can be built,
     * and otherwise a plain one built from the message itself, so a new message never arrives silently.
     * Muted and on-screen conversations are skipped on purpose.
     */
    suspend fun showFor(app: CommunicationsApp, threadId: Long?, address: String, body: String) {
        val handled = try {
            threadId != null && showFull(app, threadId)
        } catch (e: Exception) {
            false
        }
        if (!handled) showSimple(app, threadId, address, body)
    }

    /** A plain notification with sound and buzz, used if the full one can't be built. */
    private suspend fun showSimple(app: CommunicationsApp, threadId: Long?, address: String, body: String) {
        runCatching {
            channel(app)
            val title = runCatching { app.contacts.findByNumber(address)?.contact?.displayName }.getOrNull()?.ifBlank { null } ?: address
            val open = if (threadId != null) {
                Intent(app, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_THREAD).putExtra(EXTRA_THREAD, threadId)
            } else {
                Intent(app, MainActivity::class.java)
            }
            val id = threadId?.toInt() ?: address.hashCode()
            val n = NotificationCompat.Builder(app, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_message)
                .setContentTitle(title)
                .setContentText(body.ifBlank { "Photo" })
                .setStyle(NotificationCompat.BigTextStyle().bigText(body.ifBlank { "Photo" }))
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(app, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                .build()
            NotificationManagerCompat.from(app).notify(id, n)
        }
    }

    /** A notification about a problem with an incoming message (for example, a picture message that didn't download). */
    fun alert(context: Context, id: Int, title: String, text: String) {
        runCatching {
            channel(context)
            val open = Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_TAB).putExtra(MainActivity.EXTRA_TAB, "Chats")
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_message)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                .build()
            NotificationManagerCompat.from(context).notify(id, n)
        }
    }

    /** True if the notification was shown or skipped on purpose; false if it couldn't be built. */
    private suspend fun showFull(app: CommunicationsApp, threadId: Long): Boolean {
        if (app.chatPrefs.isMuted(threadId)) return true
        if (ChatsState.openThread == threadId) return true // You're looking at it.
        channel(app)
        val thread = MessageStore.threads(app).firstOrNull { it.id == threadId } ?: return false
        val address = thread.addresses.firstOrNull() ?: return false
        val contact = app.contacts.findByNumber(address)
        val title = contact?.contact?.displayName?.ifBlank { null } ?: address
        val unread = MessageStore.messages(app, threadId).filter { !it.outgoing }.takeLast(maxOf(thread.unread, 1)).takeLast(6)
        if (unread.isEmpty()) return false

        val icon = IconCompat.createWithBitmap(avatarBitmap(title, contact))
        val sender = Person.Builder().setName(title).setKey(address).setIcon(icon).setImportant(contact != null).build()
        val me = Person.Builder().setName("You").build()
        val style = NotificationCompat.MessagingStyle(me)
        unread.forEach { m -> style.addMessage(m.body.ifBlank { "Photo" }, m.date, sender) }

        // A shortcut for the conversation lets Android show it as a bubble.
        val shortcutId = "thread_$threadId"
        val open = Intent(app, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_THREAD).putExtra(EXTRA_THREAD, threadId)
        runCatching {
            ShortcutManagerCompat.pushDynamicShortcut(
                app,
                ShortcutInfoCompat.Builder(app, shortcutId)
                    .setShortLabel(title)
                    .setLongLived(true)
                    .setPerson(sender)
                    .setIcon(icon)
                    .setIntent(open)
                    .setCategories(setOf("com.blackout.communications.category.CONVERSATION"))
                    .build(),
            )
        }

        val contentIntent = PendingIntent.getActivity(app, threadId.toInt(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val reply = NotificationCompat.Action.Builder(
            0, "Reply",
            PendingIntent.getBroadcast(
                app, threadId.toInt(),
                Intent(app, MessageActionReceiver::class.java).setAction(MessageActionReceiver.REPLY).putExtra(EXTRA_THREAD, threadId).putExtra("address", address),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            ),
        ).addRemoteInput(RemoteInput.Builder(KEY_REPLY).setLabel("Reply").build())
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()
        val markRead = NotificationCompat.Action.Builder(
            0, "Mark as read",
            PendingIntent.getBroadcast(
                app, threadId.toInt() + 1_000_000,
                Intent(app, MessageActionReceiver::class.java).setAction(MessageActionReceiver.MARK_READ).putExtra(EXTRA_THREAD, threadId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        ).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ).setShowsUserInterface(false).build()

        val builder = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setStyle(style)
            .setShortcutId(shortcutId)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setNumber(thread.unread)
            .addAction(reply)
            .addAction(markRead)
            .setColor(AppColors.Accent.toArgb())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // show sender and message on the lock screen
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL) // sound and buzz on older Android versions
            .setOnlyAlertOnce(false) // every new text makes a sound, even if the chat already had a notification

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bubbleIntent = PendingIntent.getActivity(
                app, threadId.toInt() + 2_000_000,
                Intent(app, BubbleActivity::class.java).putExtra(EXTRA_THREAD, threadId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            builder.setBubbleMetadata(
                NotificationCompat.BubbleMetadata.Builder(bubbleIntent, icon)
                    .setDesiredHeight(640)
                    .setSuppressNotification(false)
                    .build(),
            )
        }
        try {
            NotificationManagerCompat.from(app).notify(threadId.toInt(), builder.build())
        } catch (e: SecurityException) {
            // Notifications not allowed.
        }
        return true
    }

    fun cancel(context: Context, threadId: Long) = NotificationManagerCompat.from(context).cancel(threadId.toInt())

    /**
     * Clears a chat's notification after you've read it, unless it's showing as a bubble:
     * Android closes a bubble when its notification is cleared.
     */
    fun cancelUnlessBubble(context: Context, threadId: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val active = context.getSystemService(NotificationManager::class.java).activeNotifications
            val bubbled = active.any { it.id == threadId.toInt() && (it.notification.flags and android.app.Notification.FLAG_BUBBLE) != 0 }
            if (bubbled) return
        }
        cancel(context, threadId)
    }

    /** The contact's picture, or a colored circle with their first letter. */
    fun avatarBitmap(name: String, contact: ContactWithNumbers?): Bitmap {
        val size = 192
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val clip = Path().apply { addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW) }
        canvas.clipPath(clip)
        val photo = contact?.contact?.photoPath?.let { BitmapFactory.decodeFile(it) }
        if (photo != null) {
            val scale = maxOf(size.toFloat() / photo.width, size.toFloat() / photo.height)
            canvas.save()
            canvas.translate((size - photo.width * scale) / 2, (size - photo.height * scale) / 2)
            canvas.scale(scale, scale)
            canvas.drawBitmap(photo, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.restore()
        } else {
            val palette = AppColors.AvatarPalette
            canvas.drawColor(palette[Math.floorMod(name.hashCode(), palette.size)].toArgb())
            val letter = name.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "#"
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE; textSize = size * 0.45f; textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(letter, size / 2f, size / 2f - (p.descent() + p.ascent()) / 2, p)
        }
        return out
    }
}

/** Reply and Mark as read buttons on message notifications. */
class MessageActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(MessageNotifications.EXTRA_THREAD, -1)
        if (threadId < 0) return
        when (intent.action) {
            REPLY -> {
                val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(MessageNotifications.KEY_REPLY)?.toString()
                val address = intent.getStringExtra("address")
                if (!text.isNullOrBlank() && address != null) Messenger.sendText(context, address, text, threadId)
                markRead(context, threadId)
            }
            MARK_READ -> markRead(context, threadId)
        }
    }

    private fun markRead(context: Context, threadId: Long) {
        val pending = goAsync()
        Messenger.scope.launch {
            MessageStore.markRead(context, threadId)
            MessageNotifications.cancel(context, threadId)
            pending.finish()
        }
    }

    companion object {
        const val REPLY = "com.blackout.communications.REPLY"
        const val MARK_READ = "com.blackout.communications.MARK_READ"
    }
}

/** Which conversation is on screen right now (app in front), so it doesn't notify. */
object ChatsState {
    @Volatile var openThread: Long? = null
}
