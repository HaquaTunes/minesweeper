package com.example.minesweeper.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import com.example.minesweeper.logic.GameEngine
import com.example.minesweeper.model.CellState
import com.example.minesweeper.model.GameStatus
import kotlin.math.max
import kotlin.math.min

class MinesweeperView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            // 親指の腹や手のひらの接触による誤作動を完全に防ぐため、
            // 指同士の距離（スパン）が非常に離れている（明確な両手操作）場合のみ許可。
            // 750pxは一般的なスマホ画面幅の約6割に相当し、片手操作ではまず到達しません。
            return detector.currentSpan > 750f
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val factor = detector.scaleFactor
            
            // 指の震えや微小な移動による「カクつき」や「勝手なズーム」を防ぐデッドゾーン。
            // 変化率が3%以内の場合は操作を無視する。
            if (factor > 0.97f && factor < 1.03f) return false

            val oldScale = scaleFactor
            scaleFactor *= factor
            scaleFactor = max(0.2f, min(scaleFactor, 5.0f))
            
            val actualFactor = scaleFactor / oldScale
            translateX = detector.focusX - (detector.focusX - translateX) * actualFactor
            translateY = detector.focusY - (detector.focusY - translateY) * actualFactor

            invalidate()
            return true
        }
    }).apply {
        // ダブルタップ後のドラッグによるクイックズームを無効化。
        // これが有効だと、素早い連打（タップ）がズームとして誤検知される。
        isQuickScaleEnabled = false
    }

    init {
        isClickable = true
        isLongClickable = true
    }

    private var gameEngine: GameEngine? = null
    private var isFlagMode: Boolean = false
    private var onCellClick: ((Int, Int) -> Unit)? = null
    private var onCellLongClick: ((Int, Int) -> Unit)? = null
    private var onQuickOpenClick: ((Int, Int) -> Unit)? = null

    private var pressedCell: Pair<Int, Int>? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat() * 1.5f
    private var startX = 0f
    private var startY = 0f
    private var isDragging = false
    private var longPressTriggered = false

    private val cellBaseSize = 100f
    private var scaleFactor = 1.0f
    private var translateX = 0f
    private var translateY = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 70f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 70f
        style = Paint.Style.STROKE
        strokeWidth = 4f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val flagPath = android.graphics.Path()
    private val reuseRect = RectF()

    // 数字の色と背景色をキャッシュ
    private val numberColors = intArrayOf(
        0,
        Color.parseColor("#64B5F6"), // 1
        Color.parseColor("#81C784"), // 2
        Color.parseColor("#E57373"), // 3
        Color.parseColor("#9575CD"), // 4
        Color.parseColor("#FFB74D"), // 5
        Color.parseColor("#4DD0E1"), // 6
        Color.parseColor("#F06292"), // 7
        Color.parseColor("#BDBDBD")  // 8
    )
    private val openedBgColor = Color.parseColor("#1A1A1A")
    private val gridColor = Color.parseColor("#333333")
    private val bevelLightColor = Color.parseColor("#EEEEEE")
    private val bevelDarkColor = Color.parseColor("#777777")
    private val flagGray = Color.argb(80, 0, 0, 0)
    private val flagRedAlpha = Color.argb(80, 255, 0, 0)

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val vibrationAttributes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_MEDIA).build()
    } else null

    private val vibrationEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        VibrationEffect.createOneShot(10, 50)
    } else null

    private fun performHapticFeedback() {
        vibrator?.let {
            if (!it.hasVibrator()) return@let
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && vibrationAttributes != null && vibrationEffect != null) {
                it.vibrate(vibrationEffect, vibrationAttributes)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrationEffect != null) {
                it.vibrate(vibrationEffect)
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(10)
            }
        }
    }


    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onLongPress(e: MotionEvent) {
            val coords = screenToGrid(e.x, e.y)
            if (coords != null) {
                longPressTriggered = true
                performHapticFeedback()
                onCellLongClick?.invoke(coords.first, coords.second)
            }
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            translateX -= distanceX
            translateY -= distanceY
            invalidate()
            return true
        }
    })

    fun setGameEngine(engine: GameEngine) {
        this.gameEngine = engine
        if (engine.isFirstTap) {
            // 新規ゲーム時は即座に中央へ戻し、ズームをリセットして描き直す
            resetView()
        } else {
            invalidate()
        }
    }

    fun setFlagMode(enabled: Boolean) {
        this.isFlagMode = enabled
        invalidate()
    }

    fun refresh() {
        invalidate()
    }

    fun resetView() {
        scaleFactor = 1.0f
        scrollToCenter()
        invalidate()
    }

    private fun scrollToCenter() {
        val engine = gameEngine ?: return
        val doScroll = {
            val totalWidth = engine.width * cellBaseSize * scaleFactor
            val totalHeight = engine.height * cellBaseSize * scaleFactor
            translateX = (width - totalWidth) / 2f
            translateY = (height - totalHeight) / 2f
            invalidate()
        }
        if (width > 0 && height > 0) {
            doScroll()
        } else {
            post { doScroll() }
        }
    }

    fun setOnCellClickListener(listener: (Int, Int) -> Unit) {
        this.onCellClick = listener
    }

    fun setOnCellLongClickListener(listener: (Int, Int) -> Unit) {
        this.onCellLongClick = listener
    }
    
    fun setOnQuickOpenClickListener(listener: (Int, Int) -> Unit) {
        this.onQuickOpenClick = listener
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        
        if (scaleDetector.isInProgress) {
            pressedCell = null
            invalidate()
            return true
        }

        gestureDetector.onTouchEvent(event)
        
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                isDragging = false
                longPressTriggered = false
                pressedCell = screenToGrid(event.x, event.y)
                invalidate()
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) {
                    val dx = event.x - startX
                    val dy = event.y - startY
                    if (Math.hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                        isDragging = true
                        pressedCell = null
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging && !longPressTriggered) {
                    val coords = screenToGrid(event.x, event.y)
                    if (coords != null && coords == pressedCell) {
                        handleCellClick(coords.first, coords.second)
                        performClick()
                    }
                }
                pressedCell = null
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedCell = null
                invalidate()
            }
        }
        
        return true
    }

    private fun handleCellClick(x: Int, y: Int) {
        performHapticFeedback()
        val engine = gameEngine ?: return
        if (engine.cells[y][x].state == CellState.OPENED) {
            onQuickOpenClick?.invoke(x, y)
        } else {
            onCellClick?.invoke(x, y)
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun screenToGrid(screenX: Float, screenY: Float): Pair<Int, Int>? {
        val engine = gameEngine ?: return null
        val gridX = ((screenX - translateX) / (cellBaseSize * scaleFactor)).toInt()
        val gridY = ((screenY - translateY) / (cellBaseSize * scaleFactor)).toInt()
        
        if (gridX in 0 until engine.width && gridY in 0 until engine.height) {
            return gridX to gridY
        }
        return null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val engine = gameEngine ?: return

        canvas.save()
        canvas.translate(translateX, translateY)
        canvas.scale(scaleFactor, scaleFactor)

        val cellSize = cellBaseSize
        
        // Calculate visible range to optimize
        val visibleLeft = ((-translateX) / (cellSize * scaleFactor)).toInt()
        val visibleTop = ((-translateY) / (cellSize * scaleFactor)).toInt()
        val visibleRight = ((width - translateX) / (cellSize * scaleFactor)).toInt() + 1
        val visibleBottom = ((height - translateY) / (cellSize * scaleFactor)).toInt() + 1

        val startX = max(0, visibleLeft)
        val startY = max(0, visibleTop)
        val endX = min(engine.width - 1, visibleRight)
        val endY = min(engine.height - 1, visibleBottom)

        for (y in startY..endY) {
            for (x in startX..endX) {
                drawCell(canvas, engine.cells[y][x], x, y)
            }
        }

        canvas.restore()
    }

    private fun drawCell(canvas: Canvas, cell: com.example.minesweeper.model.Cell, x: Int, y: Int) {
        val left = x * cellBaseSize
        val top = y * cellBaseSize
        val right = left + cellBaseSize
        val bottom = top + cellBaseSize
        
        // 現在の画面上での実際のサイズ
        val actualSize = cellBaseSize * scaleFactor
        
        when (cell.state) {
            CellState.CLOSED -> {
                val isPressed = pressedCell?.first == x && pressedCell?.second == y
                
                // Base
                paint.color = Color.LTGRAY
                canvas.drawRect(left, top, right, bottom, paint)
                
                // Bevel effect (LOD: skip if too small)
                if (actualSize > 10) {
                    val b = cellBaseSize * 0.1f
                    
                    if (isPressed) {
                        // Inverted bevel for pressed state
                        paint.color = bevelDarkColor
                        canvas.drawRect(left, top, right, top + b, paint)
                        canvas.drawRect(left, top, left + b, bottom, paint)
                        paint.color = bevelLightColor
                        canvas.drawRect(left, bottom - b, right, bottom, paint)
                        canvas.drawRect(right - b, top, right, bottom, paint)
                    } else {
                        // Normal bevel
                        paint.color = bevelLightColor
                        canvas.drawRect(left, top, right, top + b, paint)
                        canvas.drawRect(left, top, left + b, bottom, paint)
                        paint.color = bevelDarkColor
                        canvas.drawRect(left, bottom - b, right, bottom, paint)
                        canvas.drawRect(right - b, top, right, bottom, paint)
                    }
                    
                    // Face
                    paint.color = Color.LTGRAY
                    canvas.drawRect(left + b, top + b, right - b, bottom - b, paint)
                }
                
                // 旗立てモード中のヒント表示
                if (isFlagMode && actualSize > 25) {
                    val centerX = left + cellBaseSize / 2f
                    val centerY = top + cellBaseSize / 2f
                    val size = cellBaseSize * 0.5f
                    
                    paint.color = flagGray
                    canvas.drawRect(centerX - 2, centerY - size / 2, centerX + 2, centerY + size / 2, paint)
                    
                    flagPath.reset()
                    flagPath.moveTo(centerX, centerY - size / 2)
                    flagPath.lineTo(centerX - size / 2, centerY - size / 4)
                    flagPath.lineTo(centerX, centerY)
                    flagPath.close()
                    
                    paint.color = flagRedAlpha
                    canvas.drawPath(flagPath, paint)
                }
            }
            CellState.FLAGGED -> {
                // Base (Closed style)
                paint.color = Color.LTGRAY
                canvas.drawRect(left, top, right, bottom, paint)
                if (actualSize > 10) {
                    val b = cellBaseSize * 0.1f
                    paint.color = bevelLightColor
                    canvas.drawRect(left, top, right, top + b, paint)
                    canvas.drawRect(left, top, left + b, bottom, paint)
                    paint.color = bevelDarkColor
                    canvas.drawRect(left, bottom - b, right, bottom, paint)
                    canvas.drawRect(right - b, top, right, bottom, paint)
                    paint.color = Color.LTGRAY
                    canvas.drawRect(left + b, top + b, right - b, bottom - b, paint)
                }

                val centerX = left + cellBaseSize / 2f
                val centerY = top + cellBaseSize / 2f
                
                // 旗の描画（非常に小さい時は赤い点にする）
                if (actualSize > 15) {
                    val size = cellBaseSize * 0.6f
                    paint.color = Color.BLACK
                    canvas.drawRect(centerX - 2, centerY - size / 2, centerX + 2, centerY + size / 2, paint)
                    
                    flagPath.reset()
                    flagPath.moveTo(centerX, centerY - size / 2)
                    flagPath.lineTo(centerX - size * 0.5f, centerY - size * 0.25f)
                    flagPath.lineTo(centerX, centerY)
                    flagPath.close()
                    paint.color = Color.RED
                    canvas.drawPath(flagPath, paint)
                } else {
                    paint.color = Color.RED
                    canvas.drawCircle(centerX, centerY, cellBaseSize * 0.3f, paint)
                }

                if (cell.isWrongFlag && actualSize > 30) {
                    paint.color = Color.BLACK
                    paint.strokeWidth = 5f
                    canvas.drawLine(left + 10, top + 10, right - 10, bottom - 10, paint)
                    canvas.drawLine(right - 10, top + 10, left + 10, bottom - 10, paint)
                }
            }
            CellState.OPENED -> {
                // Grid/Background
                paint.color = gridColor
                canvas.drawRect(left, top, right, bottom, paint)
                
                // Content area
                reuseRect.set(left + 1, top + 1, right - 1, bottom - 1)
                
                if (cell.isMine) {
                    paint.color = if (cell.isRedMine) Color.RED else Color.DKGRAY
                    canvas.drawRect(reuseRect, paint)
                    
                    if (actualSize > 20) {
                        val centerX = reuseRect.centerX()
                        val centerY = reuseRect.centerY()
                        val radius = cellBaseSize / 4f
                        
                        paint.color = Color.BLACK
                        paint.strokeWidth = 4f
                        val spikeLen = radius * 1.5f
                        canvas.drawLine(centerX - spikeLen, centerY, centerX + spikeLen, centerY, paint)
                        canvas.drawLine(centerX, centerY - spikeLen, centerX, centerY + spikeLen, paint)
                        val diag = spikeLen * 0.707f
                        canvas.drawLine(centerX - diag, centerY - diag, centerX + diag, centerY + diag, paint)
                        canvas.drawLine(centerX + diag, centerY - diag, centerX - diag, centerY + diag, paint)
                        canvas.drawCircle(centerX, centerY, radius, paint)
                        paint.color = Color.WHITE
                        canvas.drawCircle(centerX - radius / 3, centerY - radius / 3, radius / 5, paint)
                    }
                } else {
                    paint.color = openedBgColor
                    canvas.drawRect(reuseRect, paint)
                    
                    // 数字はある程度大きくないと描画しない
                    if (cell.adjacentMineCount > 0 && actualSize > 30) {
                        val count = cell.adjacentMineCount
                        val numColor = if (count in 1..8) numberColors[count] else Color.WHITE
                        
                        val cX = reuseRect.centerX()
                        // 垂直方向の中央揃え調整
                        val cY = reuseRect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2)
                        
                        outlinePaint.color = Color.BLACK // Changed from WHITE for better contrast on dark bg
                        outlinePaint.alpha = 100
                        canvas.drawText(count.toString(), cX, cY, outlinePaint)
                        
                        textPaint.color = numColor
                        canvas.drawText(count.toString(), cX, cY, textPaint)
                    }
                }
            }
        }
    }

}
