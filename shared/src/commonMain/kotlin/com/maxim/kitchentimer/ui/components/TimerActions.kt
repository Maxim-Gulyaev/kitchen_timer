package com.maxim.kitchentimer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.maxim.kitchentimer.timer.TimerIntent
import com.maxim.kitchentimer.timer.TimerState
import com.maxim.kitchentimer.timer.TimerStatus
import kotlin.time.Duration.Companion.ZERO

@Composable
fun TimerActions(
    state: TimerState,
    onIntent: (TimerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state.status) {
        TimerStatus.Idle -> Button(
            onClick = { onIntent(TimerIntent.Start) },
            enabled = state.initialDuration > ZERO,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
            Text("Start")
        }

        TimerStatus.Running -> ActiveTimerActions(
            primaryLabel = "Pause",
            primaryIntent = TimerIntent.Pause,
            onIntent = onIntent,
            modifier = modifier,
        )

        TimerStatus.Paused -> ActiveTimerActions(
            primaryLabel = "Resume",
            primaryIntent = TimerIntent.Resume,
            onIntent = onIntent,
            modifier = modifier,
        )

        TimerStatus.Finished -> Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { onIntent(TimerIntent.Stop) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp),
            ) {
                Text("Dismiss")
            }
            Button(
                onClick = { onIntent(TimerIntent.Restart) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp),
            ) {
                Text("Restart")
            }
        }
    }
}

@Composable
private fun ActiveTimerActions(
    primaryLabel: String,
    primaryIntent: TimerIntent,
    onIntent: (TimerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = { onIntent(primaryIntent) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
            Text(primaryLabel)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { onIntent(TimerIntent.Stop) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
            ) {
                Text("Stop")
            }
            OutlinedButton(
                onClick = { onIntent(TimerIntent.Reset) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
            ) {
                Text("Reset")
            }
        }
    }
}
