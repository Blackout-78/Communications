package com.blackout.communications.chats

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.blackout.communications.MainActivity
import com.blackout.communications.ui.chats.ConversationScreen
import com.blackout.communications.ui.theme.AppColors
import com.blackout.communications.ui.theme.CommunicationsTheme

/** The small chat window that opens from a chat bubble, over whatever app you're using. */
class BubbleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val threadId = intent.getLongExtra(MessageNotifications.EXTRA_THREAD, -1)
        if (threadId < 0) { finish(); return }
        setContent {
            CommunicationsTheme {
                Surface(color = AppColors.Background, modifier = Modifier.fillMaxSize()) {
                    ConversationScreen(
                        threadId = threadId,
                        onBack = null,
                        onOpenProfile = { id ->
                            startActivity(
                                Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_CONTACT)
                                    .putExtra(MainActivity.EXTRA_CONTACT, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                    )
                }
            }
        }
    }
}
