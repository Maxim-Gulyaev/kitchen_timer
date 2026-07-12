package com.maxim.kitchentimer.timer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class TimerDurationFormatterTest {
    @Test
    fun formatsSubHourDurationAsMinutesAndSeconds() {
        assertEquals("00:00", formatTimerDuration(0.seconds))
        assertEquals("00:01", formatTimerDuration(1.milliseconds))
        assertEquals("01:00", formatTimerDuration(59_001.milliseconds))
        assertEquals("03:07", formatTimerDuration(3.minutes + 7.seconds))
        assertEquals("59:59", formatTimerDuration(59.minutes + 59.seconds))
    }

    @Test
    fun formatsHourDurationWithHours() {
        assertEquals("01:00:00", formatTimerDuration(1.hours))
        assertEquals("12:03:07", formatTimerDuration(12.hours + 3.minutes + 7.seconds))
    }
}
