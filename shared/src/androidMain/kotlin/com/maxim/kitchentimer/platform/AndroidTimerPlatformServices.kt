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
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
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

    override fun playCompletion() {
        handler.post {
            runCatching {
                stopOnMainThread()
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ringtone = RingtoneManager.getRingtone(appContext, uri)?.apply {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    isLooping = true
                    play()
                }
                handler.postDelayed(stopPlayback, MAX_PLAYBACK_MILLIS)
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
    private val alarmIntent = timerAlarmPendingIntent(appContext)

    init {
        ensureTimerNotificationChannel(appContext)
    }

    override fun scheduleCompletion(after: Duration) {
        val triggerAt = SystemClock.elapsedRealtime() + after.inWholeMilliseconds.coerceAtLeast(0L)
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
        alarmManager.cancel(alarmIntent)
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

            ensureTimerNotificationChannel(context)
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
            val notification = Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
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
)

private fun ensureTimerNotificationChannel(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) != null) return
    val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    manager.createNotificationChannel(
        NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Timer completion",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Alerts when the kitchen timer finishes"
            enableVibration(true)
            setSound(alarmSound, audioAttributes)
        },
    )
}

private fun timerAlarmPendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
    context,
    0,
    Intent(context, TimerAlarmReceiver::class.java).setAction(ACTION_TIMER_COMPLETED),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
)

private const val ACTION_TIMER_COMPLETED = "com.maxim.kitchentimer.action.TIMER_COMPLETED"
private const val NOTIFICATION_CHANNEL_ID = "timer_completion"
private const val NOTIFICATION_ID = 1_001
