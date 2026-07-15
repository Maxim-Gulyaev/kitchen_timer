package com.maxim.kitchentimer.platform

import com.maxim.kitchentimer.settings.TimerSoundSetting
import com.maxim.kitchentimer.timer.CoroutineTimerTicker
import com.maxim.kitchentimer.timer.TimerIntent
import com.maxim.kitchentimer.timer.TimerState
import com.maxim.kitchentimer.timer.TimerStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

class TimerController(
    private val services: TimerPlatformServices,
    coroutineScope: CoroutineScope,
) {
    private val store = TimerStore(
        clock = services.clock,
        ticker = CoroutineTimerTicker(),
        coroutineScope = coroutineScope,
    )
    private val coordinator = TimerCoordinator(store, services, coroutineScope)

    val state: StateFlow<TimerState> = coordinator.state
    val selectedSound: StateFlow<TimerSoundSetting> = services.soundSettings.selectedSound
    val canChooseSound: Boolean = services.soundSettings.canChooseSound

    fun dispatch(intent: TimerIntent): Boolean = coordinator.dispatch(intent)

    fun selectSound(selection: TimerSoundSetting) {
        services.soundSettings.saveSelection(selection)
    }

    fun previewSelectedSound() {
        services.soundPlayer.preview(selectedSound.value.reference)
    }

    fun stopSoundPreview() {
        services.soundPlayer.stop()
    }

    fun close() {
        coordinator.close()
        store.close()
    }
}
