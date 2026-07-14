package com.maxim.kitchentimer

import android.Manifest
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import android.content.pm.PackageManager
import com.maxim.kitchentimer.timer.TimerIntent

class MainActivity : ComponentActivity() {
    private val timerViewModel: AndroidTimerViewModel by viewModels()
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Denial is a supported no-notification mode. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App(
                controller = timerViewModel.controller,
                onIntent = ::dispatchTimerIntent,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        timerViewModel.appLifecycle.moveToForeground()
    }

    override fun onStop() {
        timerViewModel.appLifecycle.moveToBackground()
        super.onStop()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !timerViewModel.notificationPermissionRequested &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            timerViewModel.markNotificationPermissionRequested()
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun dispatchTimerIntent(intent: TimerIntent) {
        if (
            intent == TimerIntent.Start ||
            intent == TimerIntent.Resume ||
            intent == TimerIntent.Restart
        ) {
            requestNotificationPermissionIfNeeded()
        }
        timerViewModel.controller.dispatch(intent)
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
