package com.maxim.kitchentimer.ui.syncfinish

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxim.kitchentimer.syncfinish.SyncFinishIntent
import com.maxim.kitchentimer.syncfinish.SyncFinishState
import com.maxim.kitchentimer.syncfinish.SyncFinishStatus
import com.maxim.kitchentimer.timer.formatTimerDuration

@Composable
fun SyncFinishRunningScreen(
    state: SyncFinishState,
    onIntent: (SyncFinishIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeContentPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = "Sync Finish",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .semantics { liveRegion = LiveRegionMode.Assertive },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = if (state.status == SyncFinishStatus.Finished) {
                            "Ready to serve"
                        } else {
                            "Do this now"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = state.currentInstruction ?: "Plan started — follow the next cue",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            if (state.status == SyncFinishStatus.Running) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Next step in",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = formatTimerDuration(state.remainingToNextCue),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                    Text(
                        text = state.nextCue?.message ?: "Serve now",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                    text = "Timeline",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                state.cues.forEach { cue ->
                    val completed = cue.id in state.emittedCueIds
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(if (completed) "✓" else "○")
                        Text(
                            text = formatTimerDuration(cue.offsetFromStart),
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            text = cue.message,
                            modifier = Modifier.weight(1f),
                            color = if (completed) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }

            if (state.status == SyncFinishStatus.Finished) {
                Button(
                    onClick = { onIntent(SyncFinishIntent.Reset) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Create another plan")
                }
            } else {
                OutlinedButton(
                    onClick = { onIntent(SyncFinishIntent.Cancel) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cancel plan")
                }
            }
        }
    }
}
