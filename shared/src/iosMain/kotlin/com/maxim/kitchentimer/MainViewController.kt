package com.maxim.kitchentimer

import androidx.compose.ui.window.ComposeUIViewController
import com.maxim.kitchentimer.platform.createIosTimerPlatformServices

fun MainViewController() = createIosTimerPlatformServices().let { services ->
    ComposeUIViewController {
        App(platformServices = services)
    }
}
