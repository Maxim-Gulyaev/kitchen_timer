package com.maxim.kitchentimer.syncfinish.persistence

import android.content.Context
import androidx.room.Room

class AndroidCookingPlanPersistence(context: Context) {
    private val database = createAndroidKitchenTimerDatabase(context)
    val repository: CookingPlanRepository = RoomCookingPlanRepository(
        dao = database.savedPlanDao(),
        clock = EpochMillisClock(System::currentTimeMillis),
    )

    fun close() {
        database.close()
    }
}

private fun createAndroidKitchenTimerDatabase(context: Context): KitchenTimerDatabase =
    buildKitchenTimerDatabase(
        Room.databaseBuilder<KitchenTimerDatabase>(
            context = context.applicationContext,
            name = context.getDatabasePath(DATABASE_NAME).absolutePath,
        ),
    )

private const val DATABASE_NAME = "kitchen-timer.db"
