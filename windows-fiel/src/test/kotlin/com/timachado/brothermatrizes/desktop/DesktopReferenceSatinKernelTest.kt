package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.SatinUnderlayMode
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.desktop.platform.DesktopContourPoint
import com.timachado.brothermatrizes.desktop.platform.DesktopReferenceSatinKernel
import com.timachado.brothermatrizes.desktop.platform.DesktopSkiaGlyphGeometry
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Regressão do MESMO algoritmo de pontos Android num host Windows.
 * Não declara paridade de rasterização TTF/OTF nem autoriza liberação.
 */
class DesktopReferenceSatinKernelTest {
    private fun font(): Path {
        val win = Path.of(System.getenv("WINDIR") ?: "C:\\Windows", "Fonts")
        return listOf(
            win.resolve("arial.ttf"),
            win.resolve("segoeui.ttf"),
            Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
        ).firstOrNull { Files.isRegularFile(it) }
            ?: error("Uma fonte TTF local de testes é necessária.")
    }

    private fun options(mode: SatinUnderlayMode = SatinUnderlayMode.BOTH) =
        TextMatrixOptions(
            text = "Maria",
            heightMm = 12f,
            style = TextStitchStyle.SATIN,
            satinUnderlayMode = mode
        )

    @Test
    fun `kernel oficial gera pontos reais Satin a partir de contornos Skia`() {
        val geometry = DesktopSkiaGlyphGeometry.sample(
            font(), "Maria", targetHeightUnits = 120f, spacingUnits = 3f
        )
        val points = DesktopReferenceSatinKernel.generate(
            geometry.contoursByGlyph, options()
        )
        assertTrue(points.count { it.command == StitchCommand.STITCH } > 100)
        assertTrue(points.any { it.command == StitchCommand.JUMP })
        assertTrue(points.size < 100_000)
        assertTrue(points.all {
            kotlin.math.abs(it.xUnits) <= 1_000_000 &&
                kotlin.math.abs(it.yUnits) <= 1_000_000
        })
        // Resultado deterministicamente idêntico com a mesma fonte/contorno.
        assertEquals(points, DesktopReferenceSatinKernel.generate(
            geometry.contoursByGlyph, options()
        ))
    }

    @Test
    fun `underlay do Android gera camadas adicionais a mesma geometria`() {
        val geometry = DesktopSkiaGlyphGeometry.sample(font(), "O", 120f, 3f)
        val without = DesktopReferenceSatinKernel.generate(
            geometry.contoursByGlyph, options(SatinUnderlayMode.NONE)
        )
        val with = DesktopReferenceSatinKernel.generate(
            geometry.contoursByGlyph, options(SatinUnderlayMode.BOTH)
        )
        assertTrue(with.size >= without.size)
        assertTrue(with.any { it.command == StitchCommand.STITCH })
    }

    @Test
    fun `kernel nao aceita geometria vazia nan nem densidade insegura`() {
        val square = listOf(
            DesktopContourPoint(0f, 0f), DesktopContourPoint(20f, 0f),
            DesktopContourPoint(20f, 20f), DesktopContourPoint(0f, 20f)
        )
        assertFailsWith<IllegalArgumentException> {
            DesktopReferenceSatinKernel.generate(emptyList(), options())
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopReferenceSatinKernel.generate(
                listOf(listOf(square.map { it.copy(x = Float.NaN) })), options()
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopReferenceSatinKernel.generate(
                listOf(listOf(square)),
                options().copy(satinDensityMm = 0f)
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopReferenceSatinKernel.generate(
                listOf(listOf(square)),
                options().copy(satinDensityMm = 2f)
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopReferenceSatinKernel.generate(
                listOf(listOf(square)),
                options().copy(satinPullCompensationMm = 2f)
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopReferenceSatinKernel.generate(
                listOf(listOf(square)),
                options().copy(style = TextStitchStyle.RUNNING)
            )
        }
    }

    @Test
    fun `metodos kernel gerados devem corresponder ao codigo Android sem adaptacoes`() {
        val root = Path.of("").toAbsolutePath().normalize()
        val original = Files.readString(root.resolve(
            "../app/src/main/java/com/timachado/brothermatrizes/" +
                "font/ImportedFontMatrixGenerator.kt"
        )).replace("\r\n", "\n")
        val a = original.indexOf("    private data class ReferenceSatinState(")
        val b = original.indexOf("    private fun densifyReferenceContours(", a)
        val c = original.indexOf("    private fun scanReferenceSpans(", b)
        val d = original.indexOf("    private fun buildCenterlineRunning(", c)
        assertTrue(a > 0 && b > a && c > b && d > c)
        val originalKernel = original.substring(a, b) + original.substring(c, d)
        val generated = Files.readString(root.resolve(
            "build/generated/satin-android/kotlin/" +
                "com/timachado/brothermatrizes/desktop/platform/" +
                "DesktopReferenceSatinKernel.kt"
        ))
        assertTrue(generated.contains(originalKernel),
            "Alteração indevida no kernel oficial de satin, underlay ou travas.")
    }
}
