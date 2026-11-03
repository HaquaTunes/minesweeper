package com.example.minesweeper.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GameRecordDao {
    @Query("SELECT * FROM game_records ORDER BY dateTimestamp DESC")
    fun getAllRecords(): Flow<List<GameRecord>>

    @Insert
    suspend fun insertRecord(record: GameRecord)

    @Query("SELECT * FROM save_state WHERE id = 0")
    suspend fun getSaveState(): SaveState?

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun saveGame(state: SaveState)

    @Query("DELETE FROM save_state WHERE id = 0")
    suspend fun deleteSaveState()
}
