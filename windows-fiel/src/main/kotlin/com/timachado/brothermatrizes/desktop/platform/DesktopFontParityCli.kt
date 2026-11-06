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
    // Medir glifo isolado e palavra inteira. Os parâmetros e a TTF
    // são os mesmos dos casos produzidos no emulador Android.
    for (text in listOf("O", "Maria")) {
        val geometry = DesktopSkiaGlyphGeometry.sample(
            fontFile = font,
            text = text,
            targetHeightUnits = 120f,
            spacingUnits = 3f
        )
        require(geometry.contoursByGlyph.isNotEmpty()) {
            "A fonte não gerou contornos no Skia Desktop."
        }
        if (text == "O") require(geometry.contoursByGlyph.size == 1 &&
            geometry.contoursByGlyph.first().size >= 2) {
            "A letra O perdeu o vazado interior."
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
        val target = if (text == "O") output else
            output.resolveSibling("bm-parity-windows-outline-maria.tsv")
        Files.createDirectories(target.parent)
        Files.writeString(target, lines.joinToString("\n", postfix = "\n"))
        println("BM_FONT_PARITY_EXPORTED text=" + text + " sha256=" + sha +
            " contours=" + geometry.contourCount)
    }
    println("BM_ACTIVE_SATIN_WINDOWS_OUTLINES_READY sha256=" + sha)
}
