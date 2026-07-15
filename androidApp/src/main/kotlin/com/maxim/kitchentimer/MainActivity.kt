package com.maxim.kitchentimer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
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
import com.maxim.kitchentimer.settings.TimerSoundSetting
import com.maxim.kitchentimer.syncfinish.SyncFinishIntent

class MainActivity : ComponentActivity() {
    private val timerViewModel: AndroidTimerViewModel by viewModels()
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Denial is a supported no-notification mode. */ }
    private val timerSoundPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val uri = result.data?.timerSoundUri() ?: return@registerForActivityResult
        val displayName = RingtoneManager.getRingtone(this, uri)
            ?.getTitle(this)
            ?.takeIf(String::isNotBlank)
            ?: "System sound"
        timerViewModel.controller.selectSound(
            TimerSoundSetting(
                reference = uri.toString(),
                displayName = displayName,
                isDefault = RingtoneManager.getDefaultType(uri) != -1,
            ),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App(
                controller = timerViewModel.controller,
                syncFinishController = timerViewModel.syncFinishController,
                onIntent = ::dispatchTimerIntent,
                onSyncFinishIntent = ::dispatchSyncFinishIntent,
                onChooseTimerSound = ::openTimerSoundPicker,
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

    private fun dispatchSyncFinishIntent(intent: SyncFinishIntent) {
        if (intent == SyncFinishIntent.Start) {
            requestNotificationPermissionIfNeeded()
        }
        timerViewModel.syncFinishController.dispatch(intent)
    }

    private fun openTimerSoundPicker() {
        val existingUri = timerViewModel.controller.selectedSound.value.reference
            ?.let(Uri::parse)
        val pickerIntent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_TYPE,
                RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_NOTIFICATION,
            )
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            )
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existingUri)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Timer completion sound")
        }
        timerSoundPickerLauncher.launch(pickerIntent)
    }
}

@Suppress("DEPRECATION")
private fun Intent.timerSoundUri(): Uri? =
    getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
