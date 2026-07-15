package com.maxim.kitchentimer.syncfinish.persistence

import androidx.room.Room
import com.maxim.kitchentimer.syncfinish.CookingComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

class RoomCookingPlanRepositoryTest {
    @Test
    fun realRoomDatabaseCreatesUpdatesOrdersAndDeletesPlans() = runTest {
        val database = buildKitchenTimerDatabase(
            Room.inMemoryDatabaseBuilder<KitchenTimerDatabase>(
                factory = KitchenTimerDatabaseConstructor::initialize,
            ),
        )
        var now = 10L
        val repository = RoomCookingPlanRepository(
            dao = database.savedPlanDao(),
            clock = EpochMillisClock { now },
        )
        val original = listOf(
            CookingComponent("component-1", "Main", 10.minutes),
            CookingComponent("component-2", "Side", 5.minutes),
        )

        val firstId = repository.create("First", original, 15.minutes)
        now = 20L
        val secondId = repository.create("Second", original, 20.minutes)

        assertEquals(
            listOf(secondId, firstId),
            repository.plans.first().map(SavedCookingPlan::id),
        )
        assertEquals(original, repository.find(firstId)!!.components)

        now = 30L
        val replacement = original.reversed().mapIndexed { index, component ->
            component.copy(name = "Updated $index")
        }
        repository.update(firstId, "First updated", replacement, 25.minutes)

        val updated = repository.find(firstId)!!
        assertEquals("First updated", updated.name)
        assertEquals(replacement, updated.components)
        assertEquals(10L, updated.createdAtMillis)
        assertEquals(30L, updated.updatedAtMillis)
        assertEquals(
            listOf(firstId, secondId),
            repository.plans.first().map(SavedCookingPlan::id),
        )

        repository.delete(firstId)
        assertNull(repository.find(firstId))
        assertEquals(listOf(secondId), repository.plans.first().map(SavedCookingPlan::id))
        database.close()
    }
}
