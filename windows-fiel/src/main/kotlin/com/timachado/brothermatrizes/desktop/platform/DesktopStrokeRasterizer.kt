package com.timachado.brothermatrizes.desktop.platform

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PathBuilder
import org.jetbrains.skia.PathFillMode
import org.jetbrains.skia.Surface
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Porta JVM da máscara EVEN_ODD gerada pelo buildSatinByAxis do Android.
 * Mantém as mesmas coordenadas inteiras, max(0.75, maiorLado/1500),
 * padding=8, anti-alias ativado e limiar alpha >=96.
 *
 * Não é um gerador de bordado e não deve ser disponibilizado aos clientes
 * até aprovar comparação numérica das máscaras Android x Skia Desktop.
 */
internal class DesktopStrokeRaster(
    val mask: BooleanArray,
    val width: Int,
    val height: Int,
    val unitsPerPixel: Float,
    val minX: Int,
    val maxY: Int,
    val padding: Int
) {
    fun trace(): List<List<DesktopSkeletonPoint>> =
        DesktopStrokeSkeleton.analyze(mask, width, height)
}

internal object DesktopStrokeRasterizer {
    fun rasterize(glyph: List<List<DesktopContourPoint>>): DesktopStrokeRaster {
        require(glyph.size in 1..64) { "Quantidade de contornos inválida." }
        val paths = glyph.map { source ->
            require(source.size in 3..20_000 &&
                source.all { it.x.isFinite() && it.y.isFinite() &&
                    kotlin.math.abs(it.x) <= 1_000_000f &&
                    kotlin.math.abs(it.y) <= 1_000_000f }) {
                "Polígono contém geometria inválida."
            }
            source.map { it.x.roundToInt() to it.y.roundToInt() }
                .fold(mutableListOf<Pair<Int,Int>>()) { acc, p ->
                    if (acc.lastOrNull() != p) acc += p
                    acc
                }
        }.filter { it.size >= 3 }
        require(paths.isNotEmpty()) { "A fonte não gerou contornos úteis." }
        val all = paths.flatten()
        val minX = all.minOf { it.first }
        val maxX = all.maxOf { it.first }
        val minY = all.minOf { it.second }
        val maxY = all.maxOf { it.second }
        val majorSpan = max(maxX - minX, maxY - minY).coerceAtLeast(1)
        val unitsPerPixel = max(0.75f, majorSpan / 1500f)
        val padding = 8
        val width = ((maxX - minX) / unitsPerPixel).roundToInt() + padding * 2 + 1
        val height = ((maxY - minY) / unitsPerPixel).roundToInt() + padding * 2 + 1
        require(width in 17..1800 && height in 17..1800 &&
            width.toLong() * height.toLong() <= 1_500_000L) {
            "Máscara de fonte excedeu os limites de processamento seguro."
        }
        val skiaPath = PathBuilder(PathFillMode.EVEN_ODD)
        try {
            paths.forEach { polygon ->
                val first = polygon.first()
                skiaPath.moveTo(
                    (first.first - minX) / unitsPerPixel + padding,
                    (maxY - first.second) / unitsPerPixel + padding
                )
                polygon.drop(1).forEach { (x, y) ->
                    skiaPath.lineTo(
                        (x - minX) / unitsPerPixel + padding,
                        (maxY - y) / unitsPerPixel + padding
                    )
                }
                skiaPath.closePath()
            }
            val immutablePath = skiaPath.detach()
            try {
              val surface = Surface.makeRasterN32Premul(width, height)
              try {
                val paint = Paint()
                try {
                    paint.isAntiAlias = true
                    paint.mode = PaintMode.FILL
                    paint.color = 0xFFFFFFFF.toInt()
                    surface.canvas.drawPath(immutablePath, paint)
                    val bitmap = Bitmap()
                    try {
                        require(bitmap.allocN32Pixels(width, height)) {
                            "Bitmap de máscara TTF não pôde ser alocado."
                        }
                        require(surface.readPixels(bitmap, 0, 0)) {
                            "Máscara TTF não pôde ser lida do Skia."
                        }
                        val mask = BooleanArray(width * height) { index ->
                            bitmap.getAlphaf(index % width, index / width) *
                                255f >= 96f
                        }
                        require(mask.any { it }) {
                            "O contorno rasterizado não contém área útil."
                        }
                        return DesktopStrokeRaster(
                            mask, width, height,
                            unitsPerPixel, minX, maxY, padding
                        )
                    } finally {
                        bitmap.close()
                    }
                } finally {
                    paint.close()
                }
              } finally {
                  surface.close()
              }
            } finally {
                immutablePath.close()
            }
        } finally {
            skiaPath.close()
        }
    }
}
