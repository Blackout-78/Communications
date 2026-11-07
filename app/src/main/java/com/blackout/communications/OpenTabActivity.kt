package com.blackout.communications

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Tiny entry points for the Contacts, Messages and Call Log home-screen icons.
 * Each has its own icon, so launchers never swap it for the main app icon.
 * They open Communications on the right tab, then close.
 */
open class OpenTabActivity(private val tab: String) : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_TAB)
                .putExtra(MainActivity.EXTRA_TAB, tab)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        finish()
    }
}

class OpenContactsActivity : OpenTabActivity("Contacts")
class OpenMessagesActivity : OpenTabActivity("Chats")
class OpenCallLogActivity : OpenTabActivity("Calls")
