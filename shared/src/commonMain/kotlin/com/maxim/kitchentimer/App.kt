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
import com.maxim.kitchentimer.ui.TimerScreen
import com.maxim.kitchentimer.ui.SettingsScreen
import com.maxim.kitchentimer.ui.theme.KitchenTimerTheme

@Composable
@Preview
fun App(platformServices: TimerPlatformServices? = null) {
    val scope = rememberCoroutineScope()
    val services = remember(platformServices) {
        platformServices ?: TimerPlatformServices()
    }
    val controller = remember(scope, services) {
        TimerController(services, scope)
    }
    DisposableEffect(controller) {
        onDispose(controller::close)
    }
    App(controller)
}

@Composable
fun App(
    controller: TimerController,
    onIntent: (TimerIntent) -> Unit = { controller.dispatch(it) },
    onChooseTimerSound: () -> Unit = {},
) {
    KitchenTimerTheme {
        val state by controller.state.collectAsState()
        val selectedSound by controller.selectedSound.collectAsState()
        var currentScreen by remember { mutableStateOf(AppScreen.Timer) }

        when (currentScreen) {
            AppScreen.Timer -> TimerScreen(
                state = state,
                onIntent = onIntent,
                onOpenSettings = { currentScreen = AppScreen.Settings },
            )

            AppScreen.Settings -> SettingsScreen(
                selectedSound = selectedSound,
                canChooseSound = controller.canChooseSound,
                onBack = { currentScreen = AppScreen.Timer },
                onChooseSound = onChooseTimerSound,
                onPreviewSound = controller::previewSelectedSound,
                onStopPreview = controller::stopSoundPreview,
            )
        }
    }
}

private enum class AppScreen {
    Timer,
    Settings,
}
