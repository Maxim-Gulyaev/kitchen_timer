package com.maxim.kitchentimer.timer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

fun interface MonotonicClock {
    fun nowMillis(): Long
}

/** A ticker implementation must not fail except through coroutine cancellation. */
fun interface TimerTicker {
    suspend fun awaitTick()
}

/**
 * Common state holder that serializes user intents and ticker updates.
 * Platform effects consume [events]; they do not belong in this store or reducer.
 */
class TimerStore(
    clock: MonotonicClock,
    ticker: TimerTicker,
    coroutineScope: CoroutineScope,
    initialState: TimerState = TimerState(),
) {
    private val storeJob = SupervisorJob(coroutineScope.coroutineContext[Job])
    private val scope = CoroutineScope(coroutineScope.coroutineContext + storeJob)
    private val intents = Channel<TimerIntent>(Channel.UNLIMITED)
    private val eventChannel = Channel<TimerEvent>(Channel.UNLIMITED)

    private val mutableState = MutableStateFlow(initialState)
    val state: StateFlow<TimerState> = mutableState.asStateFlow()

    val events: Flow<TimerEvent> = eventChannel.receiveAsFlow()

    private var tickerJob: Job? = null

    init {
        require(initialState.status != TimerStatus.Running) {
            "A new store cannot restore a running timer without lifecycle reconciliation"
        }

        scope.launch {
            for (intent in intents) {
                val transition = TimerReducer.reduce(
                    state = mutableState.value,
                    intent = intent,
                    nowMillis = clock.nowMillis(),
                )
                mutableState.value = transition.state
                transition.event?.let { eventChannel.send(it) }
                syncTicker(transition.state.status, ticker)
            }
        }
    }

    /** Enqueues an intent for ordered processing. Returns false after [close]. */
    fun dispatch(intent: TimerIntent): Boolean = intents.trySend(intent).isSuccess

    fun close() {
        intents.close()
        eventChannel.close()
        scope.cancel()
    }

    private fun syncTicker(status: TimerStatus, ticker: TimerTicker) {
        if (status != TimerStatus.Running) {
            tickerJob?.cancel()
            tickerJob = null
            return
        }
        if (tickerJob?.isActive == true) return

        tickerJob = scope.launch {
            while (isActive) {
                ticker.awaitTick()
                intents.send(TimerIntent.Tick)
            }
        }
    }
}
