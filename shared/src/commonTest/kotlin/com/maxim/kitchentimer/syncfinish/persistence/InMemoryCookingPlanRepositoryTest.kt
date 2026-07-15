package com.maxim.kitchentimer.syncfinish.persistence

import com.maxim.kitchentimer.syncfinish.CookingComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

class InMemoryCookingPlanRepositoryTest {
    private val components = listOf(
        CookingComponent("main", "Main", 15.minutes),
        CookingComponent("side", "Side", 10.minutes),
    )

    @Test
    fun createUpdateCopyAndDeletePreserveOrderedComponents() = runTest {
        var now = 100L
        val repository = InMemoryCookingPlanRepository(EpochMillisClock { now })
        val id = repository.create(" Dinner ", components, 20.minutes)

        val created = repository.find(id)!!
        assertEquals("Dinner", created.name)
        assertEquals(components, created.components)
        assertEquals(100L, created.createdAtMillis)

        now = 200L
        val reversed = components.reversed()
        repository.update(id, "Dinner updated", reversed, 25.minutes)

        val updated = repository.find(id)!!
        assertEquals(reversed, updated.components)
        assertEquals(100L, updated.createdAtMillis)
        assertEquals(200L, updated.updatedAtMillis)
        assertEquals(listOf(id), repository.plans.first().map(SavedCookingPlan::id))

        repository.delete(id)
        assertNull(repository.find(id))
    }

    @Test
    fun invalidPlanIsRejectedBeforeWrite() = runTest {
        val repository = InMemoryCookingPlanRepository()

        assertFailsWith<IllegalArgumentException> {
            repository.create("", components, 20.minutes)
        }
        assertFailsWith<IllegalArgumentException> {
            repository.create("Too short", components, 5.minutes)
        }
        assertEquals(emptyList(), repository.plans.first())
    }
}
