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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.andrewmacheret.i5.Alarms
import com.andrewmacheret.i5.CameraImages
import com.andrewmacheret.i5.Drawing
import com.andrewmacheret.i5.LaneColors
import com.andrewmacheret.i5.Lanes
import com.andrewmacheret.i5.Notifier
import com.andrewmacheret.i5.Prefs
import com.andrewmacheret.i5.R
import com.andrewmacheret.i5.Updates
import com.andrewmacheret.i5.Work
import com.andrewmacheret.i5.core.Assessment
import com.andrewmacheret.i5.core.Camera
import com.andrewmacheret.i5.core.Cameras
import com.andrewmacheret.i5.core.DaySegment
import com.andrewmacheret.i5.core.Format
import com.andrewmacheret.i5.core.LaneStatus
import com.andrewmacheret.i5.core.LaneWindow
import com.andrewmacheret.i5.core.Page
import com.andrewmacheret.i5.core.Seattle
import com.andrewmacheret.i5.core.Snapshot
import com.andrewmacheret.i5.core.Sources
import com.andrewmacheret.i5.core.TimeOfDay
import com.andrewmacheret.i5.widget.LanesWidget
import com.andrewmacheret.i5.widget.LanesWidgetReceiver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

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
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var camerasOpen by rememberSaveable { mutableStateOf(false) }
    var camerasY by remember { mutableIntStateOf(0) }
    val showCameras = {
        camerasOpen = true
        scope.launch { scroll.animateScrollTo(camerasY) }
        Unit
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
                            Icon(painterResource(R.drawable.ic_refresh), contentDescription = "Refresh")
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
                val loading = Lanes.loading(s)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll)
                        .padding(horizontal = 16.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Header(s, loading)
                    if (!loading && s.check.verdict == Assessment.Verdict.UNCERTAIN) Concerns(s, showCameras)
                    Notices(s)
                    Timelines(s)
                    TravelTimes(s)
                    if (s.status.cameras.isNotEmpty()) {
                        CamerasSection(
                            s.status.cameras,
                            camerasOpen,
                            onToggle = { camerasOpen = !camerasOpen },
                            modifier = Modifier.onGloballyPositioned { camerasY = it.positionInParent().y.toInt() },
                        )
                    }
                    ComingUp(s)
                    Planned(s)
                    RegularSchedule(s)
                    SourcesSection(s)
                    SettingsSection()
                    DataStatus(s, loading)
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

// Status

@Composable
private fun Header(s: Snapshot, loading: Boolean) {
    val dark = isSystemInDarkTheme()
    val density = LocalDensity.current
    val badgePx = with(density) { 64.dp.roundToPx() }
    val badge = remember(s.check.verdict, s.check.scheduled) { Drawing.icon(s.check, badgePx).asImageBitmap() }
    val h = Page.header(s, loading)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Image(badge, contentDescription = null, modifier = Modifier.size(64.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                if (Seattle.differsFromLocal(s.now)) "I-5 EXPRESS LANES · SEATTLE TIME" else "I-5 EXPRESS LANES",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                h.title,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = h.tone?.let { LaneColors.text(it, dark) } ?: LaneColors.warningText(dark),
            )
            Text(h.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Concerns(s: Snapshot, showCameras: () -> Unit) {
    val dark = isSystemInDarkTheme()
    val linkStyle = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (c in s.check.concerns) {
            Callout(R.drawable.ic_warning, LaneColors.warningText(dark), c.title, c.detail, c.link)
        }
        // Bluesky first, since unplanned closures get posted there first
        val text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(Page.LIVE_SOURCES) }
            append(" ")
            withLink(LinkAnnotation.Url(Sources.WSDOT_TRAFFIC, linkStyle)) { append("WSDOT Traffic on Bluesky") }
            append(" · ")
            val cameras = if (s.status.cameras.isNotEmpty()) {
                LinkAnnotation.Clickable("cameras", linkStyle) { showCameras() }
            } else {
                LinkAnnotation.Url(Sources.TRAVEL_MAP, linkStyle)
            }
            withLink(cameras) { append("Traffic cameras") }
            append(" · ")
            withLink(LinkAnnotation.Url(Sources.EXPRESS_LANES, linkStyle)) { append("WSDOT page") }
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
    }
}

// Notices

@Composable
private fun Notices(s: Snapshot) {
    val dark = isSystemInDarkTheme()
    val items = Page.notices(s)
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (item in items) {
            val (icon, tint) = when (item.tone) {
                Page.Tone.BAD -> R.drawable.ic_block to LaneColors.text(LaneStatus.CLOSED, dark)
                Page.Tone.WARN -> R.drawable.ic_warning to LaneColors.warningText(dark)
                Page.Tone.MUTED -> R.drawable.ic_history to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Callout(icon, tint, item.notice.title, item.text, item.notice.link)
        }
    }
}

// Schedule

@Composable
private fun Timelines(s: Snapshot) {
    val confident = s.check.verdict == Assessment.Verdict.CONFIDENT
    Section(Page.timelineTitle(s)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TimelineRow("Today", Page.todayWarning(s), s.todaySegments, Seattle.time(s.now), faded = !confident)
            TimelineRow("Tomorrow", Page.tomorrowWarning(s), s.tomorrowSegments, null, faded = !confident)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Legend(LaneColors.timeline(LaneStatus.SOUTHBOUND), "Southbound")
                Legend(LaneColors.timeline(LaneStatus.NORTHBOUND), "Northbound")
                Legend(LaneColors.timeline(LaneStatus.CLOSED), "Closed")
            }
        }
    }
}

