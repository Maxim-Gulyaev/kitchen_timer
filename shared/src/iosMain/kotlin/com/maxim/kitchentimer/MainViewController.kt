package com.maxim.kitchentimer

import androidx.compose.ui.window.ComposeUIViewController
import com.maxim.kitchentimer.platform.createIosTimerPlatformServices
import com.maxim.kitchentimer.syncfinish.persistence.IosCookingPlanPersistence

fun MainViewController() = IosCookingPlanPersistence().let { persistence ->
    val services = createIosTimerPlatformServices()
    ComposeUIViewController {
        App(
            platformServices = services,
            plansRepository = persistence.repository,
        )
    }
}
