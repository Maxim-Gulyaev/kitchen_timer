package com.maxim.kitchentimer.syncfinish.persistence

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

@Database(
    entities = [
        SavedPlanEntity::class,
        SavedComponentEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(KitchenTimerDatabaseConstructor::class)
abstract class KitchenTimerDatabase : RoomDatabase() {
    abstract fun savedPlanDao(): SavedPlanDao
}

@Suppress("KotlinNoActualForExpect")
expect object KitchenTimerDatabaseConstructor :
    RoomDatabaseConstructor<KitchenTimerDatabase> {
    override fun initialize(): KitchenTimerDatabase
}

fun buildKitchenTimerDatabase(
    builder: RoomDatabase.Builder<KitchenTimerDatabase>,
): KitchenTimerDatabase = builder
    .setDriver(BundledSQLiteDriver())
    .build()
