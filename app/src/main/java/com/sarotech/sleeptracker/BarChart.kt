package com.sarotech.sleeptracker

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import java.util.Locale
import kotlin.math.max

/**
 * Interactive 7-Night Monochrome Glass Bar Chart with dashed goal reference line.
 */
class BarChart(ctx: Context) : View(ctx) {

    var labels: List<String> = emptyList()
    var hours: List<Float> = emptyList()
    var goalH: Float = 8f
    var selectedIdx: Int = -1
    var onBarSelected: ((Int) -> Unit)? = null

    private val dens = ctx.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP && hours.isNotEmpty() && width > 0) {
            val idx = ((event.x / width) * hours.size).toInt().coerceIn(0, hours.size - 1)
            selectedIdx = if (selectedIdx == idx) -1 else idx
            invalidate()
            onBarSelected?.invoke(selectedIdx)
            performClick()
            return true
        }
        return event.action == MotionEvent.ACTION_DOWN || super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val n = hours.size
        if (n == 0) return

        val w = width.toFloat()
        val h = height.toFloat()
        val bottom = h - 26f * dens
        val top = 24f * dens
        val chartH = (bottom - top).coerceAtLeast(10f)
        val maxV = max(goalH, hours.maxOrNull() ?: 0f).coerceAtLeast(1f) * 1.15f
        val slot = w / n
        val bw = slot * 0.48f

        // Dashed Goal Line
        val gy = bottom - (goalH / maxV) * chartH
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f * dens
        paint.color = T.muted
        paint.pathEffect = DashPathEffect(floatArrayOf(7f * dens, 6f * dens), 0f)
        c.drawLine(0f, gy, w, gy, paint)
        paint.pathEffect = null

        // Bars & Labels
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 11f * dens
        borderPaint.strokeWidth = 1.2f * dens

        for (i in 0 until n) {
            val cx = slot * i + slot / 2f
            val v = hours[i]
            val bh = if (v > 0f) max(6f * dens, (v / maxV) * chartH) else 4f * dens
            val rect = RectF(cx - bw / 2f, bottom - bh, cx + bw / 2f, bottom)

            val isSelected = i == selectedIdx
            paint.color = when {
                v <= 0f -> T.track
                isSelected -> T.barSelected
                v >= goalH -> T.text
                else -> T.barDim
            }
            c.drawRoundRect(rect, 10f * dens, 10f * dens, paint)

            // Crisp glass rim on non-empty bars
            if (v > 0f) {
                borderPaint.color = if (isSelected) T.text else T.border
                c.drawRoundRect(rect, 10f * dens, 10f * dens, borderPaint)

                paint.color = T.text
                c.drawText(
                    String.format(Locale.US, "%.1f", v),
                    cx,
                    bottom - bh - 6f * dens,
                    paint
                )
            }

            paint.color = if (isSelected) T.text else T.muted
            if (i < labels.size) {
                c.drawText(labels[i], cx, h - 6f * dens, paint)
            }
        }
    }
}
