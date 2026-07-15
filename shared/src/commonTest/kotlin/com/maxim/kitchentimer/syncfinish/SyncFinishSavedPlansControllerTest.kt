package com.maxim.kitchentimer.syncfinish

import com.maxim.kitchentimer.platform.TimerPlatformServices
import com.maxim.kitchentimer.syncfinish.persistence.InMemoryCookingPlanRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class SyncFinishSavedPlansControllerTest {
    @Test
    fun saveLoadEditUpdateAndDeleteFlowIsExplicit() = runTest {
        val repository = InMemoryCookingPlanRepository()
        val controller = SyncFinishController(
            services = TimerPlatformServices(),
            coroutineScope = backgroundScope,
            plansRepository = repository,
        )
        runCurrent()

        controller.saveCurrentDraft("Sunday dinner")
        runCurrent()
        val saved = controller.savedPlansState.value.openedPlan!!
        assertEquals("Sunday dinner", saved.name)

        controller.dispatch(
            SyncFinishIntent.RenameComponent(saved.components.first().id, "Edited main"),
        )
        runCurrent()
        assertEquals("Main dish", repository.find(saved.id)!!.components.first().name)
        assertEquals(true, controller.savedPlansState.value.isDirty(controller.state.value))

        controller.updateOpenedPlan()
        runCurrent()
        assertEquals("Edited main", repository.find(saved.id)!!.components.first().name)

        controller.newPlan()
        runCurrent()
        assertNull(controller.savedPlansState.value.openedPlan)

        controller.loadSavedPlan(saved.id)
        runCurrent()
        assertEquals("Edited main", controller.state.value.components.first().name)

        controller.deleteSavedPlan(saved.id)
        runCurrent()
        assertNull(repository.find(saved.id))
        assertNull(controller.savedPlansState.value.openedPlan)
        controller.close()
    }

    @Test
    fun saveAsCopyCreatesIndependentPlan() = runTest {
        val repository = InMemoryCookingPlanRepository()
        val controller = SyncFinishController(
            services = TimerPlatformServices(),
            coroutineScope = backgroundScope,
            plansRepository = repository,
        )
        runCurrent()

        controller.saveCurrentDraft("Original")
        runCurrent()
        controller.saveCurrentDraftAsCopy("Copy")
        runCurrent()

        assertEquals(listOf("Copy", "Original"), repository.plans.first().map { it.name })
        controller.close()
    }
}
