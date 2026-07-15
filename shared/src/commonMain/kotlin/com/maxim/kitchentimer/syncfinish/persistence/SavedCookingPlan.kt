package com.maxim.kitchentimer.syncfinish.persistence

import com.maxim.kitchentimer.syncfinish.CookingComponent
import kotlin.time.Duration

data class SavedCookingPlan(
    val id: Long,
    val name: String,
    val components: List<CookingComponent>,
    val serveAfter: Duration,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

fun interface EpochMillisClock {
    fun nowMillis(): Long
}
