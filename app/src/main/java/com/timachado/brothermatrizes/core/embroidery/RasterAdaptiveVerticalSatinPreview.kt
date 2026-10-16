package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Experimental, non-exportable stitch-path PREVIEW for multicomponent images.
 *
 * Finds local vertical satin rail pairs by dividing the raster into narrow
 * horizontal bands, then follows continuous color runs from left to right.
 * Each vertical rail crossing is a STITCH; disconnected portions start with
 * JUMP/TRIM. No horizontal raster-fill stitches and no made-up display lines.
 *
 * This is not production digitization: seams between bands, running
 * stitch paths, anchoring, underlay and fabric tension still require
 * manual editing and a real Brother stitch-out. NEVER export as PES.
 */
internal object RasterAdaptiveVerticalSatinPreview {
    private const val MAX_COMMANDS = 110_000
    private const val MAX_CROSSING_UNITS = 60 // 6 mm; still QA only
    private const val MAX_STITCH_UNITS = 75 // 7.5 mm
    private const val MIN_CROSSING_UNITS = 8 // 0.8 mm
    private const val DENSITY_UNITS = 5f // 0.5 mm nominal horizontal advance

    private data class Run(val x: Int, val top: Int, val bottom: Int) {
        val mid get() = (top + bottom) / 2
        val length get() = bottom - top + 1
    }
    private class Track(start: Run) {
        val runs = ArrayList<Run>().apply { add(start) }
        val last get() = runs.last()
    }

