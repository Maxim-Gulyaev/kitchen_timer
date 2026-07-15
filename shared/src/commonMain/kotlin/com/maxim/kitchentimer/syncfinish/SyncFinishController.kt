package com.maxim.kitchentimer.syncfinish

import com.maxim.kitchentimer.platform.TimerPlatformServices
import com.maxim.kitchentimer.syncfinish.persistence.CookingPlanRepository
import com.maxim.kitchentimer.syncfinish.persistence.InMemoryCookingPlanRepository
import com.maxim.kitchentimer.timer.CoroutineTimerTicker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SyncFinishController(
    services: TimerPlatformServices,
    coroutineScope: CoroutineScope,
    private val plansRepository: CookingPlanRepository = InMemoryCookingPlanRepository(),
) {
    private val libraryJob = SupervisorJob(coroutineScope.coroutineContext[Job])
    private val libraryScope = CoroutineScope(coroutineScope.coroutineContext + libraryJob)
    private val store = SyncFinishStore(
        clock = services.clock,
        ticker = CoroutineTimerTicker(),
        coroutineScope = coroutineScope,
    )
    private val coordinator = SyncFinishCoordinator(store, services, coroutineScope)

    val state: StateFlow<SyncFinishState> = coordinator.state
    private val mutableSavedPlansState = MutableStateFlow(SavedPlansState())
    private val libraryActionMutex = Mutex()
    val savedPlansState: StateFlow<SavedPlansState> = mutableSavedPlansState.asStateFlow()

    init {
        libraryScope.launch {
            plansRepository.plans
                .catch { error ->
                    mutableSavedPlansState.value = mutableSavedPlansState.value.copy(
                        isBusy = false,
                        errorMessage = error.message ?: "Could not load saved plans",
                    )
                }
                .collect { plans ->
                    val openedId = mutableSavedPlansState.value.openedPlan?.id
                    mutableSavedPlansState.value = mutableSavedPlansState.value.copy(
                        plans = plans,
                        openedPlan = plans.firstOrNull { it.id == openedId },
                        isBusy = false,
                    )
                }
        }
    }

    fun dispatch(intent: SyncFinishIntent): Boolean = coordinator.dispatch(intent)

    fun newPlan() {
        coordinator.dispatch(SyncFinishIntent.Reset)
        mutableSavedPlansState.value = mutableSavedPlansState.value.copy(
            openedPlan = null,
            errorMessage = null,
        )
    }

    fun loadSavedPlan(id: Long) {
        mutableSavedPlansState.value = mutableSavedPlansState.value.copy(openedPlan = null)
        launchLibraryAction {
            val plan = plansRepository.find(id) ?: error("Saved plan $id does not exist")
            check(state.value.status == SyncFinishStatus.Draft) {
                "A saved plan can only replace a draft"
            }
            coordinator.dispatch(
                SyncFinishIntent.ReplaceDraft(
                    components = plan.components,
                    serveAfter = plan.serveAfter,
                ),
            )
            mutableSavedPlansState.value = mutableSavedPlansState.value.copy(openedPlan = plan)
        }
    }

    fun saveCurrentDraft(name: String) = launchLibraryAction {
        val draft = requireDraft()
        val id = plansRepository.create(name, draft.components, draft.serveAfter)
        mutableSavedPlansState.value = mutableSavedPlansState.value.copy(
            openedPlan = plansRepository.find(id),
        )
    }

    fun updateOpenedPlan() = launchLibraryAction {
        val opened = requireNotNull(mutableSavedPlansState.value.openedPlan) {
            "No saved plan is open"
        }
        val draft = requireDraft()
        plansRepository.update(
            id = opened.id,
            name = opened.name,
            components = draft.components,
            serveAfter = draft.serveAfter,
        )
        mutableSavedPlansState.value = mutableSavedPlansState.value.copy(
            openedPlan = plansRepository.find(opened.id),
        )
    }

    fun saveCurrentDraftAsCopy(name: String) = saveCurrentDraft(name)

    fun deleteSavedPlan(id: Long) = launchLibraryAction {
        plansRepository.delete(id)
        if (mutableSavedPlansState.value.openedPlan?.id == id) {
            mutableSavedPlansState.value = mutableSavedPlansState.value.copy(openedPlan = null)
        }
    }

    fun renameSavedPlan(id: Long, name: String) = launchLibraryAction {
        val plan = plansRepository.find(id) ?: error("Saved plan $id does not exist")
        plansRepository.update(
            id = id,
            name = name,
            components = plan.components,
            serveAfter = plan.serveAfter,
        )
        if (mutableSavedPlansState.value.openedPlan?.id == id) {
            mutableSavedPlansState.value = mutableSavedPlansState.value.copy(
                openedPlan = plansRepository.find(id),
            )
        }
    }

    fun duplicateSavedPlan(id: Long) = launchLibraryAction {
        val plan = plansRepository.find(id) ?: error("Saved plan $id does not exist")
        plansRepository.create(
            name = "${plan.name} copy",
            components = plan.components,
            serveAfter = plan.serveAfter,
        )
    }

    fun clearSavedPlansError() {
        mutableSavedPlansState.value = mutableSavedPlansState.value.copy(errorMessage = null)
    }

    fun close() {
        libraryScope.cancel()
        coordinator.close()
        store.close()
    }

    private fun requireDraft(): SyncFinishState = state.value.also {
        check(it.status == SyncFinishStatus.Draft) { "Only a draft can be saved" }
    }

    private fun launchLibraryAction(block: suspend () -> Unit) {
        mutableSavedPlansState.value = mutableSavedPlansState.value.copy(
            isBusy = true,
            errorMessage = null,
        )
        libraryScope.launch {
            runCatching { libraryActionMutex.withLock { block() } }
                .onFailure { error ->
                    mutableSavedPlansState.value = mutableSavedPlansState.value.copy(
                        errorMessage = error.message ?: "Saved plan operation failed",
                    )
                }
            mutableSavedPlansState.value = mutableSavedPlansState.value.copy(isBusy = false)
        }
    }
}
