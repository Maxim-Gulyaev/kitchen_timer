package com.maxim.kitchentimer.platform

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import com.maxim.kitchentimer.settings.TimerSoundSetting
import com.maxim.kitchentimer.settings.TimerSoundSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration

class AndroidMonotonicClock : MonotonicClock {
    override fun nowMillis(): Long = SystemClock.elapsedRealtime()
}

class AndroidAppLifecycleObserver : AppLifecycleObserver {
    private val mutableForeground = MutableStateFlow(false)
    override val isForeground: StateFlow<Boolean> = mutableForeground.asStateFlow()

    fun moveToForeground() {
        isAppInForeground = true
        mutableForeground.value = true
    }

    fun moveToBackground() {
        mutableForeground.value = false
        isAppInForeground = false
    }

    companion object {
        @Volatile
        internal var isAppInForeground: Boolean = false
            private set
    }
}

class AndroidTimerSoundPlayer(context: Context) : TimerSoundPlayer {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null
    private val stopPlayback = Runnable(::stopOnMainThread)

    override fun playCompletion(soundReference: String?) {
        play(soundReference, looping = true, maxPlaybackMillis = MAX_PLAYBACK_MILLIS)
    }

    override fun preview(soundReference: String?) {
        play(soundReference, looping = false, maxPlaybackMillis = MAX_PREVIEW_MILLIS)
    }

    private fun play(soundReference: String?, looping: Boolean, maxPlaybackMillis: Long) {
        handler.post {
            runCatching {
                stopOnMainThread()
                val uri = resolveTimerSoundUri(appContext, soundReference)
                ringtone = RingtoneManager.getRingtone(appContext, uri)?.apply {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    isLooping = looping
                    play()
                }
                if (ringtone != null) {
                    handler.postDelayed(stopPlayback, maxPlaybackMillis)
                }
            }
        }
    }

    override fun stop() {
        handler.post { runCatching(::stopOnMainThread) }
    }

    private fun stopOnMainThread() {
        handler.removeCallbacks(stopPlayback)
        ringtone?.stop()
        ringtone = null
    }

    private companion object {
        const val MAX_PLAYBACK_MILLIS = 30_000L
        const val MAX_PREVIEW_MILLIS = 5_000L
    }
}

class AndroidTimerSoundSettings(context: Context) : TimerSoundSettings {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableSelectedSound = MutableStateFlow(loadSelection())

    override val selectedSound: StateFlow<TimerSoundSetting> = mutableSelectedSound.asStateFlow()
    override val canChooseSound: Boolean = true

    override fun saveSelection(selection: TimerSoundSetting) {
        val normalized = normalizeSelection(selection)
        preferences.edit()
            .putString(SOUND_URI_KEY, normalized.reference)
            .putString(SOUND_NAME_KEY, normalized.displayName)
            .putBoolean(SOUND_DEFAULT_KEY, normalized.isDefault)
            .apply()
        mutableSelectedSound.value = normalized
    }

    private fun loadSelection(): TimerSoundSetting {
        val storedReference = preferences.getString(SOUND_URI_KEY, null)
        if (storedReference == null) return defaultSelection()
        return normalizeSelection(
            TimerSoundSetting(
                reference = storedReference,
                displayName = preferences.getString(SOUND_NAME_KEY, null)
                    ?.takeIf(String::isNotBlank)
                    ?: soundTitle(appContext, Uri.parse(storedReference)),
                isDefault = preferences.getBoolean(SOUND_DEFAULT_KEY, false),
            ),
        )
    }

    private fun normalizeSelection(selection: TimerSoundSetting): TimerSoundSetting {
        val uri = selection.reference?.let(Uri::parse) ?: return defaultSelection()
        if (!isTimerSoundAvailable(appContext, uri)) return defaultSelection()
        val ringtone = runCatching { RingtoneManager.getRingtone(appContext, uri) }.getOrNull()
            ?: return defaultSelection()
        val title = runCatching { ringtone.getTitle(appContext) }
            .getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: selection.displayName
        return selection.copy(displayName = title)
    }

    private fun defaultSelection(): TimerSoundSetting {
        val uri = defaultTimerSoundUri()
        return TimerSoundSetting(
            reference = uri.toString(),
            displayName = soundTitle(appContext, uri),
            isDefault = true,
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "timer_sound_settings"
        const val SOUND_URI_KEY = "sound_uri"
        const val SOUND_NAME_KEY = "sound_name"
        const val SOUND_DEFAULT_KEY = "sound_is_default"
    }
}

class AndroidTimerHaptics(context: Context) : TimerHaptics {
    private val vibrator = context.applicationContext.getSystemService(Vibrator::class.java)

