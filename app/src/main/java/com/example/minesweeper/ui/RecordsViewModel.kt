package com.example.minesweeper.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.minesweeper.model.AppDatabase
import com.example.minesweeper.model.GameRecord
import kotlinx.coroutines.flow.Flow

class RecordsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    val allRecords: Flow<List<GameRecord>> = db.gameRecordDao().getAllRecords()
}
