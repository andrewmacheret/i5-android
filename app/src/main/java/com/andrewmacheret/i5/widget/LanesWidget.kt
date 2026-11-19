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
import com.andrewmacheret.i5.core.LaneStatus
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
        Work.refreshNow(context)
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Work.refreshNow(context)
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
    val next = snap.next?.takeIf { height >= 80.dp && snap.check.verdict == Assessment.Verdict.CONFIDENT }
    // Smaller or shorter if needed, and no time at all rather than a cut off one
    val context = LocalContext.current
    val time = next?.let {
        val clock = Format.clock(it.at)
        listOf("until $clock" to 11f, "until $clock" to 10f, clock to 11f, clock to 10f).firstNotNullOfOrNull { (text, sp) ->
            TextFit.first(context, listOf(text), sp, width - 12.dp)?.let { fit -> fit to sp }
        }
    }
    val badge = minOf(width - 12.dp, height - (if (time != null) 34.dp else 16.dp)).coerceIn(24.dp, 72.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
        Badge(snap, badge)
        if (time != null) {
            Text(
                time.first,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = time.second.sp),
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
    val lines = if (showBar && !narrow) 1 else 2
    val badge = if (narrow) 34.dp else 40.dp
    val gap = if (narrow) 10.dp else 12.dp
    Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Badge(snap, badge)
            Spacer(GlanceModifier.width(gap))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Title(snap, if (narrow) 16 else 18, width - 28.dp - badge - gap)
                Text(
                    fittedDetail(snap, 13f, width - 28.dp - badge - gap, lines),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                    maxLines = lines,
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
    val context = LocalContext.current
    val textWidth = width - 88.dp
    val detail = fittedDetail(snap, 13f, textWidth, 2)
    val oneLine = TextFit.first(context, listOf(detail), 13f, textWidth) != null
    Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Badge(snap, 48.dp)
            Spacer(GlanceModifier.width(12.dp))
            Column {
                val captions = captions(snap)
                Text(
                    TextFit.first(context, captions, 11f, textWidth) ?: captions.last(),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
                Title(snap, 21, textWidth)
                Text(
                    detail,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                    maxLines = 2,
                )
            }
        }
        // Fill spare height with what comes after the next change, after room for a second line of detail
        val rows = ((height - 160.dp - (if (oneLine) 0.dp else 17.dp)) / 18.dp).toInt().coerceIn(0, 3)
        if (rows > 0) {
            Spacer(GlanceModifier.height(6.dp))
            ExtraRows(snap, rows, width - 28.dp)
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
            val sources = sourceTexts(snap)
            Text(
                TextFit.first(context, sources, 11f, width - 56.dp) ?: TextFit.keepTimesTogether(sources.last()),
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
private fun ExtraRows(snap: Snapshot, rows: Int, width: Dp) {
    val context = LocalContext.current
    val small = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp)
    if (snap.check.verdict == Assessment.Verdict.UNCERTAIN) {
        val posted = snap.check.scheduled.title.lowercase()
        val first = TextFit.first(context, listOf("Posted schedule: $posted, not confirmed", "Posted: $posted"), 12f, width)
        val lines = listOfNotNull(first) + snap.check.concerns.drop(1).map { "⚠ ${it.title}" }
        for (line in lines.take(rows)) Text(line, style = small, maxLines = 1)
        return
    }
    if (snap.check.verdict != Assessment.Verdict.CONFIDENT) return
    for (c in snap.changes.drop(1).take(rows)) {
        // Shorter words before a cut off time. The spaces stand in for the gap between the two.
        val time = Format.whenText(c.at, snap.now)
        val (status, at) = listOf(c.to.title to time, short(c.to) to time, short(c.to) to Format.clock(c.at))
            .firstOrNull { (s, t) -> TextFit.first(context, listOf("●  $s    $t"), 12f, width) != null } ?: return
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("● ", style = TextStyle(color = colors(LaneColors.badge(c.to)), fontSize = 12.sp))
            Text(status, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp), maxLines = 1, modifier = GlanceModifier.defaultWeight())
            Text(TextFit.keepTimesTogether(at), style = small, maxLines = 1)
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
private fun Title(snap: Snapshot, sizeSp: Int, width: Dp) {
    val color = when (snap.check.verdict) {
        Assessment.Verdict.CONFIDENT -> colors(LaneColors.text(snap.check.scheduled, false), LaneColors.text(snap.check.scheduled, true))
        Assessment.Verdict.CHECKING -> GlanceTheme.colors.onSurface
        Assessment.Verdict.UNCERTAIN -> colors(LaneColors.warningText(false), LaneColors.warningText(true))
    }
    // A bit smaller, then the short word, so "Northbound" never shows as "Northbo…"
    val context = LocalContext.current
    val full = headline(snap)
    val short = shortHeadline(snap)
    val (text, sp) = listOf(full to sizeSp, full to sizeSp - 2, short to sizeSp - 2, short to sizeSp - 4)
        .firstOrNull { (t, sp) -> TextFit.first(context, listOf(t), sp.toFloat(), width, bold = true) != null } ?: (short to sizeSp - 4)
    Text(text, style = TextStyle(color = color, fontSize = sp.sp, fontWeight = FontWeight.Bold), maxLines = 1)
}

private fun headline(snap: Snapshot) = when (snap.check.verdict) {
    Assessment.Verdict.CONFIDENT -> snap.check.scheduled.title
    Assessment.Verdict.CHECKING -> "Checking…"
    Assessment.Verdict.UNCERTAIN -> "Can't confirm"
}

private fun shortHeadline(snap: Snapshot) = when (snap.check.verdict) {
    Assessment.Verdict.CONFIDENT -> short(snap.check.scheduled)
    Assessment.Verdict.CHECKING -> "Checking"
    Assessment.Verdict.UNCERTAIN -> "Unsure"
}

private fun short(status: LaneStatus) = when (status) {
    LaneStatus.NORTHBOUND -> "North"
    LaneStatus.SOUTHBOUND -> "South"
    else -> status.title
}

private fun detail(snap: Snapshot) = when (snap.check.verdict) {
    Assessment.Verdict.CONFIDENT -> snap.nextText
    Assessment.Verdict.CHECKING -> "Reading WSDOT's schedule"
    Assessment.Verdict.UNCERTAIN -> snap.check.concerns.firstOrNull()?.title ?: "Tap for details"
}

/** "Closes at 11:00 PM" if it fits, down to "until 11:00 PM", but never a cut off time */
@Composable
private fun fittedDetail(snap: Snapshot, sp: Float, width: Dp, lines: Int): String {
    val next = snap.next
    if (snap.check.verdict != Assessment.Verdict.CONFIDENT || next == null) return detail(snap)
    val options = listOf(
        snap.nextText,
        "until ${Format.whenText(next.at, snap.now)}",
        "until ${Format.clock(next.at)}",
        Format.clock(next.at),
    )
    return TextFit.first(LocalContext.current, options, sp, width, lines) ?: TextFit.keepTimesTogether(options.last())
}

/** Mentions Seattle time when the phone is set to another time zone. Longest first. */
private fun captions(snap: Snapshot): List<String> {
    val here = ZoneId.systemDefault().rules.getOffset(snap.now)
    val there = Seattle.zone.rules.getOffset(snap.now)
    return if (here == there) listOf("I-5 EXPRESS LANES", "I-5 EXPRESS") else listOf("I-5 EXPRESS · SEATTLE TIME", "SEATTLE TIME")
}

/** Longest first, for when the widget is narrow */
private fun sourceTexts(snap: Snapshot): List<String> {
    if (snap.check.verdict == Assessment.Verdict.CHECKING) return listOf("Checking WSDOT…")
    return when (val s = snap.result.source) {
        is DataSource.Live -> Format.whenText(s.at, snap.now).let { listOf("Checked WSDOT $it", "Checked $it", it) }
        is DataSource.Cached -> Format.whenText(s.at, snap.now).let { listOf("Saved copy from $it", "Saved $it", it) }
        DataSource.BuiltIn -> listOf("Built-in schedule only")
    }
}