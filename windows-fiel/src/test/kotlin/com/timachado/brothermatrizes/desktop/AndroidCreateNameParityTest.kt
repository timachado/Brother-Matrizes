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

/**
 * Os Composables CreateName, Viewer e Simulator são copiados diretamente do
 * Android. Os testes abaixo validam os adaptadores e o mesmo motor de matriz.
 * Não equivalem à homologação visual ou ao teste numa bordadeira real.
 */
class AndroidCreateNameParityTest {
    private val root = Path.of("").toAbsolutePath().normalize()

    @Test
    fun `Criar Nome Desktop preserva exatamente código Android salvo adaptações de plataforma`() {
        val android = Files.readString(
            root.resolve("../app/src/main/java/com/timachado/brothermatrizes/CreateNameScreen.kt")
        ).replace("\r\n", "\n")
        val desktop = Files.readString(
            root.resolve("build/generated/android-create-name/kotlin/com/timachado/brothermatrizes/CreateNameScreen.kt")
        )
        val expected = android
            .replace(
                "import android.content.Context",
                "import com.timachado.brothermatrizes.desktop.platform.Context"
            )
            .replace(
                "import androidx.compose.ui.platform.LocalContext",
                "import com.timachado.brothermatrizes.desktop.platform.LocalContext"
            )
            .replace(
                "import com.timachado.brothermatrizes.font.ImportedFontMatrixGenerator",
                "import com.timachado.brothermatrizes.desktop.platform.ImportedFontMatrixGenerator"
            )
            .replace(
                "import com.timachado.brothermatrizes.font.ImportedFontStore",
                "import com.timachado.brothermatrizes.desktop.platform.ImportedFontStore"
            )
        assertEquals(expected, desktop, "Criar Nome foi redesenhado ou reescrito.")
        assertTrue(desktop.contains("TextLayoutGenerator"))
        assertTrue(desktop.contains("EmbroideryCanvas("))
        assertTrue(desktop.contains("\"Criar matriz\""))
    }

    @Test
    fun `mesmo motor oficial gera pontadas e formatos PES DST com nome sintetico`() {
        val options = TextLayoutOptions(
            textOptions = TextMatrixOptions(
                text = "Maria",
                heightMm = 12f,
                style = TextStitchStyle.RUNNING
            )
        )
        val generated = TextLayoutGenerator.generate(options).getOrThrow()
        assertTrue(generated.points.count { it.command == StitchCommand.STITCH } > 15)
        assertTrue(generated.bounds.widthMm > 0f)
        assertTrue(generated.bounds.heightMm > 0f)
        assertEquals("Maria", options.textOptions.text)
        for (format in listOf("PES", "DST")) {
            val converted = MatrixConverter.convert(generated, format).getOrThrow()
            assertTrue(converted.bytes.size > 100)
            assertTrue(converted.fileName.endsWith("." + format.lowercase()))
        }
    }
}
