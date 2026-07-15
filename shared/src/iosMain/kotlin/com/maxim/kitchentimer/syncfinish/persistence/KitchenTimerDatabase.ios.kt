package com.maxim.kitchentimer.syncfinish.persistence

import androidx.room.Room
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.posix.gettimeofday
import platform.posix.timeval

@OptIn(ExperimentalForeignApi::class)
class IosCookingPlanPersistence {
    private val database = createIosKitchenTimerDatabase()
    val repository: CookingPlanRepository = RoomCookingPlanRepository(
        dao = database.savedPlanDao(),
        clock = EpochMillisClock(::currentEpochMillis),
    )

    fun close() {
        database.close()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun currentEpochMillis(): Long = memScoped {
    val time = alloc<timeval>()
    gettimeofday(time.ptr, null)
    time.tv_sec * MILLIS_PER_SECOND + time.tv_usec / MICROS_PER_MILLI
}

@OptIn(ExperimentalForeignApi::class)
private fun createIosKitchenTimerDatabase(): KitchenTimerDatabase {
    val directory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    val path = requireNotNull(directory?.path) + "/$DATABASE_NAME"
    return buildKitchenTimerDatabase(
        Room.databaseBuilder<KitchenTimerDatabase>(name = path),
    )
}

private const val DATABASE_NAME = "kitchen-timer.db"
private const val MILLIS_PER_SECOND = 1_000L
private const val MICROS_PER_MILLI = 1_000L
