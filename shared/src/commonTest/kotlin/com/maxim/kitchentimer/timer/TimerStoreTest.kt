package com.maxim.kitchentimer.timer

import com.maxim.kitchentimer.platform.MonotonicClock
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class TimerStoreTest {
    @Test
    fun startRejectsZeroAndStartsOneTickerForNonZeroDuration() = runTest {
        val harness = harness()

        harness.store.dispatch(TimerIntent.Start)
        runCurrent()
        assertEquals(TimerStatus.Idle, harness.store.state.value.status)
        assertEquals(0, harness.ticker.activeAwaiters)

        harness.store.dispatch(TimerIntent.ChangeDuration(1.minutes))
        harness.store.dispatch(TimerIntent.Start)
        harness.store.dispatch(TimerIntent.Start)
        runCurrent()

        assertEquals(TimerStatus.Running, harness.store.state.value.status)
        assertEquals(1, harness.ticker.activeAwaiters)
        harness.close()
    }

    @Test
    fun pauseResumePreservesTimeAndOwnsTickerLifecycle() = runTest {
        val harness = harness(TimerState(initialDuration = 1.minutes))
        harness.store.dispatch(TimerIntent.Start)
        runCurrent()

        harness.clock.advanceBy(20.seconds)
        harness.store.dispatch(TimerIntent.Pause)
        runCurrent()
        assertEquals(TimerStatus.Paused, harness.store.state.value.status)
        assertEquals(40.seconds, harness.store.state.value.remainingDuration)
        assertEquals(0, harness.ticker.activeAwaiters)

        harness.clock.advanceBy(1.minutes)
        harness.store.dispatch(TimerIntent.Resume)
        runCurrent()
        assertEquals(1, harness.ticker.activeAwaiters)

        harness.clock.advanceBy(10.seconds)
        harness.ticker.tick()
        runCurrent()
        assertEquals(30.seconds, harness.store.state.value.remainingDuration)
        harness.close()
    }

    @Test
    fun stopAndResetCancelTickerFromRunningAndPaused() = runTest {
        val harness = harness(TimerState(initialDuration = 1.minutes))

        harness.store.dispatch(TimerIntent.Start)
        runCurrent()
        harness.clock.advanceBy(10.seconds)
        harness.store.dispatch(TimerIntent.Stop)
        runCurrent()
        assertEquals(TimerStatus.Idle, harness.store.state.value.status)
        assertEquals(50.seconds, harness.store.state.value.remainingDuration)
        assertEquals(0, harness.ticker.activeAwaiters)

        harness.store.dispatch(TimerIntent.Reset)
        harness.store.dispatch(TimerIntent.Start)
        runCurrent()
        harness.clock.advanceBy(10.seconds)
        harness.store.dispatch(TimerIntent.Pause)
        harness.store.dispatch(TimerIntent.Stop)
        runCurrent()
        assertEquals(TimerStatus.Idle, harness.store.state.value.status)
        assertEquals(50.seconds, harness.store.state.value.remainingDuration)
        assertEquals(0, harness.ticker.activeAwaiters)

        harness.store.dispatch(TimerIntent.Reset)
        harness.store.dispatch(TimerIntent.Start)
        runCurrent()
        harness.clock.advanceBy(10.seconds)
        harness.store.dispatch(TimerIntent.Reset)
        runCurrent()
        assertEquals(TimerStatus.Idle, harness.store.state.value.status)
        assertEquals(1.minutes, harness.store.state.value.remainingDuration)
        assertEquals(0, harness.ticker.activeAwaiters)

        harness.store.dispatch(TimerIntent.Start)
        runCurrent()
        harness.clock.advanceBy(10.seconds)
        harness.store.dispatch(TimerIntent.Pause)
        harness.store.dispatch(TimerIntent.Reset)
        runCurrent()
        assertEquals(TimerStatus.Idle, harness.store.state.value.status)
        assertEquals(1.minutes, harness.store.state.value.remainingDuration)
        assertEquals(0, harness.ticker.activeAwaiters)
        harness.close()
    }

    @Test
    fun missedTicksReconcilePastZeroAndEmitCompletionOnce() = runTest {
        val harness = harness(TimerState(initialDuration = 5.seconds))
        val firstEvent = async { harness.store.events.first() }
        runCurrent()

        harness.store.dispatch(TimerIntent.Start)
        runCurrent()
        harness.clock.advanceBy(30.seconds)
        harness.ticker.tick()
        runCurrent()

        assertEquals(TimerStatus.Finished, harness.store.state.value.status)
        assertEquals(0.seconds, harness.store.state.value.remainingDuration)
        assertEquals(TimerEvent.Completed, firstEvent.await())
        assertEquals(0, harness.ticker.activeAwaiters)

        val unexpectedEvent = async { harness.store.events.first() }
        harness.ticker.tick()
        harness.store.dispatch(TimerIntent.Tick)
        runCurrent()
        assertFalse(unexpectedEvent.isCompleted)
        unexpectedEvent.cancel()
        harness.close()
    }

    @Test
    fun restartAfterCompletionStartsOriginalDurationAgain() = runTest {
        val harness = harness(TimerState(initialDuration = 5.seconds))
        harness.store.dispatch(TimerIntent.Start)
        runCurrent()
        harness.clock.advanceBy(5.seconds)
        harness.ticker.tick()
        runCurrent()

        harness.clock.advanceBy(10.seconds)
        harness.store.dispatch(TimerIntent.Restart)
        runCurrent()

        assertEquals(TimerStatus.Running, harness.store.state.value.status)
        assertEquals(5.seconds, harness.store.state.value.remainingDuration)
        assertEquals(1, harness.ticker.activeAwaiters)
        harness.close()
    }

    @Test
    fun presetChangesOnlyInAllowedStates() = runTest {
        val harness = harness()
        harness.store.dispatch(TimerIntent.SelectPreset(TimerPresets.ThreeMinutes))
        runCurrent()
        assertEquals(TimerPresets.ThreeMinutes.id, harness.store.state.value.selectedPresetId)

        harness.store.dispatch(TimerIntent.Start)
        harness.store.dispatch(TimerIntent.SelectPreset(TimerPresets.ThirtyMinutes))
        runCurrent()
        assertEquals(TimerPresets.ThreeMinutes.id, harness.store.state.value.selectedPresetId)

        harness.clock.advanceBy(3.minutes)
        harness.ticker.tick()
        runCurrent()
        harness.store.dispatch(TimerIntent.SelectPreset(TimerPresets.FiveMinutes))
        runCurrent()
        assertEquals(TimerStatus.Idle, harness.store.state.value.status)
        assertEquals(TimerPresets.FiveMinutes.id, harness.store.state.value.selectedPresetId)
        harness.close()
    }

    @Test
    fun closedStoreRejectsNewIntents() = runTest {
        val harness = harness(TimerState(initialDuration = 1.minutes))
        harness.store.dispatch(TimerIntent.Start)
        runCurrent()
        assertEquals(1, harness.ticker.activeAwaiters)

        harness.close()
        runCurrent()

        assertFalse(harness.store.dispatch(TimerIntent.Start))
        assertEquals(0, harness.ticker.activeAwaiters)
    }

    private fun TestScope.harness(initialState: TimerState = TimerState()): StoreHarness {
        val clock = FakeMonotonicClock()
        val ticker = FakeTimerTicker()
        return StoreHarness(
            store = TimerStore(clock, ticker, backgroundScope, initialState),
            clock = clock,
            ticker = ticker,
        )
    }
}

private data class StoreHarness(
    val store: TimerStore,
    val clock: FakeMonotonicClock,
    val ticker: FakeTimerTicker,
) {
    fun close() = store.close()
}

private class FakeMonotonicClock : MonotonicClock {
    private var currentMillis = 0L

    override fun nowMillis(): Long = currentMillis

    fun advanceBy(duration: kotlin.time.Duration) {
        currentMillis += duration.inWholeMilliseconds
    }
}

private class FakeTimerTicker : TimerTicker {
    private val ticks = Channel<Unit>(Channel.UNLIMITED)
    var activeAwaiters: Int = 0
        private set

    override suspend fun awaitTick() {
        activeAwaiters += 1
        try {
            ticks.receive()
        } finally {
            activeAwaiters -= 1
        }
    }

    fun tick() {
        assertTrue(ticks.trySend(Unit).isSuccess)
    }
}
