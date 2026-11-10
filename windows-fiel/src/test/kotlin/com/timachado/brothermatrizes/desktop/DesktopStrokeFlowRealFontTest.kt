package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.desktop.platform.DesktopStrokeFlowPipeline
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integração TTF real -> Skia -> stroke-flow -> pontadas.
 * Ainda exige confronto com Android para uso comercial.
 */
class DesktopStrokeFlowRealFontTest {
    private fun font(): Path {
        val fonts = Path.of(System.getenv("WINDIR") ?: "C:\\Windows", "Fonts")
        return listOf(
            fonts.resolve("arial.ttf"),
            fonts.resolve("segoeui.ttf"),
            Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
        ).firstOrNull { Files.isRegularFile(it) }
            ?: error("Sem fonte TTF do sistema no runner Windows.")
    }

    @Test
    fun `fonte verdadeira percorre mask skeleton satin e produz pontadas`() {
        val path = font()
        val result = DesktopStrokeFlowPipeline.generateFromFont(
            fontFile = path,
            text = "O",
            heightUnits = 120f,
            spacingUnits = 3f,
            densityMm = 0.45f,
            pullCompensationMm = 0.2f,
            includeUnderlay = true
        )
        assertTrue(result.count { it.command == StitchCommand.STITCH } > 20)
        assertTrue(result.any { it.command == StitchCommand.JUMP })
        assertTrue(result.size < 100_000)
        assertEquals(result, DesktopStrokeFlowPipeline.generateFromFont(
            fontFile = path, text = "O",
            heightUnits = 120f, spacingUnits = 3f,
            densityMm = 0.45f, pullCompensationMm = 0.2f,
            includeUnderlay = true
        ))
    }
}
