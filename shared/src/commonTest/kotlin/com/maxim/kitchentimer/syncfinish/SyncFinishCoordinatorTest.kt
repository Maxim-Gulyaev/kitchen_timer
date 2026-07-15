package com.maxim.kitchentimer.syncfinish

import com.maxim.kitchentimer.platform.AppLifecycleObserver
import com.maxim.kitchentimer.platform.CookingPlanNotifier
import com.maxim.kitchentimer.platform.MonotonicClock
import com.maxim.kitchentimer.platform.ScheduledCookingCue
import com.maxim.kitchentimer.platform.TimerHaptics
import com.maxim.kitchentimer.platform.TimerPlatformServices
import com.maxim.kitchentimer.platform.TimerSoundPlayer
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

@OptIn(ExperimentalCoroutinesApi::class)
class SyncFinishCoordinatorTest {
    @Test
    fun backgroundSchedulesOnlyRemainingCuesFromActualElapsedTime() = runTest {
        val harness = harness(isForeground = true)
        harness.coordinator.dispatch(SyncFinishIntent.Start)
        runCurrent()

        harness.clock.advanceBy(2.minutes)
        harness.lifecycle.setForeground(false)
        runCurrent()

        assertEquals(1, harness.notifier.scheduledPlans.size)
        assertEquals(
            listOf(3.minutes, 18.minutes),
            harness.notifier.scheduledPlans.single().map(ScheduledCookingCue::delay),
        )
        harness.close()
    }

    @Test
    fun returningAfterDeadlineReconcilesOnceAndSignalsServe() = runTest {
        val harness = harness(isForeground = false)
        harness.coordinator.dispatch(SyncFinishIntent.Start)
        runCurrent()
        harness.clock.advanceBy(30.minutes)

        harness.lifecycle.setForeground(true)
        runCurrent()
        harness.coordinator.dispatch(SyncFinishIntent.Tick)
        runCurrent()

        assertEquals(SyncFinishStatus.Finished, harness.coordinator.state.value.status)
        assertEquals(1, harness.sound.playCount)
        assertEquals(1, harness.haptics.count)
        assertEquals(2, harness.notifier.cancelCount)
        harness.close()
    }

    @Test
    fun cancellingPlanCancelsNotificationsAndReturnsToDraft() = runTest {
        val harness = harness(isForeground = false)
        harness.coordinator.dispatch(SyncFinishIntent.Start)
        runCurrent()

        harness.coordinator.dispatch(SyncFinishIntent.Cancel)
        runCurrent()

        assertEquals(SyncFinishStatus.Draft, harness.coordinator.state.value.status)
        assertEquals(1, harness.notifier.cancelCount)
        harness.close()
    }

    private fun TestScope.harness(isForeground: Boolean): Harness {
        val clock = FakeClock()
        val ticker = ManualTicker()
        val lifecycle = FakeLifecycle(isForeground)
        val notifier = FakeCookingPlanNotifier()
        val sound = FakeSoundPlayer()
        val haptics = FakeHaptics()
        val services = TimerPlatformServices(
            clock = clock,
            cookingPlanNotifier = notifier,
            soundPlayer = sound,
            haptics = haptics,
            lifecycle = lifecycle,
        )
        val store = SyncFinishStore(clock, ticker, backgroundScope)
        return Harness(
            store = store,
            coordinator = SyncFinishCoordinator(store, services, backgroundScope),
            clock = clock,
            ticker = ticker,
            lifecycle = lifecycle,
            notifier = notifier,
            sound = sound,
            haptics = haptics,
        )
    }
}

private data class Harness(
    val store: SyncFinishStore,
    val coordinator: SyncFinishCoordinator,
    val clock: FakeClock,
    val ticker: ManualTicker,
    val lifecycle: FakeLifecycle,
    val notifier: FakeCookingPlanNotifier,
    val sound: FakeSoundPlayer,
    val haptics: FakeHaptics,
) {
    fun close() {
        coordinator.close()
        store.close()
    }
}

private class FakeClock : MonotonicClock {
    private var now = 0L
    override fun nowMillis(): Long = now
    fun advanceBy(duration: Duration) {
        now += duration.inWholeMilliseconds
    }
}

private class ManualTicker : TimerTicker {
    private val ticks = Channel<Unit>(Channel.UNLIMITED)
    override suspend fun awaitTick() {
        ticks.receive()
    }
}

private class FakeLifecycle(initialForeground: Boolean) : AppLifecycleObserver {
    private val mutableForeground = MutableStateFlow(initialForeground)
    override val isForeground: StateFlow<Boolean> = mutableForeground.asStateFlow()
    fun setForeground(foreground: Boolean) {
        mutableForeground.value = foreground
    }
}

private class FakeCookingPlanNotifier : CookingPlanNotifier {
    val scheduledPlans = mutableListOf<List<ScheduledCookingCue>>()
    var cancelCount = 0

    override fun schedulePlan(
        planId: String,
        cues: List<ScheduledCookingCue>,
        soundReference: String?,
    ) {
        scheduledPlans += cues
    }

    override fun cancelPlan(planId: String) {
        cancelCount++
    }
}

private class FakeSoundPlayer : TimerSoundPlayer {
    var playCount = 0
    override fun playCompletion(soundReference: String?) {
        playCount++
    }
    override fun preview(soundReference: String?) = Unit
    override fun stop() = Unit
}

private class FakeHaptics : TimerHaptics {
    var count = 0
    override fun performCompletion() {
        count++
    }
}
