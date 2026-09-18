package com.duddletech.blockpuzzlegame.viewModel

import android.app.Application
import androidx.annotation.VisibleForTesting
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.duddletech.blockpuzzlegame.data.GameStateRepository
import com.duddletech.blockpuzzlegame.data.HighScoreRepository
import com.duddletech.blockpuzzlegame.logic.CompleteLines
import com.duddletech.blockpuzzlegame.logic.GameEngine
import com.duddletech.blockpuzzlegame.model.CellOffset
import com.duddletech.blockpuzzlegame.model.GameState
import com.duddletech.blockpuzzlegame.model.GameState.Companion.GRID_SIZE
import com.duddletech.blockpuzzlegame.model.Grid
import com.duddletech.blockpuzzlegame.model.Shape
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One-shot haptic feedback events emitted by the ViewModel. */
enum class HapticEvent { PLACE, LINE_CLEAR, WARNING_TICK }

/** Ephemeral score pop info for the "+N" floating animation. */
data class ScorePop(
    val points: Int,
    /** Center row on the grid (fractional). */
    val centerRow: Float,
    /** Center col on the grid (fractional). */
    val centerCol: Float,
    /** True for line-clear bonuses (shown larger/brighter). */
    val isBonus: Boolean,
    /** Unique id so Compose can key animations. */
    val id: Long
)

/** Where a drag originated — tray slot or hold box. */
enum class DragSource { TRAY, HOLD }

/**
 * Drag state tracked separately from game state for performance —
 * drag offsets change every frame, game state changes only on drop.
 */
data class DragState(
    val shapeIndex: Int = -1,
    /** Where the drag started. [shapeIndex] is only meaningful when [source] is [DragSource.TRAY]. */
    val source: DragSource = DragSource.TRAY,
    val shape: Shape? = null,
    /** Finger position in root (window) coordinates — used to position the floating shape. */
    val fingerRootOffset: Offset = Offset.Zero,
    val ghostRow: Int = -1,
    val ghostCol: Int = -1,
    val ghostValid: Boolean = false,
    /** Cells in rows/columns that would be cleared if the shape is dropped here. */
    val highlightCells: Set<CellOffset> = emptySet(),
    /** True while the drop shrink/fade animation is playing. */
    val isDropAnimating: Boolean = false
)

