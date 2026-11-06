package com.timachado.brothermatrizes.font

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
