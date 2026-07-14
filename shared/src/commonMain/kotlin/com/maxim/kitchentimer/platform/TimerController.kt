package com.maxim.kitchentimer.platform

import com.maxim.kitchentimer.timer.CoroutineTimerTicker
import com.maxim.kitchentimer.timer.TimerIntent
import com.maxim.kitchentimer.timer.TimerState
import com.maxim.kitchentimer.timer.TimerStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

class TimerController(
    services: TimerPlatformServices,
    coroutineScope: CoroutineScope,
) {
    private val store = TimerStore(
        clock = services.clock,
        ticker = CoroutineTimerTicker(),
        coroutineScope = coroutineScope,
    )
    private val coordinator = TimerCoordinator(store, services, coroutineScope)

    val state: StateFlow<TimerState> = coordinator.state

    fun dispatch(intent: TimerIntent): Boolean = coordinator.dispatch(intent)

    fun close() {
        coordinator.close()
        store.close()
    }
}
