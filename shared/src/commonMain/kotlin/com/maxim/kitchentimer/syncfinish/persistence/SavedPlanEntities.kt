package com.maxim.kitchentimer.syncfinish.persistence

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "saved_plans")
data class SavedPlanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val serveAfterMillis: Long,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "saved_components",
    primaryKeys = ["planId", "componentId"],
    foreignKeys = [
        ForeignKey(
            entity = SavedPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("planId")],
)
data class SavedComponentEntity(
    val planId: Long,
    val componentId: String,
    val position: Int,
    val name: String,
    val cookingDurationMillis: Long,
    val restingDurationMillis: Long,
)

data class SavedPlanWithComponents(
    @Embedded val plan: SavedPlanEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "planId",
    )
    val components: List<SavedComponentEntity>,
)
