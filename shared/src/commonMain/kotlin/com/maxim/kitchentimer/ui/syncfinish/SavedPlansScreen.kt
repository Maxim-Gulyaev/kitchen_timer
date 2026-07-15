package com.maxim.kitchentimer.ui.syncfinish

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import com.maxim.kitchentimer.syncfinish.SavedPlansState
import com.maxim.kitchentimer.syncfinish.persistence.SavedCookingPlan
import com.maxim.kitchentimer.timer.formatTimerDuration

@Composable
fun SavedPlansScreen(
    state: SavedPlansState,
    onBack: () -> Unit,
    onNewPlan: () -> Unit,
    onOpenPlan: (Long) -> Unit,
    onRenamePlan: (Long, String) -> Unit,
    onDuplicatePlan: (Long) -> Unit,
    onDeletePlan: (Long) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var planToRename by remember { mutableStateOf<SavedCookingPlan?>(null) }
    var planToDelete by remember { mutableStateOf<SavedCookingPlan?>(null) }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeContentPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("Back") }
                Text(
                    text = "My cooking plans",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Button(
                onClick = onNewPlan,
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("New Sync Finish plan")
            }

            state.errorMessage?.let { error ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = error,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = onDismissError) { Text("Dismiss") }
                    }
                }
            }

            if (state.plans.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "No saved plans yet",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Create a timeline once and reuse it next time.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.plans, key = SavedCookingPlan::id) { plan ->
                        SavedPlanCard(
                            plan = plan,
                            enabled = !state.isBusy,
                            onOpen = { onOpenPlan(plan.id) },
                            onRename = { planToRename = plan },
                            onDuplicate = { onDuplicatePlan(plan.id) },
                            onDelete = { planToDelete = plan },
                        )
                    }
                }
            }
        }
    }

    planToRename?.let { plan ->
        PlanNameDialog(
            title = "Rename plan",
            initialName = plan.name,
            confirmLabel = "Rename",
            onDismiss = { planToRename = null },
            onConfirm = { name ->
                onRenamePlan(plan.id, name)
                planToRename = null
            },
        )
    }

    planToDelete?.let { plan ->
        AlertDialog(
            onDismissRequest = { planToDelete = null },
            title = { Text("Delete ${plan.name}?") },
            text = { Text("The saved plan will be removed permanently.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePlan(plan.id)
                        planToDelete = null
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { planToDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SavedPlanCard(
    plan: SavedCookingPlan,
    enabled: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = plan.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${plan.components.size} components · ${formatTimerDuration(plan.serveAfter)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onRename, enabled = enabled) { Text("Rename") }
                TextButton(onClick = onDuplicate, enabled = enabled) { Text("Copy") }
                TextButton(onClick = onDelete, enabled = enabled) { Text("Delete") }
            }
            Button(
                onClick = onOpen,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Open") }
        }
    }
}

@Composable
internal fun PlanNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_PLAN_NAME_LENGTH) },
                label = { Text("Plan name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private const val MAX_PLAN_NAME_LENGTH = 60
