package com.maxim.kitchentimer.syncfinish.persistence

import com.maxim.kitchentimer.syncfinish.CookingComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

interface CookingPlanRepository {
    val plans: Flow<List<SavedCookingPlan>>

    suspend fun create(
        name: String,
        components: List<CookingComponent>,
        serveAfter: Duration,
    ): Long

    suspend fun update(
        id: Long,
        name: String,
        components: List<CookingComponent>,
        serveAfter: Duration,
    )

    suspend fun find(id: Long): SavedCookingPlan?

    suspend fun delete(id: Long)
}

class RoomCookingPlanRepository(
    private val dao: SavedPlanDao,
    private val clock: EpochMillisClock,
) : CookingPlanRepository {
    override val plans: Flow<List<SavedCookingPlan>> = dao.observeAll().map { plans ->
        plans.map(SavedPlanWithComponents::toDomain)
    }

    override suspend fun create(
        name: String,
        components: List<CookingComponent>,
        serveAfter: Duration,
    ): Long {
        val normalizedName = validateSavedPlan(name, components, serveAfter)
        val now = clock.nowMillis()
        return dao.create(
            plan = SavedPlanEntity(
                name = normalizedName,
                serveAfterMillis = serveAfter.inWholeMilliseconds,
                createdAtMillis = now,
                updatedAtMillis = now,
            ),
            components = components.toEntities(planId = 0L),
        )
    }

    override suspend fun update(
        id: Long,
        name: String,
        components: List<CookingComponent>,
        serveAfter: Duration,
    ) {
        val normalizedName = validateSavedPlan(name, components, serveAfter)
        val existing = dao.find(id) ?: error("Saved plan $id does not exist")
        dao.replace(
            plan = existing.plan.copy(
                name = normalizedName,
                serveAfterMillis = serveAfter.inWholeMilliseconds,
                updatedAtMillis = clock.nowMillis(),
            ),
            components = components.toEntities(planId = id),
        )
    }

    override suspend fun find(id: Long): SavedCookingPlan? = dao.find(id)?.toDomain()

    override suspend fun delete(id: Long) {
        dao.delete(id)
    }

}

private fun SavedPlanWithComponents.toDomain(): SavedCookingPlan {
    val domainComponents = components
        .sortedBy(SavedComponentEntity::position)
        .map { component ->
            CookingComponent(
                id = component.componentId,
                name = component.name,
                cookingDuration = component.cookingDurationMillis.milliseconds,
                restingDuration = component.restingDurationMillis.milliseconds,
            )
        }
    val serveAfter = plan.serveAfterMillis.milliseconds
    val normalizedName = validateSavedPlan(plan.name, domainComponents, serveAfter)
    return SavedCookingPlan(
        id = plan.id,
        name = normalizedName,
        components = domainComponents,
        serveAfter = serveAfter,
        createdAtMillis = plan.createdAtMillis,
        updatedAtMillis = plan.updatedAtMillis,
    )
}

private fun List<CookingComponent>.toEntities(planId: Long): List<SavedComponentEntity> =
    mapIndexed { position, component ->
        SavedComponentEntity(
            planId = planId,
            componentId = component.id,
            position = position,
            name = component.name,
            cookingDurationMillis = component.cookingDuration.inWholeMilliseconds,
            restingDurationMillis = component.restingDuration.inWholeMilliseconds,
        )
    }
