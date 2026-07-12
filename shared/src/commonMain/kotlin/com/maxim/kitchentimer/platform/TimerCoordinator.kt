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

    val state: StateFlow<TimerState> = store.state

    init {
        scope.launch {
            store.transitions.collect { effectTransition ->
                val transition = effectTransition.transition
                applyStateEffects(effectTransition.previousState, transition.state)
                transition.event?.let(::applyEventEffects)
            }
        }
    }

    fun dispatch(intent: TimerIntent): Boolean = store.dispatch(intent)

    fun close() {
        if (closed) return
        closed = true
        scope.cancel()
        safely(services.soundPlayer::stop)
        safely(services.notifier::cancelCompletion)
    }

    private fun applyStateEffects(previous: TimerState, current: TimerState) {
        if (previous.status == TimerStatus.Finished && current.status != TimerStatus.Finished) {
            safely(services.soundPlayer::stop)
        }

        if (previous.status != TimerStatus.Running && current.status == TimerStatus.Running) {
            safely { services.notifier.scheduleCompletion(current.remainingDuration) }
        }

        if (previous.status == TimerStatus.Running && current.status == TimerStatus.Paused) {
            safely(services.notifier::cancelCompletion)
        }

        if (
            previous.status in setOf(TimerStatus.Running, TimerStatus.Paused, TimerStatus.Finished) &&
            current.status == TimerStatus.Idle
        ) {
            safely(services.notifier::cancelCompletion)
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

    private inline fun safely(effect: () -> Unit) {
        runCatching(effect)
    }
}
