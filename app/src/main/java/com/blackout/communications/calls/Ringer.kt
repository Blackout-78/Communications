package com.blackout.communications.calls

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.io.File

/**
 * Rings for incoming calls. Plays the caller's own ringtone (copied from your music) when set,
 * otherwise the phone's default ringtone. Follows the phone's ring, vibrate and silent modes.
 */
class Ringer(private val context: Context) {
    private var player: MediaPlayer? = null
    private var vibrating = false

    fun start(ringtonePath: String?) {
        if (player != null || vibrating) return
        val audio = context.getSystemService(AudioManager::class.java)
        when (audio.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> return
            AudioManager.RINGER_MODE_VIBRATE -> vibrate()
            else -> {
                vibrate()
                play(ringtonePath)
            }
        }
    }

    fun stop() {
        player?.runCatching { stop(); release() }
        player = null
        if (vibrating) vibrator().cancel()
        vibrating = false
    }

    private fun play(ringtonePath: String?) {
        val custom = ringtonePath?.let { File(it) }?.takeIf { it.exists() }?.let { Uri.fromFile(it) }
        val uri = custom ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE) ?: return
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                setDataSource(context, uri)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
        // If the custom song can't be played, fall back to the default ringtone.
        if (player == null && custom != null) play(null)
    }

    private fun vibrate() {
        val pattern = longArrayOf(0, 800, 800)
        vibrator().vibrate(
            VibrationEffect.createWaveform(pattern, 1),
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build(),
        )
        vibrating = true
    }

    private fun vibrator(): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
}
