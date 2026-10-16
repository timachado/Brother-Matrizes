package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/**
 * Conservative exact-mask decomposition for simple orthogonal O/T-shaped
 * components. Each bar is an independent Satin section. This is NOT a
 * typographic/OCR heuristic and does not attempt to infer arbitrary curves.
 *
 * The entire binary mask must match the proposed bars pixel for pixel; if
 * not, the caller can try more sophisticated geometry or manual editing.
 */
internal object RasterOrthogonalSatinPlanner {
    private fun section(
        w: Int, h: Int,
        horizontal: Boolean,
        from: Int, to: Int,
        low: Int, high: Int
    ): ManualSatinSection {
        require(from >= 0 && to < (if (horizontal) w else h) &&
            low >= 0 && high < (if (horizontal) h else w) && from < to && low < high)
        // Three or more rails, interpolated only through the exact solid bar.
        val steps = (to - from).coerceIn(4, 80)
        val rails = (0..steps).map { step ->
            val along = from + (to - from) * step.toFloat() / steps
            val a = (low + .5f)
            val b = (high + .5f)
            if (horizontal) SatinRailPair(
                (along + .5f) / w, a / h,
                (along + .5f) / w, b / h
            ) else SatinRailPair(
                a / w, (along + .5f) / h,
                b / w, (along + .5f) / h
            )
        }
        return ManualSatinSection(rails = rails, connectToPrevious = false)
    }

    fun propose(
        source: RasterImagePixels,
        part: RasterSatinObject,
        fullWidthMm: Float
    ): Result<List<ManualSatinSection>> = runCatching {
        val mask = part.mask
        val w = mask.maxX - mask.minX + 1
        val h = mask.maxY - mask.minY + 1
        require(w in 12..512 && h in 12..512 &&
            mask.pixels.size == source.width * source.height &&
            fullWidthMm in 10f..200f && fullWidthMm.isFinite()) {
            "Tamanho de arte incompatível."
        }
        val mmPerPixel = fullWidthMm / source.width.toFloat()
        fun ink(x: Int, y: Int) =
            mask.pixels[(mask.minY + y) * source.width + mask.minX + x] == 0
        fun rowSolid(y: Int): Boolean = (0 until w).all { ink(it, y) }
        fun colSolid(x: Int): Boolean = (0 until h).all { ink(x, it) }
        fun validSpan(pixels: Int): Boolean =
            (pixels - 1) * mmPerPixel in .8f..6.5f

        // Ring: four solid bars, one empty rectangular counter. Every pixel
        // must be explained by the union of the four bars.
        val top = (0 until h).takeWhile { rowSolid(it) }.size
        val bottom = (0 until h).takeWhile { rowSolid(h - 1 - it) }.size
        val left = (0 until w).takeWhile { colSolid(it) }.size
        val right = (0 until w).takeWhile { colSolid(w - 1 - it) }.size
        val ring = top in 3..h/3 && bottom in 3..h/3 &&
            left in 3..w/3 && right in 3..w/3 &&
            listOf(top, bottom, left, right).all(::validSpan) &&
            (0 until h).all { y ->
                (0 until w).all { x ->
                    ink(x, y) ==
                        (y < top || y >= h - bottom || x < left || x >= w - right)
                }
            }
        if (ring) {
            return@runCatching listOf(
                section(w,h,true,0,w-1,0,top-1),
                section(w,h,false,0,h-1,0,left-1),
                section(w,h,true,0,w-1,h-bottom,h-1),
                section(w,h,false,0,h-1,w-right,w-1)
            )
        }

        // T: a full-width rectangular head and a single centered narrow stem.
        // Split head into left and right rails; third rail covers the stem.
        require(top in 3..h/3 && validSpan(top)) {
            "Esta forma exige subdivisão manual dos contornos."
        }
        val stemLeft = (0 until w).firstOrNull { ink(it,h-1) }
            ?: error("A figura não possui haste.")
        val stemRight = (0 until w).lastOrNull { ink(it,h-1) }
            ?: error("A figura não possui haste.")
        val cx = (stemLeft + stemRight) / 2
        require(stemLeft >= 3 && stemRight <= w - 4 &&
            stemRight - stemLeft + 1 < w * .36f &&
            abs(cx - (w - 1)/2f) < w * .22f &&
            validSpan(stemRight - stemLeft + 1) &&
            cx >= 8 && w - cx >= 8) {
            "Junção excessivamente larga ou assimétrica para Satin automático."
        }
        require((0 until h).all { y ->
            (0 until w).all { x ->
                ink(x, y) == (y < top || x in stemLeft..stemRight)
            }
        }) {
            "A máscara contém curvas ou ramos não representados pelos trilhos."
        }
        listOf(
            section(w,h,true,0,cx,0,top-1),
            section(w,h,true,cx,w-1,0,top-1),
            section(w,h,false,top-1,h-1,stemLeft,stemRight)
        )
    }
}
