package com.blackout.communications.calls

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.blackout.communications.IRecorderService
import com.blackout.communications.R
import rikka.shizuku.Shizuku
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records a call, both voices, through Shizuku. Android lets only the shell user record the call line,
 * so a small recorder runs in Shizuku's shell process ([ShellRecorder]) and writes into the file made here.
 * Files go to the Recordings folder, named with name, number, date and time.
 */
class RecordingService : Service() {
    private var fd: ParcelFileDescriptor? = null
    private var uri: Uri? = null
    private var recorder: IRecorderService? = null
    private var args: Shizuku.UserServiceArgs? = null
    private var conn: ServiceConnection? = null
    private val handler = Handler(Looper.getMainLooper())

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
        if (conn != null) return
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
            fail("Couldn't start recording")
            return
        }

        val notReady = ShizukuRec.notReady(this)
        if (notReady != null) {
            fail(notReady)
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
        try {
            val item = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)!!
            uri = item
            fd = contentResolver.openFileDescriptor(item, "w")!!
        } catch (e: Exception) {
            fail("Couldn't create the recording file")
            return
        }

        val a = Shizuku.UserServiceArgs(ComponentName(packageName, ShellRecorder::class.java.name))
            .processNameSuffix("recorder").debuggable(false).daemon(false).version(1)
        val c = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val svc = IRecorderService.Stub.asInterface(binder)
                recorder = svc
                val file = fd ?: return
                Thread {
                    val err = try { svc.start(file) } catch (t: Throwable) { "${t.javaClass.simpleName}: ${t.message}" }
                    handler.post {
                        if (err.isEmpty()) {
                            CallManager.recording.value = number
                            CallManager.recordingBothSides.value = true
                        } else {
                            fail("Couldn't record the call: $err")
                        }
                    }
                }.start()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                // Shizuku stopped mid-call: the file was never finished, so it can't be played.
                if (recorder != null) handler.post { fail("Shizuku stopped, so the recording was lost") }
            }
        }
        args = a
        conn = c
        try {
            Shizuku.bindUserService(a, c)
        } catch (e: Throwable) {
            fail("Couldn't start the recorder: ${e.message}")
        }
    }

    /** Shows why recording did not work, throws away the unfinished file and stops. */
    private fun fail(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        cleanUp(keep = false)
        stopSelf()
    }

    private fun finish() {
        val svc = recorder
        val ok = svc != null && try { svc.stop().isEmpty() } catch (e: Throwable) { false }
        cleanUp(keep = ok)
    }

    private fun cleanUp(keep: Boolean) {
        recorder = null
        val a = args
        val c = conn
        if (a != null && c != null) runCatching { Shizuku.unbindUserService(a, c, true) }
        args = null
        conn = null
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
