package com.maxim.kitchentimer.syncfinish

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class SyncFinishReducerTest {
    private val plan = SyncFinishState(
        components = listOf(
            CookingComponent("long", "Long dish", 20.minutes),
            CookingComponent("short", "Short dish", 5.minutes),
        ),
        serveAfter = 20.minutes,
    )

    @Test
    fun startEmitsImmediateCueOnlyOnce() {
        val started = SyncFinishReducer.reduce(plan, SyncFinishIntent.Start, nowMillis = 1_000L)

        assertEquals(SyncFinishStatus.Running, started.state.status)
        assertEquals(listOf("long:start"), started.events.map { it.cue.id })

        val repeated = SyncFinishReducer.reduce(
            started.state,
            SyncFinishIntent.Tick,
            nowMillis = 1_000L,
        )
        assertTrue(repeated.events.isEmpty())
    }

    @Test
    fun missedTickEmitsEveryCrossedCueAndFinishesOnce() {
        val started = SyncFinishReducer.reduce(plan, SyncFinishIntent.Start, nowMillis = 0L)
        val finished = SyncFinishReducer.reduce(
            started.state,
            SyncFinishIntent.Tick,
            nowMillis = 30.minutes.inWholeMilliseconds,
        )

        assertEquals(SyncFinishStatus.Finished, finished.state.status)
        assertEquals(
            listOf("short:start", "plan:serve"),
            finished.events.map { it.cue.id },
        )
        assertEquals(20.minutes, finished.state.elapsed)

        val repeated = SyncFinishReducer.reduce(
            finished.state,
            SyncFinishIntent.Tick,
            nowMillis = 40.minutes.inWholeMilliseconds,
        )
        assertTrue(repeated.events.isEmpty())
    }

    @Test
    fun invalidDraftStaysEditableAndExposesError() {
        val invalid = plan.copy(serveAfter = 10.minutes)

        val transition = SyncFinishReducer.reduce(invalid, SyncFinishIntent.Start, 0L)

        assertEquals(SyncFinishStatus.Draft, transition.state.status)
        assertEquals(
            "Serve time is shorter than the longest component",
            transition.state.validationError,
        )
    }

    @Test
    fun cancelReturnsToDraftAndClearsRuntimeState() {
        val started = SyncFinishReducer.reduce(plan, SyncFinishIntent.Start, 0L).state

        val cancelled = SyncFinishReducer.reduce(started, SyncFinishIntent.Cancel, 1L).state

        assertEquals(SyncFinishStatus.Draft, cancelled.status)
        assertTrue(cancelled.cues.isEmpty())
        assertTrue(cancelled.emittedCueIds.isEmpty())
    }

    @Test
    fun simultaneousCuesRemainVisibleAsOneActionGroup() {
        val equalPlan = plan.copy(
            components = listOf(
                CookingComponent("a", "A", 20.minutes),
                CookingComponent("b", "B", 20.minutes),
            ),
        )

        val started = SyncFinishReducer.reduce(equalPlan, SyncFinishIntent.Start, 0L)

        assertEquals(listOf("a:start", "b:start"), started.state.currentCues.map { it.id })
        assertEquals("Start A\nStart B", started.state.currentInstruction)
    }

    @Test
    fun savedPlanCanReplaceDraftButNotRunningPlan() {
        val replacement = listOf(
            CookingComponent("x", "X", 7.minutes),
            CookingComponent("y", "Y", 9.minutes),
        )
        val replaced = SyncFinishReducer.reduce(
            plan,
            SyncFinishIntent.ReplaceDraft(replacement, 12.minutes),
            0L,
        )

        assertEquals(replacement, replaced.state.components)
        assertEquals(12.minutes, replaced.state.serveAfter)

        val running = SyncFinishReducer.reduce(replaced.state, SyncFinishIntent.Start, 0L).state
        val ignored = SyncFinishReducer.reduce(
            running,
            SyncFinishIntent.ReplaceDraft(plan.components, 20.minutes),
            1L,
        )
        assertEquals(running, ignored.state)
    }
}
