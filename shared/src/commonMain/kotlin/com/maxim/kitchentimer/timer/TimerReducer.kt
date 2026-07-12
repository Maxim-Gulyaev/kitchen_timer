package com.maxim.kitchentimer.timer

import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds

/**
 * Pure timer state machine. [nowMillis] must come from a monotonic time source.
 * Supplying time explicitly keeps the domain deterministic and platform-free.
 */
object TimerReducer {
    fun reduce(
        state: TimerState,
        intent: TimerIntent,
        nowMillis: Long,
    ): TimerTransition {
        require(nowMillis >= 0L) { "Monotonic time must not be negative" }
        return when (intent) {
        is TimerIntent.ChangeDuration -> changeDuration(state, intent.duration)
        is TimerIntent.SelectPreset -> selectPreset(state, intent.preset)
        TimerIntent.Start -> start(state, nowMillis)
        TimerIntent.Pause -> pause(state, nowMillis)
        TimerIntent.Resume -> resume(state, nowMillis)
        TimerIntent.Stop -> stop(state, nowMillis)
        TimerIntent.Reset -> reset(state)
        TimerIntent.Restart -> restart(state, nowMillis)
        TimerIntent.Tick -> reconcile(state, nowMillis)
        }
    }

    private fun changeDuration(state: TimerState, duration: Duration): TimerTransition {
        if (state.status !in setOf(TimerStatus.Idle, TimerStatus.Finished)) return state.unchanged()

        val safeDuration = duration.coerceAtLeast(ZERO).inWholeMilliseconds.milliseconds
        return TimerTransition(
            state.copy(
                initialDuration = safeDuration,
                remainingDuration = safeDuration,
                status = TimerStatus.Idle,
                selectedPresetId = null,
            ),
        )
    }

    private fun selectPreset(state: TimerState, preset: TimerPreset): TimerTransition {
        if (state.status !in setOf(TimerStatus.Idle, TimerStatus.Finished)) return state.unchanged()

        return TimerTransition(
            state.copy(
                initialDuration = preset.duration,
                remainingDuration = preset.duration,
                status = TimerStatus.Idle,
                selectedPresetId = preset.id,
            ),
        )
    }

    private fun start(state: TimerState, nowMillis: Long): TimerTransition {
        if (state.status != TimerStatus.Idle || state.remainingDuration <= ZERO) {
            return state.unchanged()
        }
        return running(state, state.remainingDuration, nowMillis)
    }

    private fun pause(state: TimerState, nowMillis: Long): TimerTransition {
        val reconciled = reconcile(state, nowMillis)
        if (reconciled.event != null || reconciled.state.status != TimerStatus.Running) {
            return reconciled
        }
        return TimerTransition(
            reconciled.state.copy(
                status = TimerStatus.Paused,
                deadlineMillis = null,
            ),
        )
    }

    private fun resume(state: TimerState, nowMillis: Long): TimerTransition {
        if (state.status != TimerStatus.Paused || state.remainingDuration <= ZERO) {
            return state.unchanged()
        }
        return running(state, state.remainingDuration, nowMillis)
    }

    private fun stop(state: TimerState, nowMillis: Long): TimerTransition {
        val reconciled = reconcile(state, nowMillis)
        if (reconciled.event != null) return reconciled
        if (reconciled.state.status == TimerStatus.Finished) {
            return reset(reconciled.state)
        }
        if (reconciled.state.status !in setOf(TimerStatus.Running, TimerStatus.Paused)) {
            return reconciled.state.unchanged()
        }
        return TimerTransition(
            reconciled.state.copy(
                status = TimerStatus.Idle,
                deadlineMillis = null,
            ),
        )
    }

    private fun reset(state: TimerState): TimerTransition = TimerTransition(
        state.copy(
            remainingDuration = state.initialDuration,
            status = TimerStatus.Idle,
            deadlineMillis = null,
        ),
    )

    private fun restart(state: TimerState, nowMillis: Long): TimerTransition {
        if (state.status != TimerStatus.Finished || state.initialDuration <= ZERO) {
            return state.unchanged()
        }
        return running(state, state.initialDuration, nowMillis)
    }

    private fun reconcile(state: TimerState, nowMillis: Long): TimerTransition {
        if (state.status != TimerStatus.Running) return state.unchanged()

        val deadline = requireNotNull(state.deadlineMillis)
        val remainingMillis = (deadline - nowMillis).coerceAtLeast(0L)
        if (remainingMillis == 0L) {
            return TimerTransition(
                state.copy(
                    remainingDuration = ZERO,
                    status = TimerStatus.Finished,
                    deadlineMillis = null,
                ),
                TimerEvent.Completed,
            )
        }
        return TimerTransition(state.copy(remainingDuration = remainingMillis.milliseconds))
    }

    private fun running(
        state: TimerState,
        duration: Duration,
        nowMillis: Long,
    ): TimerTransition {
        val durationMillis = duration.inWholeMilliseconds.coerceAtLeast(1L)
        val deadline = if (Long.MAX_VALUE - durationMillis < nowMillis) {
            Long.MAX_VALUE
        } else {
            nowMillis + durationMillis
        }
        return TimerTransition(
            state.copy(
                remainingDuration = duration,
                status = TimerStatus.Running,
                deadlineMillis = deadline,
            ),
        )
    }

    private fun TimerState.unchanged() = TimerTransition(this)
}
