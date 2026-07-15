package com.maxim.kitchentimer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.maxim.kitchentimer.platform.TimerController
import com.maxim.kitchentimer.platform.TimerPlatformServices
import com.maxim.kitchentimer.timer.TimerIntent
import com.maxim.kitchentimer.syncfinish.SyncFinishController
import com.maxim.kitchentimer.syncfinish.SyncFinishIntent
import com.maxim.kitchentimer.syncfinish.SyncFinishStatus
import com.maxim.kitchentimer.syncfinish.persistence.CookingPlanRepository
import com.maxim.kitchentimer.syncfinish.persistence.InMemoryCookingPlanRepository
import com.maxim.kitchentimer.ui.TimerScreen
import com.maxim.kitchentimer.ui.SettingsScreen
import com.maxim.kitchentimer.ui.syncfinish.SyncFinishEditorScreen
import com.maxim.kitchentimer.ui.syncfinish.SyncFinishRunningScreen
import com.maxim.kitchentimer.ui.syncfinish.SavedPlansScreen
import com.maxim.kitchentimer.ui.theme.KitchenTimerTheme

@Composable
@Preview
fun App(
    platformServices: TimerPlatformServices? = null,
    plansRepository: CookingPlanRepository? = null,
) {
    val scope = rememberCoroutineScope()
    val services = remember(platformServices) {
        platformServices ?: TimerPlatformServices()
    }
    val resolvedPlansRepository = remember(plansRepository) {
        plansRepository ?: InMemoryCookingPlanRepository()
    }
    val timerController = remember(scope, services) {
        TimerController(services, scope)
    }
    val syncFinishController = remember(scope, services, resolvedPlansRepository) {
        SyncFinishController(services, scope, resolvedPlansRepository)
    }
    DisposableEffect(timerController, syncFinishController) {
        onDispose {
            syncFinishController.close()
            timerController.close()
        }
    }
    App(timerController, syncFinishController)
}

@Composable
fun App(
    controller: TimerController,
    syncFinishController: SyncFinishController,
    onIntent: (TimerIntent) -> Unit = { controller.dispatch(it) },
    onSyncFinishIntent: (SyncFinishIntent) -> Unit = { syncFinishController.dispatch(it) },
    onChooseTimerSound: () -> Unit = {},
) {
    KitchenTimerTheme {
        val state by controller.state.collectAsState()
        val syncFinishState by syncFinishController.state.collectAsState()
        val savedPlansState by syncFinishController.savedPlansState.collectAsState()
        val selectedSound by controller.selectedSound.collectAsState()
        var currentScreen by remember { mutableStateOf(AppScreen.Timer) }

        when (currentScreen) {
            AppScreen.Timer -> TimerScreen(
                state = state,
                onIntent = onIntent,
                onOpenSettings = { currentScreen = AppScreen.Settings },
                onOpenSyncFinish = { currentScreen = AppScreen.SyncFinishLibrary },
            )

            AppScreen.Settings -> SettingsScreen(
                selectedSound = selectedSound,
                canChooseSound = controller.canChooseSound,
                onBack = { currentScreen = AppScreen.Timer },
                onChooseSound = onChooseTimerSound,
                onPreviewSound = controller::previewSelectedSound,
                onStopPreview = controller::stopSoundPreview,
            )

            AppScreen.SyncFinishLibrary -> SavedPlansScreen(
                state = savedPlansState,
                onBack = { currentScreen = AppScreen.Timer },
                onNewPlan = {
                    syncFinishController.newPlan()
                    currentScreen = AppScreen.SyncFinishEditor
                },
                onOpenPlan = { id ->
                    syncFinishController.loadSavedPlan(id)
                    currentScreen = AppScreen.SyncFinishEditor
                },
                onRenamePlan = syncFinishController::renameSavedPlan,
                onDuplicatePlan = syncFinishController::duplicateSavedPlan,
                onDeletePlan = syncFinishController::deleteSavedPlan,
                onDismissError = syncFinishController::clearSavedPlansError,
            )

            AppScreen.SyncFinishEditor -> {
                if (syncFinishState.status == SyncFinishStatus.Draft) {
                    SyncFinishEditorScreen(
                        state = syncFinishState,
                        onIntent = onSyncFinishIntent,
                        onBack = { currentScreen = AppScreen.SyncFinishLibrary },
                        openedPlanName = savedPlansState.openedPlan?.name,
                        isDirty = savedPlansState.isDirty(syncFinishState),
                        isBusy = savedPlansState.isBusy,
                        persistenceError = savedPlansState.errorMessage,
                        onSavePlan = syncFinishController::saveCurrentDraft,
                        onUpdatePlan = syncFinishController::updateOpenedPlan,
                        onSavePlanAsCopy = syncFinishController::saveCurrentDraftAsCopy,
                    )
                } else {
                    SyncFinishRunningScreen(
                        state = syncFinishState,
                        onIntent = { intent ->
                            if (intent == SyncFinishIntent.Reset) {
                                syncFinishController.newPlan()
                            } else {
                                onSyncFinishIntent(intent)
                            }
                        },
                    )
                }
            }
        }
    }
}

private enum class AppScreen {
    Timer,
    Settings,
    SyncFinishLibrary,
    SyncFinishEditor,
}