    fun create(
        source: RasterImagePixels,
        art: SeparatedColorArtwork,
        widthMm: Float,
        hoop: HoopProfile
    ): Result<EmbroideryDesign> = runCatching {
        require(source.width in 8..512 && source.height in 8..512 &&
            source.argb.size == source.width * source.height &&
            art.pixels.size == source.width * source.height &&
            art.colors.isNotEmpty() && art.colors.size <= 4 &&
            widthMm.isFinite() && widthMm in 10f..200f) {
            "Imagem incompatível com a prévia Satin."
        }
        val heightMm = widthMm * source.height / source.width.toFloat()
        require(widthMm <= hoop.usableWidthMm && heightMm <= hoop.usableHeightMm) {
            "A arte não cabe na área útil do bastidor."
        }
        val units = widthMm * 10f / source.width
        val stepPx = max(1, (DENSITY_UNITS / units).roundToInt())
        val bandHeightPx = max(1, floor(MAX_CROSSING_UNITS / units).toInt())
        val points = ArrayList<EmbroideryPoint>()
        val activeColors = ArrayList<Int>()
        var segments = 0

        fun xCoord(x: Int): Int =
            ((x + .5f - source.width / 2f) * units).roundToInt()
        fun yCoord(y: Int): Int =
            ((source.height / 2f - y - .5f) * units).roundToInt()
        fun put(x: Int, y: Int, command: StitchCommand, color: Int) {
            require(points.size < MAX_COMMANDS) {
                "Imagem complexa demais para a simulação. Selecione parte da logo."
            }
            points += EmbroideryPoint(x, y, command, color)
        }
        fun matching(old: Run, next: Run): Boolean {
            val overlap = min(old.bottom, next.bottom) -
                max(old.top, next.top) + 1
            return overlap >= max(1, min(old.length, next.length) / 3) &&
                abs(old.mid - next.mid) * units <= 13f // 1.3mm rail drift
        }

        for (sourceColor in art.colors.indices) {
            val tracks = ArrayList<Track>()
            // Each band is limited to <=6mm in the needle direction.
            // Cross-band transitions are JUMPs, never an elongated STITCH.
            var yBand = art.minY
            while (yBand <= art.maxY) {
                val bandBottom = min(art.maxY, yBand + bandHeightPx - 1)
                var previous = ArrayList<Track>()
                var x = art.minX
                while (x <= art.maxX) {
                    val runs = ArrayList<Run>()
                    var y = yBand
                    while (y <= bandBottom) {
                        while (y <= bandBottom &&
                            art.pixels[y * source.width + x] != sourceColor) y++
                        if (y > bandBottom) break
                        val top = y
                        while (y <= bandBottom &&
                            art.pixels[y * source.width + x] == sourceColor) y++
                        val bottom = y - 1
                        if ((bottom - top + 1) * units >= MIN_CROSSING_UNITS) {
                            runs += Run(x, top, bottom)
                        }
                    }
                    val assigned = HashSet<Track>()
                    val next = ArrayList<Track>()
                    for (run in runs) {
                        val match = previous.filter {
                            it !in assigned && matching(it.last, run)
                        }.minByOrNull { abs(it.last.mid - run.mid) }
                        val track = match ?: Track(run).also { tracks += it }
                        if (match != null) track.runs += run
                        assigned += track
                        next += track
                    }
                    previous = next
                    x += stepPx
                }
                yBand = bandBottom + 1
            }

            var usedColor = false
            val colorIndex = activeColors.size
            for (track in tracks) {
                if (track.runs.size < 2) continue
                val start = track.runs[0]
                // Only stitch true vertical rail crossings for locally
                // continuous sections, alternating top/bottom rails.
                fun crossing(run: Run): Pair<Int, Int> {
                    return yCoord(run.top) to yCoord(run.bottom)
                }
                val a = crossing(start)
                if (abs(a.first - a.second) !in MIN_CROSSING_UNITS..MAX_CROSSING_UNITS) {
                    continue
                }
                if (!usedColor) {
                    if (points.isNotEmpty()) {
                        val last = points.last()
                        put(last.xUnits, last.yUnits, StitchCommand.TRIM, colorIndex - 1)
                        put(last.xUnits, last.yUnits, StitchCommand.COLOR_CHANGE, colorIndex)
                    }
                    activeColors += art.colors[sourceColor]
                    usedColor = true
                } else {
                    val last = points.last()
                    put(last.xUnits, last.yUnits, StitchCommand.TRIM, colorIndex)
                }

                put(xCoord(start.x), a.first, StitchCommand.JUMP, colorIndex)
                put(xCoord(start.x), a.second, StitchCommand.STITCH, colorIndex)
                var endWasBottom = true
                for (i in 1 until track.runs.size) {
                    val nextRun = track.runs[i]
                    val rail = crossing(nextRun)
                    val targetY = if (endWasBottom) rail.first else rail.second
                    val targetX = xCoord(nextRun.x)
                    val previousPoint = points.last()
                    val deltaX = abs(targetX - previousPoint.xUnits)
                    val deltaY = abs(targetY - previousPoint.yUnits)
                    val length = hypot(deltaX.toDouble(), deltaY.toDouble())
                    if (deltaY < deltaX || length > MAX_STITCH_UNITS ||
                        length < 1.0) {
                        // A connected rail is no longer suitable for a Satin
                        // stitch. Explicitly jump, then resume a vertical
                        // crossing instead of creating a long/sideways STITCH.
                        put(targetX, rail.first, StitchCommand.JUMP, colorIndex)
                        if (abs(rail.first - rail.second) in
                            MIN_CROSSING_UNITS..MAX_CROSSING_UNITS) {
                            put(targetX, rail.second, StitchCommand.STITCH, colorIndex)
                            endWasBottom = true
                        }
                    } else {
                        put(targetX, targetY, StitchCommand.STITCH, colorIndex)
                        endWasBottom = !endWasBottom
                    }
                }
                segments++
            }
        }
        require(points.any { it.command == StitchCommand.STITCH } && segments > 0) {
            "Esta seleção não contém colunas Satin adequadas. Ajuste o recorte ou a escala."
        }
        val last = points.last()
        put(last.xUnits, last.yUnits, StitchCommand.END, activeColors.lastIndex)
        EmbroideryDesign(
            fileName = "SATIN-REVISAO-SEM-EXPORTACAO.qa",
            format = "QA_SATIN_REVIEW",
            label = "Satin vertical experimental por seções — só prévia",
            points = points,
            bounds = EmbroideryBounds(
                points.minOf { it.xUnits }, points.maxOf { it.xUnits },
                points.minOf { it.yUnits }, points.maxOf { it.yUnits }
            ),
            stitchCount = points.count { it.command == StitchCommand.STITCH },
            jumpCount = points.count { it.command == StitchCommand.JUMP },
            colorChanges = points.count { it.command == StitchCommand.COLOR_CHANGE },
            endFound = true,
            sourceBytes = ByteArray(0), // not PES and never machine-transferable
            threadColors = activeColors,
            isModified = true,
            hoopProfile = hoop
        )
    }
}
