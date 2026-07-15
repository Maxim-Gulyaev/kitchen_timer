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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    openedPlanName: String? = null,
    isDirty: Boolean = true,
    isBusy: Boolean = false,
    persistenceError: String? = null,
    onSavePlan: (String) -> Unit = {},
    onUpdatePlan: () -> Unit = {},
    onSavePlanAsCopy: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var saveDialogMode by remember { mutableStateOf<SaveDialogMode?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }
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
                TextButton(
                    onClick = {
                        if (openedPlanName != null && isDirty) {
                            showDiscardDialog = true
                        } else {
                            onBack()
                        }
                    },
                ) {
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

            openedPlanName?.let { name ->
                Text(
                    text = "Editing saved plan: $name",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

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
            persistenceError?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (openedPlanName == null) {
                FilledTonalButton(
                    onClick = { saveDialogMode = SaveDialogMode.New },
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save plan")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilledTonalButton(
                        onClick = onUpdatePlan,
                        enabled = isDirty && !isBusy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Update")
                    }
                    FilledTonalButton(
                        onClick = { saveDialogMode = SaveDialogMode.Copy },
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Save copy")
                    }
                }
            }

            Button(
                onClick = { onIntent(SyncFinishIntent.Start) },
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Start cooking plan")
            }
        }
    }

    saveDialogMode?.let { mode ->
        PlanNameDialog(
            title = if (mode == SaveDialogMode.New) "Save cooking plan" else "Save a copy",
            initialName = if (mode == SaveDialogMode.New) "" else "$openedPlanName copy",
            confirmLabel = "Save",
            onDismiss = { saveDialogMode = null },
            onConfirm = { name ->
                if (mode == SaveDialogMode.New) onSavePlan(name) else onSavePlanAsCopy(name)
                saveDialogMode = null
            },
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard unsaved changes?") },
            text = { Text("The saved plan will stay unchanged.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onBack()
                    },
                ) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Keep editing") }
            },
        )
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

private enum class SaveDialogMode {
    New,
    Copy,
}
