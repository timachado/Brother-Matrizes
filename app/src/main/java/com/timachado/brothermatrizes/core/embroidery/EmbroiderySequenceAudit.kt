package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Diagnostico somente leitura. Nao corrige ou reorganiza pontadas:
 * verifica o percurso produzido pelo gerador e o percurso REAL que
 * chega ao Viewer/Simulador apos gravar e reabrir PES.
 *
 * Coordenadas normalizadas permitem comparar antes/depois da
 * translacao de origem do arquivo PES. Ao contrario de comparar
 * quantidade de pontos, a amostragem por distancia costurada tolera
 * a subdivisao de pontos longos pelo writer Brother.
 */
object EmbroiderySequenceAudit {
    data class Checkpoint(
        val fraction: Int,
        val x: Double,
        val visualY: Double
    )
    data class Report(
        val stage: String,
        val points: Int,
        val stitches: Int,
        val jumps: Int,
        val trims: Int,
        val travelMm: Double,
        val significantBacktracks: Int,
        val revisitBins: Int,
        val checkpoints: List<Checkpoint>
    ) {
        fun csv(): String = buildString {
            appendLine("stage,progress_pct,x_fraction,y_fraction,points,stitches,jumps,trims,backtracks,revisit_bins")
            checkpoints.forEach { p ->
                appendLine(
                    "$stage,${p.fraction},${p.x},${p.visualY},$points,$stitches,$jumps,$trims,$significantBacktracks,$revisitBins"
                )
            }
        }
    }
    data class Comparison(
        val firstDistance: Double,
        val lastDistance: Double,
        val maxCheckpointDistance: Double,
        val checkpoints: Int,
        val before: Report,
        val after: Report
    ) {
        // E uma verificacao de sequencia, nao uma prova de qualidade Satin.
        // O limite em coordenadas normalizadas admite pequenas correcoes
        // de arredondamento sem aceitar trocar a ordem de ramos da letra.
        val orderedPathPreserved: Boolean
            get() = checkpoints >= 5 &&
                firstDistance <= 0.08 &&
                lastDistance <= 0.08 &&
                maxCheckpointDistance <= 0.14

        fun summary(): String =
            "before=${before.stitches} stitches / ${before.jumps} jumps " +
                "after=${after.stitches} stitches / ${after.jumps} jumps " +
                "first=${firstDistance} last=${lastDistance} " +
                "maxCheckpoint=${maxCheckpointDistance} " +
                "revisits=${before.revisitBins}->${after.revisitBins} " +
                "backtracks=${before.significantBacktracks}->${after.significantBacktracks} " +
                "preserved=$orderedPathPreserved"
    }

    fun compare(
        generated: EmbroideryDesign,
        reopened: EmbroideryDesign
    ): Comparison {
        val before = inspect("generated", generated)
        val after = inspect("reopened-PES", reopened)
        val distances = before.checkpoints.zip(after.checkpoints).map { (a,b) ->
            hypot(a.x - b.x, a.visualY - b.visualY)
        }
        return Comparison(
            firstDistance = distances.firstOrNull() ?: Double.POSITIVE_INFINITY,
            lastDistance = distances.lastOrNull() ?: Double.POSITIVE_INFINITY,
            maxCheckpointDistance = distances.maxOrNull() ?: Double.POSITIVE_INFINITY,
            checkpoints = distances.size,
            before = before,
            after = after
        )
    }

    /**
     * CSV COMPLETO na ordem do arquivo, nao apenas a vista final
     * da simulacao. Inclui cada STITCH/JUMP/TRIM, inclusive os
     * eventos inseridos na exportacao PES.
     */
    fun fullCsv(stage: String, design: EmbroideryDesign): String {
        val stitches = design.points.filter {
            it.command == StitchCommand.STITCH
        }
        val minX = stitches.minOfOrNull { it.xUnits } ?: 0
        val maxX = stitches.maxOfOrNull { it.xUnits } ?: minX
        val minY = stitches.minOfOrNull { it.yUnits } ?: 0
        val maxY = stitches.maxOfOrNull { it.yUnits } ?: minY
        val dx = max(1, maxX - minX).toDouble()
        val dy = max(1, maxY - minY).toDouble()

        return buildString {
            appendLine("stage,index,command,xUnits,yUnits,xNormalized,yVisualNormalized,colorIndex")
            design.points.forEachIndexed { index, point ->
                val x = (point.xUnits - minX) / dx
                val y = (point.yUnits - minY) / dy
                val visualY = if (design.sourceYAxisDown) y else 1.0 - y
                appendLine(
                    "$stage,$index,${point.command.name}," +
                        "${point.xUnits},${point.yUnits},$x,$visualY,${point.colorIndex}"
                )
            }
        }
    }

    fun inspect(stage: String, design: EmbroideryDesign): Report {
        val path = design.points.filter {
            it.command == StitchCommand.STITCH
        }
        val minX = path.minOfOrNull { it.xUnits } ?: 0
        val maxX = path.maxOfOrNull { it.xUnits } ?: minX
        val minY = path.minOfOrNull { it.yUnits } ?: 0
        val maxY = path.maxOfOrNull { it.yUnits } ?: minY
        val width = max(1, maxX - minX).toDouble()
        val height = max(1, maxY - minY).toDouble()

        val locations = path.map { point ->
            val x = (point.xUnits - minX) / width
            val y = (point.yUnits - minY) / height
            x to (if (design.sourceYAxisDown) y else 1.0 - y)
        }
        val lengths = mutableListOf(0.0)
        for (i in 1 until path.size) {
            val a = path[i - 1]
            val b = path[i]
            lengths += lengths.last() +
                hypot((b.xUnits - a.xUnits).toDouble(), (b.yUnits - a.yUnits).toDouble())
        }
        val total = lengths.lastOrNull() ?: 0.0
        val checkpoints = (0..10).map { index ->
            val target = total * index / 10.0
            val slot = lengths.indexOfFirst { it >= target }.let {
                if (it < 0) locations.lastIndex else it
            }.coerceAtLeast(0)
            val coordinate = locations.getOrElse(slot) { 0.0 to 0.0 }
            Checkpoint(index * 10, coordinate.first, coordinate.second)
        }

        // Retorno significativo: voltas > 20% da largura apos a
        // progressao para outra zona. E apenas indicador de diagnostico,
        // nao erro automatico em letras cursivas com lacos.
        var farthestX = Double.NEGATIVE_INFINITY
        var backtracks = 0
        var wasBacktracking = false
        var lastBand = -1
        var bandTransitions = 0
        val visitedBands = mutableSetOf<Int>()
        locations.forEach { (x, _) ->
            farthestX = max(farthestX, x)
            val backwards = farthestX - x > 0.20
            if (backwards && !wasBacktracking) backtracks++
            wasBacktracking = backwards
            val band = (x * 9).roundToInt().coerceIn(0, 9)
            if (band != lastBand) {
                if (lastBand >= 0 && band in visitedBands &&
                    abs(band - lastBand) >= 2
                ) bandTransitions++
                visitedBands.add(band)
                lastBand = band
            }
        }
        return Report(
            stage = stage,
            points = design.points.size,
            stitches = path.size,
            jumps = design.points.count { it.command == StitchCommand.JUMP },
            trims = design.points.count { it.command == StitchCommand.TRIM },
            travelMm = total / 10.0,
            significantBacktracks = backtracks,
            revisitBins = bandTransitions,
            checkpoints = checkpoints
        )
    }
}
