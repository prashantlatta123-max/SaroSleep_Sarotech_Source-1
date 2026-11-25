package com.sarotech.sleeptracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager

class SleepService : Service() {

    companion object {
        const val CHANNEL_ID = "sarosleep_tracking_channel"
        const val NOTIF_ID = 101
        const val ACTION_REFRESH = "com.sarotech.sleeptracker.REFRESH_NOTIF"
    }

    private var registered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Tracker.onScreenOff(ctx)
                    updateNotification()
                }
                Intent.ACTION_SCREEN_ON -> Tracker.onScreenOn(ctx)
                Intent.ACTION_USER_PRESENT -> {
                    Tracker.onUnlock(ctx)
                    updateNotification()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            startFg()
        } catch (_: Exception) {
            stopSelf()
            return
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
        registered = true

        // If device restarted while screen is already off, begin pending lock timer
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (pm != null && !pm.isInteractive && Store.pendingOff(this) == 0L) {
            Store.setPendingOff(this, System.currentTimeMillis())
            updateNotification()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Store.mon(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        updateNotification()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (registered) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (_: Exception) {
            }
            registered = false
        }
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Night Sleep Tracking",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Monitors phone lock and inactivity after evening start time"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)

        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pendingOff = Store.pendingOff(this)
        val statusText = if (pendingOff > 0L && Tracker.isInNightWindow(this)) {
            "Phone locked · Recording sleep since ${fmtClock(pendingOff)}"
        } else {
            "Active · Tracks locked inactivity after ${fmtHour(Store.startHour(this))}"
        }

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_moon)
            .setContentTitle("SaroSleep · Sarotech")
            .setContentText(statusText)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun startFg() {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun updateNotification() {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIF_ID, buildNotification())
        } catch (_: Exception) {
        }
    }
}
