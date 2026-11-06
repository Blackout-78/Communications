package com.blackout.communications

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.blackout.communications.ui.AppRoot
import com.blackout.communications.ui.theme.CommunicationsTheme

class MainActivity : ComponentActivity() {
    /** A number to show on the dialpad, from a tel: link or "Add call". */
    private val dialRequest = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent {
            CommunicationsTheme {
                AppRoot(dialRequest = dialRequest.value, onDialHandled = { dialRequest.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_DIAL, Intent.ACTION_VIEW -> {
                val data = intent.data
                if (data == null || data.scheme == "tel") dialRequest.value = data?.schemeSpecificPart.orEmpty()
            }
            ACTION_ADD_CALL -> dialRequest.value = ""
        }
    }

    companion object {
        const val ACTION_ADD_CALL = "com.blackout.communications.ADD_CALL"
    }
}
