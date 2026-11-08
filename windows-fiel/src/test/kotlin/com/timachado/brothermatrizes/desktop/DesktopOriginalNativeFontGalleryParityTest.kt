package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.EmbroideryFontPreset
import com.timachado.brothermatrizes.core.embroidery.SatinUnderlayMode
import com.timachado.brothermatrizes.core.embroidery.TextMatrixGenerator
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Original Android UI parity: never duplicate font-card stitch logic. */
class DesktopOriginalNativeFontGalleryParityTest {
    private val root = Path.of("").toAbsolutePath().normalize()

    @Test
    fun windowsFontCardsAreCopiedLiterallyFromOriginalAndroidScreen() {
        val original = Files.readString(root.resolve(
            "../app/src/main/java/com/timachado/brothermatrizes/FontLibraryScreen.kt"
        )).replace("\r\n", "\n")
        val generated = Files.readString(root.resolve(
            "build/generated/android-native-font-gallery/kotlin/com/timachado/" +
                "brothermatrizes/desktop/OriginalAndroidNativeFontCard.kt"
        )).replace("\r\n", "\n")
        val first = original.indexOf("                val preview =\n",
            original.indexOf("            items(\n                EmbroideryFontPreset"))
        val last = original.indexOf(
            "\n            }\n\n            item {\n                Text(\n" +
                "                    \"As fontes nativas usam", first
        )
        assertTrue(first > 0 && last > first)
        val originalCard = original.substring(first, last)
        assertTrue(generated.contains(originalCard),
            "Prévia de fonte nativa não corresponde ao Android oficial.")
        assertTrue(generated.contains("EmbroideryCanvas("))
        assertTrue(generated.contains("font.displayName"))
        assertTrue(generated.contains("stitchCount"))
        val desktopUi = Files.readString(root.resolve(
            "src/main/kotlin/com/timachado/brothermatrizes/desktop/" +
                "DesktopFontCatalogScreen.kt"
        ))
        assertTrue(desktopUi.contains("OriginalAndroidNativeFontCard(preset)"))
        assertTrue(desktopUi.contains("items(EmbroideryFontPreset.entries"))
        assertTrue(EmbroideryFontPreset.entries.isNotEmpty())
        assertEquals(
            EmbroideryFontPreset.entries.size,
            EmbroideryFontPreset.entries.map { it.name }.distinct().size
        )
    }

    @Test
    fun originalBuiltInFontActuallyGeneratesItsBordadoPreview() {
        val design = EmbroideryFontPreset.entries.asSequence()
            .mapNotNull { font ->
                TextMatrixGenerator.generate(
                    TextMatrixOptions(
                        text = "BM", heightMm = 13f,
                        style = TextStitchStyle.SATIN,
                        satinWidthMm = 2.2f,
                        satinDensityMm = 0.5f,
                        satinUnderlayMode = SatinUnderlayMode.CENTER,
                        font = font
                    )
                ).getOrNull()
            }.firstOrNull { it.stitchCount > 0 }
        assertTrue(design != null,
            "Nenhum preset Android gerou a amostra de pontadas BM.")
    }
}
