package com.maxim.kitchentimer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.tooling.preview.Preview
import com.maxim.kitchentimer.timer.CoroutineTimerTicker
import com.maxim.kitchentimer.timer.DefaultMonotonicClock
import com.maxim.kitchentimer.timer.TimerStore
import com.maxim.kitchentimer.ui.TimerScreen
import com.maxim.kitchentimer.ui.theme.KitchenTimerTheme

@Composable
@Preview
fun App() {
    KitchenTimerTheme {
        val scope = rememberCoroutineScope()
        val store = remember(scope) {
            TimerStore(
                clock = DefaultMonotonicClock(),
                ticker = CoroutineTimerTicker(),
                coroutineScope = scope,
            )
        }
        DisposableEffect(store) {
            onDispose(store::close)
        }

        val state by store.state.collectAsState()
        TimerScreen(
            state = state,
            onIntent = store::dispatch,
        )
    }
}
