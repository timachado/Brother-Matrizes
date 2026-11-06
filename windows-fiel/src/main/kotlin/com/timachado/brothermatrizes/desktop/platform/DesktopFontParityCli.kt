package com.timachado.brothermatrizes.desktop.platform

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Exportador de diagnóstico: SEM licença, sem UI e sem gerar arquivo de
 * bordado de cliente. O arquivo da fonte é fornecido pelo job Android da
 * MESMA execução GitHub Actions, verificado por SHA-256.
 */
fun main(args: Array<String>) {
    require(args.size == 2) { "Uso QA: fonte.ttf saída.tsv" }
    val font = Path.of(args[0]).toAbsolutePath().normalize()
    val output = Path.of(args[1]).toAbsolutePath().normalize()
    require(Files.isRegularFile(font) && !Files.isSymbolicLink(font)) {
        "Fonte QA inexistente ou link simbólico."
    }
    val bytes = Files.readAllBytes(font)
    require(bytes.size in 1000..(12 * 1024 * 1024)) {
        "Fonte de QA fora do limite de 12 MB."
    }
    val sha = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    val text = "O"
    val geometry = DesktopSkiaGlyphGeometry.sample(
        fontFile = font,
        text = text,
        targetHeightUnits = 120f,
        spacingUnits = 3f
    )
    require(geometry.contoursByGlyph.size == 1 &&
        geometry.contoursByGlyph.first().size >= 2) {
        "O caractere O perdeu contornos no Skia Desktop."
    }
    val lines = mutableListOf("FONT\t" + sha + "\t" + bytes.size + "\t" + text)
    geometry.contoursByGlyph.forEachIndexed { g, glyph ->
        glyph.forEachIndexed { c, contour ->
            contour.forEachIndexed { i, point ->
                lines += "P\t" + g + "\t" + c + "\t" + i +
                    "\t" + point.x + "\t" + point.y
            }
        }
    }
    lines += "META\t" + (geometry.right - geometry.left) +
        "\t" + (geometry.bottom - geometry.top) + "\tfalse"
    Files.createDirectories(output.parent)
    Files.writeString(output, lines.joinToString("\n", postfix = "\n"))
    println("BM_ACTIVE_SATIN_WINDOWS_OUTLINES_READY sha256=" + sha)
}
