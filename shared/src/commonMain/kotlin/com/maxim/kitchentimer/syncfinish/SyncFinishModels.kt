package com.maxim.kitchentimer.syncfinish

import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.minutes

const val MIN_SYNC_FINISH_COMPONENTS = 2
const val MAX_SYNC_FINISH_COMPONENTS = 6

data class CookingComponent(
    val id: String,
    val name: String,
    val cookingDuration: Duration,
    val restingDuration: Duration = ZERO,
)

enum class CookingCueType {
    StartCooking,
    FinishCooking,
    Serve,
}

data class CookingCue(
    val id: String,
    val componentId: String?,
    val offsetFromStart: Duration,
    val type: CookingCueType,
    val message: String,
)

data class SyncFinishSchedule(
    val serveAfter: Duration,
    val cues: List<CookingCue>,
)

sealed interface SyncFinishScheduleResult {
    data class Success(val schedule: SyncFinishSchedule) : SyncFinishScheduleResult
    data class Invalid(val message: String) : SyncFinishScheduleResult
}

enum class SyncFinishStatus {
    Draft,
    Running,
    Finished,
}

data class SyncFinishState(
    val components: List<CookingComponent> = defaultCookingComponents(),
    val serveAfter: Duration = 20.minutes,
    val status: SyncFinishStatus = SyncFinishStatus.Draft,
    val cues: List<CookingCue> = emptyList(),
    val elapsed: Duration = ZERO,
    val emittedCueIds: Set<String> = emptySet(),
    val currentCues: List<CookingCue> = emptyList(),
    val validationError: String? = null,
    internal val startedAtMillis: Long? = null,
    internal val deadlineMillis: Long? = null,
) {
    val remaining: Duration
        get() = (serveAfter - elapsed).coerceAtLeast(ZERO)

    val progress: Float
        get() = if (serveAfter <= ZERO) {
            0f
        } else {
            (elapsed / serveAfter).toFloat().coerceIn(0f, 1f)
        }

    val nextCue: CookingCue?
        get() = cues.firstOrNull { it.id !in emittedCueIds }

    val currentInstruction: String?
        get() = currentCues
            .takeIf { it.isNotEmpty() }
            ?.joinToString(separator = "\n", transform = CookingCue::message)

    val remainingToNextCue: Duration
        get() = nextCue?.let { (it.offsetFromStart - elapsed).coerceAtLeast(ZERO) } ?: ZERO
}

sealed interface SyncFinishIntent {
    data object AddComponent : SyncFinishIntent
    data class RemoveComponent(val componentId: String) : SyncFinishIntent
    data class RenameComponent(val componentId: String, val name: String) : SyncFinishIntent
    data class ChangeCookingDuration(
        val componentId: String,
        val duration: Duration,
    ) : SyncFinishIntent

    data class ChangeRestingDuration(
        val componentId: String,
        val duration: Duration,
    ) : SyncFinishIntent

    data class ChangeServeAfter(val duration: Duration) : SyncFinishIntent
    data object UseMinimumServeTime : SyncFinishIntent
    data object Start : SyncFinishIntent
    data object Tick : SyncFinishIntent
    data object Cancel : SyncFinishIntent
    data object Reset : SyncFinishIntent
}

data class SyncFinishEvent(val cue: CookingCue)

data class SyncFinishTransition(
    val state: SyncFinishState,
    val events: List<SyncFinishEvent> = emptyList(),
)

private fun defaultCookingComponents(): List<CookingComponent> = listOf(
    CookingComponent(
        id = "component-1",
        name = "Main dish",
        cookingDuration = 15.minutes,
    ),
    CookingComponent(
        id = "component-2",
        name = "Side dish",
        cookingDuration = 20.minutes,
    ),
)
