package com.maxim.kitchentimer.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TimerSoundSetting(
    val reference: String? = null,
    val displayName: String = DEFAULT_TIMER_SOUND_NAME,
    val isDefault: Boolean = true,
) {
    init {
        require(displayName.isNotBlank()) { "Sound display name must not be blank" }
    }
}

interface TimerSoundSettings {
    val selectedSound: StateFlow<TimerSoundSetting>
    val canChooseSound: Boolean

    fun saveSelection(selection: TimerSoundSetting)
}

class InMemoryTimerSoundSettings(
    initialSelection: TimerSoundSetting = TimerSoundSetting(),
    override val canChooseSound: Boolean = false,
) : TimerSoundSettings {
    private val mutableSelectedSound = MutableStateFlow(initialSelection)
    override val selectedSound: StateFlow<TimerSoundSetting> = mutableSelectedSound.asStateFlow()

    override fun saveSelection(selection: TimerSoundSetting) {
        mutableSelectedSound.value = selection
    }
}

const val DEFAULT_TIMER_SOUND_NAME = "System default"
