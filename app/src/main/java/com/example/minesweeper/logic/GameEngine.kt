package com.example.minesweeper.logic

import com.example.minesweeper.model.*
import kotlin.random.Random

class GameEngine(
    var width: Int = 200,
    var height: Int = 200
) {
    var cells: Array<Array<Cell>> = Array(height) { y ->
        Array(width) { x -> Cell(x, y) }
    }
    
    var status: GameStatus = GameStatus.READY
    var difficulty: Difficulty = Difficulty.NORMAL
    var mineCount: Int = 0
    var flagCount: Int = 0
    var isFirstTap: Boolean = true

    fun startNewGame(difficulty: Difficulty) {
        this.difficulty = difficulty
        this.status = GameStatus.PLAYING
        this.isFirstTap = true
        this.flagCount = 0
        
        // Reset cells
        cells = Array(height) { y ->
            Array(width) { x -> Cell(x, y) }
        }
    }

    private fun placeMines(firstX: Int, firstY: Int) {
        val random = Random.Default
        var placedMines = 0
        val targetMineCount = (width * height * difficulty.mineProbability).toInt()
        this.mineCount = targetMineCount

        // 効率化：最初に全てのadjacentMineCountを0にする
        for (y in 0 until height) {
            for (x in 0 until width) {
                cells[y][x].adjacentMineCount = 0
            }
        }

        while (placedMines < targetMineCount) {
            val rx = random.nextInt(width)
            val ry = random.nextInt(height)

            if (Math.abs(rx - firstX) <= 1 && Math.abs(ry - firstY) <= 1) continue
            
            if (!cells[ry][rx].isMine) {
                cells[ry][rx].isMine = true
                placedMines++
                updateAdjacentCounts(rx, ry, 1) // 地雷を置いた瞬間に周囲を+1
            }
        }
    }

    private fun updateAdjacentCounts(mineX: Int, mineY: Int, delta: Int) {
        for (dy in -1..1) {
            for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val ny = mineY + dy
                val nx = mineX + dx
                if (ny in 0 until height && nx in 0 until width) {
                    cells[ny][nx].adjacentMineCount += delta
                }
            }
        }
    }

    private fun calculateAdjacentMines() {
        // legacy method - now handled incrementally by updateAdjacentCounts
    }

    fun toggleFlag(x: Int, y: Int) {
        if (status != GameStatus.PLAYING) return
        val cell = cells[y][x]
        if (cell.state == CellState.OPENED) return

        if (cell.state == CellState.FLAGGED) {
            cell.state = CellState.CLOSED
            flagCount--
        } else {
            cell.state = CellState.FLAGGED
            flagCount++
        }
    }

    fun openCell(x: Int, y: Int) {
        if (status != GameStatus.PLAYING) return
        val cell = cells[y][x]
        if (cell.state != CellState.CLOSED) return

        if (isFirstTap) {
            placeMines(x, y)
            isFirstTap = false
            autoOpen(x, y)
        } else if (cell.isMine) {
            // 開いている数字と矛盾しない交換先があれば救済（運ゲーだった）、無ければゲームオーバー
            val target = findRescueTarget(x, y)
            if (target == null) {
                gameOver(x, y)
            } else {
                moveMine(x, y, target.first, target.second)
                autoOpen(x, y)
            }
        } else {
            autoOpen(x, y)
        }

        checkWin()
    }

    /** (x, y) の周囲にある開いたマスの座標集合（y * width + x で表す） */
    private fun openedNeighborKeys(x: Int, y: Int): Set<Int> {
        val keys = HashSet<Int>()
        for (dy in -1..1) {
            for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val ny = y + dy
                val nx = x + dx
                if (ny in 0 until height && nx in 0 until width && cells[ny][nx].state == CellState.OPENED) {
                    keys.add(ny * width + nx)
                }
            }
        }
        return keys
    }

    private fun isRescueCandidate(x: Int, y: Int, fromX: Int, fromY: Int): Boolean {
        if (x == fromX && y == fromY) return false
        val cell = cells[y][x]
        return !cell.isMine && cell.state == CellState.CLOSED
    }

    /**
     * (x, y) の地雷を移しても、開いている数字が1つも変わらない交換先を探す。
     * 条件は「周囲の開いたマスの集合が (x, y) と完全に同じ」こと。
     * 見つかれば別配置でも盤面情報と矛盾しない（＝確定していなかった）ので救済できる。
     */
    private fun findRescueTarget(x: Int, y: Int): Pair<Int, Int>? {
        val signature = openedNeighborKeys(x, y)
        val candidates = ArrayList<Pair<Int, Int>>()

        if (signature.isEmpty()) {
            // 周囲が未開通の完全な運ゲー：同じく周囲に開いたマスが無い閉じたマスならどこでも良い
            for (ty in 0 until height) {
                for (tx in 0 until width) {
                    if (isRescueCandidate(tx, ty, x, y) && openedNeighborKeys(tx, ty).isEmpty()) {
                        candidates.add(tx to ty)
                    }
                }
            }
        } else {
            // 交換先は全ての開いた隣接マスの隣でもあるので、その1つの周囲8マスだけ調べれば足りる
            val anchor = signature.first()
            val ax = anchor % width
            val ay = anchor / width
            for (dy in -1..1) {
                for (dx in -1..1) {
                    val tx = ax + dx
                    val ty = ay + dy
                    if (tx in 0 until width && ty in 0 until height &&
                        isRescueCandidate(tx, ty, x, y) && openedNeighborKeys(tx, ty) == signature
                    ) {
                        candidates.add(tx to ty)
                    }
                }
            }
        }
        return if (candidates.isEmpty()) null else candidates[Random.Default.nextInt(candidates.size)]
    }

    private fun moveMine(fromX: Int, fromY: Int, toX: Int, toY: Int) {
        cells[fromY][fromX].isMine = false
        updateAdjacentCounts(fromX, fromY, -1)
        cells[toY][toX].isMine = true
        updateAdjacentCounts(toX, toY, 1)
    }

    private fun autoOpen(startX: Int, startY: Int) {
        val queue = java.util.ArrayDeque<Pair<Int, Int>>()
        queue.add(startX to startY)

        var openedCount = 0

        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeFirst()
            val cell = cells[y][x]

            if (cell.state != CellState.CLOSED || cell.isMine) continue

            cell.state = CellState.OPENED
            openedCount++

            if (cell.adjacentMineCount == 0) {
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val ny = y + dy
                        val nx = x + dx
                        if (ny in 0 until height && nx in 0 until width) {
                            if (cells[ny][nx].state == CellState.CLOSED) {
                                queue.add(nx to ny)
                            }
                        }
                    }
                }
            }
        }
    }

    fun quickOpen(x: Int, y: Int) {
        if (status != GameStatus.PLAYING) return
        val cell = cells[y][x]
        if (cell.state != CellState.OPENED || cell.adjacentMineCount == 0) return

        var flags = 0
        for (dy in -1..1) {
            for (dx in -1..1) {
                val ny = y + dy
                val nx = x + dx
                if (ny in 0 until height && nx in 0 until width && cells[ny][nx].state == CellState.FLAGGED) {
                    flags++
                }
            }
        }

        if (flags == cell.adjacentMineCount) {
            for (dy in -1..1) {
                for (dx in -1..1) {
                    val ny = y + dy
                    val nx = x + dx
                    if (ny in 0 until height && nx in 0 until width) {
                        val neighbor = cells[ny][nx]
                        if (neighbor.state == CellState.CLOSED) {
                            if (neighbor.isMine) {
                                gameOver(nx, ny)
                                return
                            } else {
                                autoOpen(nx, ny)
                            }
                        }
                    }
                }
            }
            checkWin()
        }
    }

    private fun gameOver(hitX: Int, hitY: Int) {
        status = GameStatus.LOST
        cells[hitY][hitX].isRedMine = true
        // Show all mines and wrong flags
        for (y in 0 until height) {
            for (x in 0 until width) {
                val cell = cells[y][x]
                if (cell.isMine && cell.state != CellState.FLAGGED) {
                    cell.state = CellState.OPENED
                } else if (!cell.isMine && cell.state == CellState.FLAGGED) {
                    cell.isWrongFlag = true
                }
            }
        }
    }

    private fun checkWin() {
        for (y in 0 until height) {
            for (x in 0 until width) {
                val cell = cells[y][x]
                if (!cell.isMine && cell.state != CellState.OPENED) return
            }
        }
        status = GameStatus.WON
        // Auto flag all mines
        for (y in 0 until height) {
            for (x in 0 until width) {
                val cell = cells[y][x]
                if (cell.isMine) cell.state = CellState.FLAGGED
            }
        }
    }

    fun serialize(): ByteArray {
        val buffer = java.nio.ByteBuffer.allocate(width * height * 2)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val cell = cells[y][x]
                // 1st byte: isMine (1), state (2), isRedMine (1), isWrongFlag (1), padding (3)
                var b1 = 0
                if (cell.isMine) b1 = b1 or 0x01
                b1 = b1 or (cell.state.ordinal shl 1)
                if (cell.isRedMine) b1 = b1 or 0x08
                if (cell.isWrongFlag) b1 = b1 or 0x10
                buffer.put(b1.toByte())
                // 2nd byte: adjacentMineCount
                buffer.put(cell.adjacentMineCount.toByte())
            }
        }
        return buffer.array()
    }

    fun deserialize(data: ByteArray) {
        val buffer = java.nio.ByteBuffer.wrap(data)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val b1 = buffer.get().toInt()
                val b2 = buffer.get().toInt()
                val cell = cells[y][x]
                cell.isMine = (b1 and 0x01) != 0
                cell.state = CellState.values()[(b1 shr 1) and 0x03]
                cell.isRedMine = (b1 and 0x08) != 0
                cell.isWrongFlag = (b1 and 0x10) != 0
                cell.adjacentMineCount = b2
            }
        }
    }
}
