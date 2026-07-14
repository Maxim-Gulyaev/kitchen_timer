package com.maxim.kitchentimer.platform

import com.maxim.kitchentimer.timer.TimerIntent
import com.maxim.kitchentimer.timer.TimerState
import com.maxim.kitchentimer.timer.TimerStatus
import com.maxim.kitchentimer.timer.TimerStore
import com.maxim.kitchentimer.timer.TimerTicker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class TimerCoordinatorTest {
    @Test
    fun runningTransitionsScheduleAndCancelNotificationInOrder() = runTest {
        val harness = harness(TimerState(initialDuration = 1.minutes))

        harness.coordinator.dispatch(TimerIntent.Start)
        harness.coordinator.dispatch(TimerIntent.Start)
        harness.coordinator.dispatch(TimerIntent.Pause)
        runCurrent()

        assertEquals(listOf(1.minutes), harness.notifier.scheduledAfter)
        assertEquals(1, harness.notifier.cancelCount)
        assertEquals(TimerStatus.Paused, harness.coordinator.state.value.status)

        harness.coordinator.dispatch(TimerIntent.Resume)
        runCurrent()
        harness.clock.advanceBy(20.seconds)
        harness.coordinator.dispatch(TimerIntent.Pause)
        runCurrent()
        harness.coordinator.dispatch(TimerIntent.Resume)
        runCurrent()
        harness.coordinator.dispatch(TimerIntent.Reset)
        runCurrent()

        assertEquals(listOf(1.minutes, 1.minutes, 40.seconds), harness.notifier.scheduledAfter)
        assertEquals(3, harness.notifier.cancelCount)
        assertEquals(TimerStatus.Idle, harness.coordinator.state.value.status)
        harness.close()
    }

    @Test
    fun completionPlaysSoundAndHapticsExactlyOnce() = runTest {
        val harness = harness(TimerState(initialDuration = 5.seconds))
        harness.coordinator.dispatch(TimerIntent.Start)
        runCurrent()

        harness.clock.advanceBy(30.seconds)
        harness.ticker.tick()
        runCurrent()
        harness.coordinator.dispatch(TimerIntent.Tick)
        runCurrent()

        assertEquals(TimerStatus.Finished, harness.coordinator.state.value.status)
        assertEquals(1, harness.sound.playCount)
        assertEquals(1, harness.haptics.completionCount)
        assertEquals(0, harness.notifier.cancelCount)
        harness.close()
    }

    @Test
    fun dismissStopsSignalAndCleansUpNotification() = runTest {
        val harness = harness(TimerState(initialDuration = 5.seconds))
        harness.coordinator.dispatch(TimerIntent.Start)
        runCurrent()
        harness.clock.advanceBy(5.seconds)
        harness.ticker.tick()
        runCurrent()

        harness.coordinator.dispatch(TimerIntent.Stop)
        runCurrent()

        assertEquals(TimerStatus.Idle, harness.coordinator.state.value.status)
        assertEquals(1, harness.sound.stopCount)
        assertEquals(1, harness.notifier.cancelCount)
        harness.close()
    }

    @Test
    fun restartStopsOldSignalAndSchedulesNewCompletion() = runTest {
        val harness = harness(TimerState(initialDuration = 5.seconds))
        harness.coordinator.dispatch(TimerIntent.Start)
        runCurrent()
        harness.clock.advanceBy(5.seconds)
        harness.ticker.tick()
        runCurrent()

        harness.coordinator.dispatch(TimerIntent.Restart)
        runCurrent()

        assertEquals(TimerStatus.Running, harness.coordinator.state.value.status)
        assertEquals(1, harness.sound.stopCount)
        assertEquals(listOf(5.seconds, 5.seconds), harness.notifier.scheduledAfter)
        harness.close()
    }

    @Test
    fun unavailablePlatformServicesCannotBreakTimerState() = runTest {
        val clock = FakeClock()
        val ticker = ManualTicker()
        val store = TimerStore(clock, ticker, backgroundScope, TimerState(initialDuration = 1.seconds))
        val throwingServices = TimerPlatformServices(
            clock = clock,
            soundPlayer = object : TimerSoundPlayer {
                override fun playCompletion(): Nothing = error("Sound unavailable")
                override fun stop(): Nothing = error("Sound unavailable")
            },
            haptics = TimerHaptics { error("Haptics unavailable") },
            notifier = object : TimerNotifier {
                override fun scheduleCompletion(after: Duration): Nothing = error("Notifications unavailable")
                override fun cancelCompletion(): Nothing = error("Notifications unavailable")
            },
        )
        val coordinator = TimerCoordinator(store, throwingServices, backgroundScope)

        coordinator.dispatch(TimerIntent.Start)
        runCurrent()
        clock.advanceBy(1.seconds)
        ticker.tick()
        runCurrent()
        coordinator.dispatch(TimerIntent.Stop)
        runCurrent()

        assertEquals(TimerStatus.Idle, coordinator.state.value.status)
        coordinator.close()
        store.close()
    }

    @Test
    fun backgroundCompletionReliesOnScheduledNotification() = runTest {
        val harness = harness(
            initialState = TimerState(initialDuration = 5.seconds),
            isForeground = false,
        )
        harness.coordinator.dispatch(TimerIntent.Start)
        runCurrent()
        harness.clock.advanceBy(5.seconds)
        runCurrent()

        assertEquals(TimerStatus.Running, harness.coordinator.state.value.status)
        assertEquals(listOf(5.seconds), harness.notifier.scheduledAfter)
        assertEquals(0, harness.sound.playCount)
        assertEquals(0, harness.haptics.completionCount)
        harness.close()
    }

    @Test
    fun enteringBackgroundReschedulesNotificationFromDeadlineNotStaleUiState() = runTest {
        val harness = harness(TimerState(initialDuration = 1.minutes))
        harness.coordinator.dispatch(TimerIntent.Start)
        runCurrent()
        harness.clock.advanceBy(20.seconds)

        harness.lifecycle.setForeground(false)
        runCurrent()

        assertEquals(TimerStatus.Running, harness.coordinator.state.value.status)
        assertEquals(1.minutes, harness.coordinator.state.value.remainingDuration)
        assertEquals(listOf(1.minutes, 40.seconds), harness.notifier.scheduledAfter)
        harness.close()
    }

    @Test
    fun returningToForegroundImmediatelyReconcilesElapsedTime() = runTest {
        val harness = harness(
            initialState = TimerState(initialDuration = 5.seconds),
            isForeground = false,
        )
        harness.coordinator.dispatch(TimerIntent.Start)
        runCurrent()
        harness.clock.advanceBy(30.seconds)

        harness.lifecycle.setForeground(true)
        runCurrent()

        assertEquals(TimerStatus.Finished, harness.coordinator.state.value.status)
        assertEquals(1, harness.sound.playCount)
        assertEquals(1, harness.haptics.completionCount)
        assertEquals(1, harness.notifier.cancelCount)
        harness.close()
    }

    @Test
    fun elapsedDeadlineCompletesOnlyOnceAcrossRepeatedLifecycleTransitions() = runTest {
        val harness = harness(TimerState(initialDuration = 5.seconds))
        harness.coordinator.dispatch(TimerIntent.Start)
        runCurrent()
        harness.lifecycle.setForeground(false)
        runCurrent()
        harness.clock.advanceBy(30.seconds)

        harness.lifecycle.setForeground(true)
        runCurrent()
        harness.lifecycle.setForeground(false)
        harness.lifecycle.setForeground(true)
        harness.coordinator.dispatch(TimerIntent.Tick)
        runCurrent()

        assertEquals(TimerStatus.Finished, harness.coordinator.state.value.status)
        assertEquals(1, harness.sound.playCount)
        assertEquals(1, harness.haptics.completionCount)
        harness.close()
    }

    @Test
    fun lifecycleChangeBeforeCollectorsStartIsNotDropped() = runTest {
        val harness = harness(TimerState(initialDuration = 1.minutes))
        harness.coordinator.dispatch(TimerIntent.Start)
        harness.lifecycle.setForeground(false)

        runCurrent()

        assertEquals(TimerStatus.Running, harness.coordinator.state.value.status)
        assertEquals(listOf(1.minutes), harness.notifier.scheduledAfter)
        harness.close()
    }

    @Test
    fun coordinatorAttachedAfterStartStillSchedulesNotification() = runTest {
        val clock = FakeClock()
        val ticker = ManualTicker()
        val notifier = RecordingNotifier()
        val store = TimerStore(clock, ticker, backgroundScope, TimerState(initialDuration = 1.minutes))
        store.dispatch(TimerIntent.Start)
        runCurrent()

        val coordinator = TimerCoordinator(
            store = store,
            services = TimerPlatformServices(clock = clock, notifier = notifier),
            coroutineScope = backgroundScope,
        )
        runCurrent()

        assertEquals(listOf(1.minutes), notifier.scheduledAfter)
        coordinator.close()
        store.close()
    }

    @Test
    fun closeCleansUpEffectsAndIsIdempotent() = runTest {
        val harness = harness(TimerState(initialDuration = 1.minutes))
        harness.coordinator.dispatch(TimerIntent.Start)
        runCurrent()

        harness.coordinator.close()
        harness.coordinator.close()
        runCurrent()

        assertEquals(1, harness.sound.stopCount)
        assertEquals(1, harness.notifier.cancelCount)
        harness.store.close()
    }

    private fun TestScope.harness(
        initialState: TimerState,
        isForeground: Boolean = true,
    ): CoordinatorHarness {
        val clock = FakeClock()
        val ticker = ManualTicker()
        val sound = RecordingSoundPlayer()
        val haptics = RecordingHaptics()
        val notifier = RecordingNotifier()
        val lifecycle = FakeLifecycleObserver(isForeground)
        val store = TimerStore(clock, ticker, backgroundScope, initialState)
        val coordinator = TimerCoordinator(
            store = store,
            services = TimerPlatformServices(clock, sound, haptics, notifier, lifecycle),
            coroutineScope = backgroundScope,
        )
        return CoordinatorHarness(store, coordinator, clock, ticker, sound, haptics, notifier, lifecycle)
    }
}

