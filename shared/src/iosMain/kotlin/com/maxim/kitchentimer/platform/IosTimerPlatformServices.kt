package com.maxim.kitchentimer.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationState
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.math.max
import kotlin.time.Duration

@OptIn(ExperimentalForeignApi::class)
class IosMonotonicClock : MonotonicClock {
    override fun nowMillis(): Long =
        (NSProcessInfo.processInfo.systemUptime * MILLIS_PER_SECOND).toLong().coerceAtLeast(0L)
}

@OptIn(ExperimentalForeignApi::class)
class IosAppLifecycleObserver : AppLifecycleObserver {
    private val mutableForeground = MutableStateFlow(
        UIApplication.sharedApplication.applicationState ==
            UIApplicationState.UIApplicationStateActive,
    )

    override val isForeground: StateFlow<Boolean> = mutableForeground.asStateFlow()

    init {
        val center = NSNotificationCenter.defaultCenter
        center.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { mutableForeground.value = true }
        center.addObserverForName(
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { mutableForeground.value = false }
    }
}

@OptIn(ExperimentalForeignApi::class)
class IosTimerSoundPlayer : TimerSoundPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var playbackJob: Job? = null

    override fun playCompletion(soundReference: String?) {
        stop()
        playbackJob = scope.launch {
            repeat(COMPLETION_SOUND_REPETITIONS) {
                AudioServicesPlaySystemSound(COMPLETION_SYSTEM_SOUND_ID)
                delay(COMPLETION_SOUND_INTERVAL_MILLIS)
            }
        }
    }

    override fun preview(soundReference: String?) {
        stop()
        AudioServicesPlaySystemSound(COMPLETION_SYSTEM_SOUND_ID)
    }

    override fun stop() {
        playbackJob?.cancel()
        playbackJob = null
    }

    fun close() {
        stop()
        scope.cancel()
    }
}

@OptIn(ExperimentalForeignApi::class)
class IosTimerHaptics : TimerHaptics {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun performCompletion() {
        scope.launch {
            UINotificationFeedbackGenerator().apply {
                prepare()
                notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeWarning)
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
class IosTimerNotifier(
    private val center: UNUserNotificationCenter = UNUserNotificationCenter.currentNotificationCenter(),
) : TimerNotifier {
    override fun scheduleCompletion(after: Duration, soundReference: String?) {
        cancelCompletion()
        center.requestAuthorizationWithOptions(
            options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound,
        ) { _, _ -> }

        val content = UNMutableNotificationContent().apply {
            setTitle("Kitchen timer finished")
            setBody("Time is up")
            setSound(UNNotificationSound.defaultSound)
        }
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(
            timeInterval = max(MIN_NOTIFICATION_DELAY_SECONDS, after.inWholeMilliseconds / MILLIS_PER_SECOND),
            repeats = false,
        )
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = TIMER_NOTIFICATION_ID,
            content = content,
            trigger = trigger,
        )
        center.addNotificationRequest(request, withCompletionHandler = null)
    }

    override fun cancelCompletion() {
        val identifiers = listOf(TIMER_NOTIFICATION_ID)
        center.removePendingNotificationRequestsWithIdentifiers(identifiers)
        center.removeDeliveredNotificationsWithIdentifiers(identifiers)
    }
}

@OptIn(ExperimentalForeignApi::class)
class IosCookingPlanNotifier(
    private val center: UNUserNotificationCenter = UNUserNotificationCenter.currentNotificationCenter(),
) : CookingPlanNotifier {
    private val identifiersByPlan = mutableMapOf<String, List<String>>()

    override fun schedulePlan(
        planId: String,
        cues: List<ScheduledCookingCue>,
        soundReference: String?,
    ) {
        cancelPlan(planId)
        if (cues.isEmpty()) return
        center.requestAuthorizationWithOptions(
            options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound,
        ) { _, _ -> }

        val identifiers = cues.map { cue ->
            val identifier = "$COOKING_PLAN_NOTIFICATION_PREFIX:$planId:${cue.id}"
            val content = UNMutableNotificationContent().apply {
                setTitle(cue.title)
                setBody(cue.message)
                setSound(UNNotificationSound.defaultSound)
            }
            val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(
                timeInterval = max(
                    MIN_NOTIFICATION_DELAY_SECONDS,
                    cue.delay.inWholeMilliseconds / MILLIS_PER_SECOND,
                ),
                repeats = false,
            )
            center.addNotificationRequest(
                UNNotificationRequest.requestWithIdentifier(identifier, content, trigger),
                withCompletionHandler = null,
            )
            identifier
        }
        identifiersByPlan[planId] = identifiers
    }

    override fun cancelPlan(planId: String) {
        val identifiers = identifiersByPlan.remove(planId).orEmpty()
        if (identifiers.isEmpty()) return
        center.removePendingNotificationRequestsWithIdentifiers(identifiers)
        center.removeDeliveredNotificationsWithIdentifiers(identifiers)
    }
}

fun createIosTimerPlatformServices(): TimerPlatformServices = TimerPlatformServices(
    clock = IosMonotonicClock(),
    soundPlayer = IosTimerSoundPlayer(),
    haptics = IosTimerHaptics(),
    notifier = IosTimerNotifier(),
    cookingPlanNotifier = IosCookingPlanNotifier(),
    lifecycle = IosAppLifecycleObserver(),
)

private const val MILLIS_PER_SECOND = 1_000.0
private const val MIN_NOTIFICATION_DELAY_SECONDS = 1.0
private const val COMPLETION_SYSTEM_SOUND_ID = 1_005u
private const val COMPLETION_SOUND_REPETITIONS = 15
private const val COMPLETION_SOUND_INTERVAL_MILLIS = 2_000L
private const val TIMER_NOTIFICATION_ID = "kitchen-timer-completion"
private const val COOKING_PLAN_NOTIFICATION_PREFIX = "kitchen-timer-cooking-plan"
