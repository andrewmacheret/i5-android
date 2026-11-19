package com.andrewmacheret.i5

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import com.andrewmacheret.i5.core.Assessment
import com.andrewmacheret.i5.core.LaneStatus
import com.andrewmacheret.i5.core.Segment
import com.andrewmacheret.i5.core.TimeOfDay

/** Bitmaps for the widget, which can't draw custom shapes itself. Also used by the app. */
object Drawing {
    private val bold: Typeface =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.DEFAULT, 900, false)
        else Typeface.DEFAULT_BOLD

    /** The badge for a trust state. A direction only shows when it's confirmed. */
    fun icon(check: Assessment, sizePx: Int): Bitmap = when (check.verdict) {
        Assessment.Verdict.CONFIDENT -> badge(check.scheduled, sizePx)
        Assessment.Verdict.CHECKING -> badge(LaneStatus.UNKNOWN, sizePx)
        Assessment.Verdict.UNCERTAIN -> warning(sizePx)
    }

    /** A colored rounded square with a white N, S, ✕ or ? */
    fun badge(status: LaneStatus, sizePx: Int): Bitmap {
        val s = sizePx.coerceAtLeast(8).toFloat()
        val bmp = createBitmap(s.toInt(), s.toInt())
        val c = Canvas(bmp)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LaneColors.badge(status).toArgb() }
        c.drawRoundRect(RectF(0f, 0f, s, s), s * 0.26f, s * 0.26f, fill)
        drawCentered(c, status.letter, s / 2, s / 2, s * 0.62f)
        return bmp
    }

    /** An amber triangle, shaped differently from the badges so it can't be misread */
    fun warning(sizePx: Int): Bitmap {
        val s = sizePx.coerceAtLeast(8).toFloat()
        val bmp = createBitmap(s.toInt(), s.toInt())
        val c = Canvas(bmp)
        val stroke = s * 0.08f
        val top = s * 0.08f
        val bottom = s * 0.9f
        val path = Path().apply {
            moveTo(s / 2, top + stroke / 2)
            lineTo(s - stroke / 2, bottom)
            lineTo(stroke / 2, bottom)
            close()
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LaneColors.warning.toArgb()
            style = Paint.Style.FILL_AND_STROKE
            strokeWidth = stroke
            strokeJoin = Paint.Join.ROUND
        }
        c.drawPath(path, paint)
        drawCentered(c, "!", s / 2, s * 0.6f, s * 0.5f)
        return bmp
    }

    private fun drawCentered(c: Canvas, text: String, cx: Float, cy: Float, size: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = size
            typeface = bold
            textAlign = Paint.Align.CENTER
        }
        val m = p.fontMetrics
        c.drawText(text, cx, cy - (m.ascent + m.descent) / 2, p)
    }

    /** A day as colored runs, with an optional marker for now. Faded when not confirmed. */
    fun timeline(segments: List<Segment>, now: TimeOfDay?, widthPx: Int, heightPx: Int, faded: Boolean): Bitmap {
        val w = widthPx.coerceAtLeast(16).toFloat()
        val h = heightPx.coerceAtLeast(8).toFloat()
        val bmp = createBitmap(w.toInt(), h.toInt())
        val c = Canvas(bmp)
        val barTop = h * 0.2f
        val barBottom = h * 0.8f
        val radius = (barBottom - barTop) * 0.3f
        val bar = RectF(0f, barTop, w, barBottom)
        val clip = Path().apply { addRoundRect(bar, radius, radius, Path.Direction.CW) }
        val alpha = if (faded) 0.45f else 1f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        c.withClip(clip) {
            paint.color = LaneColors.timeline(LaneStatus.CLOSED).copy(alpha = 0.35f * alpha).toArgb()
            drawRect(bar, paint)
            for (seg in segments) {
                if (!seg.status.isOpen) continue
                paint.color = LaneColors.timeline(seg.status).copy(alpha = alpha).toArgb()
                drawRect(x(seg.start, w), barTop, x(seg.end, w), barBottom, paint)
            }
        }

        if (now != null) {
            // White line with a dark edge shows up on light and dark home screens
            val nx = x(now, w).coerceIn(h * 0.1f, w - h * 0.1f)
            paint.color = 0xCC202124.toInt()
            c.drawRoundRect(RectF(nx - h * 0.1f, 0f, nx + h * 0.1f, h), h * 0.1f, h * 0.1f, paint)
            paint.color = android.graphics.Color.WHITE
            c.drawRoundRect(RectF(nx - h * 0.05f, h * 0.05f, nx + h * 0.05f, h * 0.95f), h * 0.05f, h * 0.05f, paint)
        }
        return bmp
    }

    private fun x(t: TimeOfDay, w: Float) = w * t.minutes / (24f * 60f)
}
