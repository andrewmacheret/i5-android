package com.andrewmacheret.i5.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.andrewmacheret.i5.Drawing
import com.andrewmacheret.i5.LaneColors
import com.andrewmacheret.i5.Lanes
import com.andrewmacheret.i5.R
import com.andrewmacheret.i5.Work
import com.andrewmacheret.i5.core.Assessment
import com.andrewmacheret.i5.core.DataSource
import com.andrewmacheret.i5.core.Format
import com.andrewmacheret.i5.core.Seattle
import com.andrewmacheret.i5.core.Snapshot
import com.andrewmacheret.i5.ui.MainActivity
import java.time.ZoneId

class LanesWidget : GlanceAppWidget() {
    // Exact sizes, so the timeline bitmap matches the real width
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Lanes.load()
        provideContent {
            val version by Lanes.version.collectAsState()
            val refreshing by Lanes.refreshing.collectAsState()
            val snap = remember(version, refreshing) { Lanes.snapshot() }
            GlanceTheme {
                WidgetContent(snap)
            }
        }
    }
}

class LanesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LanesWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // First widget placed. Get fresh data and set the next wake up.
        Work.refreshSoon(context)
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Work.refreshSoon(context)
    }
}

private fun colors(day: Color, night: Color = day) = ColorProvider(day = day, night = night)

@Composable
private fun WidgetContent(snap: Snapshot) {
    val size = LocalSize.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(horizontal = if (size.width < 100.dp) 6.dp else 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            size.width < 150.dp -> Tiny(snap, size.width, size.height)
            size.height < 150.dp -> Compact(snap, size.width, size.height)
            else -> Full(snap, size.width, size.height)
        }
    }
}

/** One cell: just the badge, with the next change time if there's room */
@Composable
private fun Tiny(snap: Snapshot, width: Dp, height: Dp) {
    val showTime = height >= 80.dp && snap.check.verdict == Assessment.Verdict.CONFIDENT && snap.next != null
    val badge = minOf(width - 12.dp, height - (if (showTime) 34.dp else 16.dp)).coerceIn(24.dp, 72.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
        Badge(snap, badge)
        if (showTime) {
            val next = snap.next!!
            Text(
                "until ${Format.clock(next.at)}",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                maxLines = 1,
                modifier = GlanceModifier.padding(top = 4.dp),
            )
        }
    }
}

/** A short row: badge, status and what's next, plus today's bar when there's room */
@Composable
private fun Compact(snap: Snapshot, width: Dp, height: Dp) {
    val showBar = height >= 96.dp
    val narrow = width < 230.dp
    Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Badge(snap, if (narrow) 34.dp else 40.dp)
            Spacer(GlanceModifier.width(if (narrow) 10.dp else 12.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Title(snap, if (narrow) 16 else 18)
                Text(
                    if (narrow) shortDetail(snap) else detail(snap),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                    maxLines = if (showBar && !narrow) 1 else 2,
                )
            }
        }
        if (showBar) {
            Spacer(GlanceModifier.height(8.dp))
            Timeline(snap, width - 28.dp, 14.dp)
        }
    }
}

@Composable
private fun Timeline(snap: Snapshot, width: Dp, height: Dp) {
    val density = LocalContext.current.resources.displayMetrics.density
    Image(
        provider = ImageProvider(
            Drawing.timeline(
                snap.today,
                Seattle.time(snap.now),
                (width.value * density).toInt(),
                (height.value * density).toInt(),
                faded = snap.check.verdict != Assessment.Verdict.CONFIDENT,
            ),
        ),
        contentDescription = "Today's schedule",
        contentScale = ContentScale.FillBounds,
        modifier = GlanceModifier.fillMaxWidth().height(height),
    )
}

/** The full card: status, today's timeline and where the data came from */
@Composable
private fun Full(snap: Snapshot, width: Dp, height: Dp) {
    Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Badge(snap, 48.dp)
            Spacer(GlanceModifier.width(12.dp))
            Column {
                Text(
                    caption(snap),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
                Title(snap, 21)
                Subtitle(snap, maxLines = 2)
            }
        }
        // Fill spare height with what comes after the next change
        val rows = ((height - 160.dp) / 18.dp).toInt().coerceIn(0, 3)
        if (rows > 0) {
            Spacer(GlanceModifier.height(6.dp))
            ExtraRows(snap, rows)
        }
        Spacer(GlanceModifier.defaultWeight())
        Timeline(snap, width - 28.dp, 18.dp)
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            for ((i, mark) in listOf("12a", "6a", "12p", "6p", "12a").withIndex()) {
                if (i > 0) Spacer(GlanceModifier.defaultWeight())
                Text(mark, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 9.sp))
            }
        }
        Spacer(GlanceModifier.height(2.dp))
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                sourceText(snap),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            Image(
                provider = ImageProvider(R.drawable.ic_refresh),
                contentDescription = "Refresh",
                colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
                modifier = GlanceModifier.size(28.dp).padding(4.dp).clickable(actionRunCallback<RefreshAction>()),
            )
        }
    }
}

