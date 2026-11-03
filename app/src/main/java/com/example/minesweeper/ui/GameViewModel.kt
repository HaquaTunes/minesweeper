package com.example.minesweeper.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.minesweeper.logic.GameEngine
import com.example.minesweeper.model.AppDatabase
import com.example.minesweeper.model.Difficulty
import com.example.minesweeper.model.GameRecord
import com.example.minesweeper.model.GameStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GameViewModel(application: android.app.Application) : androidx.lifecycle.AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val engine = GameEngine(200, 200)
    
    private val _gameState = MutableStateFlow(engine)
    val gameState: StateFlow<GameEngine> = _gameState.asStateFlow()

    private val _toggleUpdate = MutableStateFlow(0L)
    val toggleUpdate: StateFlow<Long> = _toggleUpdate.asStateFlow()

    private val _gameStatus = MutableStateFlow(GameStatus.PLAYING)
    val gameStatus: StateFlow<GameStatus> = _gameStatus.asStateFlow()

    private val _elapsedTime = MutableStateFlow(0L)
    val elapsedTime: StateFlow<Long> = _elapsedTime.asStateFlow()

    private var timerJob: Job? = null
    
    private val _isFlagMode = MutableStateFlow(false)
    val isFlagMode: StateFlow<Boolean> = _isFlagMode.asStateFlow()

    private val _restartCount = MutableStateFlow(0)
    val restartCount: StateFlow<Int> = _restartCount.asStateFlow()

    fun startNewGame(difficulty: Difficulty) {
        engine.startNewGame(difficulty)
        
        // 状態の変化をComposeに強制的に通知するため、新しいGameStatusを確実にセット
        _gameStatus.value = GameStatus.READY 
        _gameStatus.value = engine.status // PLAYINGになる
        
        _elapsedTime.value = 0
        stopTimer()
        
        // StateFlowに「変更」を認識させるため、必ずtriggerUpdateを呼ぶ
        triggerUpdate() 
        _restartCount.value++ // Viewを強制再生成させるためのカウンター

        viewModelScope.launch {
            db.gameRecordDao().deleteSaveState()
        }
    }

    fun resumeGame(saveState: com.example.minesweeper.model.SaveState) {
        engine.width = saveState.width // Note: engine properties might need to be var
        engine.height = saveState.height
        engine.difficulty = Difficulty.valueOf(saveState.difficulty)
        engine.status = GameStatus.valueOf(saveState.status)
        _gameStatus.value = engine.status
        engine.mineCount = saveState.mineCount
        engine.flagCount = saveState.flagCount
        engine.isFirstTap = saveState.isFirstTap
        _elapsedTime.value = saveState.elapsedTime
        
        engine.cells = Array(engine.height) { y ->
            Array(engine.width) { x -> com.example.minesweeper.model.Cell(x, y) }
        }
        engine.deserialize(saveState.cellsData)
        
        if (engine.status == GameStatus.PLAYING && !engine.isFirstTap) {
            startTimer()
        }
        triggerUpdate()
        _gameState.value = copyEngineState()
    }

    fun saveGame() {
        if (engine.status != GameStatus.PLAYING) return
        
        viewModelScope.launch {
            val state = com.example.minesweeper.model.SaveState(
                width = engine.width,
                height = engine.height,
                difficulty = engine.difficulty.name,
                status = engine.status.name,
                elapsedTime = _elapsedTime.value,
                flagCount = engine.flagCount,
                mineCount = engine.mineCount,
                isFirstTap = engine.isFirstTap,
                cellsData = engine.serialize()
            )
            db.gameRecordDao().saveGame(state)
        }
    }

    fun onCellClicked(x: Int, y: Int) {
        if (engine.status != GameStatus.PLAYING) return

        // 旗モードの初手は地雷未配置のままなので、まだゲーム開始扱いにしない
        if (engine.isFirstTap && !_isFlagMode.value) {
            startTimer()
        }

        if (_isFlagMode.value) {
            engine.toggleFlag(x, y)
        } else {
            engine.openCell(x, y)
        }
        
        handleStatusChange()
        triggerUpdate()
    }

    private fun triggerUpdate() {
        _toggleUpdate.value++
    }

    private fun handleStatusChange() {
        _gameStatus.value = engine.status
        if (engine.status == GameStatus.WON) {
            stopTimer()
            saveRecord()
            clearSaveState()
        } else if (engine.status == GameStatus.LOST) {
            stopTimer()
            clearSaveState()
        }
    }

    // 決着が付いたゲームを「続きから再開」で巻き戻せないようにする
    private fun clearSaveState() {
        viewModelScope.launch {
            db.gameRecordDao().deleteSaveState()
        }
    }

    private fun saveRecord() {
        viewModelScope.launch {
            db.gameRecordDao().insertRecord(
                GameRecord(
                    difficulty = engine.difficulty.name,
                    timeSeconds = _elapsedTime.value
                )
            )
        }
    }

    fun onCellLongClicked(x: Int, y: Int) {
        if (engine.status != GameStatus.PLAYING) return
        engine.toggleFlag(x, y)
        triggerUpdate()
    }

    fun onQuickOpenClicked(x: Int, y: Int) {
        if (engine.status != GameStatus.PLAYING) return
        engine.quickOpen(x, y)
        handleStatusChange()
        triggerUpdate()
    }

    fun toggleMode() {
        _isFlagMode.value = !_isFlagMode.value
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis() - _elapsedTime.value * 1000
            while (true) {
                _elapsedTime.value = (System.currentTimeMillis() - startTime) / 1000
                delay(1000)
            }
        }
    }

    // バックグラウンド中は時間を止め、復帰時に経過時間から再開する
    fun pauseTimer() {
        stopTimer()
    }

    fun resumeTimer() {
        if (engine.status == GameStatus.PLAYING && !engine.isFirstTap && timerJob == null) {
            startTimer()
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun copyEngineState(): GameEngine {
        return engine
    }
}
