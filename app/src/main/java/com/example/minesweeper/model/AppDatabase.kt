package com.example.minesweeper.model

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [GameRecord::class, SaveState::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameRecordDao(): GameRecordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "minesweeper_db"
                )
                .fallbackToDestructiveMigration() // 開発中なので単純化
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
