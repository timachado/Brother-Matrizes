package com.timachado.brothermatrizes.font

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.timachado.brothermatrizes.core.embroidery.SatinUnderlayMode
import com.timachado.brothermatrizes.core.embroidery.EmbroideryPoint
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Referência real de fonte importada, SEM arquivos de clientes.
 * Roboto vem do Android emulador e seus mesmos bytes serão usados no Windows.
 */
@RunWith(AndroidJUnit4::class)
class ActiveSatinFontGoldenTest {
    @Test
    fun captureSameFontOutlinesAndCommercialEngineDiagnostics() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = listOf(
            File("/system/fonts/Roboto-Regular.ttf"),
            File("/system/fonts/RobotoStatic-Regular.ttf")
        ).firstOrNull { it.isFile }
            ?: error("Emulador sem fonte Roboto TTF regular.")
        val bytes = source.readBytes()
        require(bytes.size in 1000..(12 * 1024 * 1024)) {
            "Fonte Android fora do limite de segurança."
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        val dest = File(context.filesDir, "bm-parity-font.ttf")
        dest.writeBytes(bytes)
        val font = ImportedFont(
            id = "qa-$digest",
            displayName = "Roboto QA Android",
            fileName = dest.name,
            extension = "ttf",
            absolutePath = dest.absolutePath
        )
        // Golden da verificação de silhueta COMERCIAL, independente da
        // fonte TTF: mesma geometria e mesmas linhas reais em Android/Skia.
        val qualityGlyph = listOf(listOf(listOf(
            AndroidSkiaOutlineExtractor.Point(0f, 0f),
            AndroidSkiaOutlineExtractor.Point(120f, 0f),
            AndroidSkiaOutlineExtractor.Point(120f, 80f),
            AndroidSkiaOutlineExtractor.Point(0f, 80f)
        )))
        val qualityStitches = buildList {
            for (y in 3..77 step 6) {
                add(EmbroideryPoint(3, y, StitchCommand.JUMP, 0))
                add(EmbroideryPoint(117, y, StitchCommand.STITCH, 0))
            }
        }
        val quality = ImportedFontCoverageGuard.check(
            qualityGlyph, qualityStitches
        )
        val displaced = qualityStitches.map {
            it.copy(xUnits = it.xUnits + 300)
        }
        val blocked = runCatching {
            ImportedFontCoverageGuard.check(qualityGlyph, displaced)
        }.isFailure
        assertTrue("Guard Android não bloqueou bordado deslocado.", blocked)
        File(context.filesDir, "bm-parity-android-coverage.tsv")
            .writeText(
                "SCORE\t" + quality.precision + "\t" + quality.coverage +
                    "\nBLOCKED\t" + blocked + "\n",
                Charsets.UTF_8
            )

        val text = "O"
        val geometry = AndroidSkiaOutlineExtractor.extract(
            font = font, text = text,
            targetCapHeightUnits = 120f,
            spacingUnits = 3f,
            rotationDegrees = 0f
        )
        val polygons = geometry.glyphPolygons
        assertTrue("O deve ter perímetro e vazado.",
            polygons.size == 1 && polygons[0].size >= 2)
        val lines = mutableListOf<String>()
        lines += "FONT\t" + digest + "\t" + bytes.size + "\t" + text
        polygons.forEachIndexed { g, glyph ->
            glyph.forEachIndexed { c, contour ->
                contour.forEachIndexed { i, point ->
                    lines += "P\t" + g + "\t" + c + "\t" + i + "\t" +
                        point.x + "\t" + point.y
                }
            }
        }
        lines += "META\t" + geometry.sourceWidth + "\t" +
            geometry.sourceHeight + "\t" + geometry.contextualShapingPreserved
        File(context.filesDir, "bm-parity-android-outline.tsv")
            .writeText(lines.joinToString("\n", postfix = "\n"), Charsets.UTF_8)

        // Segundo cenário: palavra inteira na MESMA TTF. O Android usa
        // getRunAdvance + shaping contextual; não aceitar a soma das
        // larguras isoladas do Desktop como equivalência presumida.
        // Comparar pontadas do motor COMERCIAL ativo em geometria O,
        // sem publicar uma matriz: a etapa de guard permanece obrigatória
        // para qualquer geração destinada a clientes.
        val rails = CleanRoomSatinEngine.planContours(
            polygons[0].map { contour ->
                contour.map { point ->
                    CleanRoomSatinEngine.V(point.x, point.y)
                }
            },
            densityMm = .45f,
            pullMm = .2f
        )
        assertTrue("A fonte O não criou trilhos Satin.",
            rails.rails.isNotEmpty())
        val qaStitches = CleanRoomSatinEngine.stitchPlannedGlyphForTest(
            rails, SatinUnderlayMode.BOTH
        )
        assertTrue("A fonte O não produziu pontos no kernel ativo.",
            qaStitches.count { it.command == StitchCommand.STITCH } > 20)
        val stitchRows = mutableListOf("STITCHES\t" + qaStitches.size)
        qaStitches.forEachIndexed { i, point ->
            stitchRows += "P\t" + i + "\t" + point.xUnits +
                "\t" + point.yUnits + "\t" + point.command.name +
                "\t" + point.colorIndex
        }
        // Medir também a guarda de SILHUETA para o O REAL da mesma
        // fonte e do mesmo kernel; a matriz não é publicada. Se ambos
        // os SO bloquearem, o diagnóstico deve refletir esse bloqueio.
        val realCoverage = runCatching {
            ImportedFontCoverageGuard.check(polygons, qaStitches)
        }.fold(
            onSuccess = { "PASS\t" + it.precision + "\t" + it.coverage },
            onFailure = { "BLOCKED" }
        )
        File(context.filesDir, "bm-parity-android-coverage-real-o.tsv")
            .writeText(realCoverage + "\n", Charsets.UTF_8)

        File(context.filesDir, "bm-parity-android-kernel-o.tsv")
            .writeText(stitchRows.joinToString("\n", postfix = "\n"), Charsets.UTF_8)

        val word = "Maria"
        val wordGeometry = AndroidSkiaOutlineExtractor.extract(
            font = font, text = word,
            targetCapHeightUnits = 120f,
            spacingUnits = 3f,
            rotationDegrees = 0f
        )
        val wordLines = mutableListOf<String>()
        wordLines += "FONT\t" + digest + "\t" + bytes.size + "\t" + word
        wordGeometry.glyphPolygons.forEachIndexed { g, glyph ->
            glyph.forEachIndexed { c, contour ->
                contour.forEachIndexed { i, point ->
                    wordLines += "P\t" + g + "\t" + c + "\t" + i + "\t" +
                        point.x + "\t" + point.y
                }
            }
        }
        wordLines += "META\t" + wordGeometry.sourceWidth + "\t" +
            wordGeometry.sourceHeight + "\t" +
            wordGeometry.contextualShapingPreserved
        assertTrue("A palavra não produziu contornos.",
            wordLines.count { it.startsWith("P\t") } > 50)
        File(context.filesDir, "bm-parity-android-outline-maria.tsv")
            .writeText(wordLines.joinToString("\n", postfix = "\n"), Charsets.UTF_8)

        val result = CleanRoomSatinEngine.generate(
            font = font,
            sourceText = text,
            options = TextMatrixOptions(
                text = text,
                heightMm = 12f,
                spacingMm = .3f,
                style = TextStitchStyle.SATIN,
                satinDensityMm = .45f,
                satinPullCompensationMm = .2f,
                enforceHoop = false
            ),
            filePrefix = "qa"
        )
        val status = result.fold(
            onSuccess = { design ->
                val rows = buildList {
                    add("STATUS\tSUCCESS\t" + design.points.size)
                    design.points.forEachIndexed { index, p ->
                        add("" + index + "\t" + p.xUnits + "\t" +
                            p.yUnits + "\t" + p.command.name +
                            "\t" + p.colorIndex)
                    }
                }
                File(context.filesDir, "bm-parity-android-stitches.tsv")
                    .writeText(rows.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
                "SUCCESS\t" + design.stitchCount + "\t" + design.jumpCount
            },
            onFailure = { failure ->
                val detail = failure.message.orEmpty()
                    .replace('\n', ' ').replace('\t', ' ').take(400)
                "FAILURE\t" + failure.javaClass.simpleName + "\t" + detail
            }
        )
        File(context.filesDir, "bm-parity-android-status.txt")
            .writeText("FONT_SHA256=$digest\nACTIVE_CLEANROOM=$status\n", Charsets.UTF_8)
        assertTrue("Contornos extraídos estão vazios.",
            lines.count { it.startsWith("P\t") } > 20)
    }
}
