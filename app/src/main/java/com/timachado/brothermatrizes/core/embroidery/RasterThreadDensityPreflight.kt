package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.floor
import kotlin.math.hypot

/**
 * Coarse QA screen of actual THREAD laid down per 1 x 1 mm cell.
 *
 * NeedleRiskAudit screens STITCH *endpoints*. OverlapAudit counts distinct
 * thread colors. Neither detects many overlapping segments of one color.
 * Here every STITCH is split geometrically at cell boundaries (no sampled
 * bitmap approximation) and its physical sewn length is accumulated.
 *
 * These are deliberately conservative, provisional SOFTWARE thresholds.
 * 20 mm of thread centreline in a 1 mm square is a review trigger, NOT a
 * published Brother density limit, fabric model or safe-machine guarantee.
 * Adjacent rows, thread width, underlay, stabilizer, pull and stitch-outs
 * still require separate physical QA. This never writes or reads PES bytes.
 */
internal data class RasterThreadDensityReport(
    val sewnSegments: Int,
    val cellsObserved: Int,
    val sewnThreadLengthMm: Double,
    val peakThreadLengthMmPerCell: Double
)

internal object RasterThreadDensityPreflight {
    private const val CELL_UNITS = 10.0 // units are 0.1 mm
    private const val LIMIT_MM_PER_CELL = 20.0
    private const val MAX_CELLS = 150_000
    private const val MAX_COMMANDS = 90_000

    fun inspect(
        design: EmbroideryDesign
    ): Result<RasterThreadDensityReport> = runCatching {
        require(
            design.format in setOf(
                "QA_AUTO_STITCH", "QA_SATIN_REVIEW",
                "QA_RUNNING_REVIEW", "QA_TATAMI_REVIEW"
            ) &&
            design.sourceBytes.isEmpty() && design.isModified &&
            design.points.size in 3..MAX_COMMANDS &&
            design.points.last().command == StitchCommand.END &&
            design.threadColors.isNotEmpty()
        ) {
            "A densidade de linha aceita apenas pontos de QA, nunca PES importado."
        }
        val lengthByCell = HashMap<Long, Double>()
        var previous: EmbroideryPoint? = null
        var stitches = 0
        var jumps = 0
        var sewnMm = 0.0
        var maximumMm = 0.0

        fun cellKey(x: Double, y: Double): Long {
            val cx = floor(x / CELL_UNITS).toLong()
            val cy = floor(y / CELL_UNITS).toLong()
            return (cx shl 32) xor (cy and 0xffffffffL)
        }

        for ((index, point) in design.points.withIndex()) {
            require(point.colorIndex in design.threadColors.indices) {
                "Índice de cor inválido no comando ${index + 1}."
            }
            when (point.command) {
                StitchCommand.JUMP -> {
                    previous = point
                    jumps++
                }
                StitchCommand.STITCH -> {
                    val a = previous ?: error(
                        "STITCH sem JUMP depois do corte ou troca de cor."
                    )
                    require(a.colorIndex == point.colorIndex) {
                        "Mudança de cor sem comando específico."
                    }
                    val ax = a.xUnits.toDouble()
                    val ay = a.yUnits.toDouble()
                    val bx = point.xUnits.toDouble()
                    val by = point.yUnits.toDouble()
                    val dx = bx-ax
                    val dy = by-ay
                    val mm = hypot(dx,dy)/10.0
                    require(mm in .20..7.80) {
                        "Pontada fora dos limites geométricos preliminares."
                    }
                    // Exact cell-length allocation: split the segment where
                    // it intersects any horizontal/vertical 1-mm gridline.
                    val cut = ArrayList<Double>(22)
                    cut += 0.0
                    cut += 1.0
                    fun addCrossings(start: Double, delta: Double) {
                        if (delta == 0.0) return
                        val end = start + delta
                        val lo = minOf(start,end)
                        val hi = maxOf(start,end)
                        var line = (floor(lo / CELL_UNITS) + 1.0)*CELL_UNITS
                        while(line < hi) {
                            val t = (line-start)/delta
                            if (t>1e-10 && t<1.0-1e-10) cut += t
                            line += CELL_UNITS
                        }
                    }
                    addCrossings(ax,dx)
                    addCrossings(ay,dy)
                    cut.sort()
                    for (i in 0 until cut.lastIndex) {
                        val mid = (cut[i]+cut[i+1])/2.0
                        val partMm = mm*(cut[i+1]-cut[i])
                        if(partMm<=1e-9)continue
                        val key = cellKey(
                            ax+dx*mid,
                            ay+dy*mid
                        )
                        val accumulated = (lengthByCell[key] ?: 0.0)+partMm
                        lengthByCell[key] = accumulated
                        maximumMm = maxOf(maximumMm,accumulated)
                        require(lengthByCell.size <= MAX_CELLS) {
                            "Limite de memória da auditoria de densidade ultrapassado."
                        }
                        require(accumulated <= LIMIT_MM_PER_CELL + 1e-8) {
                            "Concentração de linha excessiva: mais de 20 mm " +
                                "de segmentos costurados numa célula de 1 mm². " +
                                "Revisar Satin/Tatami, camadas e underlay."
                        }
                    }
                    sewnMm += mm
                    stitches++
                    previous=point
                }
                StitchCommand.TRIM,StitchCommand.COLOR_CHANGE,
                StitchCommand.STOP -> previous=null
                StitchCommand.END -> require(index==design.points.lastIndex) {
                    "Comando END prematuro."
                }
                StitchCommand.SEQUIN -> error(
                    "Lantejoula não suportada na densidade QA."
                )
            }
        }
        require(stitches>0 && jumps>0 &&
            stitches==design.stitchCount && jumps==design.jumpCount
        ) { "Contagem de pontos e saltos inconsistente." }
        RasterThreadDensityReport(
            sewnSegments=stitches,
            cellsObserved=lengthByCell.size,
            sewnThreadLengthMm=sewnMm,
            peakThreadLengthMmPerCell=maximumMm
        )
    }
}