class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val highScoreRepo = HighScoreRepository(app)
    private val gameStateRepo = GameStateRepository(app)

    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _dragState = MutableStateFlow(DragState())
    val dragState: StateFlow<DragState> = _dragState.asStateFlow()

    private val _hapticEvents = MutableSharedFlow<HapticEvent>(extraBufferCapacity = 4)
    val hapticEvents: SharedFlow<HapticEvent> = _hapticEvents.asSharedFlow()

    private val _scorePops = MutableStateFlow<List<ScorePop>>(emptyList())
    val scorePops: StateFlow<List<ScorePop>> = _scorePops.asStateFlow()
    private var nextPopId = 0L

    /** High score snapshot taken at the start of each game (before any in-game updates). */
    private var startingHighScore: Int = 0
    /** True once the mid-game "beat the high score" confetti has fired this game. */
    private var midGameConfettiFired: Boolean = false
    /** Highest 50k milestone reached this game (to avoid re-firing). */
    private var lastScoreMilestone: Int = 0

    /** Incremented each time confetti should fire; UI launches a new animation on each change. */
    private val _confettiTrigger = MutableStateFlow(0)
    val confettiTrigger: StateFlow<Int> = _confettiTrigger.asStateFlow()

    /**
     * Seconds left in the game-over grace period, or null when no warning is showing.
     * Kept out of [GameState] so the once-per-second tick doesn't recompose the grid,
     * and so it never needs persisting.
     */
    private val _graceCountdown = MutableStateFlow<Int?>(null)
    val graceCountdown: StateFlow<Int?> = _graceCountdown.asStateFlow()

    /** Runs the pause → countdown → verdict sequence. Cancelled if the player recovers. */
    private var graceJob: Job? = null

    /** The initial saved-game load, tracked so tests can force state without it racing back in. */
    private var initJob: Job? = null

    /** Called by the UI after a score pop animation finishes. */
    fun dismissScorePop(id: Long) {
        _scorePops.update { pops -> pops.filter { it.id != id } }
    }

    private fun emitScorePop(points: Int, centerRow: Float, centerCol: Float, isBonus: Boolean) {
        val pop = ScorePop(points, centerRow, centerCol, isBonus, nextPopId++)
        _scorePops.update { it + pop }
    }

    // Grid layout info from the composable — set by onGridLayout callback
    var gridScreenOffset: Offset = Offset.Zero
    var cellSizePx: Float = 0f

    /** Screen rect of the hold box — set by the HoldBox composable for drop hit-testing. */
    var holdBoxScreenRect: Rect = Rect.Zero

    /** Base lift in px (96dp converted) — set by GameScreen for hold-box hit-testing. */
    var liftBasePx: Float = 0f

    init {
        initJob = viewModelScope.launch {
            val savedHighScore = highScoreRepo.highScoreFlow.first()
            val savedGame = gameStateRepo.load()

            if (savedGame != null && !savedGame.isGameOver) {
                _gameState.value = savedGame.copy(highScore = savedHighScore)
                startingHighScore = savedHighScore
                lastScoreMilestone = savedGame.score / 50_000
            } else {
                startingHighScore = savedHighScore
                _gameState.update {
                    GameState(
                        grid = GameState.emptyGrid(),
                        currentShapes = GameEngine.generateShapeTriple(GameState.emptyGrid(), ensureFit = easyShapes),
                        score = 0,
                        highScore = savedHighScore
                    )
                }
                _dragState.value = DragState()
            }

            // A game saved mid-grace-period comes back with isGameOver still false —
            // re-check so an unplayable board isn't stranded with no way to end.
            evaluateGameOver()
        }
    }

    @VisibleForTesting
    internal fun setGameStateForTest(state: GameState) {
        initJob?.cancel()
        _gameState.value = state
        evaluateGameOver()
    }

    fun startNewGame() {
        cancelGracePeriod()
        startingHighScore = _gameState.value.highScore
        midGameConfettiFired = false
        lastScoreMilestone = 0

        _gameState.update {
            GameState(
                grid = GameState.emptyGrid(),
                currentShapes = GameEngine.generateShapeTriple(GameState.emptyGrid(), ensureFit = easyShapes),
                score = 0,
                highScore = it.highScore
            )
        }
        _dragState.value = DragState()
        viewModelScope.launch { gameStateRepo.clear() }
    }

    /** Rotate the shape at the given tray index 90° clockwise. */
    fun rotateShape(index: Int) {
        _gameState.update { state ->
            val shape = state.currentShapes[index] ?: return@update state
            val newShapes = state.currentShapes.toMutableList()
            newShapes[index] = shape.rotateCW()
            state.copy(currentShapes = newShapes)
        }
        persistGameState()
        evaluateGameOver()
    }

    /** Refreshes all tray shapes, deducting up to 500 points (score never goes below 0). */
    fun refreshTray() {
        val state = _gameState.value
        val deduction = minOf(500, state.score)
        val newShapes = GameEngine.generateShapeTriple(state.grid, ensureFit = easyShapes)
        _gameState.update { it.copy(currentShapes = newShapes, score = it.score - deduction) }
        persistGameState()
        evaluateGameOver()
    }

    /** Rotate the shape in the hold box 90° clockwise. */
    fun rotateHoldShape() {
        _gameState.update { state ->
            val shape = state.holdShape ?: return@update state
            state.copy(holdShape = shape.rotateCW())
        }
        persistGameState()
        evaluateGameOver()
    }

    /** Called when the player starts dragging a shape from the hold box. */
    fun onHoldDragStart(shape: Shape) {
        if (_gameState.value.clearingCells.isNotEmpty()) return
        _dragState.value = DragState(source = DragSource.HOLD, shape = shape)
    }

    /** Called when the player starts dragging a shape from the tray. */
    fun onDragStart(shapeIndex: Int, shape: Shape) {
        if (_gameState.value.clearingCells.isNotEmpty()) return

        _dragState.value = DragState(
            shapeIndex = shapeIndex,
            shape = shape
        )
    }

    /**
     * Called every frame during drag.
     * @param fingerInRoot Finger position in root (window) coordinates.
     * @param fingerInGrid Finger position relative to the grid composable's top-left.
     * @param floatingShapeCenterYOffset Vertical offset (in px) from the finger to the
     *        center of the floating shape visual (negative = above finger).
     */
    fun onDrag(fingerInRoot: Offset, fingerInGrid: Offset, floatingShapeCenterYOffset: Float) {
        val drag = _dragState.value
        val shape = drag.shape ?: return

        val visualX = fingerInGrid.x
        val visualY = fingerInGrid.y + floatingShapeCenterYOffset

        val centerRow = (visualY - gridScreenOffset.y) / cellSizePx
        val centerCol = (visualX - gridScreenOffset.x) / cellSizePx

        val adjRow = kotlin.math.round(centerRow - shape.height / 2f).toInt()
        val adjCol = kotlin.math.round(centerCol - shape.width / 2f).toInt()

        if (adjRow == drag.ghostRow && adjCol == drag.ghostCol) {
            _dragState.value = drag.copy(fingerRootOffset = fingerInRoot)
            return
        }

        val grid = _gameState.value.grid
        val valid = adjRow >= 0 && adjCol >= 0 &&
                GameEngine.canPlace(grid, shape, adjRow, adjCol)

        val highlight = if (valid) {
            val simGrid = GameEngine.placeShape(grid, shape, adjRow, adjCol)
            val lines = GameEngine.findCompleteLines(simGrid)
            lines.toCellSet(GRID_SIZE)
        } else emptySet()

        _dragState.value = drag.copy(
            fingerRootOffset = fingerInRoot,
            ghostRow = adjRow,
            ghostCol = adjCol,
            ghostValid = valid,
            highlightCells = highlight
        )
    }

    /** Called when the player lifts their finger. */
    fun onDragEnd() {
        val drag = _dragState.value
        drag.shape ?: return

        if (_gameState.value.clearingCells.isNotEmpty()) {
            _dragState.value = DragState()
            return
        }

        val shape = drag.shape!!
        val liftPx = liftBasePx + cellSizePx
        val shapeTop = drag.fingerRootOffset.y - shape.height * cellSizePx - liftPx
        val shapeBottom = drag.fingerRootOffset.y - liftPx
        val halfW = shape.width * cellSizePx / 2f
        val shapeRect = Rect(
            left = drag.fingerRootOffset.x - halfW,
            top = shapeTop,
            right = drag.fingerRootOffset.x + halfW,
            bottom = shapeBottom
        )
        if (drag.source == DragSource.TRAY &&
            _gameState.value.holdShape == null &&
            shapeRect.overlaps(holdBoxScreenRect)
        ) {
            executeHoldDrop(drag)
            return
        }

        if (drag.ghostValid && drag.ghostRow >= 0 && drag.ghostCol >= 0) {
            _dragState.update { it.copy(isDropAnimating = true) }
        } else {
            _dragState.value = DragState()
        }
    }

    /** Called by the UI after the drop animation finishes — performs the actual placement. */
    fun onDropAnimationDone() {
        val drag = _dragState.value
        val shape = drag.shape

        if (shape != null && drag.ghostValid && drag.ghostRow >= 0 && drag.ghostCol >= 0) {
            executePlacement(drag, shape)
        }

        _dragState.value = DragState()
    }

    /** Moves a tray shape into the empty hold box. */
    private fun executeHoldDrop(drag: DragState) {
        val droppedShape = drag.shape ?: return
        _gameState.update { state ->
            val newShapes = state.currentShapes.toMutableList()
            newShapes[drag.shapeIndex] = null
            val finalShapes = if (newShapes.all { it == null }) {
                GameEngine.generateShapeTriple(state.grid, ensureFit = easyShapes)
            } else newShapes
            state.copy(holdShape = droppedShape, currentShapes = finalShapes)
        }
        persistGameState()
        evaluateGameOver()
        _dragState.value = DragState()
    }

    /** Applies a valid shape placement to the game state (grid, score, line clears). */
    private fun executePlacement(drag: DragState, shape: Shape) {
        val grid = _gameState.value.grid
        val newGrid = GameEngine.placeShape(grid, shape, drag.ghostRow, drag.ghostCol)
        val placementPts = GameEngine.placementPoints(shape)

        val currentState = _gameState.value
        val finalShapes: List<Shape?>
        val finalHoldShape: Shape?

        if (drag.source == DragSource.HOLD) {
            finalShapes = currentState.currentShapes
            finalHoldShape = null
        } else {
            val newShapes = currentState.currentShapes.toMutableList()
            newShapes[drag.shapeIndex] = null
            finalShapes = if (newShapes.all { it == null }) {
                GameEngine.generateShapeTriple(newGrid, ensureFit = easyShapes)
            } else newShapes
            finalHoldShape = currentState.holdShape
        }

        val lines = GameEngine.findCompleteLines(newGrid)

        if (hapticEnabled) _hapticEvents.tryEmit(HapticEvent.PLACE)

        if (lines.isNotEmpty) {
            val clearing = lines.toCellSet(GRID_SIZE)

            _gameState.update {
                it.copy(
                    grid = newGrid,
                    currentShapes = finalShapes,
                    holdShape = finalHoldShape,
                    score = it.score + placementPts,
                    clearingCells = clearing
                )
            }

            viewModelScope.launch {
                delay(CLEAR_ANIMATION_MS)
                val clearResult = GameEngine.clearLines(newGrid, lines)
                val clearCenterRow = clearing.map { it.row }.average().toFloat()
                val clearCenterCol = clearing.map { it.col }.average().toFloat()

                if (hapticEnabled) _hapticEvents.tryEmit(HapticEvent.LINE_CLEAR)

                finalizePlacement(
                    newGrid = clearResult.grid,
                    finalShapes = finalShapes,
                    finalHoldShape = finalHoldShape,
                    points = clearResult.points,
                    popCenterRow = clearCenterRow,
                    popCenterCol = clearCenterCol,
                    isBonus = true,
                    lines = lines
                )
            }
        } else {
            finalizePlacement(
                newGrid = newGrid,
                finalShapes = finalShapes,
                finalHoldShape = finalHoldShape,
                points = placementPts,
                popCenterRow = drag.ghostRow + shape.height / 2f,
                popCenterCol = drag.ghostCol + shape.width / 2f,
                isBonus = false,
                lines = CompleteLines()
            )
        }
    }

    /** Common finalization: update score, check game over, persist, and emit UI events. */
    private fun finalizePlacement(
        newGrid: Grid,
        finalShapes: List<Shape?>,
        finalHoldShape: Shape?,
        points: Int,
        popCenterRow: Float,
        popCenterCol: Float,
        isBonus: Boolean,
        lines: CompleteLines = CompleteLines()
    ) {
        val newScore = _gameState.value.score + points
        val newHighScore = maxOf(newScore, _gameState.value.highScore)
        persistHighScoreIfNeeded(newHighScore)

        _gameState.update {
            it.copy(
                grid = newGrid,
                currentShapes = finalShapes,
                holdShape = finalHoldShape,
                score = newScore,
                highScore = newHighScore,
                clearingCells = emptySet()
            )
        }

        persistGameState()
        emitScorePop(points, popCenterRow, popCenterCol, isBonus)
        checkConfetti(newScore, lines)

        if (lines.isNotEmpty && newGrid.all { row -> row.none { it.filled } }) {
            val boardClearBonus = 5000
            val updatedScore = newScore + boardClearBonus
            val updatedHighScore = maxOf(updatedScore, newHighScore)
            persistHighScoreIfNeeded(updatedHighScore)
            _gameState.update { it.copy(score = updatedScore, highScore = updatedHighScore) }
            persistGameState()
            emitScorePop(boardClearBonus, 3.5f, 3.5f, isBonus = true)
            _confettiTrigger.value++
        }

        evaluateGameOver()
    }

    /** Trigger confetti for: beating the high score, clearing 3+ lines, or crossing a 50k milestone. */
    private fun checkConfetti(newScore: Int, lines: CompleteLines) {
        if (!midGameConfettiFired && startingHighScore > 0 && newScore > startingHighScore) {
            midGameConfettiFired = true
            _confettiTrigger.value++
            return
        }

        if (lines.rows.size + lines.cols.size >= 3) {
            _confettiTrigger.value++
            return
        }

        val milestone = newScore / 50_000
        if (milestone > lastScoreMilestone) {
            lastScoreMilestone = milestone
            _confettiTrigger.value++
        }
    }

    private fun evaluateGameOver() {
        val state = _gameState.value
        if (state.isGameOver) return

        val remaining = state.currentShapes + listOfNotNull(state.holdShape)
        if (GameEngine.isGameOver(state.grid, remaining)) startGracePeriod() else cancelGracePeriod()
    }

    private fun startGracePeriod() {
        if (graceJob?.isActive == true) return

        graceJob = viewModelScope.launch {
            delay(GRACE_PAUSE_MS)

            for (second in GRACE_SECONDS downTo 1) {
                _graceCountdown.value = second
                if (hapticEnabled) _hapticEvents.tryEmit(HapticEvent.WARNING_TICK)
                delay(1000)
            }
            _graceCountdown.value = null

            val state = _gameState.value
            val remaining = state.currentShapes + listOfNotNull(state.holdShape)
            if (GameEngine.isGameOver(state.grid, remaining)) {
                _gameState.update { it.copy(isGameOver = true) }
                persistGameState()
            }
        }
    }

    private fun cancelGracePeriod() {
        graceJob?.cancel()
        graceJob = null
        _graceCountdown.value = null
    }

    private fun persistGameState() {
        viewModelScope.launch { gameStateRepo.save(_gameState.value) }
    }

    private fun persistHighScoreIfNeeded(newHighScore: Int) {
        if (newHighScore > _gameState.value.highScore) {
            viewModelScope.launch { highScoreRepo.saveHighScore(newHighScore) }
        }
    }

    companion object {
        const val CLEAR_ANIMATION_MS = 750L
        const val GRACE_PAUSE_MS = 1500L
        const val GRACE_SECONDS = 5
    }

    var hapticEnabled: Boolean = false
    var easyShapes: Boolean = false

    fun onDragCancel() {
        _dragState.value = DragState()
    }
}