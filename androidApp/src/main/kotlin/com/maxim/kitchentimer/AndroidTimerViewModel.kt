package com.maxim.kitchentimer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.maxim.kitchentimer.platform.AndroidAppLifecycleObserver
import com.maxim.kitchentimer.platform.TimerController
import com.maxim.kitchentimer.platform.createAndroidTimerPlatformServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class AndroidTimerViewModel(application: Application) : AndroidViewModel(application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val appLifecycle = AndroidAppLifecycleObserver()
    val controller = TimerController(
        services = createAndroidTimerPlatformServices(application, appLifecycle),
        coroutineScope = scope,
    )

    var notificationPermissionRequested: Boolean = false
        private set

    fun markNotificationPermissionRequested() {
        notificationPermissionRequested = true
    }

    override fun onCleared() {
        controller.close()
        scope.cancel()
    }
}
