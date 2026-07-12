package com.maxim.kitchentimer.timer

import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

fun formatTimerDuration(duration: Duration): String {
    val totalMillis = duration.coerceAtLeast(ZERO).inWholeMilliseconds
    val totalSeconds = if (totalMillis == 0L) 0L else (totalMillis - 1L) / 1_000L + 1L
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        "${hours.padded()}:${minutes.padded()}:${seconds.padded()}"
    } else {
        "${minutes.padded()}:${seconds.padded()}"
    }
}

private fun Long.padded(): String = toString().padStart(2, '0')
