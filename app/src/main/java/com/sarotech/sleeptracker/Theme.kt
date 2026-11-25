package com.sarotech.sleeptracker

import android.graphics.Color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pure Black & White Glassmorphism Theme.
 * Supports Dark Glass (default OLED black + frosted white glass)
 * and Light Glass (frost white + smoked obsidian glass).
 */
object T {
    var dark: Boolean = true

    val bg1: Int get() = if (dark) Color.rgb(0, 0, 0) else Color.rgb(235, 236, 240)
    val bg2: Int get() = if (dark) Color.rgb(18, 18, 22) else Color.rgb(252, 252, 255)
    val bg3: Int get() = if (dark) Color.rgb(6, 6, 8) else Color.rgb(228, 230, 236)

    val text: Int get() = if (dark) Color.rgb(255, 255, 255) else Color.rgb(10, 10, 12)
    val muted: Int get() = if (dark) Color.argb(165, 255, 255, 255) else Color.argb(148, 12, 12, 15)
    val faint: Int get() = if (dark) Color.argb(95, 255, 255, 255) else Color.argb(85, 0, 0, 0)

    // Frosted Glass surface gradients (top-left sheen -> bottom-right translucent frost)
    val glassA: Int get() = if (dark) Color.argb(42, 255, 255, 255) else Color.argb(195, 255, 255, 255)
    val glassB: Int get() = if (dark) Color.argb(14, 255, 255, 255) else Color.argb(115, 255, 255, 255)
    val glassStrongA: Int get() = if (dark) Color.argb(62, 255, 255, 255) else Color.argb(230, 255, 255, 255)
    val glassStrongB: Int get() = if (dark) Color.argb(22, 255, 255, 255) else Color.argb(150, 255, 255, 255)

    // Glass rim highlight
    val border: Int get() = if (dark) Color.argb(68, 255, 255, 255) else Color.argb(52, 0, 0, 0)
    val divider: Int get() = if (dark) Color.argb(34, 255, 255, 255) else Color.argb(28, 0, 0, 0)

    // Tracks & bars
    val track: Int get() = if (dark) Color.argb(36, 255, 255, 255) else Color.argb(26, 0, 0, 0)
    val barDim: Int get() = if (dark) Color.argb(120, 255, 255, 255) else Color.argb(115, 0, 0, 0)
    val barSelected: Int get() = if (dark) Color.rgb(255, 255, 255) else Color.rgb(0, 0, 0)

    // Ambient glass backdrop orbs
    val blobA: Int get() = if (dark) Color.argb(68, 255, 255, 255) else Color.argb(42, 0, 0, 0)
    val blobB: Int get() = if (dark) Color.argb(0, 255, 255, 255) else Color.argb(0, 0, 0, 0)

    // Solid pill inverse colors
    val solidBg: Int get() = if (dark) Color.rgb(255, 255, 255) else Color.rgb(12, 12, 14)
    val solidText: Int get() = if (dark) Color.rgb(8, 8, 10) else Color.rgb(255, 255, 255)
}

fun fmtDur(ms: Long): String {
    val totalMin = (ms / 60_000L).coerceAtLeast(0L)
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

fun fmtHour(h: Int): String {
    val norm = ((h % 24) + 24) % 24
    val hh = if (norm % 12 == 0) 12 else norm % 12
    val suffix = if (norm < 12) "AM" else "PM"
    return "$hh $suffix"
}

fun fmtHourMin(h: Int, m: Int): String {
    val norm = ((h % 24) + 24) % 24
    val hh = if (norm % 12 == 0) 12 else norm % 12
    val suffix = if (norm < 12) "AM" else "PM"
    return String.format(Locale.US, "%d:%02d %s", hh, m.coerceIn(0, 59), suffix)
}

fun fmtClock(ms: Long): String {
    return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(ms))
}

fun fmtShortDate(ms: Long): String {
    return SimpleDateFormat("EEE, dd MMM", Locale.getDefault()).format(Date(ms))
}
