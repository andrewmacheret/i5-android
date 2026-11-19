package com.andrewmacheret.i5.core

import java.time.Instant
import java.time.LocalDate

/** A moment in Seattle time. */
fun seattle(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0): Instant = Seattle.instant(LocalDate.of(y, mo, d), TimeOfDay.of(h, mi))

fun day(y: Int, m: Int, d: Int): LocalDate = LocalDate.of(y, m, d)

fun t(h: Int, m: Int = 0) = TimeOfDay.of(h, m)

fun fixture(): String =
    requireNotNull(object {}.javaClass.getResource("/express-lanes-2026-11-18.html")) { "missing fixture" }.readText()

const val DEFAULT_SCHEDULE = """
<h3>Monday-Friday</h3><ul><li>Southbound - 5 a.m. to 11 a.m.</li><li>Northbound - 11:15 a.m. to 11 p.m.</li><li>Closed: 11 p.m. - 5 a.m.</li></ul>
<h3>Saturday &amp; Sunday</h3><ul><li>Southbound - 8 a.m. to 1:30 p.m.</li><li>Northbound - 1:45 to 11 p.m.</li></ul>
"""

/** Wraps snippets in the page's real outline so tests can tweak one part. */
fun page(schedule: String = DEFAULT_SCHEDULE, special: String = "") = """
    <h2>Main navigation</h2>
    <h2>I-5 express lanes schedule</h2>$schedule
    <h2>Special events schedule</h2>$special
    <h2>I-5 express lanes map</h2><h2>Footer</h2><p>Copyright Nov. 1</p>
"""
