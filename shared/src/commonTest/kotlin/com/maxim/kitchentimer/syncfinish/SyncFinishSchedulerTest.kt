package com.maxim.kitchentimer.syncfinish

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes

class SyncFinishSchedulerTest {
    @Test
    fun schedulesEveryComponentAgainstOneServeTime() {
        val result = SyncFinishScheduler.create(
            components = listOf(
                CookingComponent("potatoes", "Potatoes", 25.minutes),
                CookingComponent("steak", "Steak", 12.minutes, 5.minutes),
                CookingComponent("sauce", "Sauce", 8.minutes),
            ),
            serveAfter = 30.minutes,
        )

        val schedule = assertIs<SyncFinishScheduleResult.Success>(result).schedule
        assertEquals(
            listOf(
                "potatoes:start" to 5.minutes,
                "steak:start" to 13.minutes,
                "sauce:start" to 22.minutes,
                "steak:finish" to 25.minutes,
                "plan:serve" to 30.minutes,
            ),
            schedule.cues.map { it.id to it.offsetFromStart },
        )
    }

    @Test
    fun minimumServeTimeIncludesRestingTime() {
        val components = listOf(
            CookingComponent("a", "A", 10.minutes, 5.minutes),
            CookingComponent("b", "B", 12.minutes),
        )

        assertEquals(15.minutes, SyncFinishScheduler.minimumServeAfter(components))
    }

    @Test
    fun rejectsPlanThatCannotFinishOnTime() {
        val result = SyncFinishScheduler.create(
            components = listOf(
                CookingComponent("a", "A", 20.minutes),
                CookingComponent("b", "B", 5.minutes),
            ),
            serveAfter = 10.minutes,
        )

        assertEquals(
            "Serve time is shorter than the longest component",
            assertIs<SyncFinishScheduleResult.Invalid>(result).message,
        )
    }

    @Test
    fun rejectsIncompleteDraft() {
        val result = SyncFinishScheduler.create(
            components = listOf(CookingComponent("a", "", 5.minutes)),
            serveAfter = 5.minutes,
        )

        assertEquals(
            "Add at least 2 components",
            assertIs<SyncFinishScheduleResult.Invalid>(result).message,
        )
    }
}
