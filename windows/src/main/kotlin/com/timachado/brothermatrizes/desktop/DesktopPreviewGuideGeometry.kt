package com.timachado.brothermatrizes.desktop

/**
 * Grade de referência em milímetros, usada apenas no canvas Windows.
 *
 * As coordenadas dos motores de bordado são décimos de milímetro (0,1 mm).
 * Não altera pontos, escala real, bastidor, comandos, nem arquivos.
 */
internal data class DesktopPreviewGuideLayout(
    val verticalUnits: List<Int>,
    val horizontalUnits: List<Int>,
    val centerXUnits: Int,
    val centerYUnits: Int,
    val effectiveSpacingUnits: Long
)

internal object DesktopPreviewGuideGeometry {
    const val MAX_LINES_PER_AXIS = 80

    fun calculate(bounds: PreviewBounds, spacingMm: Int): DesktopPreviewGuideLayout {
        require(spacingMm == 5 || spacingMm == 10) {
            "A grade aceita apenas espaçamento de 5 ou 10 mm."
        }
        require(bounds.maxX >= bounds.minX && bounds.maxY >= bounds.minY) {
            "Os limites do bordado são inválidos."
        }
        val minX = bounds.minX.toLong()
        val maxX = bounds.maxX.toLong()
        val minY = bounds.minY.toLong()
        val maxY = bounds.maxY.toLong()

        // Em matrizes muito grandes, usar múltiplos do passo escolhido
        // evita alocar/desenhar milhares de linhas por atualização de zoom.
        val maximumSpan = maxOf(maxX - minX, maxY - minY)
        var step = spacingMm.toLong() * 10L
        while (maximumSpan / step >= MAX_LINES_PER_AXIS) {
            step *= 2L
        }

        fun axis(from: Long, to: Long): List<Int> {
            val initial = Math.floorDiv(from, step) * step
            var line = if (initial < from) initial + step else initial
            val result = ArrayList<Int>()
            while (line <= to && result.size < MAX_LINES_PER_AXIS) {
                result += line.toInt() // garantidamente dentro dos limites Int de from..to
                line += step
            }
            return result
        }

        return DesktopPreviewGuideLayout(
            verticalUnits = axis(minX, maxX),
            horizontalUnits = axis(minY, maxY),
            centerXUnits = ((minX + maxX) / 2L).toInt(),
            centerYUnits = ((minY + maxY) / 2L).toInt(),
            effectiveSpacingUnits = step
        )
    }
}
