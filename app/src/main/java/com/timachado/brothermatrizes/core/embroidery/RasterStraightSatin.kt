package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Satin seguro e limitado a faixas retas isoladas detectadas na máscara raster.
 *
 * Não substitui o motor de Satin para fontes nem tenta transformar uma
 * letra cursiva ramificada em coluna. Cada componente precisa ter uma única
 * largura contínua em todas as seções e uma direção predominante.
 * Se houver ilhas com furos, bifurcações ou espessura imprópria, a operação
 * falha explicitamente, sem exportar uma falsa matriz "profissional".
 */
internal object RasterStraightSatin {
    private const val MAX_COMPONENTS = 160
    private const val MAX_OUTPUT = 130_000
    private data class Component(
        val id: Int,
        val minX: Int,
        val maxX: Int,
        val minY: Int,
        val maxY: Int
    )
    private data class Rail(val aX: Int, val aY: Int, val bX: Int, val bY: Int) {
        val centerX get() = (aX + bX) / 2
        val centerY get() = (aY + bY) / 2
    }

    fun generate(
        source: RasterImagePixels,
        art: SeparatedColorArtwork,
        sourceColorIndex: Int,
        outputColorIndex: Int,
        widthMm: Float,
        densityMm: Float,
        compensationMm: Float,
        centerUnderlay: Boolean
    ): Result<List<EmbroideryPoint>> = runCatching {
        require(source.width in 8..512 && source.height in 8..512) {
            "Imagem fora do intervalo seguro."
        }
        require(art.pixels.size == source.width * source.height &&
            sourceColorIndex in art.colors.indices &&
            outputColorIndex in 0..3 && widthMm in 10f..200f &&
            densityMm in .35f..1f && compensationMm in 0f..0.3f) {
            "Parâmetros de coluna Satin inválidos."
        }
        val scale = widthMm * 10f / (art.maxX - art.minX + 1)
        require(scale > 0f && scale.isFinite()) { "Escala inválida." }
        val widthUnits = (art.maxX - art.minX + 1) * scale
        val heightUnits = (art.maxY - art.minY + 1) * scale
        fun xUnit(x: Float) =
            ((x - art.minX + .5f) * scale - widthUnits / 2f).roundToInt()
        fun yUnit(y: Float) =
            (heightUnits / 2f - (y - art.minY + .5f) * scale).roundToInt()
        val compensationUnits = compensationMm * 10f

        // 4-vizinhos: diagonais soltas não são costuradas como falsas ligações.
        val ids = IntArray(art.pixels.size) { -1 }
        val queue = IntArray(art.pixels.size)
        val components = ArrayList<Component>()
        for (index in art.pixels.indices) {
            if (art.pixels[index] != sourceColorIndex || ids[index] != -1) continue
            val id = components.size
            require(id < MAX_COMPONENTS) {
                "Muitos componentes isolados; simplifique a arte para Satin."
            }
            var head = 0
            var tail = 1
            queue[0] = index
            ids[index] = id
            var left = source.width
            var right = -1
            var top = source.height
            var bottom = -1
            while (head < tail) {
                val at = queue[head++]
                val x = at % source.width
                val y = at / source.width
                left = minOf(left, x)
                right = maxOf(right, x)
                top = minOf(top, y)
                bottom = maxOf(bottom, y)
                val neighbors = intArrayOf(
                    if (x > 0) at - 1 else -1,
                    if (x + 1 < source.width) at + 1 else -1,
                    if (y > 0) at - source.width else -1,
                    if (y + 1 < source.height) at + source.width else -1
                )
                for (next in neighbors) {
                    if (next >= 0 && ids[next] == -1 &&
                        art.pixels[next] == sourceColorIndex) {
                        ids[next] = id
                        queue[tail++] = next
                    }
                }
            }
            components += Component(id, left, right, top, bottom)
        }
        require(components.isNotEmpty()) { "Não existe área Satin nessa cor." }

        val points = ArrayList<EmbroideryPoint>()
        for (component in components) {
            val widthPx = component.maxX - component.minX + 1
            val heightPx = component.maxY - component.minY + 1
            // Exigir faixa predominante — não aplicar Satin automático em blocos.
            val vertical = heightPx >= widthPx * 2.4f
            val horizontal = widthPx >= heightPx * 2.4f
            require(vertical || horizontal) {
                "A cor contém área larga/ramificada. Use Preenchimento ou redesenhe a coluna."
            }
            val start = if (vertical) component.minY else component.minX
            val end = if (vertical) component.maxY else component.maxX
            val crossMin = if (vertical) component.minX else component.minY
            val crossMax = if (vertical) component.maxX else component.maxY
            val spans = ArrayList<Pair<Int, Int>>(end - start + 1)
            var minimumCenter = Float.POSITIVE_INFINITY
            var maximumCenter = Float.NEGATIVE_INFINITY
            for (along in start..end) {
                var first = -1
                var last = -1
                var count = 0
                for (cross in crossMin..crossMax) {
                    val pixelIndex = if (vertical)
                        along * source.width + cross
                    else cross * source.width + along
                    if (ids[pixelIndex] == component.id) {
                        if (first == -1) first = cross
                        last = cross
                        count++
                    }
                }
                require(first >= 0 && count == last - first + 1) {
                    "A coluna contém buracos ou ramos; Satin reto seria inseguro."
                }
                val physicalWidth = (last - first + 1) * scale
                require(physicalWidth in 8f..55f) {
                    "A largura local não é adequada a Satin reto (0,8–5,5 mm)."
                }
                val center = (first + last) / 2f
                minimumCenter = minOf(minimumCenter, center)
                maximumCenter = maxOf(maximumCenter, center)
                spans += first to last
            }
            // A direção central não pode mudar bruscamente ou curvar
            // muito: faltaria calcular rails normais ao eixo de uma curva.
            require((maximumCenter - minimumCenter) * scale <=
                max(12f, (crossMax - crossMin + 1) * scale * 0.75f)) {
                "Curva acentuada: esta versão aceita somente Satin reto."
            }
            val physicalLength = (end - start) * scale
            require(physicalLength >= 30f) {
                "Coluna curta demais para aplicar Satin automático."
            }
            val numSteps = ceil(physicalLength / (densityMm * 10f)).toInt()
                .coerceAtLeast(1)
            require(numSteps <= 15_000) { "Coluna Satin muito extensa." }
            val rails = ArrayList<Rail>(numSteps + 1)
            for (step in 0..numSteps) {
                val at = start + (end - start) * step.toFloat() / numSteps
                val (first, last) = spans[(at - start).roundToInt()
                    .coerceIn(0, spans.lastIndex)]
                rails += if (vertical) Rail(
                    xUnit(first.toFloat()) - compensationUnits.roundToInt(),
                    yUnit(at), xUnit(last.toFloat()) + compensationUnits.roundToInt(),
                    yUnit(at)
                ) else Rail(
                    xUnit(at), yUnit(first.toFloat()) + compensationUnits.roundToInt(),
                    xUnit(at), yUnit(last.toFloat()) - compensationUnits.roundToInt()
                )
            }
            fun add(cmd: StitchCommand, x: Int, y: Int) {
                points += EmbroideryPoint(x, y, cmd, outputColorIndex)
                require(points.size <= MAX_OUTPUT) {
                    "Muitos pontos Satin: reduza o desenho."
                }
            }

            // Nunca cruzar ilhas distintas costurando por engano.
            if (points.isNotEmpty()) {
                val previous = points.last()
                add(StitchCommand.TRIM, previous.xUnits, previous.yUnits)
            }
            if (centerUnderlay) {
                val lastRail = rails.last()
                add(StitchCommand.JUMP, lastRail.centerX, lastRail.centerY)
                var fromX = lastRail.centerX
                var fromY = lastRail.centerY
                for (i in rails.lastIndex - 1 downTo 0) {
                    val target = rails[i]
                    val dx = target.centerX - fromX
                    val dy = target.centerY - fromY
                    val steps = max(1, ceil(
                        hypot(dx.toDouble(), dy.toDouble()) / 25.0
                    ).toInt())
                    for (part in 1..steps) {
                        add(
                            StitchCommand.STITCH,
                            fromX + (dx * part.toFloat() / steps).roundToInt(),
                            fromY + (dy * part.toFloat() / steps).roundToInt()
                        )
                    }
                    fromX = target.centerX
                    fromY = target.centerY
                }
            }
            add(StitchCommand.JUMP, rails[0].aX, rails[0].aY)
            add(StitchCommand.STITCH, rails[0].bX, rails[0].bY)
            for (i in 1 until rails.size) {
                val rail = rails[i]
                val px = if (i % 2 == 1) rail.aX else rail.bX
                val py = if (i % 2 == 1) rail.aY else rail.bY
                val previous = points.last()
                require(hypot(
                    (px - previous.xUnits).toDouble(),
                    (py - previous.yUnits).toDouble()
                ) <= 75.0) { "Pontada Satin excede o limite de 7,5 mm." }
                if (px != previous.xUnits || py != previous.yUnits) {
                    add(StitchCommand.STITCH, px, py)
                }
            }
        }
        require(points.any { it.command == StitchCommand.STITCH }) {
            "Satin não gerou pontos."
        }
        points
    }
}
