package com.maxim.kitchentimer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.tooling.preview.Preview
import com.maxim.kitchentimer.platform.TimerCoordinator
import com.maxim.kitchentimer.platform.TimerPlatformServices
import com.maxim.kitchentimer.timer.CoroutineTimerTicker
import com.maxim.kitchentimer.timer.TimerStore
import com.maxim.kitchentimer.ui.TimerScreen
import com.maxim.kitchentimer.ui.theme.KitchenTimerTheme

@Composable
@Preview
fun App(platformServices: TimerPlatformServices? = null) {
    KitchenTimerTheme {
        val scope = rememberCoroutineScope()
        val services = remember(platformServices) {
            platformServices ?: TimerPlatformServices()
        }
        val store = remember(scope, services.clock) {
            TimerStore(
                clock = services.clock,
                ticker = CoroutineTimerTicker(),
                coroutineScope = scope,
            )
        }
        val coordinator = remember(store, services, scope) {
            TimerCoordinator(
                store = store,
                services = services,
                coroutineScope = scope,
            )
        }
        DisposableEffect(store, coordinator) {
            onDispose {
                coordinator.close()
                store.close()
            }
        }

        val state by coordinator.state.collectAsState()
        TimerScreen(
            state = state,
            onIntent = coordinator::dispatch,
        )
    }
}
