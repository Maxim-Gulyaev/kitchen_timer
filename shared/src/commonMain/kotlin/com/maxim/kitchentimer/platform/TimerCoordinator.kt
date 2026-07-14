package com.maxim.kitchentimer.platform

import com.maxim.kitchentimer.timer.TimerEvent
import com.maxim.kitchentimer.timer.TimerIntent
import com.maxim.kitchentimer.timer.TimerState
import com.maxim.kitchentimer.timer.TimerStatus
import com.maxim.kitchentimer.timer.TimerStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Connects ordered timer transitions to platform side effects.
 * Adapter failures are isolated so they can never break timer state processing.
 */
class TimerCoordinator(
    private val store: TimerStore,
    private val services: TimerPlatformServices,
    coroutineScope: CoroutineScope,
) {
    private val coordinatorJob = SupervisorJob(coroutineScope.coroutineContext[Job])
    private val scope = CoroutineScope(coroutineScope.coroutineContext + coordinatorJob)
    private var closed = false
    private var scheduledDeadlineMillis: Long? = null
    private var notificationScheduledAtMillis: Long? = null

    val state: StateFlow<TimerState> = store.state

    init {
        var lastForeground = services.lifecycle.isForeground.value
        store.setTickerEnabled(lastForeground)
        scope.launch {
            store.transitions.collect { effectTransition ->
                val transition = effectTransition.transition
                applyStateEffects(effectTransition.previousState, transition.state)
                transition.event?.let(::applyEventEffects)
            }
        }
        scope.launch {
            services.lifecycle.isForeground
                .collect { isForeground ->
                    if (isForeground == lastForeground) return@collect
                    lastForeground = isForeground
                    handleLifecycleChange(isForeground)
                }
        }
    }

    fun dispatch(intent: TimerIntent): Boolean = store.dispatch(intent)

    fun close() {
        if (closed) return
        closed = true
        scope.cancel()
        safely(services.soundPlayer::stop)
        cancelCompletionNotification()
    }

    private fun applyStateEffects(previous: TimerState, current: TimerState) {
        if (previous.status == TimerStatus.Finished && current.status != TimerStatus.Finished) {
            safely(services.soundPlayer::stop)
        }

        if (previous.status != TimerStatus.Running && current.status == TimerStatus.Running) {
            scheduleCompletionAtDeadline(current)
        }

        if (previous.status == TimerStatus.Running && current.status == TimerStatus.Paused) {
            cancelCompletionNotification()
        }

        if (
            previous.status in setOf(TimerStatus.Running, TimerStatus.Paused, TimerStatus.Finished) &&
            current.status == TimerStatus.Idle
        ) {
            cancelCompletionNotification()
        }
    }

    private fun applyEventEffects(event: TimerEvent) {
        when (event) {
            TimerEvent.Completed -> {
                if (services.lifecycle.isForeground.value) {
                    safely(services.soundPlayer::playCompletion)
                    safely(services.haptics::performCompletion)
                }
            }
        }
    }

    private fun handleLifecycleChange(isForeground: Boolean) {
        if (isForeground) {
            // Reconciliation is ordered before ticker restart, so a UI tick can never be the
            // source of truth after returning from background.
            store.setTickerEnabled(false)
            cancelCompletionNotification()
            store.dispatch(TimerIntent.Tick)
            store.setTickerEnabled(true)
        } else {
            store.setTickerEnabled(false)
            scheduleCompletionAtDeadline(store.state.value)
        }
    }

    private fun scheduleCompletionAtDeadline(state: TimerState) {
        if (state.status != TimerStatus.Running) return
        val deadlineMillis = requireNotNull(state.deadlineMillis)
        val nowMillis = services.clock.nowMillis()
        if (
            scheduledDeadlineMillis == deadlineMillis &&
            notificationScheduledAtMillis == nowMillis
        ) {
            return
        }
        val remainingMillis = (deadlineMillis - nowMillis).coerceAtLeast(0L)
        runCatching { services.notifier.scheduleCompletion(remainingMillis.milliseconds) }
            .onSuccess {
                scheduledDeadlineMillis = deadlineMillis
                notificationScheduledAtMillis = nowMillis
            }
    }

    private fun cancelCompletionNotification() {
        safely(services.notifier::cancelCompletion)
        scheduledDeadlineMillis = null
        notificationScheduledAtMillis = null
    }

    private inline fun safely(effect: () -> Unit) {
        runCatching(effect)
    }
}
