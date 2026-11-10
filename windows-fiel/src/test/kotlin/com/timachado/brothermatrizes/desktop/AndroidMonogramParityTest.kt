package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.MonogramGenerator
import com.timachado.brothermatrizes.core.embroidery.MonogramOptions
import com.timachado.brothermatrizes.core.embroidery.MonogramStyle
import com.timachado.brothermatrizes.core.embroidery.MatrixConverter
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Monograma original Android no Desktop; apenas verificação de fonte e de
 * pontadas sintéticas. Sem homologação comercial ou física de bordadeira.
 */
class AndroidMonogramParityTest {
    private val root = Path.of("").toAbsolutePath().normalize()

    @Test
    fun `MonogramScreen do desktop corresponde byte a byte em texto ao Android`() {
        val android = Files.readString(root.resolve(
            "../app/src/main/java/com/timachado/brothermatrizes/MonogramScreen.kt"
        )).replace("\r\n", "\n")
        val desktop = Files.readString(root.resolve(
            "build/generated/android-viewer/kotlin/com/timachado/brothermatrizes/MonogramScreen.kt"
        )).replace("\r\n", "\n")
        assertEquals(android, desktop, "MonogramScreen Android foi modificado.")
        assertTrue(desktop.contains("fun MonogramScreen("))
        assertTrue(desktop.contains("MonogramGenerator.generate("))
        val host = Files.readString(root.resolve(
            "src/main/kotlin/com/timachado/brothermatrizes/desktop/FielMain.kt"
        ))
        assertTrue(host.contains("Item(\"Monograma\", onClick = { monogramLaunchRequest++ })"))
        assertTrue(host.contains("detail == ActiveDetail.MONOGRAM"))
        assertTrue(host.contains("recent = activeDesign"))
    }

    @Test
    fun `Monograma sintético gera pontos reais e matriz Brother PES`() {
        val design = MonogramGenerator.generate(
            MonogramOptions(
                initials = "TM",
                style = MonogramStyle.CLASSIC,
                outputFormat = MatrixConverter.preferredBrotherFormat
            )
        ).getOrThrow()
        assertTrue(design.points.count { it.command == StitchCommand.STITCH } > 20)
        assertTrue(design.bounds.widthMm > 0f && design.bounds.heightMm > 0f)
        val originalPoints = design.points.toList()
        val pes = MatrixConverter.convert(design, "PES").getOrThrow()
        assertTrue(pes.bytes.size > 100)
        assertTrue(pes.fileName.endsWith(".pes"))
        assertEquals(originalPoints, design.points)
    }
}
