package com.maxim.kitchentimer.syncfinish

import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds

object SyncFinishScheduler {
    fun create(
        components: List<CookingComponent>,
        serveAfter: Duration,
    ): SyncFinishScheduleResult {
        validate(components, serveAfter)?.let {
            return SyncFinishScheduleResult.Invalid(it)
        }

        val cues = buildList {
            components.forEach { component ->
                val totalDuration = component.cookingDuration + component.restingDuration
                add(
                    CookingCue(
                        id = "${component.id}:start",
                        componentId = component.id,
                        offsetFromStart = serveAfter - totalDuration,
                        type = CookingCueType.StartCooking,
                        message = "Start ${component.name}",
                    ),
                )
                if (component.restingDuration > ZERO) {
                    add(
                        CookingCue(
                            id = "${component.id}:finish",
                            componentId = component.id,
                            offsetFromStart = serveAfter - component.restingDuration,
                            type = CookingCueType.FinishCooking,
                            message = "Finish ${component.name} and let it rest",
                        ),
                    )
                }
            }
            add(
                CookingCue(
                    id = "plan:serve",
                    componentId = null,
                    offsetFromStart = serveAfter,
                    type = CookingCueType.Serve,
                    message = "Everything is ready — serve now",
                ),
            )
        }.sortedWith(compareBy<CookingCue> { it.offsetFromStart }.thenBy { it.id })

        return SyncFinishScheduleResult.Success(
            SyncFinishSchedule(
                serveAfter = serveAfter,
                cues = cues,
            ),
        )
    }

    fun minimumServeAfter(components: List<CookingComponent>): Duration =
        components.maxOfOrNull { component ->
            (component.cookingDuration + component.restingDuration).coerceAtLeast(ZERO)
        } ?: ZERO

    private fun validate(
        components: List<CookingComponent>,
        serveAfter: Duration,
    ): String? {
        if (components.size < MIN_SYNC_FINISH_COMPONENTS) {
            return "Add at least $MIN_SYNC_FINISH_COMPONENTS components"
        }
        if (components.size > MAX_SYNC_FINISH_COMPONENTS) {
            return "A plan can contain at most $MAX_SYNC_FINISH_COMPONENTS components"
        }
        if (components.map(CookingComponent::id).distinct().size != components.size) {
            return "Every component must have a unique id"
        }
        components.forEach { component ->
            if (component.id.isBlank()) return "Component id must not be blank"
            if (component.name.isBlank()) return "Enter a name for every component"
            if (component.cookingDuration <= ZERO) {
                return "Cooking time must be greater than zero"
            }
            if (component.restingDuration < ZERO) return "Resting time must not be negative"
            if (!component.cookingDuration.isWholeMillisecondsValue()) {
                return "Cooking time must use whole milliseconds"
            }
            if (!component.restingDuration.isWholeMillisecondsValue()) {
                return "Resting time must use whole milliseconds"
            }
        }
        if (serveAfter <= ZERO || !serveAfter.isWholeMillisecondsValue()) {
            return "Serve time must be greater than zero"
        }
        val minimum = minimumServeAfter(components)
        if (serveAfter < minimum) {
            return "Serve time is shorter than the longest component"
        }
        return null
    }
}

private fun Duration.isWholeMillisecondsValue(): Boolean =
    isFinite() && inWholeMilliseconds.milliseconds == this
