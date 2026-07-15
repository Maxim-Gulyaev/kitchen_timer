package com.maxim.kitchentimer.syncfinish

import com.maxim.kitchentimer.platform.MonotonicClock
import com.maxim.kitchentimer.timer.TimerTicker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SyncFinishStore(
    private val clock: MonotonicClock,
    private val ticker: TimerTicker,
    coroutineScope: CoroutineScope,
    initialState: SyncFinishState = SyncFinishState(),
) {
    private val storeJob = SupervisorJob(coroutineScope.coroutineContext[Job])
    private val scope = CoroutineScope(coroutineScope.coroutineContext + storeJob)
    private val commands = Channel<SyncFinishCommand>(Channel.UNLIMITED)
    private val mutableState = MutableStateFlow(initialState)
    private val mutableEvents = MutableSharedFlow<SyncFinishEvent>(extraBufferCapacity = 8)
    private val transitionChannel = Channel<SyncFinishEffectTransition>(Channel.UNLIMITED)
    private var tickerJob: Job? = null
    private var tickerEnabled = true

    val state: StateFlow<SyncFinishState> = mutableState.asStateFlow()
    val events: SharedFlow<SyncFinishEvent> = mutableEvents.asSharedFlow()
    internal val transitions: Flow<SyncFinishEffectTransition> = transitionChannel.receiveAsFlow()

    init {
        require(initialState.status != SyncFinishStatus.Running) {
            "A running plan requires lifecycle reconciliation before restore"
        }
        scope.launch {
            for (command in commands) {
                when (command) {
                    is SyncFinishCommand.TickerEnabled -> {
                        tickerEnabled = command.enabled
                        syncTicker()
                    }

                    is SyncFinishCommand.Intent -> {
                        val previous = mutableState.value
                        val transition = SyncFinishReducer.reduce(
                            state = previous,
                            intent = command.intent,
                            nowMillis = clock.nowMillis(),
                        )
                        mutableState.value = transition.state
                        transition.events.forEach { mutableEvents.emit(it) }
                        if (previous.status != transition.state.status || transition.events.isNotEmpty()) {
                            transitionChannel.send(SyncFinishEffectTransition(previous, transition))
                        }
                        syncTicker()
                    }
                }
            }
        }
    }

    fun dispatch(intent: SyncFinishIntent): Boolean =
        commands.trySend(SyncFinishCommand.Intent(intent)).isSuccess

    internal fun setTickerEnabled(enabled: Boolean) {
        commands.trySend(SyncFinishCommand.TickerEnabled(enabled))
    }

    fun close() {
        commands.close()
        transitionChannel.close()
        scope.cancel()
    }

    private fun syncTicker() {
        if (mutableState.value.status != SyncFinishStatus.Running || !tickerEnabled) {
            tickerJob?.cancel()
            tickerJob = null
            return
        }
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                ticker.awaitTick()
                commands.send(SyncFinishCommand.Intent(SyncFinishIntent.Tick))
            }
        }
    }
}

internal data class SyncFinishEffectTransition(
    val previousState: SyncFinishState,
    val transition: SyncFinishTransition,
)

private sealed interface SyncFinishCommand {
    data class Intent(val intent: SyncFinishIntent) : SyncFinishCommand
    data class TickerEnabled(val enabled: Boolean) : SyncFinishCommand
}
