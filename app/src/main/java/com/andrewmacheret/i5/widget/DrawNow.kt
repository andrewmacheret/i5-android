package com.andrewmacheret.i5.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.compose

/**
 * Draws every widget right now and hands it to the launcher.
 * Glance's own update runs as a background job that Doze can hold back for a long time,
 * so the alarm at a lane change uses this to make the widget flip on time.
 */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
object DrawNow {
    suspend fun all(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val glance = GlanceAppWidgetManager(context)
        for (id in glance.getGlanceIds(LanesWidget::class.java)) {
            val start = SystemClock.elapsedRealtime()
            val appId = glance.getAppWidgetId(id)
            // With no size given, Glance draws every size the launcher listed in the options
            val views = LanesWidget().compose(context, id, manager.getAppWidgetOptions(appId))
            manager.updateAppWidget(appId, views)
            Log.i("I5", "drew widget $appId in ${SystemClock.elapsedRealtime() - start} ms")
        }
    }
}
