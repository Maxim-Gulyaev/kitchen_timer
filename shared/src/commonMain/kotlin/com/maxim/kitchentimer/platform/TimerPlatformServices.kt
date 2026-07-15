package com.maxim.kitchentimer.platform

import com.maxim.kitchentimer.settings.InMemoryTimerSoundSettings
import com.maxim.kitchentimer.settings.TimerSoundSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration
import kotlin.time.TimeSource

fun interface MonotonicClock {
    fun nowMillis(): Long
}

interface TimerSoundPlayer {
    /** Starts bounded completion playback. Repeated calls replace the current playback. */
    fun playCompletion(soundReference: String?)

    /** Plays a short sample of the selected sound. Repeated calls replace the current playback. */
    fun preview(soundReference: String?)

    /** Stops playback. Must be idempotent and safe when nothing is playing. */
    fun stop()
}

fun interface TimerHaptics {
    fun performCompletion()
}

interface TimerNotifier {
    /** Schedules one completion alert and replaces any previously scheduled alert. */
    fun scheduleCompletion(after: Duration, soundReference: String?)

    /** Cancels the single scheduled alert. Must be idempotent. */
    fun cancelCompletion()
}

interface AppLifecycleObserver {
    val isForeground: StateFlow<Boolean>
}

class DefaultMonotonicClock : MonotonicClock {
    private val origin = TimeSource.Monotonic.markNow()

    override fun nowMillis(): Long = origin.elapsedNow().inWholeMilliseconds.coerceAtLeast(0L)
}

object NoOpTimerSoundPlayer : TimerSoundPlayer {
    override fun playCompletion(soundReference: String?) = Unit
    override fun preview(soundReference: String?) = Unit
    override fun stop() = Unit
}

object NoOpTimerHaptics : TimerHaptics {
    override fun performCompletion() = Unit
}

object NoOpTimerNotifier : TimerNotifier {
    override fun scheduleCompletion(after: Duration, soundReference: String?) = Unit
    override fun cancelCompletion() = Unit
}

object AlwaysForegroundLifecycleObserver : AppLifecycleObserver {
    private val foreground = MutableStateFlow(true)
    override val isForeground: StateFlow<Boolean> = foreground.asStateFlow()
}

/**
 * Platform methods must return quickly, marshal to the required platform thread internally,
 * and degrade to a safe no-op when permissions or APIs are unavailable.
 */
data class TimerPlatformServices(
    val clock: MonotonicClock = DefaultMonotonicClock(),
    val soundPlayer: TimerSoundPlayer = NoOpTimerSoundPlayer,
    val haptics: TimerHaptics = NoOpTimerHaptics,
    val notifier: TimerNotifier = NoOpTimerNotifier,
    val cookingPlanNotifier: CookingPlanNotifier = NoOpCookingPlanNotifier,
    val lifecycle: AppLifecycleObserver = AlwaysForegroundLifecycleObserver,
    val soundSettings: TimerSoundSettings = InMemoryTimerSoundSettings(),
)

data class ScheduledCookingCue(
    val id: String,
    val delay: Duration,
    val title: String,
    val message: String,
)

interface CookingPlanNotifier {
    fun schedulePlan(
        planId: String,
        cues: List<ScheduledCookingCue>,
        soundReference: String?,
    )

    fun cancelPlan(planId: String)
}

object NoOpCookingPlanNotifier : CookingPlanNotifier {
    override fun schedulePlan(
        planId: String,
        cues: List<ScheduledCookingCue>,
        soundReference: String?,
    ) = Unit

    override fun cancelPlan(planId: String) = Unit
}
