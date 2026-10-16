package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Geometric raster-to-stitches comparison for Android QA, not a simulated
 * photograph of thread and never a certification of machine-ready PES.
 */
internal data class RasterStitchFidelity(
    val inkPixels: Int,
    val coveredInkPixels: Int,
    val stitchSamples: Int,
    val offInkSamples: Int
) {
    val coverage: Double get() =
        if (inkPixels == 0) 0.0 else coveredInkPixels.toDouble() / inkPixels
    val offInkFraction: Double get() =
        if (stitchSamples == 0) 1.0 else offInkSamples.toDouble() / stitchSamples
}

/**
 * Unlike the previous "rail coverage" metric, compare the ACTUAL STITCH
 * command stream rendered by ManualSatinRails against the original component.
 *
 * - Honor JUMP/TRIM/COLOR_CHANGE, do not draw non-sewing transitions.
 * - Detect missing strokes and zigzags crossing transparent counters.
 * - A stitch point is in tenths of millimeters; convert using the full-image
 *   placement, not the local component width.
 * - Allow a small fabric-pull/anti-aliasing tolerance, not arbitrary bleed.
 *
 * This is a *conservative screen-space gate*. Physical thread, fabric,
 * machine speed, pull compensation and underlay still require physical QA.
 */
internal object RasterStitchFidelityAudit {
    fun compare(
        source: RasterImagePixels,
        component: RasterSatinObject,
        design: EmbroideryDesign,
        fullWidthMm: Float
    ): Result<RasterStitchFidelity> = runCatching {
        require(source.width in 8..512 && source.height in 8..512 &&
            source.argb.size == component.mask.pixels.size &&
            fullWidthMm.isFinite() && fullWidthMm in 10f..200f &&
            design.points.isNotEmpty() &&
            design.format in setOf("QA_SATIN_REVIEW", "QA_RUNNING_REVIEW") &&
            design.sourceBytes.isEmpty()
        ) { "Origem ou trajetória QA inválida." }

        val b = component.mask
        val w = b.maxX - b.minX + 1
        val h = b.maxY - b.minY + 1
        require(w > 0 && h > 0 && w * h <= 262144) {
            "Máscara de avaliação inválida."
        }
        val mmPerPixel = fullWidthMm / source.width.toFloat()
        val unitsPerPixel = mmPerPixel * 10.0
        val occupied = BooleanArray(w * h)
        fun inkLocal(x: Int, y: Int) =
            x in 0 until w && y in 0 until h &&
                b.pixels[(b.minY + y) * source.width + b.minX + x] == 0
        fun nearInk(x: Double, y: Double): Boolean {
            // Source is pixel-centred. The ~0.20 mm buffer tolerates edge
            // compensation without accepting a jump across a letter's hole.
            val radius = 0.2 / mmPerPixel + .55
            val cx = floor(x).toInt()
            val cy = floor(y).toInt()
            val search = ceil(radius).toInt()
            for (dy in -search..search) for (dx in -search..search) {
                val xx = cx + dx
                val yy = cy + dy
                if (!inkLocal(xx, yy)) continue
                if (hypot(xx + .5 - x, yy + .5 - y) <= radius) return true
            }
            return false
        }

        var samples = 0
        var off = 0
        // A 0.35 mm envelope approximates raster coverage of rows of
        // thread. It deliberately does NOT create stitch positions.
        val coverRadius = 0.35 / mmPerPixel + .5
        val coverSearch = ceil(coverRadius).toInt()

        fun pointInSource(p: EmbroideryPoint): Pair<Double, Double> {
            val x = p.xUnits / unitsPerPixel + source.width / 2.0
            val y = source.height / 2.0 - p.yUnits / unitsPerPixel
            return (x - b.minX) to (y - b.minY)
        }
        fun sewSegment(start: EmbroideryPoint, end: EmbroideryPoint) {
            val (ax, ay) = pointInSource(start)
            val (bx, by) = pointInSource(end)
            val length = hypot(bx - ax, by - ay)
            // Limit pathological data early; no invisible multi-cm segments.
            require(length * mmPerPixel <= 8.0) {
                "Pontada real acima do limite de 8 mm."
            }
            val steps = max(1, ceil(length * 3.0).toInt())
            for (i in 0..steps) {
                val t = i.toDouble() / steps
                val x = ax + (bx - ax) * t
                val y = ay + (by - ay) * t
                samples++
                if (!nearInk(x, y)) off++
                val ix = floor(x).toInt()
                val iy = floor(y).toInt()
                for (dy in -coverSearch..coverSearch) for (dx in -coverSearch..coverSearch) {
                    val px = ix + dx
                    val py = iy + dy
                    if (!inkLocal(px, py)) continue
                    if (hypot(px + .5 - x, py + .5 - y) <= coverRadius) {
                        occupied[py * w + px] = true
                    }
                }
            }
        }

        var previous: EmbroideryPoint? = null
        for (p in design.points) {
            when (p.command) {
                StitchCommand.JUMP -> previous = p
                StitchCommand.STITCH -> {
                    val start = previous
                    require(start != null) {
                        "Sequência possui STITCH sem deslocamento inicial."
                    }
                    sewSegment(start, p)
                    previous = p
                }
                StitchCommand.TRIM, StitchCommand.COLOR_CHANGE,
                StitchCommand.STOP, StitchCommand.END,
                StitchCommand.SEQUIN -> previous = null
            }
            require(samples <= 1_000_000) {
                "Quantidade excessiva de pontos para auditoria visual."
            }
        }
        val totalInk = (0 until h).sumOf { y ->
            (0 until w).count { x -> inkLocal(x,y) }
        }
        val covered = (0 until h).sumOf { y ->
            (0 until w).count { x ->
                inkLocal(x,y) && occupied[y*w+x]
            }
        }
        require(totalInk > 0 && samples > 0)
        RasterStitchFidelity(
            inkPixels = totalInk, coveredInkPixels = covered,
            stitchSamples = samples, offInkSamples = off
        )
    }
}
