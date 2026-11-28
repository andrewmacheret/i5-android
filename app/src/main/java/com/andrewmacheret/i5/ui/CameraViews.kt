package com.andrewmacheret.i5.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.andrewmacheret.i5.CameraImages
import com.andrewmacheret.i5.LaneColors
import com.andrewmacheret.i5.R
import com.andrewmacheret.i5.core.Camera
import com.andrewmacheret.i5.core.Cameras
import com.andrewmacheret.i5.core.LaneStatus
import kotlinx.coroutines.delay
import kotlin.math.max

// WSDOT's pictures are 335 x 244
private const val RATIO = 335f / 244f

/** One camera's picture, shared by its tile and the full-screen view */
@Stable
private class CameraPicture(initial: Bitmap?) {
    var bitmap by mutableStateOf(initial)
    /** Every try failed, so the placeholder shows */
    var failed by mutableStateOf(false)
    var loading by mutableStateOf(false)
    /** Goes up with each retry and reload, so the address is new and skips every cache */
    var reload = 0
    var restarts by mutableIntStateOf(0)

    /** Loads it again now, with all its retries */
    fun reloadNow() {
        reload++
        restarts++
    }
}

/** Loads the picture for this version, retrying a few times, then gives up until the next version or a reload */
@Composable
private fun rememberCameraPicture(c: Camera, version: Long): CameraPicture {
    val p = remember(c.image) { CameraPicture(CameraImages.cached(Cameras.imageUrl(c, version))) }
    LaunchedEffect(c.image, version, p.restarts) {
        p.failed = false
        var failures = 0
        try {
            while (true) {
                p.loading = true
                val got = CameraImages.load(Cameras.imageUrl(c, version, p.reload))
                p.loading = false
                if (got != null) {
                    p.bitmap = got
                    return@LaunchedEffect
                }
                val wait = Cameras.retryDelay(++failures) ?: break
                delay(wait.toMillis())
                p.reload++
            }
            p.failed = true
        } finally {
            p.loading = false
        }
    }
    return p
}

@Composable
fun CameraTile(c: Camera, version: Long, modifier: Modifier) {
    val picture = rememberCameraPicture(c, version)
    var full by rememberSaveable(c.id) { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(RATIO)
                .clip(RoundedCornerShape(6.dp))
                .background(LaneColors.timeline(LaneStatus.CLOSED))
                .clickable(onClickLabel = if (picture.failed) "reload" else "view full screen") {
                    if (picture.failed) picture.reloadNow() else full = true
                },
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = picture.bitmap
            when {
                picture.failed -> Text(Cameras.FAILED, style = MaterialTheme.typography.bodySmall, color = muted)
                bitmap != null -> Image(
                    bitmap.asImageBitmap(),
                    contentDescription = "WSDOT camera at ${c.name}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(c.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        CameraMeta(c, picture, MaterialTheme.typography.bodySmall, muted, MaterialTheme.colorScheme.primary)
    }
    if (full) CameraDialog(c, picture) { full = false }
}

/** "MP 168 · map", with the small reload button at the end if there's a picture to reload */
@Composable
private fun CameraMeta(c: Camera, picture: CameraPicture?, style: TextStyle, color: Color, link: Color) {
    val uri = LocalUriHandler.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("${Cameras.milepost(c)} · ", style = style, color = color)
        Text("map", style = style, color = link, modifier = Modifier.clickable { runCatching { uri.openUri(Cameras.mapLink(c)) } })
        if (picture != null) {
            Spacer(Modifier.weight(1f))
            ReloadButton(c, picture, color, small = true)
        }
    }
}

@Composable
private fun ReloadButton(c: Camera, picture: CameraPicture, tint: Color, small: Boolean) {
    val icon = if (small) 18.dp else 24.dp
    IconButton(
        onClick = picture::reloadNow,
        modifier = Modifier
            .then(
                if (small) {
                    // Still a 48dp touch target, but only takes up a line of text
                    Modifier.layout { measurable, constraints ->
                        val p = measurable.measure(constraints)
                        val w = 24.dp.roundToPx()
                        val h = 18.dp.roundToPx()
                        layout(w, h) { p.place((w - p.width) / 2, (h - p.height) / 2) }
                    }
                } else {
                    Modifier
                },
            )
            .semantics { contentDescription = Cameras.reloadLabel(c) },
    ) {
        if (picture.loading) {
            CircularProgressIndicator(Modifier.size(icon - 4.dp), color = tint, strokeWidth = if (small) 1.5.dp else 2.dp)
        } else {
            Icon(painterResource(R.drawable.ic_refresh), null, tint = tint, modifier = Modifier.size(icon))
        }
    }
}

/** The picture as big as it fits, on a dark background. Back, the close button or a tap outside the picture closes it. */
@Composable
private fun CameraDialog(c: Camera, picture: CameraPicture, onClose: () -> Unit) {
    val uri = LocalUriHandler.current
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // Light status bar icons on the dark background
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = false }
        }
        val light = Color.White
        val muted = Color(0xFFBDC1C6)
        val link = Color(0xFF8AB4F8)
        val zoom = remember { Zoom() }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) { detectTapGestures { if (!zoom.covers(it)) onClose() } },
        ) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(c.name, style = MaterialTheme.typography.titleMedium, color = light)
                        CameraMeta(c, null, MaterialTheme.typography.bodyMedium, muted, link)
                    }
                    Spacer(Modifier.width(8.dp))
                    ReloadButton(c, picture, light, small = false)
                    IconButton(onClick = onClose) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = "Close", tint = light)
                    }
                }
                val bitmap = picture.bitmap
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when {
                        picture.failed -> Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(RATIO)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF303134))
                                .clickable(onClickLabel = "reload") { picture.reloadNow() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(Cameras.FAILED, style = MaterialTheme.typography.bodyMedium, color = muted)
                        }
                        bitmap != null -> ZoomablePicture(bitmap, "WSDOT camera at ${c.name}", zoom, onClose, Modifier.fillMaxSize())
                        else -> CircularProgressIndicator(color = muted)
                    }
                }
                TextButton(onClick = { runCatching { uri.openUri(c.image) } }, modifier = Modifier.align(Alignment.End)) {
                    Text(Cameras.OPEN_IN_BROWSER, color = link)
                }
            }
        }
    }
}

