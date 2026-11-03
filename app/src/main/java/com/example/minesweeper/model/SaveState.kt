package com.example.minesweeper.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "save_state")
data class SaveState(
    @PrimaryKey val id: Int = 0,
    val width: Int,
    val height: Int,
    val difficulty: String,
    val status: String,
    val elapsedTime: Long,
    val flagCount: Int,
    val mineCount: Int,
    val isFirstTap: Boolean,
    val cellsData: ByteArray // 40,000マスの状態をシリアライズしたデータ
)
