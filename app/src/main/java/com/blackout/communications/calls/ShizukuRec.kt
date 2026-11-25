package com.blackout.communications.calls

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import rikka.shizuku.Shizuku

/**
 * Call recording goes through Shizuku (a separate open-source app, not Google). Shizuku lets
 * Communications run one fixed recorder with the same rights as "adb shell". Everything stays on the
 * phone: Communications talks to Shizuku through Android's local binder, with no internet permission.
 */
object ShizukuRec {
    const val PACKAGE = "moe.shizuku.privileged.api"
    private const val REQ = 4712

    /** Returns null when recording can start, otherwise one short sentence saying what to do. */
    fun notReady(ctx: Context): String? = try {
        when {
            Build.VERSION.SDK_INT < 30 -> "Call recording needs Android 11 or newer."
            !installed(ctx) -> "Call recording needs the Shizuku app. Install it from its GitHub releases page."
            !Shizuku.pingBinder() -> "Shizuku is not running. Open Shizuku and start it, then tap Record."
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> null
            Shizuku.shouldShowRequestPermissionRationale() ->
                "Shizuku blocked Communications. In Shizuku, open Authorized apps and allow Communications."
            else -> {
                Shizuku.requestPermission(REQ)
                "Allow Communications in the Shizuku popup, then tap Record again."
            }
        }
    } catch (e: Throwable) {
        "Shizuku is not ready: ${e.message}"
    }

    private fun installed(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (e: Throwable) {
        false
    }
}
