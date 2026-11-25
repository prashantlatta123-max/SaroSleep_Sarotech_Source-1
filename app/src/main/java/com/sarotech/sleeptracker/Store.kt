package com.sarotech.sleeptracker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class SleepEntry(
    val night: String,
    val start: Long,
    val end: Long,
    val awakeMs: Long = 0L,
    val wakeups: Int = 0,
    val manual: Boolean = false
) {
    val totalSpanMs: Long get() = (end - start).coerceAtLeast(0L)
    val durationMs: Long get() = (totalSpanMs - awakeMs).coerceAtLeast(0L)
}

object Store {
    private const val PREF_NAME = "sarosleep_sarotech_v1"

    private fun sp(c: Context) =
        c.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private fun getInt(c: Context, k: String, d: Int): Int = sp(c).getInt(k, d)
    private fun putInt(c: Context, k: String, v: Int) {
        sp(c).edit().putInt(k, v).apply()
    }

    private fun getLong(c: Context, k: String, d: Long): Long = sp(c).getLong(k, d)
    private fun putLong(c: Context, k: String, v: Long) {
        sp(c).edit().putLong(k, v).apply()
    }

    private fun getBool(c: Context, k: String, d: Boolean): Boolean = sp(c).getBoolean(k, d)
    private fun putBool(c: Context, k: String, v: Boolean) {
        sp(c).edit().putBoolean(k, v).apply()
    }

    // Theme & Monitoring Settings
    fun dark(c: Context): Boolean = getBool(c, "dark", true)
    fun setDark(c: Context, v: Boolean) = putBool(c, "dark", v)

    fun mon(c: Context): Boolean = getBool(c, "mon", true)
    fun setMon(c: Context, v: Boolean) = putBool(c, "mon", v)

    // Sleep Parameters
    fun goalMin(c: Context): Int = getInt(c, "goal", 480) // 8 hours
    fun setGoalMin(c: Context, v: Int) = putInt(c, "goal", v.coerceIn(240, 720))

    fun startHour(c: Context): Int = getInt(c, "startHour", 20) // 8 PM default
    fun setStartHour(c: Context, v: Int) = putInt(c, "startHour", v.coerceIn(17, 23))

    fun minMin(c: Context): Int = getInt(c, "minMin", 90) // 90 min minimum sleep
    fun setMinMin(c: Context, v: Int) = putInt(c, "minMin", v.coerceIn(30, 240))

    fun mergeGapMin(c: Context): Int = getInt(c, "mergeGap", 60) // Merge awake gaps <= 60 min
    fun setMergeGapMin(c: Context, v: Int) = putInt(c, "mergeGap", v.coerceIn(15, 120))

    // Bedtime Reminder
    fun reminderOn(c: Context): Boolean = getBool(c, "remOn", false)
    fun setReminderOn(c: Context, v: Boolean) = putBool(c, "remOn", v)

    fun reminderHour(c: Context): Int = getInt(c, "remH", 22) // 10:30 PM
    fun setReminderHour(c: Context, v: Int) = putInt(c, "remH", v.coerceIn(0, 23))

    fun reminderMin(c: Context): Int = getInt(c, "remM", 30)
    fun setReminderMin(c: Context, v: Int) = putInt(c, "remM", v.coerceIn(0, 59))

    // Active Live Session Tracking State
    fun pendingOff(c: Context): Long = getLong(c, "pendingOff", 0L)
    fun setPendingOff(c: Context, v: Long) {
        sp(c).edit().putLong("pendingOff", v).commit()
    }

    fun screenOn(c: Context): Long = getLong(c, "screenOn", 0L)
    fun setScreenOn(c: Context, v: Long) {
        sp(c).edit().putLong("screenOn", v).commit()
    }

