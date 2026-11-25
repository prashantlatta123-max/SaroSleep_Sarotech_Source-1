package com.sarotech.sleeptracker

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (!Store.reminderOn(ctx)) return

        try {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    "sarosleep_reminder",
                    "Bedtime Reminder",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )

            val pi = PendingIntent.getActivity(
                ctx,
                201,
                Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val n = Notification.Builder(ctx, "sarosleep_reminder")
                .setSmallIcon(R.drawable.ic_moon)
                .setContentTitle("Time to Sleep · SaroSleep")
                .setContentText("Lock your phone to start recording tonight's sleep automatically.")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()

            nm.notify(202, n)
        } catch (_: Exception) {
        }

        // Schedule next day's reminder
        ReminderScheduler.schedule(ctx)
    }
}

object ReminderScheduler {
    private const val REQ_CODE = 2002

    fun schedule(c: Context) {
        if (!Store.reminderOn(c)) {
            cancel(c)
            return
        }
        val am = c.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, Store.reminderHour(c))
            set(Calendar.MINUTE, Store.reminderMin(c))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis() + 5_000L) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        val pi = PendingIntent.getBroadcast(
            c,
            REQ_CODE,
            Intent(c, ReminderReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        try {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        } catch (_: Exception) {
        }
    }

    fun cancel(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = PendingIntent.getBroadcast(
            c,
            REQ_CODE,
            Intent(c, ReminderReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        if (pi != null) {
            am.cancel(pi)
        }
    }
}
