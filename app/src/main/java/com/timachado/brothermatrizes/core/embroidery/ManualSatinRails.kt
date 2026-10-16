package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Trilhos Satin editáveis: cada par A/B define as duas bordas de uma coluna.
 * u/v são coordenadas NORMALIZADAS ao recorte da arte [0,1], independentes
 * do tamanho do preview. A correção de rails não altera glifos do app.
 *
 * Apenas pontadas reais, nenhuma rasterização disfarçada de arquivo PES.
 * Componentes com ramificações precisam ser divididos em seções pelo usuário.
 */
data class SatinRailPair(
    val ax: Float,
    val ay: Float,
    val bx: Float,
    val by: Float
)

data class ManualSatinSection(
    val rails: List<SatinRailPair>,
    /** União explícita com a seção anterior: só aceita extremidades a <= 1,5 mm. */
    val connectToPrevious: Boolean = false
)

object ManualSatinRails {
    private const val MAX_COMMANDS = 130_000

    /**
     * Ponto de partida editável gerado com base na caixa da camada.
     * Não interpreta automaticamente caligrafia: o usuário move cada rail.
     */
    fun suggest(
        art: SeparatedColorArtwork,
        imageWidth: Int,
        imageHeight: Int,
        sourceColorIndex: Int,
        targetWidthMm: Float
    ): Result<ManualSatinSection> = runCatching {
        require(sourceColorIndex in art.colors.indices &&
            imageWidth in 8..512 && imageHeight in 8..512 &&
            art.pixels.size == imageWidth * imageHeight) { "Imagem inválida." }
        var left = imageWidth
        var right = -1
        var top = imageHeight
        var bottom = -1
        for (i in art.pixels.indices) {
            if (art.pixels[i] != sourceColorIndex) continue
            val x = i % imageWidth
            val y = i / imageWidth
            left = minOf(left, x)
            right = maxOf(right, x)
            top = minOf(top, y)
            bottom = maxOf(bottom, y)
        }
        require(right > left && bottom > top) { "Camada sem desenho suficiente." }
        val cropW = (art.maxX - art.minX + 1).toFloat()
        val cropH = (art.maxY - art.minY + 1).toFloat()
        fun u(px: Float) = ((px - art.minX + 0.5f) / cropW).coerceIn(0.01f, 0.99f)
        fun v(py: Float) = ((py - art.minY + 0.5f) / cropH).coerceIn(0.01f, 0.99f)
        require(targetWidthMm in 10f..200f) { "Tamanho do desenho inválido." }
        val vertical = bottom - top >= right - left
        // Sugestão segura e estreita: NÃO presumir que toda uma palavra cursiva
        // forma uma única coluna Satin de muitos centímetros de largura.
        val desiredPixels = 3f * cropW / targetWidthMm
        val rails = (0..5).map { step ->
            val t = step / 5f
            if (vertical) {
                val y = top + (bottom - top) * t
                val center = (left + right) / 2f
                val half = minOf((right - left) / 2f, desiredPixels / 2f)
                SatinRailPair(u(center - half), v(y), u(center + half), v(y))
            } else {
                val x = left + (right - left) * t
                val center = (top + bottom) / 2f
                val half = minOf((bottom - top) / 2f, desiredPixels / 2f)
                SatinRailPair(u(x), v(center - half), u(x), v(center + half))
            }
        }
        ManualSatinSection(rails)
    }