    @Synchronized
    fun entries(c: Context): List<SleepEntry> {
        val raw = sp(c).getString("entries", "[]") ?: "[]"
        val out = ArrayList<SleepEntry>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val n = o.getString("n")
                val s = o.getLong("s")
                val e = o.getLong("e")
                val a = o.optLong("a", 0L)
                val w = o.optInt("w", 0)
                val m = o.optBoolean("m", false)
                if (e > s) {
                    out.add(SleepEntry(n, s, e, a, w, m))
                }
            }
        } catch (_: Exception) {
        }
        return out.sortedByDescending { it.night }
    }

    private fun save(c: Context, list: List<SleepEntry>) {
        val arr = JSONArray()
        for (e in list) {
            arr.put(
                JSONObject()
                    .put("n", e.night)
                    .put("s", e.start)
                    .put("e", e.end)
                    .put("a", e.awakeMs)
                    .put("w", e.wakeups)
                    .put("m", e.manual)
            )
        }
        sp(c).edit().putString("entries", arr.toString()).commit()
    }

    /**
     * Adds or merges a sleep session for the same night.
     * Short mid-night awakenings (<= mergeGapMin) are merged while subtracting awake gap
     * so actual sleep duration stays accurate and wake-up count is tracked.
     */
    @Synchronized
    fun addMerge(c: Context, e: SleepEntry) {
        val list = entries(c).toMutableList()
        val idx = list.indexOfFirst { it.night == e.night }
        if (idx < 0) {
            list.add(e)
        } else if (e.manual) {
            list[idx] = e
        } else {
            val o = list[idx]
            val gap = when {
                e.start >= o.end -> e.start - o.end
                o.start >= e.end -> o.start - e.end
                else -> 0L
            }
            val maxGapMs = mergeGapMin(c) * 60_000L
            list[idx] = if (gap <= maxGapMs) {
                val newStart = minOf(o.start, e.start)
                val newEnd = maxOf(o.end, e.end)
                val addedWake = if (gap >= 60_000L) 1 else 0
                SleepEntry(
                    night = e.night,
                    start = newStart,
                    end = newEnd,
                    awakeMs = o.awakeMs + e.awakeMs + gap,
                    wakeups = o.wakeups + e.wakeups + addedWake,
                    manual = false
                )
            } else if (e.durationMs > o.durationMs) {
                e
            } else {
                o
            }
        }
        save(c, list)
    }

    @Synchronized
    fun delete(c: Context, night: String) {
        save(c, entries(c).filter { it.night != night })
    }

    @Synchronized
    fun clearAll(c: Context) {
        save(c, emptyList())
    }

    /**
     * Calculates a 0-100 Sleep Score based on:
     * - Goal completion (up to 75 pts)
     * - Continuity / low wake-ups (up to 15 pts)
     * - Healthy bedtime timing (up to 10 pts)
     */
    fun sleepScore(e: SleepEntry, goalMin: Int): Int {
        val durMin = (e.durationMs / 60_000L).toFloat()
        val goalPts = ((durMin / goalMin.coerceAtLeast(60)).coerceIn(0f, 1f) * 75f).toInt()
        val continuityPts = (15 - e.wakeups * 5).coerceIn(0, 15)
        val cal = Calendar.getInstance()
        cal.timeInMillis = e.start
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val bedtimePts = when {
            h in 20..23 -> 10
            h == 0 -> 7
            h == 1 -> 4
            else -> 2
        }
        return (goalPts + continuityPts + bedtimePts).coerceIn(0, 100)
    }

    fun scoreTag(score: Int): String = when {
        score >= 88 -> "Excellent"
        score >= 72 -> "Good"
        score >= 55 -> "Fair"
        else -> "Short"
    }

    fun csv(c: Context): String {
        val f = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val goal = goalMin(c)
        val sb = StringBuilder("Night,Bedtime,Wake Up,Sleep (min),Awake Gap (min),Wakeups,Score,Source
")
        for (e in entries(c).sortedBy { it.night }) {
            sb.append(e.night).append(',')
                .append(f.format(Date(e.start))).append(',')
                .append(f.format(Date(e.end))).append(',')
                .append(e.durationMs / 60_000L).append(',')
                .append(e.awakeMs / 60_000L).append(',')
                .append(e.wakeups).append(',')
                .append(sleepScore(e, goal)).append(',')
                .append(if (e.manual) "Manual" else "Auto")
                .append('
')
        }
        return sb.toString()
    }

    fun exportJson(c: Context): String {
        val root = JSONObject()
        root.put("app", "SaroSleep")
        root.put("brand", "Sarotech")
        root.put("developer", "Prashant Latta")
        root.put("exportedAt", System.currentTimeMillis())
        val arr = JSONArray()
        for (e in entries(c)) {
            arr.put(
                JSONObject()
                    .put("n", e.night)
                    .put("s", e.start)
                    .put("e", e.end)
                    .put("a", e.awakeMs)
                    .put("w", e.wakeups)
                    .put("m", e.manual)
            )
        }
        root.put("entries", arr)
        return root.toString(2)
    }

    @Synchronized
    fun importJson(c: Context, raw: String): Int {
        return try {
            val root = JSONObject(raw)
            val arr = root.getJSONArray("entries")
            var count = 0
            val map = entries(c).associateBy { it.night }.toMutableMap()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val n = o.getString("n")
                val s = o.getLong("s")
                val e = o.getLong("e")
                val a = o.optLong("a", 0L)
                val w = o.optInt("w", 0)
                val m = o.optBoolean("m", false)
                if (e > s && n.length == 10) {
                    map[n] = SleepEntry(n, s, e, a, w, m)
                    count++
                }
            }
            if (count > 0) {
                save(c, map.values.sortedByDescending { it.night })
            }
            count
        } catch (_: Exception) {
            -1
        }
    }
}
