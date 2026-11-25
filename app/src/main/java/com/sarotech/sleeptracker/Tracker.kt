package com.sarotech.sleeptracker

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Core Night Sleep Tracking Engine:
 * 1. After configured start hour (default 8:00 PM), when phone locks (SCREEN_OFF),
 *    records pendingOff timestamp.
 * 2. Even if phone was locked slightly before 8:00 PM (e.g. 7:30 PM) and stays untouched
 *    through the night until morning, automatically clamps start to 8:00 PM so no sleep is lost.
 * 3. Short notification screen flashes (< 45s without unlock) are ignored.
 * 4. Unlocking the phone (USER_PRESENT) confirms human activity and closes the sleep session.
 * 5. Alarm / prolonged lockscreen viewing (>= 45s without unlock) also ends sleep at screen-on time.
 */
object Tracker {
    private const val WAKE_SCREEN_MS = 45_000L
    private const val MAX_SLEEP_MS = 16L * 3_600_000L

    fun onScreenOff(c: Context) {
        if (!Store.mon(c)) return
        val now = System.currentTimeMillis()
        val off = Store.pendingOff(c)
        val on = Store.screenOn(c)

        if (off > 0L && on > off && now - on >= WAKE_SCREEN_MS) {
            // Prolonged screen-on without unlock (e.g. morning alarm)
            finish(c, off, on)
            Store.setPendingOff(c, now)
        } else if (off == 0L) {
            Store.setPendingOff(c, now)
        }
        Store.setScreenOn(c, 0L)
        notifyServiceUpdate(c)
    }

    fun onScreenOn(c: Context) {
        if (!Store.mon(c)) return
        Store.setScreenOn(c, System.currentTimeMillis())
    }

    fun onUnlock(c: Context) {
        val off = Store.pendingOff(c)
        if (off > 0L) {
            finish(c, off, System.currentTimeMillis())
        }
        Store.setPendingOff(c, 0L)
        Store.setScreenOn(c, 0L)
        notifyServiceUpdate(c)
    }

    /**
     * Safety fallback for phones without lock screen security (no PIN/pattern).
     * When user opens the app in the morning, close any pending sleep session.
     */
    fun onAppOpened(c: Context) {
        val km = c.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val locked = km?.isKeyguardLocked ?: false
        if (!locked && Store.pendingOff(c) > 0L) {
            onUnlock(c)
        }
    }

    private fun finish(c: Context, rawStart: Long, end: Long) {
        if (end <= rawStart) return
        val startHour = Store.startHour(c)
        val startCal = Calendar.getInstance().apply { timeInMillis = rawStart }
        val h = startCal.get(Calendar.HOUR_OF_DAY)

        val effectiveStart: Long = when {
            // Locked inside the night window (e.g., 8:00 PM to 5:59 AM)
            h >= startHour || h < 6 -> rawStart
            // Locked slightly before startHour (up to 4 hours before) and stayed untouched past startHour
            h >= (startHour - 4).coerceAtLeast(12) -> {
                val windowCal = Calendar.getInstance().apply {
                    timeInMillis = rawStart
                    set(Calendar.HOUR_OF_DAY, startHour)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val windowStart = windowCal.timeInMillis
                if (end > windowStart) windowStart else return
            }
            else -> return
        }

        val dur = end - effectiveStart
        val minMs = Store.minMin(c) * 60_000L
        if (dur < minMs || dur > MAX_SLEEP_MS) return

        Store.addMerge(
            c,
            SleepEntry(
                night = nightKey(effectiveStart),
                start = effectiveStart,
                end = end,
                awakeMs = 0L,
                wakeups = 0,
                manual = false
            )
        )
    }

    fun nightKey(t: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = t
        if (cal.get(Calendar.HOUR_OF_DAY) < 12) {
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    fun isInNightWindow(c: Context): Boolean {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return h >= Store.startHour(c) || h < 6
    }

    private fun notifyServiceUpdate(c: Context) {
        if (!Store.mon(c)) return
        try {
            val i = Intent(c, SleepService::class.java)
            i.action = SleepService.ACTION_REFRESH
            c.startForegroundService(i)
        } catch (_: Exception) {
        }
    }
}
