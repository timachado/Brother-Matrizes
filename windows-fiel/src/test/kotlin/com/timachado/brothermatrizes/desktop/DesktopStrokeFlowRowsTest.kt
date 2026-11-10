package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopStrokeRaster
import com.timachado.brothermatrizes.desktop.platform.DesktopStrokeFlowPlanner
import com.timachado.brothermatrizes.desktop.platform.DesktopStrokeFlowRows
import com.timachado.brothermatrizes.desktop.platform.DesktopStrokeSkeleton
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Valida o fluxo máscara -> skeleton -> segmentos -> linhas Satin.
 * A etapa de emissão de STITCH/JUMP do stroke-flow principal continua
 * pendente; esta classe não homologa exportação de matrizes comerciais.
 */
class DesktopStrokeFlowRowsTest {
    private fun testRaster(): DesktopStrokeRaster {
        val width = 90
        val height = 75
        val mask = BooleanArray(width * height)
        for (y in 10..60) {
            for (x in 14..23) mask[y * width + x] = true
            for (x in 50..59) mask[y * width + x] = true
        }
        return DesktopStrokeRaster(mask, width, height, 0.75f, -20, 80, 8)
    }

    @Test
    fun `esqueleto real com colunas desconectadas gera rows satin nas duas areas`() {
        val raster = testRaster()
        val initialMask = raster.mask.copyOf()
        val skeleton = DesktopStrokeSkeleton.analyze(
            raster.mask, raster.width, raster.height
        )
        val plan = DesktopStrokeFlowPlanner.plan(skeleton)
        val phases = plan.filter { it.phase == "CENTER_RUN" }
        assertTrue(phases.size >= 2)
        val results = phases.map { step ->
            DesktopStrokeFlowRows.build(step.geometry, raster, 0.45f, 0.2f)
        }
        assertTrue(results.all { it.size >= 2 },
            "Cada coluna conectada ao planejamento precisa produzir linhas Satin.")
        assertTrue(results.flatten().all {
            it.centerXUnits in -10_000..10_000 &&
                it.centerYUnits in -10_000..10_000 &&
                it.aXUnits in -10_000..10_000 &&
                it.bXUnits in -10_000..10_000 &&
                it.aYUnits in -10_000..10_000 &&
                it.bYUnits in -10_000..10_000
        })
        assertTrue(results.flatten().any { it.aXUnits != it.bXUnits })
        assertContentEquals(initialMask, raster.mask)
        assertEquals(results, phases.map {
            DesktopStrokeFlowRows.build(it.geometry, raster, 0.45f, 0.2f)
        })
    }

    @Test
    fun `compensacao de repuxo altera a cobertura sem alterar a máscara`() {
        val raster = testRaster()
        val skeleton = DesktopStrokeSkeleton.analyze(
            raster.mask, raster.width, raster.height
        )
        val first = DesktopStrokeFlowPlanner.plan(skeleton)
            .first { it.phase == "CENTER_RUN" }.geometry
        val plain = DesktopStrokeFlowRows.build(first, raster, 0.45f, 0f)
        val compensated = DesktopStrokeFlowRows.build(first, raster, 0.45f, 0.8f)
        assertEquals(plain.size, compensated.size)
        assertTrue(plain.isNotEmpty())
        assertTrue(plain.zip(compensated).any { (a, b) ->
            a.aXUnits != b.aXUnits || a.bXUnits != b.bXUnits ||
                a.aYUnits != b.aYUnits || a.bYUnits != b.bYUnits
        }, "O repuxo precisa modificar a largura projetada das linhas.")
    }

    @Test
    fun `limites oficiais da densidade e do repuxo sao obrigatorios`() {
        val raster = testRaster()
        val line = DesktopStrokeSkeleton.analyze(
            raster.mask, raster.width, raster.height
        ).first()
        for (density in listOf(0f, Float.NaN, 2f)) {
            assertFailsWith<IllegalArgumentException> {
                DesktopStrokeFlowRows.build(line, raster, density, 0.2f)
            }
        }
        for (pull in listOf(-0.1f, Float.POSITIVE_INFINITY, 2f)) {
            assertFailsWith<IllegalArgumentException> {
                DesktopStrokeFlowRows.build(line, raster, 0.45f, pull)
            }
        }
    }

    @Test
    fun `funcoes geradas de rows devem ser literalmente iguais a fonte Android`() {
        val root = Path.of("").toAbsolutePath().normalize()
        val android = Files.readString(root.resolve(
            "../app/src/main/java/com/timachado/brothermatrizes/" +
                "font/ImportedFontMatrixGenerator.kt"
        )).replace("\r\n", "\n")
        val generated = Files.readString(root.resolve(
            "build/generated/stroke-rows/kotlin/" +
                "com/timachado/brothermatrizes/desktop/platform/DesktopStrokeFlowRows.kt"
        ))
        for ((startMarker, stopMarker) in listOf(
            "    private fun buildStrokeFlowRows(" to
                "    private fun orientStrokeFlowRows(",
            "    private fun smoothAndResampleSkeleton(" to
                "    private fun boundaryDistance(",
            "    private fun boundaryDistance(" to
                "    private fun buildFilledText("
        )) {
            val start = android.indexOf(startMarker)
            val stop = android.indexOf(stopMarker, start)
            assertTrue(start > 0 && stop > start)
            assertTrue(generated.contains(android.substring(start, stop)),
                "As linhas Satin não podem divergir da implementação Android.")
        }
    }
}
