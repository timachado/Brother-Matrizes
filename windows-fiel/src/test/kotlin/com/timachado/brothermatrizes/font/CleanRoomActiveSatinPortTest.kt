package com.timachado.brothermatrizes.font

import com.timachado.brothermatrizes.core.embroidery.SatinUnderlayMode
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Testes na implementação que o Android comercial REALMENTE chama.
 * Não homologam os adaptadores gráficos Android↔Skia nem bordadeira.
 */
class CleanRoomActiveSatinPortTest {
    @Test
    fun `motor ativo é copia literal sem reescrever codigo Android`() {
        val root = Path.of("").toAbsolutePath().normalize()
        val relative = "com/timachado/brothermatrizes/font/CleanRoomSatinEngine.kt"
        val android = Files.readAllBytes(root.resolve("../app/src/main/java/$relative"))
        val windows = Files.readAllBytes(root.resolve(
            "build/generated/active-satin-android/kotlin/$relative"
        ))
        assertTrue(android.contentEquals(windows),
            "Motor ativo Windows precisa ser byte a byte igual ao código Android 1.11.8.")
        val source = windows.toString(Charsets.UTF_8)
        assertTrue(source.contains("internal object CleanRoomSatinEngine"))
        assertTrue(source.contains("planContours("))
        assertTrue(source.contains("ImportedFontCoverageGuard.check("))
    }

    @Test
    fun `cobertura satin do motor ativo alterna passadas e preserva pontos`() {
        val rails = (0..12).map { i ->
            val x = i * 4f
            CleanRoomSatinEngine.Rail(
                CleanRoomSatinEngine.V(x, 0f),
                CleanRoomSatinEngine.V(x, 45f)
            )
        }
        val plan = CleanRoomSatinEngine.PlannedGlyph(
            rails = listOf(rails),
            sampledEdges = rails.size,
            duplicateRailsDiscarded = 0
        )
        val plain = CleanRoomSatinEngine.stitchPlannedGlyphForTest(
            plan, SatinUnderlayMode.NONE
        )
        val underlay = CleanRoomSatinEngine.stitchPlannedGlyphForTest(
            plan, SatinUnderlayMode.BOTH
        )
        assertTrue(plain.count { it.command == StitchCommand.STITCH } >= 24)
        assertTrue(underlay.any { it.command == StitchCommand.STITCH })
        assertTrue(underlay != plain)
        assertEquals(plain, CleanRoomSatinEngine.stitchPlannedGlyphForTest(plan))
        assertTrue(plain.zipWithNext().all { (a,b) ->
            if (b.command != StitchCommand.STITCH) true
            else hypot(
                (b.xUnits - a.xUnits).toDouble(),
                (b.yUnits - a.yUnits).toDouble()
            ) <= 70.5
        }, "Comprimento de ponto excede a política original de 70 unidades.")
    }

    @Test
    fun `não libera fonte importada sem extrator e guard homologados`() {
        val font = ImportedFont(
            id = "qa-only",
            displayName = "Fonte QA",
            fileName = "qa.ttf",
            extension = "ttf",
            absolutePath = "nao-existe.ttf"
        )
        val result = CleanRoomSatinEngine.generate(
            font = font,
            sourceText = "Maria",
            options = TextMatrixOptions(
                text = "Maria", heightMm = 12f,
                style = TextStitchStyle.SATIN
            ),
            filePrefix = "qa"
        )
        assertTrue(result.isFailure,
            "Sem a verificação de cobertura, geração de fonte é proibida.")
        assertFalse(result.getOrNull() != null)
        assertTrue(result.exceptionOrNull() is UnsupportedOperationException)
    }
}
