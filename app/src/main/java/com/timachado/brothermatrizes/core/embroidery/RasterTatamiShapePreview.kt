package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * QA-only, counter-aware scanline filling for one broad monochrome object.
 *
 * Converts each actual ink interval on a sampled row into sewn STITCH
 * segments. Joining different intervals/rows is permitted only when EVERY
 * sampled point of that stitch remains inside the original component mask.
 * Otherwise an explicit TRIM + JUMP is emitted, never a stitched crossing
 * of the O/B/P counter or an irregular concavity.
 *
 * No underlay, pull compensation, fabric density calibration or physical
 * Brother stitch-out. Not a universal Tatami algorithm or PES exporter.
 */
internal object RasterTatamiShapePreview {
    private const val MAX_COMMANDS = 7_000
    private const val TARGET_ROW_MM = .78
    private const val END_ROW_MARGIN_MM = .36
    private const val STITCH_TARGET_MM = 2.20
    private const val MIN_STITCH_MM = .20
    private const val MAX_STITCH_MM = 2.80
    private const val MIN_RUN_MM = 1.25

    private data class P(val x: Double, val y: Double)

    fun create(
        image: RasterImagePixels,
        part: RasterSatinObject,
        widthMm: Float,
        hoop: HoopProfile
    ): Result<EmbroideryDesign> = runCatching {
        require(image.width in 8..512 && image.height in 8..512 &&
            image.argb.size == part.mask.pixels.size &&
            widthMm.isFinite() && widthMm in 10f..200f) {
            "Imagem e escala incompatíveis."
        }
        val heightMm = widthMm * image.height / image.width
        require(widthMm <= hoop.usableWidthMm &&
            heightMm <= hoop.usableHeightMm) {
            "Imagem fora da área útil do bastidor."
        }
        // Provenance is checked against RAW imported pixels rather than a
        // fresh palette clustering. It catches fabricated or stale counters,
        // changed colors and new ink adjoining the old component boundary,
        // including a stroke extending beyond the previous bounding box.
        // This works for manually reviewed masks without forcing a new
        // auto-segmentation or silently shifting their original placement.
        RasterSourceInkProvenanceAudit.inspect(image,part).getOrThrow()

        val m = part.mask
        val w = m.maxX - m.minX + 1
        val h = m.maxY - m.minY + 1
        val mmPx = widthMm / image.width.toDouble()
        require(w in 16..250 && h in 16..250 &&
            w * mmPx in 7.0..48.0 &&
            h * mmPx in 7.0..48.0 &&
            part.pixelCount >= 200
        ) {
            "Tatami em formas: somente áreas médias de 7–48 mm em QA."
        }
        fun ink(x: Int, y: Int): Boolean =
            x in m.minX..m.maxX && y in m.minY..m.maxY &&
                m.pixels[y * image.width + x] == 0

        // Reject fundamentally thin or speckled regions before planning.
        val inkFraction = part.pixelCount.toDouble() / (w * h)
        require(inkFraction in .45..1.0) {
            "Região pouco contínua: separar contornos manualmente."
        }

        fun sewInside(a: P, b: P): Boolean {
            val distance = hypot(b.x-a.x, b.y-a.y)
            val steps = ceil(distance * 5.0).toInt().coerceAtLeast(1)
            for (i in 0..steps) {
                val t = i.toDouble()/steps
                val x = a.x + (b.x-a.x)*t
                val y = a.y + (b.y-a.y)*t
                if (!ink(floor(x).toInt(),floor(y).toInt())) return false
            }
            return true
        }

        val top = m.minY + .5 + END_ROW_MARGIN_MM / mmPx
        val bottom = m.maxY + .5 - END_ROW_MARGIN_MM / mmPx
        require(bottom > top) { "Região insuficiente para linhas de preenchimento." }
        val intervals = ceil((bottom-top)*mmPx / TARGET_ROW_MM).toInt()
        require(intervals in 4..110)
        val stepMm = (bottom-top)*mmPx/intervals
        require(stepMm in .55..1.0) { "Distância entre fileiras inválida." }

        fun at(p: P, cmd: StitchCommand): EmbroideryPoint =
            EmbroideryPoint(
                ((p.x-image.width/2.0)*mmPx*10).roundToInt(),
                ((image.height/2.0-p.y)*mmPx*10).roundToInt(),
                cmd, 0
            )
        val points = ArrayList<EmbroideryPoint>()
        var previous: P? = null
        var trimCount = 0
        var totalRuns = 0
        var multiRuns = 0

        for (row in 0..intervals) {
            val y = top + (bottom-top)*row/intervals
            val maskY = floor(y).toInt()
            val runs = ArrayList<Pair<Int,Int>>()
            var x = m.minX
            while (x <= m.maxX) {
                if (!ink(x,maskY)) { x++; continue }
                val start = x
                while (x <= m.maxX && ink(x,maskY)) x++
                runs += start to (x-1)
            }
            require(runs.size in 1..3) {
                "Fileira contém vazios excessivos ou múltiplas ilhas."
            }
            multiRuns += if (runs.size > 1) 1 else 0
            val ordered = if (row % 2 == 0) runs
                else ArrayList(runs.asReversed())
            for ((startX, endX) in ordered) {
                val lengthMm = (endX-startX)*mmPx
                require(lengthMm >= MIN_RUN_MM) {
                    "Ponta estreita ou ilha pequena: preenchimento exige revisão."
                }
                val samples = ceil(lengthMm/STITCH_TARGET_MM).toInt()
                require(samples in 1..32)
                val ends = (0..samples).map { index ->
                    val t = index.toDouble()/samples
                    val xPix = if (row % 2 == 0) {
                        startX+.5+(endX-startX)*t
                    } else {
                        endX+.5-(endX-startX)*t
                    }
                    P(xPix,y)
                }
                val first = ends.first()
                val old = previous
                val joinMm = old?.let { hypot(it.x-first.x,it.y-first.y)*mmPx }
                if (old == null) {
                    points += at(first,StitchCommand.JUMP)
                } else if (joinMm != null &&
                    joinMm in MIN_STITCH_MM..MAX_STITCH_MM &&
                    sewInside(old,first)
                ) {
                    points += at(first,StitchCommand.STITCH)
                } else if (joinMm != null && joinMm < MIN_STITCH_MM &&
                    abs(old.x-first.x) < 1e-6 && abs(old.y-first.y) < 1e-6
                ) {
                    // Same exact endpoint: no extra needle penetration.
                } else {
                    points += at(old,StitchCommand.TRIM)
                    points += at(first,StitchCommand.JUMP)
                    trimCount++
                }
                var cur = first
                for (next in ends.drop(1)) {
                    val len = hypot(next.x-cur.x,next.y-cur.y)*mmPx
                    require(len in MIN_STITCH_MM..MAX_STITCH_MM &&
                        sewInside(cur,next)
                    ) {
                        "A trajetória cruzaria espaço vazio ou possui pitch inválido."
                    }
                    points += at(next,StitchCommand.STITCH)
                    cur = next
                }
                previous = cur
                totalRuns++
                require(points.size < MAX_COMMANDS) {
                    "Limite de pontos para revisão Tatami ultrapassado."
                }
            }
        }
        require(totalRuns >= 5 && points.count {
            it.command == StitchCommand.STITCH
        } >= 25)
        // With holes, every split interval must have independent travel; if
        // this fails we have accidentally sewn a bridge across a counter.
        require(multiRuns == 0 || trimCount > 0) {
            "Contraforma não foi separada por deslocamento sem costura."
        }
        val last = previous ?: error("Nenhum ponto gerado.")
        points += at(last, StitchCommand.END)
        val active = points.filter {
            it.command == StitchCommand.STITCH || it.command == StitchCommand.JUMP
        }
        val bounds = EmbroideryBounds(
            active.minOf { it.xUnits },active.maxOf { it.xUnits },
            active.minOf { it.yUnits },active.maxOf { it.yUnits }
        )
        require(bounds.widthMm <= hoop.usableWidthMm &&
            bounds.heightMm <= hoop.usableHeightMm)
        val draft = EmbroideryDesign(
            fileName = "TATAMI-CONTORNOS-QA.qa",
            format = "QA_TATAMI_REVIEW",
            label = "Tatami por máscara com contraformas — somente simulação QA",
            points = points,
            bounds = bounds,
            stitchCount = points.count { it.command == StitchCommand.STITCH },
            jumpCount = points.count { it.command == StitchCommand.JUMP },
            colorChanges = 0,
            endFound = true,
            sourceBytes = ByteArray(0),
            threadColors = listOf(part.color),
            isModified = true,hoopProfile = hoop
        )
        val needle = RasterNeedleRiskAudit.inspect(draft,hoop).getOrThrow()
        val density = RasterThreadDensityPreflight.inspect(draft).getOrThrow()
        require(needle.stitchesExamined == density.sewnSegments) {
            "A sequência Tatami possui contagens divergentes na auditoria física preliminar."
        }
        // Validate the INTEGER commands after 0.1 mm quantization: no
        // stitched line may pass through an original counter or empty pixel.
        RasterSewnMaskGate.inspect(image, part, draft, widthMm).getOrThrow()
        draft
    }
}
