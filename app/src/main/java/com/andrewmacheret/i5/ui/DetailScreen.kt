package com.andrewmacheret.i5.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.andrewmacheret.i5.Alarms
import com.andrewmacheret.i5.Drawing
import com.andrewmacheret.i5.LaneColors
import com.andrewmacheret.i5.Lanes
import com.andrewmacheret.i5.Notifier
import com.andrewmacheret.i5.Prefs
import com.andrewmacheret.i5.R
import com.andrewmacheret.i5.Updates
import com.andrewmacheret.i5.Work
import com.andrewmacheret.i5.core.Assessment
import com.andrewmacheret.i5.core.Concern
import com.andrewmacheret.i5.core.DataSource
import com.andrewmacheret.i5.core.EarlyClose
import com.andrewmacheret.i5.core.Format
import com.andrewmacheret.i5.core.LaneStatus
import com.andrewmacheret.i5.core.LaneWindow
import com.andrewmacheret.i5.core.Seattle
import com.andrewmacheret.i5.core.Segment
import com.andrewmacheret.i5.core.Snapshot
import com.andrewmacheret.i5.core.Sources
import com.andrewmacheret.i5.core.TimeOfDay
import com.andrewmacheret.i5.widget.LanesWidget
import com.andrewmacheret.i5.widget.LanesWidgetReceiver
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.net.toUri
import androidx.glance.appwidget.GlanceAppWidgetManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URI
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen() {
    val version by Lanes.version.collectAsState()
    val refreshing by Lanes.refreshing.collectAsState()
    // Redraw every 20 seconds so countdowns stay current while the screen is open
    var tick by remember { mutableLongStateOf(0L) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                tick++
                delay(20_000)
            }
        }
    }
    var snap by remember { mutableStateOf<Snapshot?>(null) }
    LaunchedEffect(version, refreshing, tick) {
        Lanes.load()
        snap = Lanes.snapshot()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("I-5 Express") },
                actions = {
                    if (refreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(2.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                    } else {
                        IconButton(onClick = { Lanes.refreshInBackground() }) {
                            Icon(painterResource(R.drawable.ic_refresh), contentDescription = "Refresh from WSDOT")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { Lanes.refreshInBackground() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            val s = snap
            if (s != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Header(s)
                    if (s.check.verdict == Assessment.Verdict.UNCERTAIN) Concerns(s.check.concerns)
                    Notices(s)
                    Timelines(s)
                    ComingUp(s)
                    Planned(s)
                    RegularSchedule(s)
                    SettingsSection()
                    SourcesSection(s)
                    Footer(s)
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

// Header

@Composable
private fun Header(s: Snapshot) {
    val dark = isSystemInDarkTheme()
    val density = LocalDensity.current
    val badgePx = with(density) { 64.dp.roundToPx() }
    val badge = remember(s.check.verdict, s.check.scheduled) { Drawing.icon(s.check, badgePx).asImageBitmap() }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Image(badge, contentDescription = null, modifier = Modifier.size(64.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(caption(s), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (s.check.verdict) {
                Assessment.Verdict.CONFIDENT -> {
                    Text(
                        s.check.scheduled.title,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = LaneColors.text(s.check.scheduled, dark),
                    )
                    val n = s.next
                    Text(
                        if (n == null) s.nextText else "${s.nextText} · in ${Format.countdown(n.at, s.now)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Assessment.Verdict.CHECKING -> {
                    Text("Checking…", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("Reading WSDOT's latest schedule", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Assessment.Verdict.UNCERTAIN -> {
                    Text("Can't confirm", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = LaneColors.warningText(dark))
                    Text(
                        "Posted schedule says ${s.check.scheduled.title.lowercase()}, but don't rely on it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun caption(s: Snapshot): String {
    val differs = ZoneId.systemDefault().rules.getOffset(s.now) != Seattle.zone.rules.getOffset(s.now)
    return if (differs) "I-5 EXPRESS LANES · SEATTLE TIME" else "I-5 EXPRESS LANES"
}

// Concerns

@Composable
private fun Concerns(concerns: List<Concern>) {
    val dark = isSystemInDarkTheme()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (c in concerns) {
            Callout(R.drawable.ic_warning, LaneColors.warningText(dark), c.title, c.detail, c.link)
        }
        Text("Check a live source before you drive:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            LinkButton("Traffic cameras", Sources.TRAVEL_MAP)
            LinkButton("@wsdot_traffic", Sources.WSDOT_TRAFFIC_X)
            LinkButton("WSDOT page", Sources.EXPRESS_LANES)
        }
    }
}

// Notices

@Composable
private fun Notices(s: Snapshot) {
    val dark = isSystemInDarkTheme()
    val tonight = s.earlyCloseTonight?.takeIf { Seattle.time(s.now) < it }
    val oldNotices = s.result.info.advisories.filter { it.isOverBefore(s.todayDay) }
    val closure = s.activeClosure
    if (closure == null && tonight == null && oldNotices.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (closure != null) {
            Callout(R.drawable.ic_block, LaneColors.text(LaneStatus.CLOSED, dark), closure.title, "Closed until ${Format.whenText(closure.end, s.now)}.")
        }
        if (tonight != null) {
            Callout(R.drawable.ic_moon, LaneColors.warningText(dark), "Closing early tonight", "The lanes close at ${tonight.label} for construction.")
        }
        for (a in oldNotices) {
            Callout(R.drawable.ic_history, MaterialTheme.colorScheme.onSurfaceVariant, "Old notice still on WSDOT's page", a.text, a.link)
        }
    }
}

// Timelines

@Composable
private fun Timelines(s: Snapshot) {
    val confident = s.check.verdict == Assessment.Verdict.CONFIDENT
    Section(if (confident) "Schedule" else "Posted schedule · not confirmed") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.semantics(mergeDescendants = true) {}) {
            TimelineRow("Today", null, s.today, Seattle.time(s.now), faded = !confident)
            TimelineRow("Tomorrow", s.tomorrowConcerns.firstOrNull()?.title, s.tomorrow, null, faded = !confident)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Legend(LaneColors.timeline(LaneStatus.SOUTHBOUND), "Southbound")
                Legend(LaneColors.timeline(LaneStatus.NORTHBOUND), "Northbound")
                Legend(LaneColors.timeline(LaneStatus.CLOSED), "Closed")
            }
        }
    }
}

@Composable
private fun TimelineRow(label: String, warning: String?, segments: List<Segment>, now: TimeOfDay?, faded: Boolean) {
    val dark = isSystemInDarkTheme()
    val marker = MaterialTheme.colorScheme.onSurface
    val description = segments.filter { it.status.isOpen }.joinToString { "${it.status.title} ${it.start.label} to ${it.end.label}" }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            if (warning != null) {
                Icon(painterResource(R.drawable.ic_warning), null, tint = LaneColors.warningText(dark), modifier = Modifier.size(14.dp))
                Text(warning, style = MaterialTheme.typography.labelMedium, color = LaneColors.warningText(dark), maxLines = 1)
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .semantics { contentDescription = "$label: ${description.ifEmpty { "closed all day" }}" },
        ) {
            val w = size.width
            val top = 3.dp.toPx()
            val h = size.height - 2 * top
            val alpha = if (faded) 0.45f else 1f
            fun x(t: TimeOfDay) = w * t.minutes / (24f * 60f)
            drawRoundRect(LaneColors.timeline(LaneStatus.CLOSED).copy(alpha = 0.35f * alpha), Offset(0f, top), Size(w, h), CornerRadius(5.dp.toPx()))
            for (seg in segments.filter { it.status.isOpen }) {
                val left = x(seg.start)
                val right = x(seg.end)
                drawRoundRect(
                    LaneColors.timeline(seg.status).copy(alpha = alpha),
                    Offset(left, top),
                    Size((right - left).coerceAtLeast(1f), h),
                    CornerRadius(if (left <= 0f || right >= w) 5.dp.toPx() else 0f),
                )
            }
            if (now != null) {
                val nx = x(now).coerceIn(2.dp.toPx(), w - 2.dp.toPx())
                drawRoundRect(marker, Offset(nx - 1.5.dp.toPx(), 0f), Size(3.dp.toPx(), size.height), CornerRadius(1.5.dp.toPx()))
            }
        }
        Row {
            for ((i, mark) in listOf("12a", "6a", "12p", "6p", "12a").withIndex()) {
                if (i > 0) Spacer(Modifier.weight(1f))
                Text(mark, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// Coming up

@Composable
private fun ComingUp(s: Snapshot) {
    val dark = isSystemInDarkTheme()
    Section("Coming up") {
        if (s.changes.isEmpty()) {
            Text("No changes in the next week", style = MaterialTheme.typography.bodyMedium)
        }
        for (c in s.changes) {
            val doubtful = Seattle.day(c.at) in s.doubtfulDays
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(LaneColors.badge(c.to)))
                Spacer(Modifier.width(10.dp))
                Text(c.to.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (doubtful) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        painterResource(R.drawable.ic_warning),
                        contentDescription = "This day may not follow the regular schedule",
                        tint = LaneColors.warningText(dark),
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(Format.whenText(c.at, s.now), style = MaterialTheme.typography.bodyMedium)
                Text(
                    Format.countdown(c.at, s.now),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(64.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
        }
        if (s.changes.any { Seattle.day(it.at) in s.doubtfulDays }) {
            Text("⚠ marks days that may not follow the regular schedule.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Planned changes

@Composable
private fun Planned(s: Snapshot) {
    val dark = isSystemInDarkTheme()
    val info = s.result.info
    val engine = s.engine
    val closures = engine.upcomingClosures(s.now)
    val early = engine.upcomingEarlyCloses(s.now)
    val unclear = info.events.filter { it.closure == null && it.days?.isOverBefore(s.todayDay) != true }
    val future = info.advisories.filter { a -> a.days?.let { s.todayDay.isBefore(it.first) } ?: false }
    val holidays = s.upcomingHolidays
    if (closures.isEmpty() && early.isEmpty() && unclear.isEmpty() && future.isEmpty() && holidays.isEmpty()) return

    Section("Planned changes") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (c in closures) {
                PlannedItem(R.drawable.ic_event, c.title) {
                    Text(
                        "Closed ${Format.shortDate(c.start)} ${Format.clock(c.start)} → ${Format.shortDate(c.end)} ${Format.clock(c.end)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            for (e in unclear) {
                PlannedItem(R.drawable.ic_help, e.title) {
                    Text("Exact times unclear. The app won't confirm a direction on these days.", style = MaterialTheme.typography.bodyMedium, color = LaneColors.warningText(dark))
                    if (e.detail.isNotEmpty()) Text(e.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            for (a in future) {
                PlannedItem(R.drawable.ic_info, "WSDOT notice") {
                    Text(a.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (early.isNotEmpty()) {
                PlannedItem(R.drawable.ic_moon, "Closing early") {
                    Text(early.joinToString("  ·  ") { earlyLabel(it) }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (holidays.isNotEmpty()) {
                PlannedItem(R.drawable.ic_star, "Holidays") {
                    Text(
                        holidays.joinToString("  ·  ") { "${it.second} (${Format.shortDate(it.first)})" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "WSDOT doesn't post holiday hours, so the app won't confirm a direction on these days.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun earlyLabel(e: EarlyClose) = "${Format.shortDate(e.day)} at ${e.time?.label ?: "unknown time"}"

@Composable
private fun PlannedItem(icon: Int, title: String, content: @Composable ColumnScope.() -> Unit) {
    Row {
        Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp).padding(top = 2.dp))
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            content()
        }
    }
}

// Regular schedule

@Composable
private fun RegularSchedule(s: Snapshot) {
    Section("Regular schedule") {
        ScheduleRow("Mon–Fri", s.result.info.schedule.weekday)
        Spacer(Modifier.height(6.dp))
        ScheduleRow("Sat–Sun", s.result.info.schedule.weekend)
    }
}

@Composable
private fun ScheduleRow(label: String, windows: List<LaneWindow>) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(72.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (w in windows) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(LaneColors.badge(w.status)))
                    Spacer(Modifier.width(8.dp))
                    Text("${w.status.title}  ${w.start.label} – ${w.end.label}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// Settings

@Composable
private fun SettingsSection() {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var notify by remember { mutableStateOf(prefs.notify) }
    var blocked by remember { mutableStateOf(false) }
    var widgets by remember { mutableStateOf(widgetCount(context)) }
    var exact by remember { mutableStateOf(Alarms.exactAllowed(context)) }

    // Settings can change while the app is in the background
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            blocked = prefs.notify && !Notifier.canPost(context)
            widgets = widgetCount(context)
            exact = Alarms.exactAllowed(context)
            while (true) {
                delay(2_000)
                widgets = widgetCount(context)
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        prefs.notify = granted
        notify = granted
        blocked = !granted
    }

    Section("Settings") {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
            toggleNotify(!notify, context, prefs, permission::launch) { notify = it; blocked = it && !Notifier.canPost(context) }
        }) {
            Icon(painterResource(R.drawable.ic_notifications), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Notify when lanes change", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Also when the app can't confirm them. Quiet for short gaps and changes missed while asleep.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = notify, onCheckedChange = {
                toggleNotify(it, context, prefs, permission::launch) { on -> notify = on; blocked = on && !Notifier.canPost(context) }
            })
        }
        if (blocked) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Notifications are blocked in Android settings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                TextButton(onClick = { openNotificationSettings(context) }) { Text("Open settings") }
            }
        }
        Spacer(Modifier.height(12.dp))
        UpdateSettings(context, prefs)
        Spacer(Modifier.height(12.dp))
        val manager = AppWidgetManager.getInstance(context)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_add), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Home screen widget", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (widgets > 0) "On your home screen. Resize it for more or less detail." else "Long press the home screen, tap Widgets, then find I-5 Express.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (manager.isRequestPinAppWidgetSupported) {
                val scope = rememberCoroutineScope()
                FilledTonalButton(
                    onClick = {
                        // Shows the live widget in the system's "Add to home screen" sheet
                        scope.launch { GlanceAppWidgetManager(context).requestPinGlanceAppWidget(LanesWidgetReceiver::class.java, preview = LanesWidget()) }
                    },
                    contentPadding = PaddingValues(horizontal = 14.dp),
                ) { Text(if (widgets > 0) "Add another" else "Add") }
            }
        }
        if (!exact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Exact alarms are off, so the widget may update up to 10 minutes after the lanes change.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData("package:${context.packageName}".toUri()))
                }) { Text("Allow") }
            }
        }
    }
}

@Composable
private fun UpdateSettings(context: Context, prefs: Prefs) {
    var wifi by remember { mutableIntStateOf(prefs.wifiMinutes) }
    var mobile by remember { mutableIntStateOf(prefs.mobileMinutes) }
    fun changed() {
        Work.schedulePeriodic(context)
        // Picks up the new setting for a check that's waiting for a network
        Work.refreshSoon(context)
    }
    IntervalRow(R.drawable.ic_wifi, "Check WSDOT on Wi-Fi every", Updates.WIFI_CHOICES, wifi) {
        prefs.wifiMinutes = it
        wifi = it
        changed()
    }
    Spacer(Modifier.height(12.dp))
    IntervalRow(R.drawable.ic_cellular, "Check WSDOT on mobile data every", Updates.MOBILE_CHOICES, mobile) {
        prefs.mobileMinutes = it
        mobile = it
        changed()
    }
    Text(
        if (mobile == 0) {
            "Only when you tap refresh. Away from Wi-Fi, the widget shows ⚠ once the data is 2 hours old."
        } else {
            "Up to about ${Updates.monthlyMegabytes(mobile)} MB a month. Opening the app checks at most this often too."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 36.dp, top = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntervalRow(@DrawableRes icon: Int, title: String, choices: List<Int>, selected: Int, onPick: (Int) -> Unit) {
    Row {
        Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(6.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                choices.forEachIndexed { i, minutes ->
                    SegmentedButton(
                        selected = minutes == selected,
                        onClick = { onPick(minutes) },
                        shape = SegmentedButtonDefaults.itemShape(i, choices.size),
                        icon = {},
                    ) { Text(Updates.label(minutes), maxLines = 1) }
                }
            }
        }
    }
}

private fun widgetCount(context: Context) =
    AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, LanesWidgetReceiver::class.java)).size

private fun toggleNotify(on: Boolean, context: Context, prefs: Prefs, ask: (String) -> Unit, done: (Boolean) -> Unit) {
    if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifier.permissionGranted(context)) {
        ask(Manifest.permission.POST_NOTIFICATIONS)
        return
    }
    prefs.notify = on
    done(on)
}

private fun openNotificationSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
}

// Sources

@Composable
private fun SourcesSection(s: Snapshot) {
    val info = s.result.info
    val extra = (info.events.mapNotNull { it.link } + info.advisories.mapNotNull { it.link })
        .filter { it != Sources.REVIVE_I5 && it != Sources.EXPRESS_LANES }
        .distinct()
    Section("Sources") {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SourceLink("WSDOT express lanes schedule", "Where this data comes from", Sources.EXPRESS_LANES)
            for (url in extra) {
                val host = runCatching { URI(url).host }.getOrNull() ?: ""
                SourceLink(if (host.contains("govdelivery")) "WSDOT bulletin" else "Construction project page", host, url)
            }
            SourceLink("Revive I-5", "Ship Canal Bridge work", Sources.REVIVE_I5)
            SourceLink("WSDOT real-time travel map", "Live traffic, alerts and cameras", Sources.TRAVEL_MAP)
            SourceLink("@wsdot_traffic", "Unplanned closures get posted here first", Sources.WSDOT_TRAFFIC_X)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "The app only shows N or S when it read WSDOT's page in the last 2 hours, the page made sense, " +
                "and nothing on it hints that today is different. Otherwise it shows ⚠. " +
                "It can't know about closures WSDOT hasn't posted yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SourceLink(title: String, subtitle: String, url: String) {
    val uri = LocalUriHandler.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { runCatching { uri.openUri(url) } }
            .padding(vertical = 6.dp),
    ) {
        Icon(painterResource(R.drawable.ic_open), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Footer

@Composable
private fun Footer(s: Snapshot) {
    val dark = isSystemInDarkTheme()
    HorizontalDivider()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val (icon, text, warn) = when (val src = s.result.source) {
            is DataSource.Live -> Triple(R.drawable.ic_check_circle, "Checked WSDOT at ${Format.whenText(src.at, s.now)}", false)
            is DataSource.Cached -> Triple(R.drawable.ic_warning, "Using copy saved ${Format.whenText(src.at, s.now)}", true)
            DataSource.BuiltIn -> Triple(R.drawable.ic_warning, "Built-in schedule only", true)
        }
        val color = if (warn) LaneColors.warningText(dark) else MaterialTheme.colorScheme.onSurfaceVariant
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = color)
        }
        s.result.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Not affiliated with WSDOT.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinkButton("Privacy policy", Sources.PRIVACY)
        }
    }
}

// Pieces

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        content()
    }
}

@Composable
private fun Callout(icon: Int, tint: Color, title: String, text: String, link: String? = null) {
    val uri = LocalUriHandler.current
    Surface(color = tint.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp)) {
            Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (link != null) {
                    Text(
                        "More info",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { runCatching { uri.openUri(link) } }.padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkButton(title: String, url: String) {
    val uri = LocalUriHandler.current
    TextButton(onClick = { runCatching { uri.openUri(url) } }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(title) }
}
