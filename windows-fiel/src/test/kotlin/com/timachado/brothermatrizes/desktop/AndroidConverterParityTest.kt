package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.MatrixConverter
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.core.embroidery.TextLayoutGenerator
import com.timachado.brothermatrizes.core.embroidery.TextLayoutOptions
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Port do Converter original Android: fonte e motor, não homologação visual. */
class AndroidConverterParityTest {
    private val root = Path.of("").toAbsolutePath().normalize()

    @Test
    fun `Converter Windows usa Compose original Android sem redesenho`() {
        val original = Files.readString(
            root.resolve("../app/src/main/java/com/timachado/brothermatrizes/ConverterScreen.kt")
        ).replace("\r\n", "\n")
        val generated = Files.readString(
            root.resolve("build/generated/android-viewer/kotlin/com/timachado/brothermatrizes/ConverterScreen.kt")
        ).replace("\r\n", "\n")
        assertEquals(original, generated, "Converter Android não foi copiado literalmente.")
        assertTrue(generated.contains("fun ConverterScreen("))
        assertTrue(generated.contains("MatrixConverter.supportedFormats"))
        assertTrue(generated.contains("onSave(selected)"))
        val host = Files.readString(
            root.resolve("src/main/kotlin/com/timachado/brothermatrizes/desktop/FielMain.kt")
        )
        assertTrue(host.contains("onConvert = { detail = ActiveDetail.CONVERTER }"))
        assertTrue(host.contains("onSave = { format -> exportDesign(format) }"))
    }

    @Test
    fun `Motor converte matriz sintética em PES DST JEF sem alterar original`() {
        val source = TextLayoutGenerator.generate(
            TextLayoutOptions(
                textOptions = TextMatrixOptions(
                    text = "Maria", heightMm = 12f,
                    style = TextStitchStyle.RUNNING
                )
            )
        ).getOrThrow()
        val original = source.points.toList()
        assertTrue(original.count { it.command == StitchCommand.STITCH } > 10)
        assertEquals(listOf("PES", "DST", "JEF"), MatrixConverter.supportedFormats)
        for (target in MatrixConverter.supportedFormats) {
            val converted = MatrixConverter.convert(source, target).getOrThrow()
            assertEquals(target, converted.format)
            assertTrue(converted.fileName.endsWith("." + target.lowercase()))
            assertTrue(converted.bytes.size > 100)
            assertEquals(original, source.points, "Conversão $target modificou a matriz.")
        }
    }
}
