package com.example.minesweeper.model

data class Cell(
    val x: Int,
    val y: Int,
    var isMine: Boolean = false,
    var state: CellState = CellState.CLOSED,
    var adjacentMineCount: Int = 0,
    var isRedMine: Boolean = false, // 踏んだ地雷
    var isWrongFlag: Boolean = false // 間違った旗
)
