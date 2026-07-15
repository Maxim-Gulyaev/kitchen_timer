package com.maxim.kitchentimer.syncfinish

import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

object SyncFinishReducer {
    fun reduce(
        state: SyncFinishState,
        intent: SyncFinishIntent,
        nowMillis: Long,
    ): SyncFinishTransition {
        require(nowMillis >= 0L) { "Monotonic time must not be negative" }
        return when (intent) {
            SyncFinishIntent.AddComponent -> edit(state) {
                if (components.size >= MAX_SYNC_FINISH_COMPONENTS) return@edit this
                val number = nextComponentNumber(components)
                copy(
                    components = components + CookingComponent(
                        id = "component-$number",
                        name = "Component $number",
                        cookingDuration = 5.minutes,
                    ),
                )
            }

            is SyncFinishIntent.RemoveComponent -> edit(state) {
                copy(components = components.filterNot { it.id == intent.componentId })
            }

            is SyncFinishIntent.RenameComponent -> edit(state) {
                updateComponent(intent.componentId) { it.copy(name = intent.name) }
            }

            is SyncFinishIntent.ChangeCookingDuration -> edit(state) {
                updateComponent(intent.componentId) {
                    it.copy(cookingDuration = intent.duration.normalizedNonNegative())
                }
            }

            is SyncFinishIntent.ChangeRestingDuration -> edit(state) {
                updateComponent(intent.componentId) {
                    it.copy(restingDuration = intent.duration.normalizedNonNegative())
                }
            }

            is SyncFinishIntent.ChangeServeAfter -> edit(state) {
                copy(serveAfter = intent.duration.normalizedNonNegative())
            }

            is SyncFinishIntent.ReplaceDraft -> edit(state) {
                copy(
                    components = intent.components,
                    serveAfter = intent.serveAfter.normalizedNonNegative(),
                    cues = emptyList(),
                    elapsed = ZERO,
                    emittedCueIds = emptySet(),
                    currentCues = emptyList(),
                )
            }

            SyncFinishIntent.UseMinimumServeTime -> edit(state) {
                copy(serveAfter = SyncFinishScheduler.minimumServeAfter(components))
            }

            SyncFinishIntent.Start -> start(state, nowMillis)
            SyncFinishIntent.Tick -> reconcile(state, nowMillis)
            SyncFinishIntent.Cancel -> cancel(state)
            SyncFinishIntent.Reset -> SyncFinishTransition(SyncFinishState())
        }
    }

    private fun start(state: SyncFinishState, nowMillis: Long): SyncFinishTransition {
        if (state.status != SyncFinishStatus.Draft) return state.unchanged()
        return when (val result = SyncFinishScheduler.create(state.components, state.serveAfter)) {
            is SyncFinishScheduleResult.Invalid -> SyncFinishTransition(
                state.copy(validationError = result.message),
            )

            is SyncFinishScheduleResult.Success -> {
                val deadline = safeDeadline(nowMillis, result.schedule.serveAfter.inWholeMilliseconds)
                reconcile(
                    state.copy(
                        status = SyncFinishStatus.Running,
                        cues = result.schedule.cues,
                        elapsed = ZERO,
                        emittedCueIds = emptySet(),
                        currentCues = emptyList(),
                        validationError = null,
                        startedAtMillis = nowMillis,
                        deadlineMillis = deadline,
                    ),
                    nowMillis,
                )
            }
        }
    }

    private fun reconcile(state: SyncFinishState, nowMillis: Long): SyncFinishTransition {
        if (state.status != SyncFinishStatus.Running) return state.unchanged()
        val startedAt = requireNotNull(state.startedAtMillis)
        val elapsedMillis = (nowMillis - startedAt)
            .coerceAtLeast(0L)
            .coerceAtMost(state.serveAfter.inWholeMilliseconds)
        val elapsed = elapsedMillis.milliseconds
        val dueCues = state.cues.filter { cue ->
            cue.id !in state.emittedCueIds && cue.offsetFromStart <= elapsed
        }
        val emittedIds = state.emittedCueIds + dueCues.map(CookingCue::id)
        val latestDueOffset = dueCues.maxOfOrNull(CookingCue::offsetFromStart)
        val currentCues = latestDueOffset?.let { offset ->
            dueCues.filter { it.offsetFromStart == offset }
        }.orEmpty()
        val finished = elapsed >= state.serveAfter
        return SyncFinishTransition(
            state.copy(
                status = if (finished) SyncFinishStatus.Finished else SyncFinishStatus.Running,
                elapsed = elapsed,
                emittedCueIds = emittedIds,
                currentCues = currentCues.ifEmpty { state.currentCues },
                startedAtMillis = if (finished) null else state.startedAtMillis,
                deadlineMillis = if (finished) null else state.deadlineMillis,
            ),
            events = dueCues.map(::SyncFinishEvent),
        )
    }

    private fun cancel(state: SyncFinishState): SyncFinishTransition {
        if (state.status != SyncFinishStatus.Running) return state.unchanged()
        return SyncFinishTransition(
            state.copy(
                status = SyncFinishStatus.Draft,
                cues = emptyList(),
                elapsed = ZERO,
                emittedCueIds = emptySet(),
                currentCues = emptyList(),
                startedAtMillis = null,
                deadlineMillis = null,
            ),
        )
    }

    private inline fun edit(
        state: SyncFinishState,
        transform: SyncFinishState.() -> SyncFinishState,
    ): SyncFinishTransition {
        if (state.status != SyncFinishStatus.Draft) return state.unchanged()
        return SyncFinishTransition(state.transform().copy(validationError = null))
    }

    private fun SyncFinishState.updateComponent(
        id: String,
        transform: (CookingComponent) -> CookingComponent,
    ): SyncFinishState = copy(
        components = components.map { component ->
            if (component.id == id) transform(component) else component
        },
    )

    private fun Duration.normalizedNonNegative(): Duration =
        coerceAtLeast(ZERO).inWholeMilliseconds.milliseconds

    private fun nextComponentNumber(components: List<CookingComponent>): Int {
        val usedIds = components.map(CookingComponent::id).toSet()
        return generateSequence(1, Int::inc)
            .first { "component-$it" !in usedIds }
    }

    private fun safeDeadline(nowMillis: Long, durationMillis: Long): Long =
        if (Long.MAX_VALUE - durationMillis < nowMillis) Long.MAX_VALUE else nowMillis + durationMillis

    private fun SyncFinishState.unchanged() = SyncFinishTransition(this)
}
