package com.example.minesweeper.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.minesweeper.model.AppDatabase
import com.example.minesweeper.model.SaveState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val _saveState = MutableStateFlow<SaveState?>(null)
    val saveState: StateFlow<SaveState?> = _saveState

    fun checkSaveState() {
        viewModelScope.launch {
            _saveState.value = db.gameRecordDao().getSaveState()
        }
    }
}
