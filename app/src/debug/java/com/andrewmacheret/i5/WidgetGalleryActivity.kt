package com.andrewmacheret.i5

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.compose
import com.andrewmacheret.i5.widget.LanesWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Debug builds only. Draws the real widget at common home screen sizes, for screenshots:
 *
 *   adb shell am start -n com.andrewmacheret.i5/.WidgetGalleryActivity
 */
class WidgetGalleryActivity : Activity() {
    // Cell sizes from a Pixel launcher, about 92 x 105 dp each
    private val sizes = listOf(
        "1 × 1" to DpSize(92.dp, 105.dp),
        "2 × 1" to DpSize(184.dp, 105.dp),
        "4 × 1" to DpSize(368.dp, 105.dp),
        "3 × 2" to DpSize(276.dp, 210.dp),
    )

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(40), dp(16), dp(40))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF1B2A41.toInt(), 0xFF0B1320.toInt()))
        }
        setContentView(ScrollView(this).apply { addView(column) })

        Lanes.scope.launch {
            Lanes.load()
            for ((label, size) in sizes) {
                val views = LanesWidget().compose(this@WidgetGalleryActivity, size = size)
                withContext(Dispatchers.Main) {
                    column.addView(TextView(this@WidgetGalleryActivity).apply {
                        text = label
                        setTextColor(Color.WHITE)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        setPadding(dp(4), dp(10), 0, dp(6))
                    })
                    val host = LinearLayout(this@WidgetGalleryActivity).apply { gravity = Gravity.START }
                    val view: View = views.apply(this@WidgetGalleryActivity, host)
                    host.addView(view, LinearLayout.LayoutParams(dp(size.width.value), dp(size.height.value)))
                    column.addView(host)
                }
            }
        }
    }

    private fun dp(v: Number) = (v.toFloat() * resources.displayMetrics.density).toInt()
}
