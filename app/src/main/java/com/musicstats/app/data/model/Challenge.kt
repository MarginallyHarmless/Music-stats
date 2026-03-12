package com.musicstats.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "challenges",
    indices = [Index(value = ["type", "weekStart"], unique = true)]
)
data class Challenge(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val weekStart: Long,
    val title: String,
    val description: String,
    val targetValue: Float,
    val currentValue: Float = 0f,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val generatedAt: Long,
    val metadata: String? = null
)
