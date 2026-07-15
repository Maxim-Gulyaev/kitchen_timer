package com.maxim.kitchentimer.syncfinish.persistence

import com.maxim.kitchentimer.syncfinish.CookingComponent
import com.maxim.kitchentimer.syncfinish.SyncFinishScheduleResult
import com.maxim.kitchentimer.syncfinish.SyncFinishScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration

class InMemoryCookingPlanRepository(
    private val clock: EpochMillisClock = EpochMillisClock { 0L },
) : CookingPlanRepository {
    private val mutablePlans = MutableStateFlow<List<SavedCookingPlan>>(emptyList())
    private var nextId = 1L

    override val plans: Flow<List<SavedCookingPlan>> = mutablePlans.asStateFlow()

    override suspend fun create(
        name: String,
        components: List<CookingComponent>,
        serveAfter: Duration,
    ): Long {
        val normalizedName = validateSavedPlan(name, components, serveAfter)
        val id = nextId++
        val now = clock.nowMillis()
        mutablePlans.value = listOf(
            SavedCookingPlan(
                id = id,
                name = normalizedName,
                components = components,
                serveAfter = serveAfter,
                createdAtMillis = now,
                updatedAtMillis = now,
            ),
        ) + mutablePlans.value
        return id
    }

    override suspend fun update(
        id: Long,
        name: String,
        components: List<CookingComponent>,
        serveAfter: Duration,
    ) {
        val normalizedName = validateSavedPlan(name, components, serveAfter)
        val existing = find(id) ?: error("Saved plan $id does not exist")
        val updated = existing.copy(
            name = normalizedName,
            components = components,
            serveAfter = serveAfter,
            updatedAtMillis = clock.nowMillis(),
        )
        mutablePlans.value = listOf(updated) + mutablePlans.value.filterNot { it.id == id }
    }

    override suspend fun find(id: Long): SavedCookingPlan? =
        mutablePlans.value.firstOrNull { it.id == id }

    override suspend fun delete(id: Long) {
        mutablePlans.value = mutablePlans.value.filterNot { it.id == id }
    }
}

internal fun validateSavedPlan(
    name: String,
    components: List<CookingComponent>,
    serveAfter: Duration,
): String {
    val normalizedName = name.trim()
    require(normalizedName.isNotEmpty()) { "Plan name must not be blank" }
    val scheduleResult = SyncFinishScheduler.create(components, serveAfter)
    require(scheduleResult is SyncFinishScheduleResult.Success) {
        (scheduleResult as SyncFinishScheduleResult.Invalid).message
    }
    return normalizedName
}
