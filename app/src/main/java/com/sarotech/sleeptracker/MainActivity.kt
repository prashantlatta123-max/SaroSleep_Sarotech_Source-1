package com.sarotech.sleeptracker

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.min

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

class MainActivity : Activity() {

    companion object {
        private const val REQ_NOTIF = 100
        private const val REQ_EXPORT_CSV = 101
        private const val REQ_BACKUP_JSON = 102
        private const val REQ_RESTORE_JSON = 103
    }

    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private val dens by lazy { resources.displayMetrics.density }
    private fun dp(v: Int): Int = (v * dens).toInt()

    // ---------------------------------------------------------------- lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        T.dark = Store.dark(this)
        applySystemBars()
        buildRoot()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
        }
    }

    override fun onResume() {
        super.onResume()
        Tracker.onAppOpened(this)
        ensureService()
        if (Store.reminderOn(this)) {
            ReminderScheduler.schedule(this)
        }
        refresh()
    }

    @Suppress("DEPRECATION")
    private fun applySystemBars() {
        window.statusBarColor = T.bg1
        window.navigationBarColor = T.bg1
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                if (T.dark) 0 else (WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS),
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        } else if (!T.dark) {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }

    private fun ensureService() {
        if (Store.mon(this)) {
            try {
                startForegroundService(Intent(this, SleepService::class.java))
            } catch (_: Exception) {
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        val uri: Uri = data.data ?: return

        when (requestCode) {
            REQ_EXPORT_CSV -> {
                try {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(Store.csv(this).toByteArray(Charsets.UTF_8))
                    }
                    toast("CSV file saved successfully")
                } catch (_: Exception) {
                    toast("Could not save CSV file")
                }
            }

            REQ_BACKUP_JSON -> {
                try {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(Store.exportJson(this).toByteArray(Charsets.UTF_8))
                    }
                    toast("Backup saved successfully")
                } catch (_: Exception) {
                    toast("Could not save backup")
                }
            }

            REQ_RESTORE_JSON -> {
                try {
                    val raw = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                    val restored = Store.importJson(this, raw)
                    if (restored >= 0) {
                        toast("Restored $restored sleep record(s)")
                        refresh()
                    } else {
                        toast("Invalid backup file")
                    }
                } catch (_: Exception) {
                    toast("Could not read backup file")
                }
            }
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    // ---------------------------------------------------------------- root / glass backdrop
    private fun buildRoot() {
        val root = FrameLayout(this)
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(T.bg1, T.bg2, T.bg3)
        )

        // Ambient glass orbs behind frosted cards
        val orbContainer = FrameLayout(this)

        val b1 = blobView(320)
        val lp1 = FrameLayout.LayoutParams(dp(320), dp(320), Gravity.TOP or Gravity.END)
        lp1.topMargin = -dp(95)
        lp1.marginEnd = -dp(95)
        orbContainer.addView(b1, lp1)

        val b2 = blobView(360)
        val lp2 = FrameLayout.LayoutParams(dp(360), dp(360), Gravity.BOTTOM or Gravity.START)
        lp2.bottomMargin = -dp(130)
        lp2.marginStart = -dp(120)
        orbContainer.addView(b2, lp2)

        val b3 = blobView(220)
        val lp3 = FrameLayout.LayoutParams(dp(220), dp(220), Gravity.CENTER_VERTICAL or Gravity.END)
        lp3.marginEnd = -dp(90)
        orbContainer.addView(b3, lp3)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            orbContainer.setRenderEffect(
                RenderEffect.createBlurEffect(50f, 50f, Shader.TileMode.CLAMP)
            )
        }
        root.addView(orbContainer, FrameLayout.LayoutParams(MATCH, MATCH))

        scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        scroll.overScrollMode = View.OVER_SCROLL_ALWAYS

        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(18), dp(24), dp(18), dp(42))
        scroll.addView(content, ViewGroup.LayoutParams(MATCH, WRAP))
        root.addView(scroll, FrameLayout.LayoutParams(MATCH, MATCH))

        setContentView(root)
    }

    private fun blobView(sizeDp: Int): View {
        val v = View(this)
        val g = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = dp(sizeDp) / 2f
            colors = intArrayOf(T.blobA, T.blobB)
        }
        v.background = g
        return v
    }

    // ---------------------------------------------------------------- glassmorphism UI helpers
    private fun tv(text: String, sp: Float, color: Int = T.text, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = sp
        t.setTextColor(color)
        if (bold) t.typeface = Typeface.DEFAULT_BOLD
        return t
    }

    private fun label(text: String): TextView {
        val t = tv(text, 11f, T.muted, true)
        t.letterSpacing = 0.16f
        return t
    }

    private fun glassBg(r: Int, strong: Boolean = false): GradientDrawable {
        val colors = if (strong) intArrayOf(T.glassStrongA, T.glassStrongB) else intArrayOf(T.glassA, T.glassB)
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
            cornerRadius = r.toFloat()
            setStroke(dp(1), T.border)
        }
    }

    private fun rounded(r: Int, color: Int): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = r.toFloat()
            setColor(color)
        }
    }

    private fun card(strong: Boolean = false): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.background = glassBg(dp(26), strong)
        c.setPadding(dp(18), dp(18), dp(18), dp(18))
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.bottomMargin = dp(14)
        c.layoutParams = lp
        return c
    }

    private fun pill(text: String, padH: Int = 16, onClick: () -> Unit): TextView {
        val t = tv(text, 13.5f, T.text, true)
        t.gravity = Gravity.CENTER
        t.background = glassBg(dp(20), strong = true)
        t.setPadding(dp(padH), dp(10), dp(padH), dp(10))
        t.setOnClickListener { onClick() }
        return t
    }

    private fun badge(text: String): TextView {
        val t = tv(text, 11.5f, T.text, true)
        t.gravity = Gravity.CENTER
        t.background = glassBg(dp(14), strong = true)
        t.setPadding(dp(10), dp(5), dp(10), dp(5))
        return t
    }

    private fun solidPill(text: String, onClick: () -> Unit): TextView {
        val t = tv(text, 14f, T.solidText, true)
        t.gravity = Gravity.CENTER
        t.background = rounded(dp(20), T.solidBg)
        t.setPadding(dp(18), dp(12), dp(18), dp(12))
        t.setOnClickListener { onClick() }
        return t
    }

    private fun fullWidth(v: View, topDp: Int = 10): View {
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.topMargin = dp(topDp)
        v.layoutParams = lp
        return v
    }

    // ---------------------------------------------------------------- screen sections
    private fun refresh() {
        val sy = scroll.scrollY
        content.removeAllViews()
        val entries = Store.entries(this)
        val goal = Store.goalMin(this)

        addHeader()
        addStatus()
        addHero(entries, goal)
        addStats(entries, goal)
        addChart(entries, goal)
        addHistory(entries, goal)
        addSettings()
        addTools()
        addFooter()

        scroll.post { scroll.scrollTo(0, sy) }
    }

    private fun addHeader() {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val rlp = LinearLayout.LayoutParams(MATCH, WRAP)
        rlp.bottomMargin = dp(18)
        row.layoutParams = rlp

        val left = LinearLayout(this)
        left.orientation = LinearLayout.VERTICAL
        left.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        left.addView(tv("SaroSleep", 30f, T.text, true))
        left.addView(tv("SAROTECH · GLASS EDITION", 11f, T.muted, true).apply {
            letterSpacing = 0.14f
        })
        row.addView(left)

        val addBtn = pill("+ Add", 14) { showManualEntryDialog() }
        val alp = LinearLayout.LayoutParams(WRAP, WRAP)
        alp.marginEnd = dp(8)
        addBtn.layoutParams = alp
        row.addView(addBtn)

        row.addView(pill(if (T.dark) "☀" else "☾", 16) {
            Store.setDark(this, !T.dark)
            recreate()
        })

        content.addView(row)
    }

    private fun addStatus() {
        val on = Store.mon(this)
        val c = card()
        val topRow = LinearLayout(this)
        topRow.orientation = LinearLayout.HORIZONTAL
        topRow.gravity = Gravity.CENTER_VERTICAL
        val lbl = label("NIGHT AUTO-TRACKER")
        lbl.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        topRow.addView(lbl)
        topRow.addView(badge(if (on) "● ACTIVE" else "○ PAUSED"))
        c.addView(topRow)

        val info = tv(
            "After ${fmtHour(Store.startHour(this))}, whenever your phone stays locked with zero human activity until morning unlock, your sleep is saved automatically.",
            13f,
            T.muted
        )
        info.setPadding(0, dp(10), 0, dp(14))
        c.addView(info)

        c.addView(solidPill(if (on) "Pause Night Tracking" else "Start Night Tracking") {
            if (on) {
                Store.setMon(this, false)
                Store.setPendingOff(this, 0L)
                stopService(Intent(this, SleepService::class.java))
            } else {
                Store.setMon(this, true)
                ensureService()
            }
            refresh()
        })
        content.addView(c)
    }

    private fun addHero(entries: List<SleepEntry>, goal: Int) {
        val last = entries.firstOrNull()
        val c = card(strong = true)

        val topRow = LinearLayout(this)
        topRow.orientation = LinearLayout.HORIZONTAL
        topRow.gravity = Gravity.CENTER_VERTICAL
        val lbl = label("LAST NIGHT SLEEP")
        lbl.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        topRow.addView(lbl)

        if (last != null) {
            val sc = Store.sleepScore(last, goal)
            topRow.addView(badge("Score $sc · ${Store.scoreTag(sc)}"))
        }
        c.addView(topRow)

        val big = tv(if (last != null) fmtDur(last.durationMs) else "--", 48f, T.text, true)
        big.setPadding(0, dp(6), 0, 0)
        c.addView(big)

        val subText = if (last != null) {
            val wakeInfo = if (last.wakeups > 0) " · ${last.wakeups} wake-up(s)" else " · Uninterrupted"
            "${fmtClock(last.start)} → ${fmtClock(last.end)}$wakeInfo"
        } else {
            "Aaj raat phone lock karke so jao — subah sleep data yahan save ho jayega."
        }
        c.addView(tv(subText, 13.5f, T.muted))

        val pct = if (last != null) {
            min(100, ((last.durationMs / 60_000L) * 100L / goal).toInt())
        } else 0

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.weightSum = 100f
        bar.background = rounded(dp(6), T.track)
        val blp = LinearLayout.LayoutParams(MATCH, dp(9))
        blp.topMargin = dp(16)
        bar.layoutParams = blp

        val fill = View(this)
        fill.background = rounded(dp(6), T.text)
        fill.layoutParams = LinearLayout.LayoutParams(0, MATCH, pct.toFloat())
        bar.addView(fill)
        c.addView(bar)

        val cap = tv("Goal ${fmtDur(goal * 60_000L)} · $pct% achieved", 12f, T.muted)
        cap.setPadding(0, dp(8), 0, 0)
        c.addView(cap)

        content.addView(c)
    }

    private fun streak(entries: List<SleepEntry>): Int {
        val keys = entries.map { it.night }.toSet()
        val f = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        if (!keys.contains(f.format(cal.time))) {
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }
        var n = 0
        while (keys.contains(f.format(cal.time))) {
            n++
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }
        return n
    }

    private fun mini(labelText: String, value: String): LinearLayout {
        val m = LinearLayout(this)
        m.orientation = LinearLayout.VERTICAL
        m.gravity = Gravity.CENTER
        m.background = glassBg(dp(20))
        m.setPadding(dp(8), dp(14), dp(8), dp(14))
        val lp = LinearLayout.LayoutParams(0, WRAP, 1f)
        lp.marginStart = dp(4)
        lp.marginEnd = dp(4)
        m.layoutParams = lp
        m.addView(tv(value, 17f, T.text, true).also { it.gravity = Gravity.CENTER })
        m.addView(tv(labelText, 11f, T.muted).also {
            it.gravity = Gravity.CENTER
            it.setPadding(0, dp(3), 0, 0)
        })
        return m
    }

    private fun addStats(entries: List<SleepEntry>, goal: Int) {
        val recent = entries.take(7)
        val avgMs = if (recent.isEmpty()) 0L else recent.map { it.durationMs }.average().toLong()
        val avg = if (recent.isEmpty()) "--" else fmtDur(avgMs)
        val best = entries.maxByOrNull { it.durationMs }
        val avgScore = if (recent.isEmpty()) "--" else "${recent.map { Store.sleepScore(it, goal) }.average().toInt()}"

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.bottomMargin = dp(14)
        lp.marginStart = -dp(4)
        lp.marginEnd = -dp(4)
        row.layoutParams = lp

        row.addView(mini("7-Day Avg", avg))
        row.addView(mini("Avg Score", avgScore))
        row.addView(mini("Best Night", if (best != null) fmtDur(best.durationMs) else "--"))
        row.addView(mini("Streak", "${streak(entries)} d"))
        content.addView(row)
    }

    private fun addChart(entries: List<SleepEntry>, goal: Int) {
        val map = entries.associateBy { it.night }
        val f = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val lf = SimpleDateFormat("EEE", Locale.getDefault())
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_MONTH, -1)

        val labels = ArrayList<String>()
        val hours = ArrayList<Float>()
        val dayEntries = ArrayList<SleepEntry?>()

        for (i in 0 until 7) {
            val e = map[f.format(cal.time)]
            labels.add(0, lf.format(cal.time))
            hours.add(0, (e?.durationMs ?: 0L) / 3_600_000f)
            dayEntries.add(0, e)
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }

        val c = card()
        c.addView(label("LAST 7 NIGHTS TREND"))

        val detailTv = tv("Tap any bar to inspect bedtime & wake time", 12f, T.muted)
        detailTv.setPadding(0, dp(6), 0, 0)
        c.addView(detailTv)

        val chart = BarChart(this)
        chart.labels = labels
        chart.hours = hours
        chart.goalH = goal / 60f
        chart.onBarSelected = { idx ->
            if (idx in dayEntries.indices) {
                val e = dayEntries[idx]
                detailTv.text = if (e != null) {
                    "${fmtShortDate(e.end)} · ${fmtClock(e.start)} → ${fmtClock(e.end)} · ${fmtDur(e.durationMs)} (Score ${Store.sleepScore(e, goal)})"
                } else {
                    "${labels[idx]} · No sleep recorded"
                }
            } else {
                detailTv.text = "Tap any bar to inspect bedtime & wake time"
            }
        }
        val clp = LinearLayout.LayoutParams(MATCH, dp(195))
        clp.topMargin = dp(8)
        chart.layoutParams = clp
        c.addView(chart)

        c.addView(tv("Dashed line = daily sleep goal (${fmtDur(goal * 60_000L)})", 11f, T.faint))
        content.addView(c)
    }

    private fun addHistory(entries: List<SleepEntry>, goal: Int) {
        val c = card()
        c.addView(label("SLEEP HISTORY"))

        if (entries.isEmpty()) {
            val t = tv("No sleep records yet. Auto-tracking starts tonight!", 13.5f, T.muted)
            t.setPadding(0, dp(10), 0, 0)
            c.addView(t)
        }

        for (e in entries.take(30)) {
            val div = View(this)
            div.setBackgroundColor(T.divider)
            val dlp = LinearLayout.LayoutParams(MATCH, 1)
            dlp.topMargin = dp(12)
            div.layoutParams = dlp
            c.addView(div)

            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, dp(12), 0, 0)

            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)

            val dateTitle = fmtShortDate(e.end) + if (e.manual) "  · Manual" else ""
            col.addView(tv(dateTitle, 14.5f, T.text, true))

            val sub = "${fmtClock(e.start)} → ${fmtClock(e.end)} · Score ${Store.sleepScore(e, goal)}"
            col.addView(tv(sub, 12f, T.muted))
            row.addView(col)

            row.addView(tv(fmtDur(e.durationMs), 16f, T.text, true))

            val del = tv("✕", 15f, T.muted, true)
            del.setPadding(dp(14), dp(6), dp(4), dp(6))
            del.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Delete sleep record?")
                    .setMessage("Remove ${fmtShortDate(e.end)} (${fmtDur(e.durationMs)})?")
                    .setPositiveButton("Delete") { _, _ ->
                        Store.delete(this, e.night)
                        refresh()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            row.addView(del)
            c.addView(row)
        }
        content.addView(c)
    }

    private fun stepper(
        parent: LinearLayout,
        name: String,
        value: String,
        minus: () -> Unit,
        plus: () -> Unit
    ) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(7), 0, dp(7))

        val l = tv(name, 13.5f)
        l.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        row.addView(l)

        row.addView(pill("−", 15) { minus() })
        val v = tv(value, 13.5f, T.text, true)
        v.gravity = Gravity.CENTER
        v.minWidth = dp(74)
        row.addView(v)
        row.addView(pill("+", 15) { plus() })

        parent.addView(row)
    }

    private fun addSettings() {
        val c = card()
        c.addView(label("SETTINGS"))

        val goal = Store.goalMin(this)
        val sh = Store.startHour(this)
        val mm = Store.minMin(this)
        val gap = Store.mergeGapMin(this)

        stepper(
            c, "Sleep Goal", fmtDur(goal * 60_000L),
            { Store.setGoalMin(this, goal - 30); refresh() },
            { Store.setGoalMin(this, goal + 30); refresh() }
        )
        stepper(
            c, "Night Starts At", fmtHour(sh),
            { Store.setStartHour(this, sh - 1); ensureService(); refresh() },
            { Store.setStartHour(this, sh + 1); ensureService(); refresh() }
        )
        stepper(
            c, "Min Sleep Saved", "$mm min",
            { Store.setMinMin(this, mm - 15); refresh() },
            { Store.setMinMin(this, mm + 15); refresh() }
        )
        stepper(
            c, "Merge Awake Gap", "$gap min",
            { Store.setMergeGapMin(this, gap - 15); refresh() },
            { Store.setMergeGapMin(this, gap + 15); refresh() }
        )

        // Divider + Bedtime Reminder Row
        val div = View(this)
        div.setBackgroundColor(T.divider)
        val dlp = LinearLayout.LayoutParams(MATCH, 1)
        dlp.topMargin = dp(8)
        dlp.bottomMargin = dp(8)
        div.layoutParams = dlp
        c.addView(div)

        val remOn = Store.reminderOn(this)
        val remTime = fmtHourMin(Store.reminderHour(this), Store.reminderMin(this))
        val rRow = LinearLayout(this)
        rRow.orientation = LinearLayout.HORIZONTAL
        rRow.gravity = Gravity.CENTER_VERTICAL
        rRow.setPadding(0, dp(4), 0, dp(4))

        val rLeft = LinearLayout(this)
        rLeft.orientation = LinearLayout.VERTICAL
        rLeft.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        rLeft.addView(tv("Bedtime Reminder", 13.5f, T.text))
        rLeft.addView(tv(if (remOn) "Daily at $remTime" else "Off", 12f, T.muted))
        rRow.addView(rLeft)

        if (remOn) {
            val timeBtn = pill(remTime, 12) {
                TimePickerDialog(
                    this,
                    { _, hourOfDay, minute ->
                        Store.setReminderHour(this, hourOfDay)
                        Store.setReminderMin(this, minute)
                        ReminderScheduler.schedule(this)
                        refresh()
                    },
                    Store.reminderHour(this),
                    Store.reminderMin(this),
                    false
                ).show()
            }
            val tlp = LinearLayout.LayoutParams(WRAP, WRAP)
            tlp.marginEnd = dp(8)
            timeBtn.layoutParams = tlp
            rRow.addView(timeBtn)
        }

        rRow.addView(pill(if (remOn) "ON" else "OFF", 14) {
            val next = !remOn
            Store.setReminderOn(this, next)
            if (next) ReminderScheduler.schedule(this) else ReminderScheduler.cancel(this)
            refresh()
        })
        c.addView(rRow)

        content.addView(c)
    }

    private fun addTools() {
        val c = card()
        c.addView(label("TOOLS & DATA"))

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            val t = tv("✓ Background battery: Unrestricted", 13f, T.muted)
            t.setPadding(0, dp(10), 0, dp(2))
            c.addView(t)
        } else {
            c.addView(fullWidth(pill("Allow Background Running (Recommended)") {
                try {
                    val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    i.data = Uri.parse("package:$packageName")
                    startActivity(i)
                } catch (_: Exception) {
                    try {
                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    } catch (_: Exception) {
                    }
                }
            }))
        }

        c.addView(fullWidth(pill("Save CSV File") {
            val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/csv"
                putExtra(Intent.EXTRA_TITLE, "SaroSleep_Report.csv")
            }
            startActivityForResult(i, REQ_EXPORT_CSV)
        }))

        c.addView(fullWidth(pill("Share CSV Summary") {
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "SaroSleep Data · Sarotech")
                putExtra(Intent.EXTRA_TEXT, Store.csv(this@MainActivity))
            }
            startActivity(Intent.createChooser(i, "Share Sleep CSV"))
        }))

        val backupRow = LinearLayout(this)
        backupRow.orientation = LinearLayout.HORIZONTAL
        val blp = LinearLayout.LayoutParams(MATCH, WRAP)
        blp.topMargin = dp(10)
        backupRow.layoutParams = blp

        val bBtn = pill("Backup JSON", 12) {
            val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "SaroSleep_Backup.json")
            }
            startActivityForResult(i, REQ_BACKUP_JSON)
        }
        bBtn.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(5) }
        backupRow.addView(bBtn)

        val rBtn = pill("Restore JSON", 12) {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            startActivityForResult(i, REQ_RESTORE_JSON)
        }
        rBtn.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(5) }
        backupRow.addView(rBtn)
        c.addView(backupRow)

        c.addView(fullWidth(pill("Clear All Sleep Records") {
            AlertDialog.Builder(this)
                .setTitle("Delete all sleep records?")
                .setMessage("This will erase your entire sleep history and cannot be undone.")
                .setPositiveButton("Delete All") { _, _ ->
                    Store.clearAll(this)
                    refresh()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }))

        content.addView(c)
    }

    private fun showManualEntryDialog() {
        // Pick bedtime first, then wake time for last night
        TimePickerDialog(
            this,
            { _, bedH, bedM ->
                TimePickerDialog(
                    this,
                    { _, wakeH, wakeM ->
                        saveManualEntry(bedH, bedM, wakeH, wakeM)
                    },
                    6,
                    30,
                    false
                ).apply {
                    setTitle("Select Morning Wake Time")
                }.show()
            },
            22,
            30,
            false
        ).apply {
            setTitle("Select Last Night Bedtime")
        }.show()
    }

    private fun saveManualEntry(bedH: Int, bedM: Int, wakeH: Int, wakeM: Int) {
        val wakeCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, wakeH)
            set(Calendar.MINUTE, wakeM)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis > System.currentTimeMillis()) {
                add(Calendar.DAY_OF_MONTH, -1)
            }
        }
        val bedCal = Calendar.getInstance().apply {
            timeInMillis = wakeCal.timeInMillis
            set(Calendar.HOUR_OF_DAY, bedH)
            set(Calendar.MINUTE, bedM)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis >= wakeCal.timeInMillis) {
                add(Calendar.DAY_OF_MONTH, -1)
            }
        }

        val dur = wakeCal.timeInMillis - bedCal.timeInMillis
        if (dur < 15 * 60_000L || dur > 18 * 3_600_000L) {
            toast("Please pick a valid sleep duration (15m - 18h)")
            return
        }

        val entry = SleepEntry(
            night = Tracker.nightKey(bedCal.timeInMillis),
            start = bedCal.timeInMillis,
            end = wakeCal.timeInMillis,
            awakeMs = 0L,
            wakeups = 0,
            manual = true
        )
        Store.addMerge(this, entry)
        toast("Saved manual sleep: ${fmtDur(dur)}")
        refresh()
    }

    private fun addFooter() {
        val c = card()
        c.gravity = Gravity.CENTER
        c.addView(tv("SaroSleep · v1.1", 14f, T.text, true).also { it.gravity = Gravity.CENTER })
        c.addView(tv("Brand: Sarotech", 12.5f, T.muted, true).also {
            it.gravity = Gravity.CENTER
            it.setPadding(0, dp(4), 0, 0)
        })
        c.addView(tv("Developer: Prashant Latta", 12f, T.muted).also {
            it.gravity = Gravity.CENTER
            it.setPadding(0, dp(2), 0, 0)
        })
        content.addView(c)
    }
}
