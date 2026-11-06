package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.VectorPoint

/** Histórico isolado em memória; editar traços nunca altera arquivo existente. */
internal data class DesktopFreehandEditSession private constructor(
    private val revisions: List<List<List<VectorPoint>>>,
    val position: Int
) {
    companion object {
        const val MAX_UNDO = 8
        fun start(strokes: List<List<VectorPoint>> = emptyList()): DesktopFreehandEditSession {
            validate(strokes)
            return DesktopFreehandEditSession(listOf(strokes), 0)
        }
        private fun validate(strokes: List<List<VectorPoint>>) {
            require(strokes.size <= DesktopDrawingService.MAX_STROKES) {
                "Número de traços excedido."
            }
            var count = 0
            for (stroke in strokes) {
                require(stroke.size in 2..DesktopDrawingService.MAX_POINTS_PER_STROKE) {
                    "Número de pontos de traço inválido."
                }
                count += stroke.size
                require(count <= DesktopDrawingService.MAX_TOTAL_POINTS) {
                    "Excesso de coordenadas no desenho."
                }
                require(stroke.all { point ->
                    point.x.isFinite() && point.y.isFinite() &&
                        point.x in 0f..1f && point.y in 0f..1f
                }) { "Coordenadas inválidas." }
            }
        }
    }
    val current: List<List<VectorPoint>> get() = revisions[position]
    val canUndo get() = position > 0
    val canRedo get() = position < revisions.lastIndex

    fun record(strokes: List<List<VectorPoint>>): DesktopFreehandEditSession {
        validate(strokes)
        if (strokes == current) return this
        val updated = (revisions.take(position + 1) + listOf(strokes))
            .takeLast(MAX_UNDO + 1)
        return DesktopFreehandEditSession(updated, updated.lastIndex)
    }

    fun undo(): DesktopFreehandEditSession {
        check(canUndo) { "Não há traço para desfazer." }
        return copy(position = position - 1)
    }
    fun redo(): DesktopFreehandEditSession {
        check(canRedo) { "Não há traço para refazer." }
        return copy(position = position + 1)
    }
}
