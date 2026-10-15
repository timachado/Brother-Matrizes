package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Satin experimental em faixa CURVA e NÃO ramificada.
 * Calcula eixo e largura por seções conexas, reamostra no comprimento de arco,
 * constrói trilhos normais à tangente e costura alternadamente ambos os trilhos.
 * Sem alteração nos motores de fonte ou simulação comercial.
 *
 * Não é conversor universal de lettering: interseções, laços, furos e bifurcações
 * exigem rails vetoriais editáveis e são recusados com erro explícito.
 */
internal object RasterCurvedSatin {
    private const val MAX_COMPONENTS = 100
    private const val MAX_COMMANDS = 130_000
    private data class PixelComponent(
        val id: Int, val left: Int, val right: Int, val top: Int, val bottom: Int
    )
    private data class Sample(val x: Double, val y: Double, val radius: Double)
    private data class Rail(val ax: Int, val ay: Int, val bx: Int, val by: Int) {
        val cx get() = (ax + bx) / 2
        val cy get() = (ay + by) / 2
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
        require(
            source.width in 8..512 && source.height in 8..512 &&
                art.pixels.size == source.width * source.height &&
                sourceColorIndex in art.colors.indices &&
                outputColorIndex in 0..3 &&
                widthMm in 10f..200f && densityMm in 0.35f..1f &&
                compensationMm in 0f..0.3f
        ) { "Parâmetros de Satin curvo inválidos." }
        val scale = widthMm * 10.0 / (art.maxX - art.minX + 1)
        require(scale.isFinite() && scale > 0.0) { "Escala inválida." }
        val widthUnits = (art.maxX - art.minX + 1) * scale
        val heightUnits = (art.maxY - art.minY + 1) * scale
        fun ux(x: Double) =
            ((x - art.minX + 0.5) * scale - widthUnits / 2.0)
        fun uy(y: Double) =
            (heightUnits / 2.0 - (y - art.minY + 0.5) * scale)

        val group = IntArray(art.pixels.size) { -1 }
        val queue = IntArray(art.pixels.size)
        val components = ArrayList<PixelComponent>()
        for (seed in art.pixels.indices) {
            if (art.pixels[seed] != sourceColorIndex || group[seed] != -1) continue
            val id = components.size
            require(id < MAX_COMPONENTS) { "A imagem contém muitas ilhas Satin." }
            var head = 0
            var tail = 1
            queue[0] = seed
            group[seed] = id
            var minX = source.width
            var maxX = -1
            var minY = source.height
            var maxY = -1
            while (head < tail) {
                val at = queue[head++]
                val x = at % source.width
                val y = at / source.width
                minX = minOf(minX, x)
                maxX = maxOf(maxX, x)
                minY = minOf(minY, y)
                maxY = maxOf(maxY, y)
                val next = intArrayOf(
                    if (x > 0) at - 1 else -1,
                    if (x < source.width - 1) at + 1 else -1,
                    if (y > 0) at - source.width else -1,
                    if (y < source.height - 1) at + source.width else -1
                )
                for (n in next) {
                    if (n >= 0 && group[n] == -1 &&
                        art.pixels[n] == sourceColorIndex) {
                        group[n] = id
                        queue[tail++] = n
                    }
                }
            }
            components += PixelComponent(id, minX, maxX, minY, maxY)
        }
        require(components.isNotEmpty()) { "Nenhuma faixa Satin localizada." }

        val output = ArrayList<EmbroideryPoint>()
        fun add(x: Int, y: Int, cmd: StitchCommand) {
            require(output.size < MAX_COMMANDS) {
                "O limite de segurança de comandos Satin foi atingido."
            }
            output += EmbroideryPoint(x, y, cmd, outputColorIndex)
        }

