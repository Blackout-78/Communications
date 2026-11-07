package com.blackout.communications.calls

import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.telecom.CallAudioState
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blackout.communications.CommunicationsApp
import com.blackout.communications.MainActivity
import com.blackout.communications.ui.calls.InCallScreen
import com.blackout.communications.ui.calls.IncomingCallScreen
import com.blackout.communications.ui.theme.CommunicationsTheme

/** Full-screen call screen: the incoming call swipe, then the in-call buttons. Shows over the lock screen. */
class CallActivity : ComponentActivity() {
    private var proximityLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()
        handle(intent)

        val app = application as CommunicationsApp
        setContent {
            CommunicationsTheme {
                val calls by CallManager.snapshot.collectAsStateWithLifecycle()
                val audio by CallManager.audio.collectAsStateWithLifecycle()
                val prefs by app.callSettings.state.collectAsStateWithLifecycle()
                val recording by CallManager.recording.collectAsStateWithLifecycle()
                val bothSides by CallManager.recordingBothSides.collectAsStateWithLifecycle()
                // "Turn on speaker when recording", unless the phone records the call line directly.
                LaunchedEffect(recording != null, bothSides) {
                    if (recording != null && !bothSides && prefs.speakerWhenRecording) CallManager.setRoute(CallAudioState.ROUTE_SPEAKER)
                }

                LaunchedEffect(calls.isEmpty()) { if (calls.isEmpty()) finish() }

                val ringing = calls.firstOrNull { it.isRinging }
                val main = calls.firstOrNull { it.isActive } ?: calls.firstOrNull { it.isDialing } ?: calls.firstOrNull()

                // Turn the screen off when the phone is held to your ear (not on speaker).
                val onSpeaker = audio?.route == CallAudioState.ROUTE_SPEAKER
                LaunchedEffect(main?.isActive, onSpeaker, ringing == null) {
                    setProximity(ringing == null && main != null && !onSpeaker)
                }

                // Automatic recording, unless the caller is on the exempt list.
                LaunchedEffect(main?.isActive, main?.number) {
                    val m = main ?: return@LaunchedEffect
                    if (m.isActive && prefs.autoRecord && recording == null &&
                        (m.contactId == null || m.contactId !in prefs.recordExempt)
                    ) {
                        RecordingService.start(this@CallActivity, m.name, m.number)
                    }
                }

                when {
                    ringing != null -> IncomingCallScreen(
                        info = ringing,
                        prefs = prefs,
                        hasOtherCall = calls.size > 1,
                    )
                    main != null -> InCallScreen(
                        calls = calls,
                        main = main,
                        audio = audio,
                        recording = recording != null,
                        recordingNote = when {
                            recording == null || bothSides -> null
                            audio?.route == CallAudioState.ROUTE_SPEAKER -> null
                            audio?.route == CallAudioState.ROUTE_BLUETOOTH -> "Bluetooth: the recording may be silent. Tap Speaker to record both voices."
                            else -> "Only your voice is recorded. Tap Speaker to record both voices."
                        },
                        onToggleRecord = {
                            if (recording != null) RecordingService.stop(this)
                            else RecordingService.start(this, main.name, main.number)
                        },
                        onAddCall = {
                            startActivity(
                                Intent(this, MainActivity::class.java)
                                    .setAction(MainActivity.ACTION_ADD_CALL)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                            )
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.action == ACTION_ANSWER) CallManager.ringing()?.let { CallManager.answer(it.call) }
    }

    @Suppress("WakelockTimeout")
    private fun setProximity(on: Boolean) {
        val pm = getSystemService(PowerManager::class.java)
        if (on) {
            if (proximityLock == null && pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                proximityLock = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "communications:proximity").apply { acquire() }
            }
        } else {
            proximityLock?.let { if (it.isHeld) it.release() }
            proximityLock = null
        }
    }

    override fun onDestroy() {
        setProximity(false)
        super.onDestroy()
    }

    companion object {
        const val ACTION_ANSWER = "com.blackout.communications.ANSWER"
    }
}
