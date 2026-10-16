package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * QA-only running stitch for a thin, single-valued, unbranched raster line.
 *
 * This is a limited implementation: monotonic horizontal/vertical hairlines
 * are converted into actual STITCH positions at controlled spacing.
 * It refuses loops, junctions, multiple ink runs per cross-section, gaps and
 * overly sharp turns. It is NOT a generic multi-path outline digitizer.
 *
 * Results are ephemeral QA designs, never machine-approved PES bytes.
 */
internal object RasterRunningOutlinePreview {
    private const val TARGET_STEP_MM = 1.6
    private const val MAX_STEP_MM = 2.5

    fun create(
        source: RasterImagePixels,
        part: RasterSatinObject,
        fullWidthMm: Float,
        hoop: HoopProfile
    ): Result<EmbroideryDesign> = runCatching {
        require(source.width in 8..512 && source.height in 8..512 &&
            source.argb.size == part.mask.pixels.size &&
            fullWidthMm.isFinite() && fullWidthMm in 10f..200f) {
            "Imagem ou escala incompatível com ponto corrido."
        }
        val fullHeightMm = fullWidthMm * source.height / source.width.toFloat()
        require(fullWidthMm <= hoop.usableWidthMm &&
            fullHeightMm <= hoop.usableHeightMm) {
            "A imagem ultrapassa a área útil do bastidor."
        }
        val m = part.mask
        val width = m.maxX - m.minX + 1
        val height = m.maxY - m.minY + 1
        val perPixelMm = fullWidthMm / source.width
        val horizontal = width >= height
        val length = if (horizontal) width else height
        val thickness = if (horizontal) height else width
        require(length >= 16 && length.toFloat() / thickness >= 3.0f &&
            thickness * perPixelMm <= 1.15f &&
            length * perPixelMm >= 3.0f &&
            part.pixelCount >= 15
        ) {
            "Este objeto não é um traço fino contínuo. " +
                "Exige separação em contornos editáveis."
        }

        fun ink(x: Int, y: Int): Boolean =
            x in 0 until width && y in 0 until height &&
                m.pixels[(m.minY + y) * source.width + m.minX + x] == 0

        data class P(val x: Double, val y: Double)
        val center = ArrayList<P>(length)
        for (along in 0 until length) {
            var first = -1
            var last = -1
            var pixels = 0
            for (side in 0 until thickness) {
                val found = if (horizontal) ink(along,side) else ink(side,along)
                if (found) {
                    if (first < 0) first = side
                    last = side
                    pixels++
                }
            }
            require(first >= 0 && pixels == last - first + 1) {
                "O traço possui descontinuidade ou ramificação nesta seção."
            }
            val cross = (first + last + 1) * 0.5
            center += if (horizontal) P(along + .5, cross)
                else P(cross, along + .5)
        }
        for (i in 1 until center.size) {
            val a = center[i-1]
            val b = center[i]
            require(hypot(a.x-b.x,a.y-b.y) <= 2.25) {
                "Traço com mudança brusca de direção: revisão necessária."
            }
        }

        fun nearOriginal(p: P): Boolean {
            // Use only a minor raster tolerance to avoid chords crossing a
            // hole even when both endpoints happen to lie on the ink.
            val ix = p.x.toInt()
            val iy = p.y.toInt()
            for (y in iy-1..iy+1) for (x in ix-1..ix+1) {
                if (!ink(x,y)) continue
                if (hypot(x+.5-p.x,y+.5-p.y) <= .95) return true
            }
            return false
        }
        val control = ArrayList<P>()
        control += center.first()
        var start = center.first()
        var carried = 0.0
        for (i in 1 until center.size) {
            val prev = center[i-1]
            val end = center[i]
            carried += hypot(prev.x-end.x,prev.y-end.y) * perPixelMm
            if (carried >= TARGET_STEP_MM || i == center.lastIndex) {
                val distanceMm = hypot(start.x-end.x,start.y-end.y)*perPixelMm
                // Do not use very short or oversized sewn segments.
                if (distanceMm < .35 && i != center.lastIndex) continue
                require(distanceMm <= MAX_STEP_MM && distanceMm >= .25) {
                    "Ponto corrido com comprimento inadequado."
                }
                val n = max(2,ceil(hypot(start.x-end.x,start.y-end.y)*3).toInt())
                for (j in 0..n) {
                    val t = j.toDouble()/n
                    require(nearOriginal(P(
                        start.x + (end.x-start.x)*t,
                        start.y + (end.y-start.y)*t
                    ))) {
                        "O ponto corrido atravessaria uma área vazia. Divida o traço."
                    }
                }
                control += end
                start = end
                carried = 0.0
            }
        }
        require(control.size in 3..2000) { "Traço insuficiente para ponto corrido." }

        val unitsPerPixel = perPixelMm * 10.0
        fun stitch(p: P, command: StitchCommand): EmbroideryPoint {
            val px = m.minX + p.x
            val py = m.minY + p.y
            return EmbroideryPoint(
                ((px-source.width/2.0)*unitsPerPixel).roundToInt(),
                ((source.height/2.0-py)*unitsPerPixel).roundToInt(),
                command,0
            )
        }
        val commands = ArrayList<EmbroideryPoint>()
        commands += stitch(control.first(), StitchCommand.JUMP)
        for (p in control.drop(1)) commands += stitch(p,StitchCommand.STITCH)
        commands += stitch(control.last(),StitchCommand.END)
        val actual = commands.dropLast(1)
        val bounds = EmbroideryBounds(
            actual.minOf { it.xUnits }, actual.maxOf { it.xUnits },
            actual.minOf { it.yUnits }, actual.maxOf { it.yUnits }
        )
        require(bounds.widthMm <= hoop.usableWidthMm &&
            bounds.heightMm <= hoop.usableHeightMm) {
            "Ponto corrido fora do bastidor."
        }
        EmbroideryDesign(
            fileName = "CONTORNO-CORRIDO-QA.qa",
            format = "QA_RUNNING_REVIEW",
            label = "Ponto corrido de linha simples — não enviar à máquina",
            points = commands, bounds = bounds,
            stitchCount = commands.count { it.command == StitchCommand.STITCH },
            jumpCount = 1, colorChanges = 0, endFound = true,
            sourceBytes = ByteArray(0),
            threadColors = listOf(part.color),
            isModified = true, hoopProfile = hoop
        )
    }
}