@Composable
private fun TimelineRow(label: String, warning: String?, segments: List<DaySegment>, now: TimeOfDay?, faded: Boolean) {
    val dark = isSystemInDarkTheme()
    val marker = MaterialTheme.colorScheme.onSurface
    val stripes = LaneColors.badge(LaneStatus.UNKNOWN).copy(alpha = 0.5f)
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
                .clipToBounds()
                .semantics { contentDescription = Page.timelineSummary(label, segments) },
        ) {
            val w = size.width
            val top = 3.dp.toPx()
            val h = size.height - 2 * top
            val alpha = if (faded) 0.45f else 1f
            fun x(t: TimeOfDay) = w * t.minutes / (24f * 60f)
            drawRoundRect(LaneColors.timeline(LaneStatus.CLOSED).copy(alpha = 0.35f * alpha), Offset(0f, top), Size(w, h), CornerRadius(5.dp.toPx()))
            for (seg in segments) {
                val left = x(seg.start)
                val right = x(seg.end)
                if (seg.status == LaneStatus.UNKNOWN) {
                    // Stripes for times the server's timeline doesn't cover
                    var sx = left - h
                    while (sx < right) {
                        val a = Offset(maxOf(sx, left), top + h - (maxOf(sx, left) - sx))
                        val b = Offset(minOf(sx + h, right), top + h - (minOf(sx + h, right) - sx))
                        drawLine(stripes.copy(alpha = stripes.alpha * alpha), a, b, strokeWidth = 2.dp.toPx())
                        sx += 6.dp.toPx()
                    }
                    continue
                }
                if (!seg.status.isOpen) continue
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

// Travel times

@Composable
private fun TravelTimes(s: Snapshot) {
    val tt = Page.travelTimes(s) ?: return
    val dark = isSystemInDarkTheme()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Section("Travel times") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row {
                Spacer(Modifier.weight(1.2f))
                Text("Express lanes", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text("Regular lanes", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            }
            for (r in tt.rows) {
                Row {
                    Text(r.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1.2f))
                    Column(Modifier.weight(1f)) {
                        Text(r.express, style = MaterialTheme.typography.bodyMedium)
                        r.saves?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = LaneColors.text(LaneStatus.NORTHBOUND, dark)) }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(r.regular, style = MaterialTheme.typography.bodyMedium)
                        r.usual?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
                    }
                }
            }
            Text(tt.hint, style = MaterialTheme.typography.bodySmall, color = muted)
        }
    }
}

// Cameras

