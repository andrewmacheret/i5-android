package com.andrewmacheret.i5

import androidx.compose.ui.graphics.Color
import com.andrewmacheret.i5.core.LaneStatus

/** One place for the status colors, so the widget and app always match. */
object LaneColors {
    /** Badge fills. White text sits on top, so these stay fairly dark. */
    fun badge(status: LaneStatus): Color = when (status) {
        LaneStatus.NORTHBOUND -> Color(0xFF1E8E3E)
        LaneStatus.SOUTHBOUND -> Color(0xFF1A73E8)
        LaneStatus.CLOSED -> Color(0xFFD93025)
        LaneStatus.UNKNOWN -> Color(0xFF80868B)
    }

    val warning = Color(0xFFF29900)

    /** Text in the status color, lighter on dark backgrounds so it stays readable */
    fun text(status: LaneStatus, dark: Boolean): Color = when (status) {
        LaneStatus.NORTHBOUND -> if (dark) Color(0xFF81C995) else Color(0xFF137333)
        LaneStatus.SOUTHBOUND -> if (dark) Color(0xFF8AB4F8) else Color(0xFF1967D2)
        LaneStatus.CLOSED -> if (dark) Color(0xFFF28B82) else Color(0xFFC5221F)
        LaneStatus.UNKNOWN -> if (dark) Color(0xFFBDC1C6) else Color(0xFF5F6368)
    }

    fun warningText(dark: Boolean) = if (dark) Color(0xFFFDD663) else Color(0xFFB06000)

    /** Closed reads better as a quiet gray on the timeline */
    fun timeline(status: LaneStatus): Color = if (status.isOpen) badge(status) else Color(0x5980868B)
}
