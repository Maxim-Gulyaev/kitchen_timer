package com.maxim.kitchentimer.syncfinish

import com.maxim.kitchentimer.syncfinish.persistence.SavedCookingPlan

data class SavedPlansState(
    val plans: List<SavedCookingPlan> = emptyList(),
    val openedPlan: SavedCookingPlan? = null,
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
) {
    fun isDirty(draft: SyncFinishState): Boolean {
        val saved = openedPlan ?: return true
        return saved.components != draft.components || saved.serveAfter != draft.serveAfter
    }
}
