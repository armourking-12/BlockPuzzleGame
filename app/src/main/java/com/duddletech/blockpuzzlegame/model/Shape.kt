package com.duddletech.blockpuzzlegame.model

/**
 * An offset relative to the shape's top-left corner.
 */
data class CellOffset(
    val row: Int,
    val col: Int,
)

/**
 * A placed or tray shape made up of individual cells.
 */
data class Shape(
    val cells: List<CellOffset>,
    val color: BlockColor,
) {
    val width: Int get() = (cells.maxOfOrNull { it.col } ?: 0) + 1
    val height: Int get() = (cells.maxOfOrNull { it.row } ?: 0) + 1

    /**
     * Rotates the shape 90 degrees clockwise and normalizes coordinates.
     */
    fun rotateCW(): Shape {
        val rotated = cells.map { CellOffset(row = it.col, col = -it.row) }
        val minRow = rotated.minOf { it.row }
        val minCol = rotated.minOf { it.col }
        val normalized = rotated.map { CellOffset(row = it.row - minRow, col = it.col - minCol) }
        return copy(cells = normalized)
    }
}