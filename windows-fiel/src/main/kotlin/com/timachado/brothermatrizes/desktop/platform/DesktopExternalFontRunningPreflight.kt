package com.timachado.brothermatrizes.desktop.platform

import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.core.embroidery.VectorArtwork
import com.timachado.brothermatrizes.core.embroidery.VectorMatrixGenerator
import com.timachado.brothermatrizes.core.embroidery.VectorMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.VectorPath
import com.timachado.brothermatrizes.core.embroidery.VectorPoint
import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.font.TextLayout
import java.awt.geom.AffineTransform
import java.awt.geom.FlatteningPathIterator
import java.awt.geom.PathIterator
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.Locale

/**
 * Offline QA preflight: real TTF/OTF outline -> the SAME Android core
 * VectorMatrixGenerator, RUNNING stitch only, entirely in memory.
 *
 * NOT the commercial ImportedFontMatrixGenerator SATIN implementation.
 * NOT wired to the embroidery editor, export, font library or quota ledger.
 * Does not import, persist, publish or return the generated matrix. Nothing
 * is uploaded and no Google / Supabase account or quota is accessed.
 *
 * Future product work must port AndroidSkiaOutlineExtractor and the 70k+
 * character CleanRoomSatinEngine with fixture-level parity and real stitch-outs
 * before enabling imported-font satin commercially.
 */
internal object DesktopExternalFontRunningPreflight {
    private const val MAX_FONT_BYTES = 12L * 1024 * 1024
    private const val MAX_CONTOURS = 600
    private const val MAX_POINTS = 60_000
    private const val MAX_CHARS = 24
    private val allowedExt = setOf("ttf", "otf")

    data class Report(
        val familyName: String,
        val testedText: String,
        val glyphContours: Int,
        val sampledOutlinePoints: Int,
        val stitchCount: Int,
        val jumpCount: Int,
        val widthMm: Float,
        val heightMm: Float,
        val generationMode: String = "RUNNING_ONLY_QA"
    )

    fun inspect(sourceFile: Path, rawText: String, requestedHeightMm: Float = 15f): Report {
        val source = sourceFile.toAbsolutePath().normalize()
        require(!Files.isSymbolicLink(source) &&
            Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(source.parent)) {
            "Fonte para diagnóstico precisa ser um arquivo local regular."
        }
        val extension = source.fileName.toString().substringAfterLast('.', "")
            .lowercase(Locale.ROOT)
        require(extension in allowedExt &&
            Files.size(source) in 4L..MAX_FONT_BYTES) {
            "Selecione fonte TTF/OTF regular com até 12 MB."
        }
        // The existing Skia probe validates magic bytes and rejects files
        // incompatible with the desktop renderer before Java2D sees them.
        DesktopReadOnlyFontAnalyzer.inspect(source)
        val awtFont = Font.createFont(Font.TRUETYPE_FONT, source.toFile())
        return inspectLoadedFont(awtFont, rawText, requestedHeightMm)
    }

    /** Deterministic QA fixture point, no filesystem or system font file. */
    internal fun inspectLoadedFont(
        font: Font, rawText: String, requestedHeightMm: Float = 15f
    ): Report {
        val text = rawText.trim()
        require(text.isNotEmpty() && text.codePointCount(0, text.length) <= MAX_CHARS &&
            text.none { Character.isISOControl(it) }) {
            "Informe de 1 a 24 caracteres sem controles."
        }
        require(requestedHeightMm.isFinite() && requestedHeightMm in 5f..60f) {
            "Altura de referência inválida (5 a 60 mm)."
        }

        val frc = FontRenderContext(AffineTransform(), true, true)
        val layout = TextLayout(text, font.deriveFont(100f), frc)
        val shape = layout.getOutline(null)
        val bounds = shape.bounds2D
        require(bounds.width.isFinite() && bounds.height.isFinite() &&
            bounds.width > 1.0 && bounds.height > 1.0) {
            "A fonte não produziu contornos utilizáveis."
        }
        val widthMm = (requestedHeightMm * bounds.width / bounds.height).toFloat()
        require(widthMm.isFinite() && widthMm in 5f..300f) {
            "A amostra não cabe na largura segura de 5 a 300 mm."
        }

        val paths = mutableListOf<VectorPath>()
        var totalPoints = 0
        var contour: MutableList<VectorPoint>? = null
        val coords = DoubleArray(6)
        val iterator = FlatteningPathIterator(shape.getPathIterator(null), 0.6)
        fun closeContour() {
            val raw = contour ?: return
            if (raw.size >= 3) {
                // VectorMatrixGenerator accepts closed contours with distinct
                // end points; never include an artificial duplicate point.
                val trimmed = if (raw.first() == raw.last()) raw.dropLast(1) else raw
                if (trimmed.size >= 3) {
                    check(paths.size < MAX_CONTOURS) {
                        "A fonte tem contornos demais para diagnóstico seguro."
                    }
                    paths += VectorPath(trimmed, closed = true)
                }
            }
            contour = null
        }

        while (!iterator.isDone) {
            val segment = iterator.currentSegment(coords)
            when (segment) {
                PathIterator.SEG_MOVETO -> {
                    closeContour()
                    contour = mutableListOf()
                    val x = coords[0].toFloat()
                    val y = coords[1].toFloat()
                    require(x.isFinite() && y.isFinite()) {
                        "Fonte com pontos fora do intervalo."
                    }
                    contour!!.add(VectorPoint(x, y))
                    totalPoints++
                }
                PathIterator.SEG_LINETO -> {
                    val x = coords[0].toFloat()
                    val y = coords[1].toFloat()
                    require(x.isFinite() && y.isFinite()) {
                        "Fonte com pontos fora do intervalo."
                    }
                    val list = contour ?: error("Contorno sem ponto inicial.")
                    val point = VectorPoint(x, y)
                    if (list.lastOrNull() != point) {
                        list.add(point)
                        totalPoints++
                    }
                }
                PathIterator.SEG_CLOSE -> closeContour()
                else -> error("O contorno não pôde ser reduzido a segmentos lineares.")
            }
            check(totalPoints <= MAX_POINTS) {
                "A fonte tem pontos demais para diagnóstico seguro."
            }
            iterator.next()
        }
        closeContour()
        require(paths.isNotEmpty()) {
            "Nenhum contorno vetorial de fonte foi encontrado."
        }

        val generated = VectorMatrixGenerator.generate(
            VectorArtwork(paths, label = "Prévia temporária de contornos"),
            VectorMatrixOptions(
                widthMm = widthMm,
                stitchStyle = TextStitchStyle.RUNNING,
                runningStitchLengthMm = 2.5f,
                outputFormat = "DST",
                hoopProfile = null,
                fabricProfile = null,
                enforceHoop = false
            )
        ).getOrThrow()
        check(generated.stitchCount > 0) {
            "Não foi possível gerar a amostra de ponto corrido."
        }
        // Return ONLY stats: never hand a commercial design to the editor or
        // expose .dst/.pes bytes without the verified import_font quota gate.
        return Report(
            familyName = font.family,
            testedText = text,
            glyphContours = paths.size,
            sampledOutlinePoints = totalPoints,
            stitchCount = generated.stitchCount,
            jumpCount = generated.jumpCount,
            widthMm = generated.bounds.widthMm,
            heightMm = generated.bounds.heightMm
        )
    }
}
