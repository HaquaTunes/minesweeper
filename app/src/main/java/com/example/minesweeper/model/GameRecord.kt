package com.example.minesweeper.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_records")
data class GameRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val difficulty: String,
    val timeSeconds: Long,
    val dateTimestamp: Long = System.currentTimeMillis()
)
