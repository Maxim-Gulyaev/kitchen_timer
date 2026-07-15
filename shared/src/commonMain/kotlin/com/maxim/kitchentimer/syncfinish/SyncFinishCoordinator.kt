package com.maxim.kitchentimer.syncfinish

import com.maxim.kitchentimer.platform.ScheduledCookingCue
import com.maxim.kitchentimer.platform.TimerPlatformServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds

class SyncFinishCoordinator(
    private val store: SyncFinishStore,
    private val services: TimerPlatformServices,
    coroutineScope: CoroutineScope,
) {
    private val coordinatorJob = SupervisorJob(coroutineScope.coroutineContext[Job])
    private val scope = CoroutineScope(coroutineScope.coroutineContext + coordinatorJob)
    private var lastForeground = services.lifecycle.isForeground.value
    private var closed = false

    val state: StateFlow<SyncFinishState> = store.state

    init {
        store.setTickerEnabled(lastForeground)
        scope.launch {
            store.transitions.collect { effect ->
                applyStateEffects(effect.previousState, effect.transition.state)
                effect.transition.events.lastOrNull()?.let(::applyEventEffect)
            }
        }
        scope.launch {
            services.lifecycle.isForeground.collect { foreground ->
                if (foreground == lastForeground) return@collect
                lastForeground = foreground
                handleLifecycleChange(foreground)
            }
        }
        scope.launch {
            services.soundSettings.selectedSound.drop(1).collect {
                if (!lastForeground && state.value.status == SyncFinishStatus.Running) {
                    scheduleRemainingCues(state.value)
                }
            }
        }
    }

    fun dispatch(intent: SyncFinishIntent): Boolean = store.dispatch(intent)

    fun close() {
        if (closed) return
        closed = true
        scope.cancel()
        safely { services.cookingPlanNotifier.cancelPlan(PLAN_ID) }
        safely(services.soundPlayer::stop)
    }

    private fun applyStateEffects(previous: SyncFinishState, current: SyncFinishState) {
        if (
            previous.status == SyncFinishStatus.Running &&
            current.status != SyncFinishStatus.Running
        ) {
            safely { services.cookingPlanNotifier.cancelPlan(PLAN_ID) }
        }
        if (
            previous.status != SyncFinishStatus.Running &&
            current.status == SyncFinishStatus.Running &&
            !lastForeground
        ) {
            scheduleRemainingCues(current)
        }
    }

    private fun applyEventEffect(event: SyncFinishEvent) {
        if (!lastForeground) return
        safely(services.haptics::performCompletion)
        if (event.cue.type == CookingCueType.Serve) {
            safely {
                services.soundPlayer.playCompletion(
                    services.soundSettings.selectedSound.value.reference,
                )
            }
        }
    }

    private fun handleLifecycleChange(foreground: Boolean) {
        if (foreground) {
            store.setTickerEnabled(false)
            safely { services.cookingPlanNotifier.cancelPlan(PLAN_ID) }
            store.dispatch(SyncFinishIntent.Tick)
            store.setTickerEnabled(true)
        } else {
            store.setTickerEnabled(false)
            scheduleRemainingCues(state.value)
        }
    }

    private fun scheduleRemainingCues(state: SyncFinishState) {
        if (state.status != SyncFinishStatus.Running) return
        val actualElapsed = state.startedAtMillis?.let { startedAt ->
            (services.clock.nowMillis() - startedAt)
                .coerceAtLeast(0L)
                .coerceAtMost(state.serveAfter.inWholeMilliseconds)
                .milliseconds
        } ?: state.elapsed
        val remainingCues = state.cues.mapNotNull { cue ->
            if (cue.id in state.emittedCueIds) return@mapNotNull null
            val delay = cue.offsetFromStart - actualElapsed
            cue.takeIf { delay > ZERO }?.let { it to delay }
        }
        val scheduledCues = remainingCues
            .groupBy { (_, delay) -> delay }
            .map { (delay, entries) ->
                val cues = entries.map { (cue, _) -> cue }
                val serveCue = cues.singleOrNull()?.takeIf { it.type == CookingCueType.Serve }
                ScheduledCookingCue(
                    id = cues.joinToString(separator = "+", transform = CookingCue::id),
                    delay = delay,
                    title = if (serveCue != null) "Sync Finish" else "Cooking step",
                    message = cues.joinToString(separator = "\n", transform = CookingCue::message),
                )
            }
            .sortedBy(ScheduledCookingCue::delay)
        safely {
            services.cookingPlanNotifier.schedulePlan(
                planId = PLAN_ID,
                cues = scheduledCues,
                soundReference = services.soundSettings.selectedSound.value.reference,
            )
        }
    }

    private inline fun safely(block: () -> Unit) {
        runCatching(block)
    }

    private companion object {
        const val PLAN_ID = "active-sync-finish"
    }
}
