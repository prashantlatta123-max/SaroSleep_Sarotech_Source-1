package com.sarotech.sleeptracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val a = i.action
        if (a == Intent.ACTION_BOOT_COMPLETED || a == Intent.ACTION_MY_PACKAGE_REPLACED) {
            if (Store.mon(c)) {
                try {
                    c.startForegroundService(Intent(c, SleepService::class.java))
                } catch (_: Exception) {
                }
            }
            if (Store.reminderOn(c)) {
                ReminderScheduler.schedule(c)
            }
        }
    }
}