@Composable
private fun CamerasSection(cameras: List<Camera>, open: Boolean, onToggle: () -> Unit, modifier: Modifier) {
    var all by rememberSaveable { mutableStateOf(false) }
    Column(modifier) {
        Section("Cameras") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onToggle).padding(vertical = 6.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_expand),
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp).rotate(if (open) 0f else -90f),
                )
                Spacer(Modifier.width(8.dp))
                Text(Cameras.summary(cameras, open), style = MaterialTheme.typography.bodyMedium)
            }
            // Nothing loads from WSDOT until the section is opened
            if (open) {
                Spacer(Modifier.height(8.dp))
                CameraGrid(Cameras.shown(cameras, all))
                Cameras.toggleLabel(cameras, all)?.let { label ->
                    OutlinedButton(onClick = { all = !all }, contentPadding = PaddingValues(horizontal = 14.dp)) { Text(label) }
                }
                Text(Cameras.HINT, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CameraGrid(cameras: List<Camera>) {
    // The clock that picks each picture's version, so they refresh every 5 minutes while open
    var now by remember { mutableStateOf(Instant.now()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                now = Instant.now()
                val every = Cameras.REFRESH_EVERY.toMillis()
                delay(every - now.toEpochMilli() % every + 1_000)
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        for (row in cameras.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (c in row) CameraTile(c, now, Modifier.weight(1f))
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CameraTile(c: Camera, now: Instant, modifier: Modifier) {
    val uri = LocalUriHandler.current
    val url = Cameras.imageUrl(c, now)
    // Keyed on the camera, so the last picture stays up while the next one loads
    var picture by remember(c.image) { mutableStateOf(CameraImages.cached(url)) }
    LaunchedEffect(url) {
        // Try once more if WSDOT is slow, rather than stay blank until the next picture
        for (attempt in 1..2) {
            CameraImages.load(url)?.let {
                picture = it
                return@LaunchedEffect
            }
            delay(5_000)
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(335f / 244f)
                .clip(RoundedCornerShape(6.dp))
                .background(LaneColors.timeline(LaneStatus.CLOSED))
                .clickable { runCatching { uri.openUri(c.image) } },
        ) {
            picture?.let {
                Image(it.asImageBitmap(), contentDescription = "WSDOT camera at ${c.name}", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Text(c.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${Cameras.milepost(c)} · ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "map",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { runCatching { uri.openUri(Cameras.mapLink(c)) } },
            )
        }
    }
}

// Coming up

@Composable
private fun ComingUp(s: Snapshot) {
    val dark = isSystemInDarkTheme()
    val changes = Page.comingUp(s)
    Section("Coming up") {
        if (changes.isEmpty()) {
            Text("No changes coming up.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        for (c in changes) {
            val doubtful = Seattle.day(c.at) in s.doubtfulDays
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(LaneColors.badge(c.to)))
                Spacer(Modifier.width(10.dp))
                Text(c.to.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (doubtful) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        painterResource(R.drawable.ic_warning),
                        contentDescription = "May not go as planned",
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
                    textAlign = TextAlign.End,
                )
            }
        }
        if (changes.any { Seattle.day(it.at) in s.doubtfulDays }) {
            Text(Page.DOUBTFUL_HINT, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Planned changes

@Composable
private fun Planned(s: Snapshot) {
    val items = Page.planned(s)
    if (items.isEmpty()) return
    val dark = isSystemInDarkTheme()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val uri = LocalUriHandler.current
    Section("Planned changes") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            for (item in items) {
                var more by rememberSaveable(item.title, item.detailLabel) { mutableStateOf(false) }
                var words by rememberSaveable(item.title, item.detailLabel) { mutableStateOf(false) }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    val times = if (more) item.times else item.times.take(Page.MAX_TIMES)
                    for (time in times) Text("•  $time", style = MaterialTheme.typography.bodyMedium, color = muted)
                    if (!more && item.times.size > Page.MAX_TIMES) {
                        Text(
                            "…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { more = true }
                                .semantics { contentDescription = "Show ${item.times.size - Page.MAX_TIMES} more times" }
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                        )
                    }
                    item.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = LaneColors.warningText(dark)) }
                    val label = item.detailLabel
                    val detail = item.detail
                    if (label != null && !detail.isNullOrEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { words = !words }.padding(vertical = 4.dp),
                        ) {
                            Icon(painterResource(R.drawable.ic_expand), null, tint = muted, modifier = Modifier.size(18.dp).rotate(if (words) 0f else -90f))
                            Spacer(Modifier.width(4.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium, color = muted)
                        }
                        if (words) Text(detail, style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(start = 22.dp))
                    }
                    item.link?.let { link ->
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
}

// Regular schedule

@Composable
private fun RegularSchedule(s: Snapshot) {
    val (schedule, hint) = Page.regularSchedule(s)
    Section("Regular schedule") {
        hint?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
        }
        ScheduleRow("Mon–Fri", schedule.weekday)
        Spacer(Modifier.height(6.dp))
        ScheduleRow("Sat–Sun", schedule.weekend)
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

// Sources

@Composable
private fun SourcesSection(s: Snapshot) {
    Section("Sources") {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (src in Page.sources(s)) SourceLink(src.title, src.subtitle, src.url)
        }
        Spacer(Modifier.height(8.dp))
        Text(Page.ABOUT, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

// Settings

@Composable
private fun SettingsSection() {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var notify by remember { mutableStateOf(prefs.notify) }
    var blocked by remember { mutableStateOf(false) }
    var widgets by remember { mutableIntStateOf(widgetCount(context)) }
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
    IntervalRow(R.drawable.ic_wifi, "Check for updates on Wi-Fi every", Updates.WIFI_CHOICES, wifi) {
        prefs.wifiMinutes = it
        wifi = it
        changed()
    }
    Spacer(Modifier.height(12.dp))
    IntervalRow(R.drawable.ic_cellular, "Check for updates on mobile data every", Updates.MOBILE_CHOICES, mobile) {
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

// Data status

@Composable
private fun DataStatus(s: Snapshot, loading: Boolean) {
    val dark = isSystemInDarkTheme()
    val d = Page.dataStatus(s, loading)
    HorizontalDivider()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val color = if (d.warn) LaneColors.warningText(dark) else MaterialTheme.colorScheme.onSurfaceVariant
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(if (d.warn) R.drawable.ic_warning else R.drawable.ic_check_circle), null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(d.text, style = MaterialTheme.typography.bodySmall, color = color)
        }
        d.reading?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        d.live?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Page.loadErrorText(s)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2)
        }
        LinkButton("Privacy policy", Sources.PRIVACY)
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
