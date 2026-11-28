package com.andrewmacheret.i5.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant

/** WSDOT's traffic cameras. Pictures come straight from WSDOT's image server, and only once the section is opened. */
object Cameras {
    /** WSDOT updates each picture about this often */
    val REFRESH_EVERY: Duration = Duration.ofMinutes(5)

    /** WSDOT's travel map zoomed in on one camera, with cameras on. Its own camera links don't work. */
    fun mapLink(c: Camera): String {
        val extent = listOf(c.lon - 0.006, c.lat - 0.004, c.lon + 0.006, c.lat + 0.004).joinToString("%2C") { fixed5(it) }
        return "${Sources.MAP}?layers=traffic-flow%2Calert%2Ccamera&base=wsdot&extent=$extent&fromLink=true"
    }

    // Like JavaScript's toFixed(5), which rounds the exact binary value half away from zero
    private fun fixed5(n: Double) = BigDecimal(n).setScale(5, RoundingMode.HALF_UP).toPlainString()

    /** The picture's address, which changes every 5 minutes so caches fetch a new one */
    fun imageUrl(c: Camera, now: Instant): String = "${c.image}?v=${Math.floorDiv(now.toEpochMilli(), REFRESH_EVERY.toMillis())}"

    fun express(cameras: List<Camera>) = cameras.filter { it.express }

    /** Express lane cameras first. All of them when asked, or when none are express. */
    fun shown(cameras: List<Camera>, all: Boolean): List<Camera> {
        val express = express(cameras)
        return if (all || express.isEmpty()) cameras else express
    }

    /** "Show 2 express lane cameras, downtown to Northgate" */
    fun summary(cameras: List<Camera>, open: Boolean): String {
        val express = express(cameras)
        val what = if (express.isNotEmpty()) "${express.size} express lane cameras" else "${cameras.size} cameras"
        return "${if (open) "Hide" else "Show"} $what, downtown to Northgate"
    }

    /** The button under the pictures, or null when there's nothing more to show */
    fun toggleLabel(cameras: List<Camera>, all: Boolean): String? {
        val express = express(cameras)
        if (express.isEmpty() || express.size >= cameras.size) return null
        return if (all) "Only express lane cameras" else "Show all ${cameras.size} cameras along I-5"
    }

    /** "MP 168" */
    fun milepost(c: Camera) = "MP ${Format.number(c.milepost)}"

    const val HINT = "From WSDOT. Each picture updates about every 5 minutes."
}
