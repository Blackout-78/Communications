package com.blackout.communications.backup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.blackout.communications.MainActivity
import com.blackout.communications.R

/**
 * Reminds you to back up every 2 weeks, with short reasons why.
 * It keeps reminding even if you tap "Not now". Backing up resets the 2 weeks.
 */
object BackupReminder {
    private const val JOB_ID = 4242
    private const val CHANNEL = "backup_reminder"
    const val NOTIFICATION_ID = 4243
    const val INTERVAL_MS = 14L * 24 * 60 * 60 * 1000

    private fun prefs(context: Context) = context.getSharedPreferences("backup", Context.MODE_PRIVATE)

    fun schedule(context: Context) {
        val p = prefs(context)
        if (!p.contains("since")) p.edit().putLong("since", System.currentTimeMillis()).apply()
        val js = context.getSystemService(JobScheduler::class.java)
        if (js.getPendingJob(JOB_ID) != null) return
        js.schedule(
            JobInfo.Builder(JOB_ID, ComponentName(context, BackupReminderJob::class.java))
                .setPeriodic(24L * 60 * 60 * 1000)
                .setPersisted(true)
                .build(),
        )
    }

    fun lastBackup(context: Context): Long = prefs(context).getLong("lastBackup", 0)

    fun markBackedUp(context: Context) {
        prefs(context).edit().putLong("lastBackup", System.currentTimeMillis()).apply()
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /** Shows the reminder if 2 weeks have passed since the last backup and the last reminder. */
    fun checkAndNotify(context: Context) {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        val since = maxOf(p.getLong("lastBackup", 0), p.getLong("lastReminder", 0), p.getLong("since", now))
        if (now - since < INTERVAL_MS) return
        p.edit().putLong("lastReminder", now).apply()

        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Backup reminder", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_BACKUP).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val reasons = listOf(
            "• Your contacts are only stored on this phone",
            "• A lost, broken or reset phone means they're gone",
            "• A backup is the only way to move to a new phone",
            "• It takes under a minute",
        ).joinToString("\n")
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setContentTitle("Time to back up Communications")
            .setContentText("Your contacts are only stored on this phone.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(reasons))
            .setContentIntent(open)
            .addAction(0, "Back up now", open)
            .addAction(
                0, "Not now",
                PendingIntent.getBroadcast(
                    context, 1, Intent(context, BackupDismissReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n) }
    }
}

/** "Not now" only hides this reminder. The next one still comes in 2 weeks. */
class BackupDismissReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationManagerCompat.from(context).cancel(BackupReminder.NOTIFICATION_ID)
    }
}

class BackupReminderJob : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        BackupReminder.checkAndNotify(this)
        return false
    }

    override fun onStopJob(params: JobParameters?) = false
}
