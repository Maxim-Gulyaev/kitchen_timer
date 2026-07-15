package com.maxim.kitchentimer.ui.syncfinish

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.maxim.kitchentimer.syncfinish.CookingComponent
import com.maxim.kitchentimer.syncfinish.MAX_SYNC_FINISH_COMPONENTS
import com.maxim.kitchentimer.syncfinish.SyncFinishIntent
import com.maxim.kitchentimer.syncfinish.SyncFinishScheduler
import com.maxim.kitchentimer.syncfinish.SyncFinishState
import kotlin.time.Duration.Companion.minutes

@Composable
fun SyncFinishEditorScreen(
    state: SyncFinishState,
    onIntent: (SyncFinishIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeContentPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) {
                    Text("Back")
                }
                Text(
                    text = "Sync Finish",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Text(
                text = "Build one timeline so every part of the meal is ready together.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.components.forEachIndexed { index, component ->
                ComponentEditor(
                    number = index + 1,
                    component = component,
                    canRemove = state.components.size > 1,
                    onIntent = onIntent,
                )
            }

            FilledTonalButton(
                onClick = { onIntent(SyncFinishIntent.AddComponent) },
                enabled = state.components.size < MAX_SYNC_FINISH_COMPONENTS,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Add component")
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Serve together",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    MinutesField(
                        label = "Serve in (minutes)",
                        minutes = state.serveAfter.inWholeMinutes,
                        onMinutesChange = {
                            onIntent(SyncFinishIntent.ChangeServeAfter(it.minutes))
                        },
                    )
                    val minimum = SyncFinishScheduler.minimumServeAfter(state.components)
                    TextButton(
                        onClick = { onIntent(SyncFinishIntent.UseMinimumServeTime) },
                    ) {
                        Text("Use minimum: ${minimum.inWholeMinutes} min")
                    }
                }
            }

            state.validationError?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Button(
                onClick = { onIntent(SyncFinishIntent.Start) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Start cooking plan")
            }
        }
    }
}

@Composable
private fun ComponentEditor(
    number: Int,
    component: CookingComponent,
    canRemove: Boolean,
    onIntent: (SyncFinishIntent) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Component $number",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(
                    onClick = { onIntent(SyncFinishIntent.RemoveComponent(component.id)) },
                    enabled = canRemove,
                ) {
                    Text("Remove")
                }
            }
            OutlinedTextField(
                value = component.name,
                onValueChange = {
                    onIntent(SyncFinishIntent.RenameComponent(component.id, it))
                },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MinutesField(
                    label = "Cook (min)",
                    minutes = component.cookingDuration.inWholeMinutes,
                    onMinutesChange = {
                        onIntent(
                            SyncFinishIntent.ChangeCookingDuration(component.id, it.minutes),
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
                MinutesField(
                    label = "Rest (min)",
                    minutes = component.restingDuration.inWholeMinutes,
                    onMinutesChange = {
                        onIntent(
                            SyncFinishIntent.ChangeRestingDuration(component.id, it.minutes),
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MinutesField(
    label: String,
    minutes: Long,
    onMinutesChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = minutes.toString(),
        onValueChange = { value ->
            onMinutesChange(
                value.filter(Char::isDigit)
                    .take(3)
                    .toIntOrNull()
                    ?.coerceIn(0, 999)
                    ?: 0,
            )
        },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
    )
}
