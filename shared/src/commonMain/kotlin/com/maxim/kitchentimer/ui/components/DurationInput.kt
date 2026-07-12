package com.maxim.kitchentimer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable
fun DurationInput(
    hours: String,
    minutes: String,
    seconds: String,
    enabled: Boolean,
    onHoursChange: (String) -> Unit,
    onMinutesChange: (String) -> Unit,
    onSecondsChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DurationField(
            value = hours,
            label = "Hr",
            accessibilityLabel = "Hours",
            enabled = enabled,
            onValueChange = onHoursChange,
            modifier = Modifier.weight(1f),
        )
        DurationField(
            value = minutes,
            label = "Min",
            accessibilityLabel = "Minutes",
            enabled = enabled,
            onValueChange = onMinutesChange,
            modifier = Modifier.weight(1f),
        )
        DurationField(
            value = seconds,
            label = "Sec",
            accessibilityLabel = "Seconds",
            enabled = enabled,
            onValueChange = onSecondsChange,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DurationField(
    value: String,
    label: String,
    accessibilityLabel: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.semantics {
            contentDescription = accessibilityLabel
        },
        enabled = enabled,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
    )
}
