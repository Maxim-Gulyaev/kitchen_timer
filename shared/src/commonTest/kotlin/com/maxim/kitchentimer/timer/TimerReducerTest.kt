package com.maxim.kitchentimer.timer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

class TimerReducerTest {
    @Test
    fun zeroDurationDoesNotStart() {
        val initial = TimerState()

        val result = initial.dispatch(TimerIntent.Start)

        assertSame(initial, result.state)
        assertNull(result.event)
    }

    @Test
    fun runningTimerUsesDeadlineInsteadOfTickCount() {
        val running = TimerState(initialDuration = 1.minutes)
            .dispatch(TimerIntent.Start, nowMillis = 1_000L).state

        val result = running.dispatch(TimerIntent.Tick, nowMillis = 51_000L)

        assertEquals(10.seconds, result.state.remainingDuration)
        assertEquals(TimerStatus.Running, result.state.status)
    }

    @Test
    fun pauseCapturesActualRemainingTimeAndResumeBuildsNewDeadline() {
        val running = TimerState(initialDuration = 1.minutes)
            .dispatch(TimerIntent.Start, nowMillis = 1_000L).state
        val paused = running.dispatch(TimerIntent.Pause, nowMillis = 21_000L).state

        assertEquals(40.seconds, paused.remainingDuration)
        assertEquals(TimerStatus.Paused, paused.status)

        val resumed = paused.dispatch(TimerIntent.Resume, nowMillis = 100_000L).state
        val afterTenSeconds = resumed.dispatch(TimerIntent.Tick, nowMillis = 110_000L).state

        assertEquals(30.seconds, afterTenSeconds.remainingDuration)
    }

    @Test
    fun completionIsClampedAndEmittedOnlyOnce() {
        val running = TimerState(initialDuration = 5.seconds)
            .dispatch(TimerIntent.Start, nowMillis = 10_000L).state

        val completed = running.dispatch(TimerIntent.Tick, nowMillis = 20_000L)
        val repeated = completed.state.dispatch(TimerIntent.Tick, nowMillis = 30_000L)

        assertEquals(TimerStatus.Finished, completed.state.status)
        assertEquals(0.seconds, completed.state.remainingDuration)
        assertEquals(TimerEvent.Completed, completed.event)
        assertNull(repeated.event)
    }

    @Test
    fun resetRestoresInitialDuration() {
        val paused = TimerState(initialDuration = 1.minutes)
            .dispatch(TimerIntent.Start, nowMillis = 0L).state
            .dispatch(TimerIntent.Pause, nowMillis = 20_000L).state

        val reset = paused.dispatch(TimerIntent.Reset).state

        assertEquals(TimerStatus.Idle, reset.status)
        assertEquals(1.minutes, reset.remainingDuration)
    }

    @Test
    fun restartRunsFinishedTimerForOriginalDuration() {
        val finished = TimerState(initialDuration = 5.seconds)
            .dispatch(TimerIntent.Start, nowMillis = 0L).state
            .dispatch(TimerIntent.Tick, nowMillis = 5_000L).state

        val restarted = finished.dispatch(TimerIntent.Restart, nowMillis = 10_000L).state
        val ticked = restarted.dispatch(TimerIntent.Tick, nowMillis = 11_000L).state

        assertEquals(TimerStatus.Running, restarted.status)
        assertEquals(4.seconds, ticked.remainingDuration)
    }

    @Test
    fun presetCannotChangeOutsideIdle() {
        val running = TimerState(initialDuration = 1.minutes)
            .dispatch(TimerIntent.Start).state

        val result = running.dispatch(TimerIntent.SelectPreset(TimerPresets.ThirtyMinutes))

        assertSame(running, result.state)
    }

    @Test
    fun repeatedStartDoesNotReplaceActiveDeadline() {
        val running = TimerState(initialDuration = 1.minutes)
            .dispatch(TimerIntent.Start, nowMillis = 1_000L).state

        val repeated = running.dispatch(TimerIntent.Start, nowMillis = 30_000L).state
        val ticked = repeated.dispatch(TimerIntent.Tick, nowMillis = 31_000L).state

        assertSame(running, repeated)
        assertEquals(30.seconds, ticked.remainingDuration)
    }

    @Test
    fun stopDismissesFinishedTimer() {
        val finished = TimerState(initialDuration = 5.seconds)
            .dispatch(TimerIntent.Start).state
            .dispatch(TimerIntent.Tick, nowMillis = 5_000L).state

        val dismissed = finished.dispatch(TimerIntent.Stop).state

        assertEquals(TimerStatus.Idle, dismissed.status)
        assertEquals(5.seconds, dismissed.remainingDuration)
    }

    @Test
    fun finishedTimerAcceptsNewPreset() {
        val finished = TimerState(initialDuration = 5.seconds)
            .dispatch(TimerIntent.Start).state
            .dispatch(TimerIntent.Tick, nowMillis = 5_000L).state

        val selected = finished.dispatch(TimerIntent.SelectPreset(TimerPresets.ThreeMinutes)).state

        assertEquals(TimerStatus.Idle, selected.status)
        assertEquals(3.minutes, selected.remainingDuration)
        assertEquals(TimerPresets.ThreeMinutes.id, selected.selectedPresetId)
    }

    @Test
    fun domainStateRejectsSubMillisecondPrecision() {
        assertFailsWith<IllegalArgumentException> {
            TimerState(initialDuration = 500_000.nanoseconds)
        }
    }

    private fun TimerState.dispatch(
        intent: TimerIntent,
        nowMillis: Long = 0L,
    ): TimerTransition = TimerReducer.reduce(this, intent, nowMillis)
}
