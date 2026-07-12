package com.maxim.kitchentimer.timer

import kotlin.time.Duration.Companion.minutes

object TimerPresets {
    val OneMinute = TimerPreset("1m", "1 min", 1.minutes)
    val ThreeMinutes = TimerPreset("3m", "3 min", 3.minutes)
    val FiveMinutes = TimerPreset("5m", "5 min", 5.minutes)
    val TenMinutes = TimerPreset("10m", "10 min", 10.minutes)
    val FifteenMinutes = TimerPreset("15m", "15 min", 15.minutes)
    val ThirtyMinutes = TimerPreset("30m", "30 min", 30.minutes)

    val all: List<TimerPreset> = listOf(
        OneMinute,
        ThreeMinutes,
        FiveMinutes,
        TenMinutes,
        FifteenMinutes,
        ThirtyMinutes,
    )
}
