package com.timachado.brothermatrizes.core.embroidery

import java.util.Locale

/**
 * Exporta os caminhos vetoriais do editor para SVG sem referências externas.
 * Não converte pontos de bordado em vetores e não aplica preenchimento automático.
 */
object VectorSvgExporter {
    fun export(
        artwork: VectorArtwork,
        strokeColor: Int = 0xE6BE70
    ): Result<ByteArray> = runCatching {
        val paths = artwork.paths.filter { it.points.size >= 2 }
        require(paths.isNotEmpty()) { "O desenho não possui traços para salvar." }
        require(paths.size <= 2_000) { "O desenho contém caminhos demais." }

        val points = paths.flatMap { it.points }
        require(points.size <= 100_000) { "O desenho contém pontos demais." }
        require(points.all { it.x.isFinite() && it.y.isFinite() &&
            kotlin.math.abs(it.x) <= 1_000_000f && kotlin.math.abs(it.y) <= 1_000_000f }) {
            "O desenho contém coordenadas inválidas."
        }

        val minX = points.minOf { it.x }
        val minY = points.minOf { it.y }
        val maxX = points.maxOf { it.x }
        val maxY = points.maxOf { it.y }
        val width = (maxX - minX).coerceAtLeast(1f)
        val height = (maxY - minY).coerceAtLeast(1f)
        val hexColor = (strokeColor and 0xFFFFFF).toString(16)
            .padStart(6, '0').uppercase(Locale.ROOT)
        val title = artwork.label.take(200)
            .filter { it == '\t' || it == '\n' || it == '\r' || it.code >= 0x20 }
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&apos;")

        val svg = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"")
            append(number(minX)).append(' ').append(number(minY)).append(' ')
            append(number(width)).append(' ').append(number(height))
            append("\" version=\"1.1\">\n")
            append("  <title>").append(title).append("</title>\n")
            append("  <g fill=\"none\" stroke=\"#").append(hexColor)
            append("\" stroke-linecap=\"round\" stroke-linejoin=\"round\">\n")
            paths.forEach { path ->
                append("    <path d=\"")
                path.points.forEachIndexed { index, point ->
                    append(if (index == 0) "M " else " L ")
                    append(number(point.x)).append(' ').append(number(point.y))
                }
                if (path.closed) append(" Z")
                append("\"/>\n")
            }
            append("  </g>\n</svg>\n")
        }.toByteArray(Charsets.UTF_8)
        require(svg.size <= 2_000_000) { "SVG excede o limite de 2 MB." }
        svg
    }

    private fun number(value: Float): String =
        String.format(Locale.US, "%.3f", value)
}
