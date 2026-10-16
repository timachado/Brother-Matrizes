package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** One connected ink component, retaining its position in the full image. */
data class RasterSatinObject(
    val id: Int,
    val color: Int,
    val pixelCount: Int,
    val mask: SeparatedColorArtwork,
    val vectorContours: List<RasterVectorContour> = emptyList()
)

/**
 * Explicit, editable Satin digitization for one isolated object.
 *
 * A suggested rail is never considered a finished digitization. The user must
 * adjust the rails over the mask; coverage and ink-intersection tests reject
 * paths that cross holes or omit most of the source shape.
 *
 * These checks catch obvious failures, NOT machine safety or fabric quality.
 * Every result is QA-only and cannot be exported as PES.
 */
object RasterSatinObjectEditor {
    private const val MAX_OBJECTS = 48
    private const val MAX_PIXELS = 512 * 512

    fun detect(source: RasterImagePixels): Result<List<RasterSatinObject>> = runCatching {
        require(source.width in 8..512 && source.height in 8..512 &&
            source.argb.size == source.width * source.height) {
            "Imagem incompatível com o editor Satin."
        }
        val separated = MultiColorRasterDigitizer.separate(
            source, MultiColorRasterOptions(maxColors = 4, preserveFineDetails = true)
        ).getOrThrow()
        require(separated.pixels.size <= MAX_PIXELS) { "Imagem acima do limite de edição." }
        val labels = IntArray(separated.pixels.size) { -1 }
        val queue = IntArray(labels.size)
        val result = ArrayList<RasterSatinObject>()
        for (root in labels.indices) {
            val colorIndex = separated.pixels[root]
            if (colorIndex < 0 || labels[root] >= 0) continue
            val component = result.size
            require(component < MAX_OBJECTS) {
                "A imagem possui mais de $MAX_OBJECTS partes. Ajuste o recorte."
            }
            var head = 0
            var tail = 1
            queue[0] = root
            labels[root] = component
            var minX = source.width
            var maxX = 0
            var minY = source.height
            var maxY = 0
            while (head < tail) {
                val p = queue[head++]
                val x = p % source.width
                val y = p / source.width
                minX = minOf(minX, x); maxX = maxOf(maxX, x)
                minY = minOf(minY, y); maxY = maxOf(maxY, y)
                // Eight-neighbour connectivity joins diagonal strokes. Other
                // colors, transparent holes and disconnected glyphs stay apart.
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (nx !in 0 until source.width || ny !in 0 until source.height) continue
                    val next = ny * source.width + nx
                    if (labels[next] == -1 && separated.pixels[next] == colorIndex) {
                        labels[next] = component
                        queue[tail++] = next
                    }
                }
            }
            // Do not silently discard any foreground ink: even a dot may
            // matter to a logo. Tiny components remain visible for review.
            val pixels = IntArray(labels.size) { index ->
                if (labels[index] == component) 0 else -1
            }
            val componentMask = SeparatedColorArtwork(
                colors = listOf(separated.colors[colorIndex]), pixels = pixels,
                minX = minX, maxX = maxX, minY = minY, maxY = maxY
            )
            result += RasterSatinObject(
                id = component + 1,
                color = separated.colors[colorIndex],
                pixelCount = tail,
                mask = componentMask,
                // A diagonal junction might be ambiguous. Manual editing
                // still works even when vectorization refuses it.
                vectorContours = RasterVectorContours.trace(
                    componentMask, source.width
                ).getOrDefault(emptyList())
            )
        }
        require(result.isNotEmpty()) { "Nenhum elemento encontrado na imagem." }
        result.sortedWith(
            compareBy<RasterSatinObject> { it.mask.minY }
                .thenBy { it.mask.minX }
        ).mapIndexed { index, item -> item.copy(id = index + 1) }
    }

    /**
     * Suggest an editable starting rail. This is ONLY a guide; it is not
     * certified by its shape or allowed to simulate until the user edits it.
     */
    fun suggest(source: RasterImagePixels, item: RasterSatinObject, fullWidthMm: Float):
        Result<ManualSatinSection> = runCatching {
        val w = (item.mask.maxX - item.mask.minX + 1).toFloat()
        val objectWidthMm = fullWidthMm * w / source.width
        require(objectWidthMm >= 1f) { "Parte pequena demais. Aumente a arte." }
        ManualSatinRails.suggest(
            item.mask, source.width, source.height, 0, max(10f, objectWidthMm)
        ).getOrThrow()
    }

    fun preview(
        source: RasterImagePixels,
        item: RasterSatinObject,
        sections: List<ManualSatinSection>,
        fullWidthMm: Float,
        hoop: HoopProfile,
        minimumCoverage: Float = .68f
    ): Result<EmbroideryDesign> = runCatching {
        require(source.width in 8..512 && source.height in 8..512 &&
            item.mask.pixels.size == source.argb.size &&
            sections.isNotEmpty() && sections.size <= 40 &&
            fullWidthMm.isFinite() && fullWidthMm in 10f..200f &&
            minimumCoverage.isFinite() && minimumCoverage in .68f..1f) {
            "Arte, objeto ou trilhos inválidos."
        }
        val fullHeightMm = fullWidthMm * source.height / source.width.toFloat()
        require(fullWidthMm <= hoop.usableWidthMm && fullHeightMm <= hoop.usableHeightMm) {
            "A arte não cabe na área útil do bastidor."
        }
        val mask = item.mask
        val w = mask.maxX - mask.minX + 1
        val h = mask.maxY - mask.minY + 1
        require(w >= 2 && h >= 2 && item.pixelCount >= 8) {
            "Parte pequena demais para Satin. Amplie a imagem."
        }
        require(sections.all { it.rails.size in 2..100 }) {
            "Cada seção requer pelo menos dois pares de trilhos."
        }
        val occupied = BooleanArray(w * h)
        fun ink(x: Float, y: Float): Boolean {
            val ix = x.roundToInt()
            val iy = y.roundToInt()
            if (ix !in 0 until w || iy !in 0 until h) return false
            return mask.pixels[(mask.minY + iy) * source.width + mask.minX + ix] == 0
        }
        fun at(u: Float, v: Float) =
            ((u * w - .5f).coerceIn(0f, (w - 1).toFloat())) to
                ((v * h - .5f).coerceIn(0f, (h - 1).toFloat()))
        fun cross(a: SatinRailPair, b: SatinRailPair, t: Float) {
            val ax = a.ax + (b.ax - a.ax) * t
            val ay = a.ay + (b.ay - a.ay) * t
            val bx = a.bx + (b.bx - a.bx) * t
            val by = a.by + (b.by - a.by) * t
            val (startX, startY) = at(ax, ay)
            val (endX, endY) = at(bx, by)
            // The sewn cross-section must lie ON the intended ink, rather
            // than crossing a transparent hole or a neighboring letter.
            for (i in 1..9) {
                val u = i / 10f
                require(ink(startX + (endX - startX) * u,
                    startY + (endY - startY) * u)) {
                    "Os trilhos atravessam um espaço vazio da arte. Ajuste esta seção."
                }
            }
            val steps = max(1, ceil(
                hypot((endX - startX).toDouble(), (endY - startY).toDouble())
            ).toInt() * 2)
            for (i in 0..steps) {
                val u = i.toFloat() / steps
                val x = (startX + (endX - startX) * u).roundToInt()
                val y = (startY + (endY - startY) * u).roundToInt()
                if (x in 0 until w && y in 0 until h) occupied[y * w + x] = true
            }
        }
        for (section in sections) {
            val rails = section.rails
            for (i in rails.indices) {
                val a = rails[i]
                require(listOf(a.ax, a.ay, a.bx, a.by).all { it.isFinite() &&
                    it in 0f..1f }) { "Trilho fora da imagem." }
                if (i < rails.lastIndex) {
                    for (sample in 0..10) cross(a, rails[i + 1], sample / 10f)
                } else {
                    cross(a, a, 0f)
                }
            }
        }
        var matched = 0
        var actualInk = 0
        for (y in 0 until h) for (x in 0 until w) {
            if (!ink(x.toFloat(), y.toFloat())) continue
            actualInk++
            if (occupied[y * w + x] ||
                (x > 0 && occupied[y * w + x - 1]) ||
                (x + 1 < w && occupied[y * w + x + 1]) ||
                (y > 0 && occupied[(y - 1) * w + x]) ||
                (y + 1 < h && occupied[(y + 1) * w + x])
            ) matched++
        }
        require(actualInk >= 8 &&
            matched.toFloat() / actualInk >= minimumCoverage) {
            "Os trilhos ainda não cobrem a maior parte deste elemento. " +
                "Ajuste as bordas e acrescente seções."
        }

        val unitsPerPixel = fullWidthMm * 10f / source.width
        val objectWidthMm = w * unitsPerPixel / 10f
        val objectHeightMm = h * unitsPerPixel / 10f
        require(objectWidthMm >= .8f) { "Elemento pequeno demais para Satin." }
        val result = ManualSatinRails.generate(
            sections = sections, outputColorIndex = 0,
            widthMm = objectWidthMm,
            imageAspectRatio = objectWidthMm / objectHeightMm,
            densityMm = .5f, pullCompensationMm = .15f,
            centerUnderlay = false, qaSmallObjectPreview = true
        ).getOrThrow()
        val cx = (mask.minX + mask.maxX + 1) / 2f
        val cy = (mask.minY + mask.maxY + 1) / 2f
        val shiftX = ((cx - source.width / 2f) * unitsPerPixel).roundToInt()
        val shiftY = ((source.height / 2f - cy) * unitsPerPixel).roundToInt()
        val positioned = result.map {
            it.copy(xUnits = it.xUnits + shiftX, yUnits = it.yUnits + shiftY)
        }
        val final = positioned.last()
        val all = positioned + EmbroideryPoint(
            final.xUnits, final.yUnits, StitchCommand.END, 0
        )
        EmbroideryDesign(
            fileName = "SATIN-MANUAL-REVISAO.qa",
            format = "QA_SATIN_REVIEW",
            label = "Parte ${item.id}: Satin manual, prévia sem exportação",
            points = all,
            bounds = EmbroideryBounds(
                all.minOf { it.xUnits }, all.maxOf { it.xUnits },
                all.minOf { it.yUnits }, all.maxOf { it.yUnits }
            ),
            stitchCount = all.count { it.command == StitchCommand.STITCH },
            jumpCount = all.count { it.command == StitchCommand.JUMP },
            colorChanges = 0, endFound = true,
            sourceBytes = ByteArray(0), threadColors = listOf(item.color),
            isModified = true, hoopProfile = hoop
        )
    }
}