    override fun performCompletion() {
        if (!vibrator.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(
            longArrayOf(0L, 300L, 150L, 300L, 150L, 500L),
            -1,
        )
        vibrator.vibrate(effect)
    }
}

class AndroidTimerNotifier(context: Context) : TimerNotifier {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    init {
        appContext.getSystemService(NotificationManager::class.java)
            .deleteNotificationChannel(LEGACY_NOTIFICATION_CHANNEL_ID)
    }

    override fun scheduleCompletion(after: Duration, soundReference: String?) {
        val triggerAt = SystemClock.elapsedRealtime() + after.inWholeMilliseconds.coerceAtLeast(0L)
        val alarmIntent = timerAlarmPendingIntent(appContext, soundReference)
        ensureTimerNotificationChannel(appContext, soundReference)
        alarmManager.cancel(alarmIntent)
        val canScheduleExact =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

        if (canScheduleExact) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    alarmIntent,
                )
                return
            } catch (_: SecurityException) {
                // Exact alarm access can be revoked between the capability check and scheduling.
            }
        }

        runCatching {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                alarmIntent,
            )
        }
    }

    override fun cancelCompletion() {
        alarmManager.cancel(timerAlarmPendingIntent(appContext))
        appContext.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }
}

class TimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_TIMER_COMPLETED) return
        runCatching {
            if (AndroidAppLifecycleObserver.isAppInForeground) return@runCatching
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return@runCatching
            }

            val soundReference = intent.getStringExtra(EXTRA_SOUND_REFERENCE)
            val channelId = ensureTimerNotificationChannel(context, soundReference)
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            if (!notificationManager.areNotificationsEnabled()) return@runCatching

            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val contentIntent = launchIntent?.let {
                PendingIntent.getActivity(
                    context,
                    0,
                    it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }
            val notification = Notification.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Kitchen timer finished")
                .setContentText("Time is up")
                .setCategory(Notification.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .build()
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }
}

fun createAndroidTimerPlatformServices(
    context: Context,
    lifecycle: AndroidAppLifecycleObserver,
): TimerPlatformServices = TimerPlatformServices(
    clock = AndroidMonotonicClock(),
    soundPlayer = AndroidTimerSoundPlayer(context),
    haptics = AndroidTimerHaptics(context),
    notifier = AndroidTimerNotifier(context),
    lifecycle = lifecycle,
    soundSettings = AndroidTimerSoundSettings(context),
)

private fun ensureTimerNotificationChannel(context: Context, soundReference: String?): String {
    val manager = context.getSystemService(NotificationManager::class.java)
    val soundUri = resolveTimerSoundUri(context, soundReference)
    val channelId = notificationChannelId(soundUri)
    manager.notificationChannels
        .asSequence()
        .filter { it.id.startsWith(NOTIFICATION_CHANNEL_PREFIX) && it.id != channelId }
        .forEach { manager.deleteNotificationChannel(it.id) }
    if (manager.getNotificationChannel(channelId) != null) return channelId
    val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    manager.createNotificationChannel(
        NotificationChannel(
            channelId,
            "Timer completion (${soundTitle(context, soundUri)})",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Alerts when the kitchen timer finishes"
            enableVibration(true)
            setSound(soundUri, audioAttributes)
        },
    )
    return channelId
}

private fun timerAlarmPendingIntent(
    context: Context,
    soundReference: String? = null,
): PendingIntent {
    val intent = Intent(context, TimerAlarmReceiver::class.java)
        .setAction(ACTION_TIMER_COMPLETED)
        .apply {
            soundReference?.let { putExtra(EXTRA_SOUND_REFERENCE, it) }
        }
    return PendingIntent.getBroadcast(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

private fun resolveTimerSoundUri(context: Context, soundReference: String?): Uri {
    val selectedUri = soundReference?.let(Uri::parse)
    if (selectedUri != null && isTimerSoundAvailable(context, selectedUri)) return selectedUri
    return defaultTimerSoundUri()
}

private fun isTimerSoundAvailable(context: Context, uri: Uri): Boolean = runCatching {
    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
}.getOrDefault(false)

private fun defaultTimerSoundUri(): Uri =
    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

private fun soundTitle(context: Context, uri: Uri): String =
    runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }
        .getOrNull()
        ?.takeIf(String::isNotBlank)
        ?: "System sound"

private fun notificationChannelId(soundUri: Uri): String =
    NOTIFICATION_CHANNEL_PREFIX + soundUri.toString().hashCode().toUInt().toString(16)

private const val ACTION_TIMER_COMPLETED = "com.maxim.kitchentimer.action.TIMER_COMPLETED"
private const val EXTRA_SOUND_REFERENCE = "timer_sound_reference"
private const val NOTIFICATION_CHANNEL_PREFIX = "timer_completion_"
private const val LEGACY_NOTIFICATION_CHANNEL_ID = "timer_completion"
private const val NOTIFICATION_ID = 1_001