        for (component in components) {
            val extentX = component.right - component.left + 1
            val extentY = component.bottom - component.top + 1
            val vertical = extentY >= extentX
            val alongFirst = if (vertical) component.top else component.left
            val alongLast = if (vertical) component.bottom else component.right
            val crossFirst = if (vertical) component.left else component.top
            val crossLast = if (vertical) component.right else component.bottom
            val centerline = ArrayList<Sample>()
            for (along in alongFirst..alongLast) {
                var first = -1
                var last = -1
                var count = 0
                for (cross in crossFirst..crossLast) {
                    val index =
                        if (vertical) along * source.width + cross
                        else cross * source.width + along
                    if (group[index] == component.id) {
                        if (first < 0) first = cross
                        last = cross
                        count++
                    }
                }
                require(first >= 0 && count == last - first + 1) {
                    "Satin curvo detectou ramificação, furo ou trilhos descontínuos."
                }
                val spanUnits = count * scale
                require(spanUnits in 8.0..54.0) {
                    "A largura da faixa não é adequada a Satin curvo (0,8–5,4 mm)."
                }
                val crossMiddle = (first + last) / 2.0
                val cx = if (vertical) ux(crossMiddle) else ux(along.toDouble())
                val cy = if (vertical) uy(along.toDouble()) else uy(crossMiddle)
                centerline += Sample(cx, cy, spanUnits / 2.0)
            }
            require(centerline.size >= 6) { "Faixa muito curta para Satin curvo." }
            var arc = 0.0
            val distances = DoubleArray(centerline.size)
            for (i in 1 until centerline.size) {
                val dx = centerline[i].x - centerline[i - 1].x
                val dy = centerline[i].y - centerline[i - 1].y
                val step = hypot(dx, dy)
                require(step in 0.001..max(16.0, 2.2 * scale)) {
                    "Descontinuidade na linha central do Satin."
                }
                arc += step
                distances[i] = arc
            }
            require(arc >= 30.0) { "Faixa Satin inferior a 3 mm." }
            val samplesCount = ceil(arc / (densityMm * 10.0)).toInt()
            require(samplesCount in 3..16000) { "Densidade Satin fora do limite." }

            fun sampleAt(distance: Double): Sample {
                var hi = 1
                while (hi < distances.size - 1 && distances[hi] < distance) hi++
                val lo = hi - 1
                val div = (distances[hi] - distances[lo]).coerceAtLeast(0.00001)
                val t = ((distance - distances[lo]) / div).coerceIn(0.0, 1.0)
                val a = centerline[lo]
                val b = centerline[hi]
                return Sample(
                    a.x + (b.x - a.x) * t,
                    a.y + (b.y - a.y) * t,
                    a.radius + (b.radius - a.radius) * t
                )
            }
            val positions = (0..samplesCount).map {
                sampleAt(arc * it / samplesCount)
            }
            val rails = ArrayList<Rail>(positions.size)
            var previousNormalX = 0.0
            var previousNormalY = 0.0
            for (i in positions.indices) {
                val prev = positions[max(0, i - 1)]
                val next = positions[minOf(i + 1, positions.lastIndex)]
                val dx = next.x - prev.x
                val dy = next.y - prev.y
                val len = hypot(dx, dy)
                require(len >= 0.001) { "O eixo Satin contém uma dobra degenerada." }
                val nx = -dy / len
                val ny = dx / len
                if (i > 0) {
                    require(nx * previousNormalX + ny * previousNormalY >= 0.66) {
                        "Curva Satin fechada demais; exige trilhos editáveis."
                    }
                }
                previousNormalX = nx
                previousNormalY = ny
                // A largura da máscara foi medida em cortes horizontais/verticais.
                // Converter o corte aparente na largura normal à trajetória.
                val alignment = if (vertical) abs(nx) else abs(ny)
                require(alignment >= 0.66) {
                    "Satin com inclinação elevada; use trilhos manuais."
                }
                val halfWidth = positions[i].radius * alignment +
                    compensationMm * 10.0
                require(halfWidth * 2.0 <= 62.0) { "Satin acima de 6,2 mm." }
                val cx = positions[i].x
                val cy = positions[i].y
                rails += Rail(
                    (cx + nx * halfWidth).roundToInt(),
                    (cy + ny * halfWidth).roundToInt(),
                    (cx - nx * halfWidth).roundToInt(),
                    (cy - ny * halfWidth).roundToInt()
                )
            }

            val current = output.lastOrNull()
            if (current != null) add(current.xUnits, current.yUnits, StitchCommand.TRIM)
            if (centerUnderlay) {
                // Sustentação do final ao início, terminando perto do começo do Satin.
                val last = rails.last()
                add(last.cx, last.cy, StitchCommand.JUMP)
                var previousX = last.cx
                var previousY = last.cy
                for (i in rails.lastIndex - 1 downTo 0) {
                    val r = rails[i]
                    val len = hypot(
                        (r.cx - previousX).toDouble(), (r.cy - previousY).toDouble()
                    )
                    val n = max(1, ceil(len / 25.0).toInt())
                    val startX = previousX
                    val startY = previousY
                    for (k in 1..n) add(
                        (startX + (r.cx - startX) * k.toDouble() / n).roundToInt(),
                        (startY + (r.cy - startY) * k.toDouble() / n).roundToInt(),
                        StitchCommand.STITCH
                    )
                    previousX = r.cx
                    previousY = r.cy
                }
            }
            add(rails.first().ax, rails.first().ay, StitchCommand.JUMP)
            var lastX = rails.first().ax
            var lastY = rails.first().ay
            for (rail in rails) {
                // Raias alternadas: A_i -> B_i -> A_(i+1) -> B_(i+1).
                for ((x, y) in listOf(rail.bx to rail.by, rail.ax to rail.ay)) {
                    val dist = hypot((x - lastX).toDouble(), (y - lastY).toDouble())
                    require(dist <= 75.0) {
                        "Pontada Satin ultrapassaria 7,5 mm."
                    }
                    if (dist >= 1.0) {
                        add(x, y, StitchCommand.STITCH)
                        lastX = x
                        lastY = y
                    }
                }
            }
        }
        require(output.any { it.command == StitchCommand.STITCH }) {
            "Nenhuma pontada Satin foi gerada."
        }
        output
    }
}
