package com.example.minesweeper.model

enum class Difficulty(val mineProbability: Double) {
    NORMAL(0.18),    // 15% -> 18% に引き上げ
    HARD(0.22),      // 20% -> 22%
    VERY_HARD(0.25)  // 23% -> 25%
}
