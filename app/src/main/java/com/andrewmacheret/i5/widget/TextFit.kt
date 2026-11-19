package com.andrewmacheret.i5.widget

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import androidx.compose.ui.unit.Dp

/**
 * Picks text that fits the widget whole, so the launcher never cuts it off with "…".
 * Measures at the phone's font size, the same way a TextView lays text out.
 */
internal object TextFit {
    // The launcher's font can be a little wider than the one measured here
    private const val SLACK = 0.85f

    /** "11:00 PM" never splits across two lines */
    fun keepTimesTogether(text: String) = text.replace(" AM", "\u00A0AM").replace(" PM", "\u00A0PM")

    /** The first option that fits in `lines` lines, or null if none does */
    fun first(context: Context, options: List<String>, sp: Float, width: Dp, lines: Int = 1, bold: Boolean = false): String? {
        val metrics = context.resources.displayMetrics
        val px = (width.value * metrics.density * SLACK).toInt()
        if (px <= 0) return null
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, metrics)
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        return options.map(::keepTimesTogether).firstOrNull { text ->
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, px).build()
            // A line that ends mid-word means a word was too long and got split
            layout.lineCount <= lines && (0 until layout.lineCount - 1).all { text[layout.getLineEnd(it) - 1] == ' ' }
        }
    }
}