/** Later changes when confirmed. Otherwise the posted schedule and any other concerns. */
@Composable
private fun ExtraRows(snap: Snapshot, rows: Int) {
    val small = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp)
    if (snap.check.verdict == Assessment.Verdict.UNCERTAIN) {
        val lines = listOf("Posted schedule: ${snap.check.scheduled.title.lowercase()}, not confirmed") +
            snap.check.concerns.drop(1).map { "⚠ ${it.title}" }
        for (line in lines.take(rows)) Text(line, style = small, maxLines = 1)
        return
    }
    if (snap.check.verdict != Assessment.Verdict.CONFIDENT) return
    for (c in snap.changes.drop(1).take(rows)) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("● ", style = TextStyle(color = colors(LaneColors.badge(c.to)), fontSize = 12.sp))
            Text(c.to.title, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp), modifier = GlanceModifier.defaultWeight())
            Text(Format.whenText(c.at, snap.now), style = small, maxLines = 1)
        }
    }
}

@Composable
private fun Badge(snap: Snapshot, size: Dp) {
    val px = (size.value * LocalContext.current.resources.displayMetrics.density).toInt()
    Image(
        provider = ImageProvider(Drawing.icon(snap.check, px)),
        contentDescription = headline(snap),
        modifier = GlanceModifier.size(size),
    )
}

@Composable
private fun Title(snap: Snapshot, sizeSp: Int) {
    val color = when (snap.check.verdict) {
        Assessment.Verdict.CONFIDENT -> colors(LaneColors.text(snap.check.scheduled, false), LaneColors.text(snap.check.scheduled, true))
        Assessment.Verdict.CHECKING -> GlanceTheme.colors.onSurface
        Assessment.Verdict.UNCERTAIN -> colors(LaneColors.warningText(false), LaneColors.warningText(true))
    }
    Text(headline(snap), style = TextStyle(color = color, fontSize = sizeSp.sp, fontWeight = FontWeight.Bold), maxLines = 1)
}

@Composable
private fun Subtitle(snap: Snapshot, maxLines: Int) {
    Text(
        detail(snap),
        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
        maxLines = maxLines,
    )
}

private fun headline(snap: Snapshot) = when (snap.check.verdict) {
    Assessment.Verdict.CONFIDENT -> snap.check.scheduled.title
    Assessment.Verdict.CHECKING -> "Checking…"
    Assessment.Verdict.UNCERTAIN -> "Can't confirm"
}

private fun detail(snap: Snapshot) = when (snap.check.verdict) {
    Assessment.Verdict.CONFIDENT -> snap.nextText
    Assessment.Verdict.CHECKING -> "Reading WSDOT's schedule"
    Assessment.Verdict.UNCERTAIN -> snap.check.concerns.firstOrNull()?.title ?: "Tap for details"
}

/** For narrow widgets: "until 9:00 PM" */
private fun shortDetail(snap: Snapshot): String {
    if (snap.check.verdict != Assessment.Verdict.CONFIDENT) return detail(snap)
    val next = snap.next ?: return "All week"
    return "until ${Format.whenText(next.at, snap.now)}"
}

/** Mentions Seattle time when the phone is set to another time zone */
private fun caption(snap: Snapshot): String {
    val here = ZoneId.systemDefault().rules.getOffset(snap.now)
    val there = Seattle.zone.rules.getOffset(snap.now)
    return if (here == there) "I-5 EXPRESS LANES" else "I-5 EXPRESS · SEATTLE TIME"
}

private fun sourceText(snap: Snapshot): String {
    if (snap.check.verdict == Assessment.Verdict.CHECKING) return "Checking WSDOT…"
    return when (val s = snap.result.source) {
        is DataSource.Live -> "Checked WSDOT ${Format.whenText(s.at, snap.now)}"
        is DataSource.Cached -> "Saved copy from ${Format.whenText(s.at, snap.now)}"
        DataSource.BuiltIn -> "Built-in schedule only"
    }
}