package com.maxim.kitchentimer.timer

import com.maxim.kitchentimer.platform.MonotonicClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/** A ticker implementation must not fail except through coroutine cancellation. */
fun interface TimerTicker {
    suspend fun awaitTick()
}

class CoroutineTimerTicker(
    private val intervalMillis: Long = 500L,
) : TimerTicker {
    init {
        require(intervalMillis > 0L) { "Ticker interval must be positive" }
    }

    override suspend fun awaitTick() {
        delay(intervalMillis)
    }
}

/**
 * Common state holder that serializes user intents and ticker updates.
 * Public observers may collect [events]; ordered platform effects are coordinated separately.
 */
class TimerStore(
    clock: MonotonicClock,
    ticker: TimerTicker,
    coroutineScope: CoroutineScope,
    initialState: TimerState = TimerState(),
) {
    private val storeJob = SupervisorJob(coroutineScope.coroutineContext[Job])
    private val scope = CoroutineScope(coroutineScope.coroutineContext + storeJob)
    private val commands = Channel<TimerStoreCommand>(Channel.UNLIMITED)
    private val mutableEvents = MutableSharedFlow<TimerEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val transitionChannel = Channel<TimerEffectTransition>(Channel.UNLIMITED)

    private val mutableState = MutableStateFlow(initialState)
    val state: StateFlow<TimerState> = mutableState.asStateFlow()

    val events: SharedFlow<TimerEvent> = mutableEvents.asSharedFlow()
    internal val transitions: Flow<TimerEffectTransition> = transitionChannel.receiveAsFlow()

    private var tickerJob: Job? = null
    private var tickerEnabled = true

    init {
        require(initialState.status != TimerStatus.Running) {
            "A new store cannot restore a running timer without lifecycle reconciliation"
        }

        scope.launch {
            for (command in commands) {
                if (command is TimerStoreCommand.TickerEnabled) {
                    tickerEnabled = command.enabled
                    syncTicker(mutableState.value.status, ticker)
                    continue
                }
                val intent = (command as TimerStoreCommand.Intent).intent
                val previousState = mutableState.value
                val transition = TimerReducer.reduce(
                    state = previousState,
                    intent = intent,
                    nowMillis = clock.nowMillis(),
                )
                mutableState.value = transition.state
                transition.event?.let { mutableEvents.emit(it) }
                if (previousState.status != transition.state.status || transition.event != null) {
                    transitionChannel.send(TimerEffectTransition(previousState, transition))
                }
                syncTicker(transition.state.status, ticker)
            }
        }
    }

    /** Enqueues an intent for ordered processing. Returns false after [close]. */
    fun dispatch(intent: TimerIntent): Boolean =
        commands.trySend(TimerStoreCommand.Intent(intent)).isSuccess

    internal fun setTickerEnabled(enabled: Boolean) {
        commands.trySend(TimerStoreCommand.TickerEnabled(enabled))
    }

    fun close() {
        commands.close()
        transitionChannel.close()
        scope.cancel()
    }

    private fun syncTicker(status: TimerStatus, ticker: TimerTicker) {
        if (status != TimerStatus.Running || !tickerEnabled) {
            tickerJob?.cancel()
            tickerJob = null
            return
        }
        if (tickerJob?.isActive == true) return

        tickerJob = scope.launch {
            while (isActive) {
                ticker.awaitTick()
                commands.send(TimerStoreCommand.Intent(TimerIntent.Tick))
            }
        }
    }
}

internal data class TimerEffectTransition(
    val previousState: TimerState,
    val transition: TimerTransition,
)

private sealed interface TimerStoreCommand {
    data class Intent(val intent: TimerIntent) : TimerStoreCommand
    data class TickerEnabled(val enabled: Boolean) : TimerStoreCommand
}
