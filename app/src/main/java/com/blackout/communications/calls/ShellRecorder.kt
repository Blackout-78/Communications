package com.blackout.communications.calls

import android.annotation.SuppressLint
import android.content.AttributionSource
import android.content.Context
import android.content.ContextWrapper
import android.media.MediaRecorder
import android.os.Build
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.Process
import com.blackout.communications.IRecorderService
import kotlin.system.exitProcess

/**
 * Runs inside Shizuku's own shell-privileged process, never inside the Communications app itself.
 * Android lets only the shell user record the call line (the VOICE_CALL source), so this is the only
 * place that can hear both people. It writes into the file the app hands over, and nothing else.
 */
class ShellRecorder : IRecorderService.Stub() {
    private var rec: MediaRecorder? = null
    private var out: ParcelFileDescriptor? = null

    override fun destroy() {
        stop()
        exitProcess(0)
    }

    override fun start(file: ParcelFileDescriptor): String {
        if (rec != null) return "already recording"
        var r: MediaRecorder? = null
        return try {
            r = newRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.VOICE_CALL)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(44_100)
            r.setAudioEncodingBitRate(128_000)
            r.setOutputFile(file.fileDescriptor)
            r.prepare()
            r.start()
            rec = r
            out = file
            ""
        } catch (e: Throwable) {
            runCatching { r?.release() }
            runCatching { file.close() }
            "${e.javaClass.simpleName}: ${e.message}"
        }
    }

    override fun stop(): String {
        val r = rec ?: return ""
        rec = null
        val err = try { r.stop(); "" } catch (e: Throwable) { "${e.javaClass.simpleName}: ${e.message}" }
        runCatching { r.release() }
        runCatching { out?.close() }
        out = null
        return err
    }

    /** A recorder that Android sees as the shell app (package com.android.shell), which holds the call-audio right. */
    @SuppressLint("NewApi", "PrivateApi")
    private fun newRecorder(): MediaRecorder {
        if (Build.VERSION.SDK_INT < 31) return MediaRecorder()
        val at = Class.forName("android.app.ActivityThread")
        var t = at.getMethod("currentActivityThread").invoke(null)
        if (t == null) {
            if (Looper.getMainLooper() == null) Looper.prepareMainLooper()
            t = at.getMethod("systemMain").invoke(null)
        }
        val system = at.getMethod("getSystemContext").invoke(t) as Context
        val shell = "com.android.shell"
        val ctx = object : ContextWrapper(system) {
            override fun getPackageName(): String = shell
            override fun getAttributionSource(): AttributionSource =
                AttributionSource.Builder(Process.myUid()).setPackageName(shell).build()
        }
        return MediaRecorder(ctx)
    }
}
