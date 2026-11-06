package com.timachado.brothermatrizes.desktop.platform

import com.timachado.brothermatrizes.core.embroidery.SatinUnderlayMode
import com.timachado.brothermatrizes.core.embroidery.EmbroideryPoint
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.font.CleanRoomSatinEngine
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
    // Probe de qualidade SOMENTE QA. A função de proteção comercial
    // em com.timachado...font.ImportedFontCoverageGuard continua fechada.
    val coverageGlyph = listOf(listOf(listOf(
        DesktopContourPoint(0f, 0f),
        DesktopContourPoint(120f, 0f),
        DesktopContourPoint(120f, 80f),
        DesktopContourPoint(0f, 80f)
    )))
    val coverageStitches = buildList {
        for (y in 3..77 step 6) {
            add(EmbroideryPoint(3, y, StitchCommand.JUMP, 0))
            add(EmbroideryPoint(117, y, StitchCommand.STITCH, 0))
        }
    }
    val coverage = DesktopCoverageGuardProbe.check(
        coverageGlyph, coverageStitches
    )
    val blocked = runCatching {
        DesktopCoverageGuardProbe.check(
            coverageGlyph,
            coverageStitches.map { it.copy(xUnits = it.xUnits + 300) }
        )
    }.isFailure
    require(blocked) { "Probe falhou em rejeitar bordado deslocado." }
    Files.createDirectories(output.parent)
    Files.writeString(
        output.resolveSibling("bm-parity-windows-coverage.tsv"),
        "SCORE\t" + coverage.precision + "\t" + coverage.coverage +
            "\nBLOCKED\t" + blocked + "\n"
    )
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
        if (text == "O") {
            // Testar o MESMO kernel Satin ativo do Android, sem abrir
            // comercialmente o gerador que exige o guard de silhueta.
            val plan = CleanRoomSatinEngine.planContours(
                geometry.contoursByGlyph[0].map { contour ->
                    contour.map { point ->
                        CleanRoomSatinEngine.V(point.x, point.y)
                    }
                },
                densityMm = .45f,
                pullMm = .2f
            )
            require(plan.rails.isNotEmpty()) {
                "A fonte O não criou trilhos Satin no Windows."
            }
            val stitches = CleanRoomSatinEngine.stitchPlannedGlyphForTest(
                plan, SatinUnderlayMode.BOTH
            )
            require(stitches.count { it.command == StitchCommand.STITCH } > 20) {
                "O motor Satin ativo não produziu pontadas Windows."
            }
            val stitchRows = mutableListOf("STITCHES\t" + stitches.size)
            stitches.forEachIndexed { i, point ->
                stitchRows += "P\t" + i + "\t" + point.xUnits +
                    "\t" + point.yUnits + "\t" + point.command.name +
                    "\t" + point.colorIndex
            }
            Files.writeString(
                output.resolveSibling("bm-parity-windows-kernel-o.tsv"),
                stitchRows.joinToString("\n", postfix = "\n")
            )
        }
        println("BM_FONT_PARITY_EXPORTED text=" + text + " sha256=" + sha +
            " contours=" + geometry.contourCount)
    }
    println("BM_ACTIVE_SATIN_WINDOWS_OUTLINES_READY sha256=" + sha)
}