    /** Curva suave sobre ambos os rails, com amostragem por comprimento da linha média. */
    fun generate(
        sections: List<ManualSatinSection>,
        outputColorIndex: Int,
        widthMm: Float,
        imageAspectRatio: Float,
        densityMm: Float,
        pullCompensationMm: Float,
        centerUnderlay: Boolean,
        /** QA image-object editor only; existing callers keep 10 mm minimum. */
        qaSmallObjectPreview: Boolean = false
    ): Result<List<EmbroideryPoint>> = runCatching {
        require(sections.isNotEmpty() && sections.size <= 80 &&
            outputColorIndex in 0..3 &&
            widthMm in (if (qaSmallObjectPreview) .8f else 10f)..200f &&
            imageAspectRatio.isFinite() && imageAspectRatio in 0.03f..20f &&
            densityMm in 0.35f..1f && pullCompensationMm in 0f..0.3f) {
            "Configuração dos trilhos Satin inválida."
        }
        val heightMm = widthMm / imageAspectRatio
        val unitsX = widthMm * 10.0
        val unitsY = heightMm * 10.0
        fun x(u: Double) = (u - .5) * unitsX
        fun y(v: Double) = (.5 - v) * unitsY
        data class P(val x: Double, val y: Double) {
            operator fun plus(p: P) = P(x + p.x, y + p.y)
            operator fun minus(p: P) = P(x - p.x, y - p.y)
            operator fun times(k: Double) = P(x * k, y * k)
        }
        fun position(rail: SatinRailPair, first: Boolean) =
            if (first) P(x(rail.ax.toDouble()), y(rail.ay.toDouble()))
            else P(x(rail.bx.toDouble()), y(rail.by.toDouble()))
        fun norm(p: P) = hypot(p.x, p.y)
        fun interpolate(points: List<P>, t: Double): P {
            val i = t.toInt().coerceIn(0, points.lastIndex - 1)
            val f = (t - i).coerceIn(0.0, 1.0)
            val a = points[max(0, i - 1)]
            val b = points[i]
            val c = points[i + 1]
            // Do not allow Catmull-Rom corner overshoot to cross a letter's
            // transparent counter in experimental image-to-Satin previews.
            // Commercial/font pipelines retain their original spline curves.
            if (qaSmallObjectPreview) return b + (c - b) * f
            val d = points[minOf(points.lastIndex, i + 2)]
            // Catmull-Rom de tensão 0,5; endpoints preservados.
            return (b * 2.0 + (c - a) * f +
                (a * 2.0 - b * 5.0 + c * 4.0 - d) * (f * f) +
                (a * -1.0 + b * 3.0 - c * 3.0 + d) * (f * f * f)) * 0.5
        }
        val result = ArrayList<EmbroideryPoint>()
        fun point(p: P, type: StitchCommand) {
            require(result.size < MAX_COMMANDS) {
                "Muitos pontos Satin; divida o bordado."
            }
            require(p.x.isFinite() && p.y.isFinite()) {
                "Trilhos possuem coordenadas inválidas."
            }
            result += EmbroideryPoint(
                p.x.roundToInt(), p.y.roundToInt(), type, outputColorIndex
            )
        }

        for ((sectionIndex, section) in sections.withIndex()) {
            require(sectionIndex > 0 || !section.connectToPrevious) {
                "A primeira seção não pode conectar-se a uma seção anterior."
            }
            val rails = section.rails
            require(rails.size in 2..100) { "Cada seção precisa de 2 a 100 trilhos." }
            rails.forEach { r ->
                require(listOf(r.ax, r.ay, r.bx, r.by).all {
                    it.isFinite() && it in 0f..1f
                }) { "Arraste os pontos para dentro da arte." }
            }
            val sideA = rails.map { position(it, true) }
            val sideB = rails.map { position(it, false) }
            val middle = sideA.indices.map { (sideA[it] + sideB[it]) * .5 }
            // Validação de largura e orientação: não permitir a coluna inverter.
            var directionSign = 0
            for (i in rails.indices) {
                val width = norm(sideB[i] - sideA[i])
                require(width in 8.0..70.0) {
                    "A largura do Satin deve estar entre 0,8 e 7 mm."
                }
                val before = middle[max(0, i - 1)]
                val after = middle[minOf(middle.lastIndex, i + 1)]
                val tangent = after - before
                require(norm(tangent) >= 1.0) {
                    "Trilhos consecutivos têm eixo sem comprimento."
                }
                val widthVector = sideB[i] - sideA[i]
                val cross = tangent.x * widthVector.y - tangent.y * widthVector.x
                require(abs(cross) >= norm(tangent) * width * 0.2) {
                    "Trilhos quase paralelos à direção do bordado."
                }
                val sign = if (cross > 0) 1 else -1
                if (directionSign != 0) require(sign == directionSign) {
                    "Trilhos Satin cruzados ou invertidos."
                }
                directionSign = sign
            }
            // Densificação de curva antes de medir o comprimento do eixo.
            val fineSamples = ArrayList<Double>()
            val lengths = ArrayList<Double>()
            var length = 0.0
            var last = middle[0]
            fineSamples += 0.0
            lengths += 0.0
            for (i in 1..(rails.lastIndex * 12)) {
                val t = i / 12.0
                val p = (interpolate(sideA, t) + interpolate(sideB, t)) * 0.5
                length += norm(p - last)
                fineSamples += t
                lengths += length
                last = p
            }
            require(length >= 30.0 && length.isFinite() && length < 10000) {
                "Cada seção Satin precisa ter de 3 a 1000 mm de comprimento."
            }
            val steps = ceil(length / (densityMm * 10.0)).toInt().coerceAtLeast(1)
            require(steps <= 20000) { "Satin com densidade excessiva." }
            val sampled = ArrayList<Pair<P, P>>(steps + 1)
            var index = 1
            for (i in 0..steps) {
                val target = length * i / steps
                while (index < lengths.lastIndex && lengths[index] < target) index++
                val lo = index - 1
                val denom = max(0.00001, lengths[index] - lengths[lo])
                val fraction = ((target - lengths[lo]) / denom).coerceIn(0.0, 1.0)
                val t = fineSamples[lo] + (fineSamples[index] - fineSamples[lo]) * fraction
                val a = interpolate(sideA, t)
                val b = interpolate(sideB, t)
                val span = b - a
                val w = norm(span)
                require(w in 7.0..75.0) {
                    "Interpolação gerou cruzamento ou largura de trilho inválida."
                }
                val comp = pullCompensationMm * 10.0
                val adjust = span * (comp / w)
                sampled += (a - adjust) to (b + adjust)
            }
            val previous = result.lastOrNull()
            val connecting = section.connectToPrevious
            val entryA = sampled.first().first
            val entryB = sampled.first().second
            // União somente mediante pedido explícito e proximidade mensurada.
            // Nunca costurar inadvertidamente entre letras/ilhas separadas.
            val entryOnA = if (connecting) {
                require(previous != null) {
                    "A conexão requer uma seção de bordado anterior."
                }
                val current = P(previous.xUnits.toDouble(), previous.yUnits.toDouble())
                val distanceA = norm(entryA - current)
                val distanceB = norm(entryB - current)
                require(minOf(distanceA, distanceB) <= 15.0) {
                    "União Satin excede 1,5 mm. Aproxime as pontas ou desligue Conectar."
                }
                distanceA <= distanceB
            } else true

            if (previous != null && !connecting) {
                point(P(previous.xUnits.toDouble(), previous.yUnits.toDouble()), StitchCommand.TRIM)
            }
            // Underlay central individual exige JUMP até a outra extremidade:
            // ao conectar, omitimos o underlay desta seção para não quebrar
            // a costura contínua com um deslocamento oculto.
            if (centerUnderlay && !connecting) {
                val reversed = sampled.asReversed()
                val c0 = (reversed.first().first + reversed.first().second) * 0.5
                point(c0, StitchCommand.JUMP)
                var at = c0
                for (pair in reversed.drop(1)) {
                    val end = (pair.first + pair.second) * 0.5
                    val d = norm(end - at)
                    val n = ceil(d / 25.0).toInt().coerceAtLeast(1)
                    for (k in 1..n) point(
                        at + (end - at) * (k.toDouble() / n),
                        StitchCommand.STITCH
                    )
                    at = end
                }
            }
            val entry = if (entryOnA) entryA else entryB
            if (connecting) {
                val current = P(previous!!.xUnits.toDouble(), previous.yUnits.toDouble())
                if (norm(entry - current) >= 1.0) point(entry, StitchCommand.STITCH)
            } else {
                point(entry, StitchCommand.JUMP)
            }
            var lastStitch = entry
            for (i in sampled.indices) {
                val destination = if ((i % 2 == 0) == entryOnA)
                    sampled[i].second else sampled[i].first
                val delta = norm(destination - lastStitch)
                require(delta <= 78.0) {
                    "Pontada Satin acima de 7,8 mm; ajuste a largura ou os trilhos."
                }
                if (delta >= 1.0) point(destination, StitchCommand.STITCH)
                lastStitch = destination
            }
        }
        require(result.any { it.command == StitchCommand.STITCH }) {
            "Os trilhos não produziram pontadas."
        }
        result
    }
}