private class FakeLifecycleObserver(isForeground: Boolean) : AppLifecycleObserver {
    private val foreground = MutableStateFlow(isForeground)
    override val isForeground: StateFlow<Boolean> = foreground.asStateFlow()

    fun setForeground(value: Boolean) {
        foreground.value = value
    }
}

private data class CoordinatorHarness(
    val store: TimerStore,
    val coordinator: TimerCoordinator,
    val clock: FakeClock,
    val ticker: ManualTicker,
    val sound: RecordingSoundPlayer,
    val haptics: RecordingHaptics,
    val notifier: RecordingNotifier,
    val lifecycle: FakeLifecycleObserver,
) {
    fun close() {
        coordinator.close()
        store.close()
    }
}

private class FakeClock : MonotonicClock {
    private var nowMillis = 0L

    override fun nowMillis(): Long = nowMillis

    fun advanceBy(duration: Duration) {
        nowMillis += duration.inWholeMilliseconds
    }
}

private class ManualTicker : TimerTicker {
    private val ticks = Channel<Unit>(Channel.UNLIMITED)

    override suspend fun awaitTick() {
        ticks.receive()
    }

    fun tick() {
        check(ticks.trySend(Unit).isSuccess)
    }
}

private class RecordingSoundPlayer : TimerSoundPlayer {
    var playCount = 0
    var stopCount = 0

    override fun playCompletion() {
        playCount += 1
    }

    override fun stop() {
        stopCount += 1
    }
}

private class RecordingHaptics : TimerHaptics {
    var completionCount = 0

    override fun performCompletion() {
        completionCount += 1
    }
}

private class RecordingNotifier : TimerNotifier {
    val scheduledAfter = mutableListOf<Duration>()
    var cancelCount = 0

    override fun scheduleCompletion(after: Duration) {
        scheduledAfter += after
    }

    override fun cancelCompletion() {
        cancelCount += 1
    }
}
