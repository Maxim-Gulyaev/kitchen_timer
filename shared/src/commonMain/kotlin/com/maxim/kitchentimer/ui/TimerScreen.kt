package com.maxim.kitchentimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.maxim.kitchentimer.timer.TimerIntent
import com.maxim.kitchentimer.timer.TimerPresets
import com.maxim.kitchentimer.timer.TimerState
import com.maxim.kitchentimer.timer.TimerStatus
import com.maxim.kitchentimer.timer.formatTimerDuration
import com.maxim.kitchentimer.ui.components.DurationInput
import com.maxim.kitchentimer.ui.components.PresetGrid
import com.maxim.kitchentimer.ui.components.TimerActions
import com.maxim.kitchentimer.ui.theme.KitchenTimerTheme
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Composable
fun TimerScreen(
    state: TimerState,
    onIntent: (TimerIntent) -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenSyncFinish: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .safeContentPadding(),
        ) {
            val compact = maxHeight < 640.dp
            val sectionSpacing = if (compact) 12.dp else 20.dp
            val duration = if (state.status == TimerStatus.Idle) {
                state.remainingDuration
            } else {
                state.initialDuration
            }
            val hours = duration.inWholeHours.coerceIn(0L, 99L).toInt()
            val minutes = (duration.inWholeMinutes % 60L).toInt()
            val seconds = (duration.inWholeSeconds % 60L).toInt()
            val editingEnabled = state.status == TimerStatus.Idle
            val displayedTime = formatTimerDuration(state.remainingDuration)
            val baseTimerSize = when {
                displayedTime.length > 5 -> 44f
                compact -> 52f
                else -> 64f
            }
            val fontScale = LocalDensity.current.fontScale
            val timerFontSize = (
                baseTimerSize * fontScale.coerceAtMost(1.3f) / fontScale
            ).sp

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = if (compact) 12.dp else 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(sectionSpacing),
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Kitchen Timer",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(48.dp)
                            .semantics { contentDescription = "Open settings" },
                    ) {
                        Text(
                            text = "⚙",
                            fontSize = 24.sp,
                        )
                    }
                }

                TimerStatusBadge(status = state.status)

                FilledTonalButton(
                    onClick = onOpenSyncFinish,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Sync Finish · coordinate a meal")
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 88.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = displayedTime,
                        modifier = Modifier.fillMaxWidth(),
                        color = if (state.status == TimerStatus.Finished) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = timerFontSize,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                }

                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Set duration",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    DurationInput(
                        hours = hours.padded(),
                        minutes = minutes.padded(),
                        seconds = seconds.padded(),
                        enabled = editingEnabled,
                        onHoursChange = { value ->
                            onIntent(
                                TimerIntent.ChangeDuration(
                                    durationOf(value.asFieldValue(99), minutes, seconds),
                                ),
                            )
                        },
                        onMinutesChange = { value ->
                            onIntent(
                                TimerIntent.ChangeDuration(
                                    durationOf(hours, value.asFieldValue(59), seconds),
                                ),
                            )
                        },
                        onSecondsChange = { value ->
                            onIntent(
                                TimerIntent.ChangeDuration(
                                    durationOf(hours, minutes, value.asFieldValue(59)),
                                ),
                            )
                        },
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Quick presets",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    PresetGrid(
                        presets = TimerPresets.all,
                        selectedPresetId = state.selectedPresetId.takeIf {
                            state.remainingDuration == state.initialDuration
                        },
                        enabled = editingEnabled,
                        onPresetSelected = { onIntent(TimerIntent.SelectPreset(it)) },
                    )
                }

                TimerActions(
                    state = state,
                    onIntent = onIntent,
                )

                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun TimerStatusBadge(status: TimerStatus) {
    val finished = status == TimerStatus.Finished
    Surface(
        modifier = Modifier.semantics {
            liveRegion = if (finished) LiveRegionMode.Assertive else LiveRegionMode.Polite
        },
        color = if (finished) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        contentColor = if (finished) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = status.label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private val TimerStatus.label: String
    get() = when (this) {
        TimerStatus.Idle -> "Ready"
        TimerStatus.Running -> "Running"
        TimerStatus.Paused -> "Paused"
        TimerStatus.Finished -> "Time is up"
    }

private fun durationOf(hours: Int, minutes: Int, seconds: Int): Duration =
    hours.hours + minutes.minutes + seconds.seconds

private fun String.asFieldValue(max: Int): Int =
    filter(Char::isDigit)
        .takeLast(2)
        .toIntOrNull()
        ?.coerceIn(0, max)
        ?: 0

private fun Int.padded(): String = toString().padStart(2, '0')

@Preview
@Composable
private fun TimerScreenPreview() {
    KitchenTimerTheme {
        TimerScreen(
            state = TimerState(initialDuration = 5.minutes),
            onIntent = {},
        )
    }
}