/** How far the picture is zoomed and moved, and where it ends up */
@Stable
private class Zoom {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    var image = IntSize.Zero
    var area = IntSize.Zero
    /** The area's top left on the dialog */
    var areaAt = Offset.Zero

    private fun center() = Offset(area.width / 2f, area.height / 2f)

    /** Keeps the point under `at` still while zooming */
    fun zoomTo(next: Float, at: Offset, pan: Offset = Offset.Zero) {
        val moved = at - center() - (at - center() - offset) * (next / scale) + pan
        val maxX = max(0f, (image.width * next - area.width) / 2f)
        val maxY = max(0f, (image.height * next - area.height) / 2f)
        scale = next
        offset = Offset(moved.x.coerceIn(-maxX, maxX), moved.y.coerceIn(-maxY, maxY))
    }

    /** Whether a point on the dialog is on the picture */
    fun covers(onDialog: Offset): Boolean {
        val at = onDialog - areaAt
        val half = Offset(image.width * scale / 2f, image.height * scale / 2f)
        val mid = center() + offset
        return at.x in (mid.x - half.x)..(mid.x + half.x) && at.y in (mid.y - half.y)..(mid.y + half.y)
    }
}

/** Pinch to zoom and drag to pan. A double tap zooms in or back out. */
@Composable
private fun ZoomablePicture(bitmap: Bitmap, description: String, zoom: Zoom, onTapOutside: () -> Unit, modifier: Modifier) {
    Box(
        modifier
            .clipToBounds()
            .onGloballyPositioned {
                zoom.area = it.size
                zoom.areaAt = it.positionInRoot()
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, change, _ -> zoom.zoomTo((zoom.scale * change).coerceIn(1f, 5f), centroid, pan) }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { at -> zoom.zoomTo(if (zoom.scale > 1f) 1f else 2.5f, at) },
                    onTap = { at -> if (!zoom.covers(at + zoom.areaAt)) onTapOutside() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap.asImageBitmap(),
            contentDescription = description,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .aspectRatio(bitmap.width.toFloat() / bitmap.height)
                .onSizeChanged { zoom.image = it }
                .graphicsLayer {
                    scaleX = zoom.scale
                    scaleY = zoom.scale
                    translationX = zoom.offset.x
                    translationY = zoom.offset.y
                },
        )
    }
}
