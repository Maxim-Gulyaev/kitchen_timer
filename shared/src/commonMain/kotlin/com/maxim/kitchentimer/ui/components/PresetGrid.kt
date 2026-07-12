package com.maxim.kitchentimer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.maxim.kitchentimer.timer.TimerPreset

@Composable
fun PresetGrid(
    presets: List<TimerPreset>,
    selectedPresetId: String?,
    enabled: Boolean,
    onPresetSelected: (TimerPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        presets.chunked(3).forEach { rowPresets ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowPresets.forEach { preset ->
                    FilterChip(
                        selected = selectedPresetId == preset.id,
                        onClick = { onPresetSelected(preset) },
                        label = { Text(preset.label) },
                        enabled = enabled,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                    )
                }
            }
        }
    }
}
