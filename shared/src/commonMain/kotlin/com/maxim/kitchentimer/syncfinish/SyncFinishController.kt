package com.maxim.kitchentimer.syncfinish

import com.maxim.kitchentimer.platform.TimerPlatformServices
import com.maxim.kitchentimer.timer.CoroutineTimerTicker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

class SyncFinishController(
    services: TimerPlatformServices,
    coroutineScope: CoroutineScope,
) {
    private val store = SyncFinishStore(
        clock = services.clock,
        ticker = CoroutineTimerTicker(),
        coroutineScope = coroutineScope,
    )
    private val coordinator = SyncFinishCoordinator(store, services, coroutineScope)

    val state: StateFlow<SyncFinishState> = coordinator.state

    fun dispatch(intent: SyncFinishIntent): Boolean = coordinator.dispatch(intent)

    fun close() {
        coordinator.close()
        store.close()
    }
}
