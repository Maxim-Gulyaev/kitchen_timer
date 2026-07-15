package com.maxim.kitchentimer.syncfinish.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SavedPlanDao {
    @Transaction
    @Query("SELECT * FROM saved_plans ORDER BY updatedAtMillis DESC, id DESC")
    abstract fun observeAll(): Flow<List<SavedPlanWithComponents>>

    @Transaction
    @Query("SELECT * FROM saved_plans WHERE id = :id")
    abstract suspend fun find(id: Long): SavedPlanWithComponents?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertPlan(plan: SavedPlanEntity): Long

    @Update
    protected abstract suspend fun updatePlan(plan: SavedPlanEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertComponents(components: List<SavedComponentEntity>)

    @Query("DELETE FROM saved_components WHERE planId = :planId")
    protected abstract suspend fun deleteComponents(planId: Long)

    @Query("DELETE FROM saved_plans WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Transaction
    open suspend fun create(
        plan: SavedPlanEntity,
        components: List<SavedComponentEntity>,
    ): Long {
        val planId = insertPlan(plan)
        insertComponents(components.map { it.copy(planId = planId) })
        return planId
    }

    @Transaction
    open suspend fun replace(
        plan: SavedPlanEntity,
        components: List<SavedComponentEntity>,
    ) {
        updatePlan(plan)
        deleteComponents(plan.id)
        insertComponents(components.map { it.copy(planId = plan.id) })
    }
}
