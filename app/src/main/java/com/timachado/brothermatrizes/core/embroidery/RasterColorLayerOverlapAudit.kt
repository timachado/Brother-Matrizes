package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot

/**
 * Cautious additional QA signal: three different thread-color layers
 * occupying the same small spatial cell can indicate excess overlap.
 *
 * Count ONLY sewn STITCH segments, not JUMP/TRIM travel. Uses a 0.8 mm
 * occupancy grid as a coarse screening heuristic (NOT a Brother density
 * specification and NOT a guarantee that two layers are always safe).
 * The original imported PES/JEF/DST simulator is not examined or altered.
 */
internal data class RasterLayerOverlapReport(
    val sewnSegments: Int,
    val cellsObserved: Int,
    val maximumDistinctColorsPerCell: Int
)

internal object RasterColorLayerOverlapAudit {
    private const val CELL_UNITS = 8 // 0.8 mm, QA heuristic
    private const val STEP_UNITS = 4.0 // 0.4 mm sampling
    private const val MAX_SAMPLES = 2_000_000
    private const val MAX_CELLS = 150_000

    fun inspect(design: EmbroideryDesign): Result<RasterLayerOverlapReport> =
        runCatching {
            require(design.format == "QA_AUTO_STITCH" &&
                design.sourceBytes.isEmpty() && design.isModified &&
                design.points.size in 3..90_000 &&
                design.threadColors.size in 1..30 &&
                design.points.last().command == StitchCommand.END
            ) {
                "Sobreposição só pode ser avaliada em pontos QA não exportáveis."
            }
            val colorsByCell = HashMap<Long,Int>()
            var previous: EmbroideryPoint? = null
            var samples = 0
            var sewn = 0
            var maxColors = 0
            fun key(x: Double,y: Double): Long {
                val cx = floor(x / CELL_UNITS).toLong()
                val cy = floor(y / CELL_UNITS).toLong()
                return (cx shl 32) xor (cy and 0xffff_ffffL)
            }
            for (p in design.points) {
                when (p.command) {
                    StitchCommand.JUMP -> previous = p
                    StitchCommand.STITCH -> {
                        val start = previous ?: error(
                            "Costura inesperada sem deslocamento de entrada."
                        )
                        require(p.colorIndex == start.colorIndex &&
                            p.colorIndex in design.threadColors.indices
                        ) { "Costura entre cores sem troca de linha." }
                        val dist = hypot(
                            (p.xUnits-start.xUnits).toDouble(),
                            (p.yUnits-start.yUnits).toDouble()
                        )
                        require(dist >= 2 && dist <= 78) {
                            "Comprimento do ponto fora da faixa preliminar QA."
                        }
                        val steps = ceil(dist/STEP_UNITS).toInt().coerceAtLeast(1)
                        val colorBit = 1 shl p.colorIndex
                        for (i in 0..steps) {
                            val t = i.toDouble()/steps
                            val xx = start.xUnits + (p.xUnits-start.xUnits)*t
                            val yy = start.yUnits + (p.yUnits-start.yUnits)*t
                            val k = key(xx,yy)
                            val visited = (colorsByCell[k] ?: 0) or colorBit
                            colorsByCell[k] = visited
                            val colorLayers = Integer.bitCount(visited)
                            maxColors = maxOf(maxColors,colorLayers)
                            require(colorLayers < 3) {
                                "Três cores de linha se sobrepõem na mesma área " +
                                    "de 0,8 mm. Excesso de camadas possível; " +
                                    "revise a ordem e o preenchimento."
                            }
                            samples++
                            require(samples <= MAX_SAMPLES &&
                                colorsByCell.size <= MAX_CELLS
                            ) { "Análise de sobreposições excedeu limite de memória." }
                        }
                        sewn++
                        previous = p
                    }
                    StitchCommand.TRIM, StitchCommand.COLOR_CHANGE,
                    StitchCommand.STOP -> previous = null
                    StitchCommand.END -> previous = null
                    StitchCommand.SEQUIN -> error("Lantejoulas não suportadas em QA.")
                }
            }
            require(sewn == design.stitchCount && sewn > 0) {
                "Contagem de pontos inconsistente."
            }
            RasterLayerOverlapReport(
                sewnSegments = sewn,
                cellsObserved = colorsByCell.size,
                maximumDistinctColorsPerCell = maxColors
            )
        }
}
