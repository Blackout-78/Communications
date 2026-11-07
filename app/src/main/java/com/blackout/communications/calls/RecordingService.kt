package com.blackout.communications.calls

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.blackout.communications.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records a call through the microphone. Android only lets built-in phone apps record
 * the other person directly, so they are clear only when the call is on speakerphone.
 * Files go to the Recordings folder, named with name, number, date and time.
 */
class RecordingService : Service() {
    private var recorder: MediaRecorder? = null
    private var fd: ParcelFileDescriptor? = null
    private var uri: Uri? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(intent.getStringExtra(EXTRA_NAME).orEmpty(), intent.getStringExtra(EXTRA_NUMBER).orEmpty())
            ACTION_STOP -> {
                finish()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun start(name: String, number: String) {
        if (recorder != null) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Call recording", NotificationManager.IMPORTANCE_LOW))
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle("Recording call")
            .setContentText(name.ifBlank { number })
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, n)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't start recording", Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }

        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
        val safeName = (name.ifBlank { "Unknown" }).replace(Regex("[^\\p{L}\\p{N} ._-]"), "").trim()
        val safeNumber = number.filter { it.isDigit() || it == '+' }
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "${safeName}_${safeNumber}_$stamp.m4a")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.RELATIVE_PATH, folder())
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val prefs = getSharedPreferences("recording", MODE_PRIVATE)
        // -1 = not tested yet, 1 = the call line records on this phone, 0 = microphone only
        val lineWorks = prefs.getInt("voiceCallWorks", -1)
        val first = if (lineWorks == 0) MediaRecorder.AudioSource.MIC else MediaRecorder.AudioSource.VOICE_CALL
        if (!begin(values, first)) {
            if (first == MediaRecorder.AudioSource.VOICE_CALL) prefs.edit().putInt("voiceCallWorks", 0).apply()
            if (first == MediaRecorder.AudioSource.MIC || !begin(values, MediaRecorder.AudioSource.MIC)) {
                Toast.makeText(this, "Couldn't start recording", Toast.LENGTH_SHORT).show()
                cleanUp(keep = false)
                stopSelf()
                return
            }
        }
        CallManager.recording.value = number
        CallManager.recordingBothSides.value = source == MediaRecorder.AudioSource.VOICE_CALL
        // The call line can "work" but record only silence on phones that block it. Check after 2.5 s.
        if (source == MediaRecorder.AudioSource.VOICE_CALL && lineWorks != 1) {
            handler.postDelayed({ checkSound(values, 0) }, 800)
        }
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var source = MediaRecorder.AudioSource.MIC

    /** Starts recording from [audioSource] into a new file. Returns false if this phone refuses it. */
    private fun begin(values: ContentValues, audioSource: Int): Boolean = try {
        val item = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)!!
        uri = item
        fd = contentResolver.openFileDescriptor(item, "w")!!
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
        recorder = r
        r.setAudioSource(audioSource)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioSamplingRate(44_100)
        r.setAudioEncodingBitRate(128_000)
        r.setOutputFile(fd!!.fileDescriptor)
        r.prepare()
        r.start()
        source = audioSource
        true
    } catch (e: Exception) {
        cleanUp(keep = false)
        false
    }

    /** Samples the volume a few times; if the call line gave only silence, switch to the microphone. */
    private fun checkSound(values: ContentValues, tries: Int) {
        val r = recorder ?: return
        val level = runCatching { r.maxAmplitude }.getOrDefault(0)
        val prefs = getSharedPreferences("recording", MODE_PRIVATE)
        when {
            level > 0 -> prefs.edit().putInt("voiceCallWorks", 1).apply()
            tries < 3 -> handler.postDelayed({ checkSound(values, tries + 1) }, 600)
            else -> {
                prefs.edit().putInt("voiceCallWorks", 0).apply()
                runCatching { r.stop() }
                cleanUp(keep = false)
                val number = CallManager.recording.value
                if (begin(values, MediaRecorder.AudioSource.MIC)) {
                    CallManager.recording.value = number
                    CallManager.recordingBothSides.value = false
                } else {
                    stopSelf()
                }
            }
        }
    }

    private fun finish() {
        val r = recorder ?: return
        val ok = runCatching { r.stop() }.isSuccess
        cleanUp(keep = ok)
    }

    private fun cleanUp(keep: Boolean) {
        if (!keep) handler.removeCallbacksAndMessages(null)
        recorder?.runCatching { release() }
        recorder = null
        fd?.runCatching { close() }
        fd = null
        uri?.let { item ->
            if (keep) {
                contentResolver.update(item, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            } else {
                runCatching { contentResolver.delete(item, null, null) }
            }
        }
        uri = null
        CallManager.recording.value = null
        CallManager.recordingBothSides.value = false
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        finish()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "call_recording"
        private const val NOTIFICATION_ID = 1002
        private const val ACTION_START = "start"
        private const val ACTION_STOP = "stop"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_NUMBER = "number"

        /** Where recordings are saved: Recordings/Communications (Android 12+) or Music/Communications Recordings. */
        fun folder(): String =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "${Environment.DIRECTORY_RECORDINGS}/Communications"
            else "${Environment.DIRECTORY_MUSIC}/Communications Recordings"

        fun start(context: Context, name: String, number: String) {
            try {
                context.startForegroundService(
                    Intent(context, RecordingService::class.java).setAction(ACTION_START)
                        .putExtra(EXTRA_NAME, name).putExtra(EXTRA_NUMBER, number),
                )
            } catch (e: Exception) {
                Toast.makeText(context, "Couldn't start recording", Toast.LENGTH_SHORT).show()
            }
        }

        fun stop(context: Context) {
            if (CallManager.recording.value == null) return
            runCatching { context.startService(Intent(context, RecordingService::class.java).setAction(ACTION_STOP)) }
        }
    }
}
