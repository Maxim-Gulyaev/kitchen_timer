package com.maxim.kitchentimer.timer

import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds

enum class TimerStatus {
    Idle,
    Running,
    Paused,
    Finished,
}

data class TimerPreset(
    val id: String,
    val label: String,
    val duration: Duration,
) {
    init {
        require(id.isNotBlank()) { "Preset id must not be blank" }
        require(label.isNotBlank()) { "Preset label must not be blank" }
        require(duration > ZERO) { "Preset duration must be positive" }
        require(duration.isWholeMilliseconds()) { "Preset duration must use whole milliseconds" }
    }
}

data class TimerState(
    val initialDuration: Duration = ZERO,
    val remainingDuration: Duration = initialDuration,
    val status: TimerStatus = TimerStatus.Idle,
    val selectedPresetId: String? = null,
    internal val deadlineMillis: Long? = null,
) {
    init {
        require(initialDuration >= ZERO) { "Initial duration must not be negative" }
        require(remainingDuration >= ZERO) { "Remaining duration must not be negative" }
        require(initialDuration.isWholeMilliseconds()) { "Initial duration must use whole milliseconds" }
        require(remainingDuration.isWholeMilliseconds()) { "Remaining duration must use whole milliseconds" }
        require(status == TimerStatus.Running || deadlineMillis == null) {
            "Only a running timer may have a deadline"
        }
        require(status != TimerStatus.Running || deadlineMillis != null) {
            "A running timer must have a deadline"
        }
    }

    val progress: Float
        get() = if (initialDuration == ZERO) {
            0f
        } else {
            (1.0 - remainingDuration / initialDuration).toFloat().coerceIn(0f, 1f)
        }
}

sealed interface TimerIntent {
    data class ChangeDuration(val duration: Duration) : TimerIntent
    data class SelectPreset(val preset: TimerPreset) : TimerIntent
    data object Start : TimerIntent
    data object Pause : TimerIntent
    data object Resume : TimerIntent
    data object Stop : TimerIntent
    data object Reset : TimerIntent
    data object Restart : TimerIntent
    data object Tick : TimerIntent
}

sealed interface TimerEvent {
    data object Completed : TimerEvent
}

data class TimerTransition(
    val state: TimerState,
    val event: TimerEvent? = null,
)

internal fun Duration.isWholeMilliseconds(): Boolean =
    isFinite() && inWholeMilliseconds.milliseconds == this
