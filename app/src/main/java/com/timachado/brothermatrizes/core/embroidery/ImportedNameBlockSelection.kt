package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.max
import kotlin.math.min

/** Retângulo selecionado pelo operador, em décimos de milímetro. */
data class StitchSelectionRect(
    val minXUnits: Int,
    val maxXUnits: Int,
    val minYUnits: Int,
    val maxYUnits: Int
) {
    fun contains(x: Int, y: Int): Boolean =
        x in minXUnits..maxXUnits && y in minYUnits..maxYUnits

    fun normalized(): StitchSelectionRect = StitchSelectionRect(
        min(minXUnits, maxXUnits),
        max(minXUnits, maxXUnits),
        min(minYUnits, maxYUnits),
        max(minYUnits, maxYUnits)
    )
}

data class IsolatedNameBlock(
    val originalWithoutBlock: EmbroideryDesign,
    val removedStitchCount: Int,
    val centerXUnits: Int,
    val centerYUnits: Int
)

/**
 * Remoção conservadora: somente o ÚLTIMO bloco de cor, separado por
 * COLOR_CHANGE, e inteiramente dentro da região selecionada.
 * A seleção não pode atravessar nenhuma pontada do desenho preservado.
 *
 * Não há detecção automática de texto nem remoção por pixel.
 * Matrizes onde flores e nome compartilham o mesmo bloco são rejeitadas.
 */
object ImportedNameBlockSelection {
    fun isolateFinalColorBlock(
        design: EmbroideryDesign,
        requestedArea: StitchSelectionRect
    ): Result<IsolatedNameBlock> = runCatching {
        require(design.editableName == null) {
            "Este nome já é editável. Altere diretamente pelo campo de texto."
        }
        require(design.points.lastOrNull()?.command == StitchCommand.END) {
            "A matriz não termina corretamente em END."
        }
        require(design.points.dropLast(1).none { it.command == StitchCommand.END }) {
            "Foram encontrados comandos END intermediários."
        }
        val area = requestedArea.normalized()
        require(area.maxXUnits - area.minXUnits >= 20 &&
            area.maxYUnits - area.minYUnits >= 20) {
            "Marque uma região de pelo menos 2 × 2 mm."
        }

        val terminalIndex = design.points.lastIndex
        val split = design.points.indexOfLast { it.command == StitchCommand.COLOR_CHANGE }
        require(split >= 1 && split < terminalIndex - 1) {
            "Seleção segura indisponível: o nome precisa estar no último bloco separado por cor."
        }
        val retained = design.points.subList(0, split)
        val selected = design.points.subList(split + 1, terminalIndex)
        require(retained.any { it.command == StitchCommand.STITCH }) {
            "A matriz não possui bordado anterior para preservar."
        }
        val stitched = selected.filter { it.command == StitchCommand.STITCH }
        require(stitched.isNotEmpty()) {
            "O último bloco não contém pontadas."
        }
        require(selected.takeWhile { it.command != StitchCommand.STITCH }
            .any { it.command == StitchCommand.JUMP }) {
            "O último bloco não possui salto inicial seguro."
        }
        require(stitched.all { area.contains(it.xUnits, it.yUnits) }) {
            "A região não envolve todo o último bloco de cor. Amplie a seleção."
        }
        require(selected.filter { it.command == StitchCommand.JUMP }
            .all { area.contains(it.xUnits, it.yUnits) }) {
            "O bloco inclui saltos fora da região selecionada."
        }

        // Detecta inclusive uma linha ornamental que atravesse o retângulo
        // com as duas extremidades fora dele.
        var previous: EmbroideryPoint? = null
        retained.forEach { point ->
            if (point.command == StitchCommand.STITCH && previous != null) {
                require(!segmentIntersectsRect(previous!!, point, area)) {
                    "Outros elementos cruzam esta área. Remoção bloqueada para proteger flores e bordas."
                }
            }
            previous = point
        }

        val previousPoint = retained.last()
        val cleanedPoints = retained.toList() + previousPoint.copy(command = StitchCommand.END)
        val highestColor = retained.maxOf { it.colorIndex }
        val remainingColors = design.threadColors.take(highestColor + 1)
        val cleaned = EmbroideryIntegrity.normalize(
            design.copy(
                points = cleanedPoints,
                sourceBytes = ByteArray(0),
                threadColors = remainingColors,
                isModified = true,
                editableName = null
            )
        ).getOrThrow().design
        require(cleaned.points.dropLast(1) == retained) {
            "A sequência da moldura não pôde ser preservada."
        }
        val hoop = cleaned.hoopProfile
        if (hoop != null) {
            require(HoopValidator.validate(cleaned, hoop).fits) {
                "O desenho preservado não cabe na área segura do bastidor."
            }
        }
        IsolatedNameBlock(
            originalWithoutBlock = cleaned,
            removedStitchCount = stitched.size,
            centerXUnits = (area.minXUnits.toLong() + area.maxXUnits).div(2).toInt(),
            centerYUnits = (area.minYUnits.toLong() + area.maxYUnits).div(2).toInt()
        )
    }

    /** Liang-Barsky inclusivo, impede remover por cima de uma costura externa. */
    private fun segmentIntersectsRect(
        a: EmbroideryPoint,
        b: EmbroideryPoint,
        rect: StitchSelectionRect
    ): Boolean {
        val dx = b.xUnits.toDouble() - a.xUnits
        val dy = b.yUnits.toDouble() - a.yUnits
        val p = doubleArrayOf(-dx, dx, -dy, dy)
        val q = doubleArrayOf(
            a.xUnits.toDouble() - rect.minXUnits,
            rect.maxXUnits.toDouble() - a.xUnits,
            a.yUnits.toDouble() - rect.minYUnits,
            rect.maxYUnits.toDouble() - a.yUnits
        )
        var t0 = 0.0
        var t1 = 1.0
        for (i in 0..3) {
            if (p[i] == 0.0) {
                if (q[i] < 0.0) return false
            } else {
                val t = q[i] / p[i]
                if (p[i] < 0) t0 = max(t0, t) else t1 = min(t1, t)
                if (t0 > t1) return false
            }
        }
        return true
    }
}
