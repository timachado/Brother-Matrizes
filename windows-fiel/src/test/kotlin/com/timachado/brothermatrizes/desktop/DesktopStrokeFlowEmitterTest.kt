package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.desktop.platform.DesktopStrokeFlowPipeline
import com.timachado.brothermatrizes.desktop.platform.DesktopStrokeRaster
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Geração de pontadas de QA, não homologação de bordado físico. */
class DesktopStrokeFlowEmitterTest {
    private fun bars(): DesktopStrokeRaster {
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
    fun `stroke flow completo produz STITCH JUMP e respeita o comprimento maximo`() {
        val raster = bars()
        val originalMask = raster.mask.copyOf()
        val points = DesktopStrokeFlowPipeline.generate(
            raster = raster, densityMm = 0.45f,
            pullCompensationMm = 0.2f, includeUnderlay = true
        )
        assertTrue(points.count { it.command == StitchCommand.STITCH } > 30)
        assertTrue(points.any { it.command == StitchCommand.JUMP })
        assertTrue(points.size < 100_000)
        points.zipWithNext().forEach { (a, b) ->
            if (b.command == StitchCommand.STITCH && a.command == StitchCommand.STITCH) {
                assertTrue(
                    hypot(
                        (b.xUnits - a.xUnits).toDouble(),
                        (b.yUnits - a.yUnits).toDouble()
                    ) <= 70.5,
                    "Pontadas Satin não podem superar maxLengthUnits=70."
                )
            }
        }
        assertContentEquals(originalMask, raster.mask)
        assertEquals(points, DesktopStrokeFlowPipeline.generate(
            raster, 0.45f, 0.2f, true
        ))
    }

    @Test
    fun `center run underlay altera contagem mas nao apaga cobertura`() {
        val raster = bars()
        val noUnderlay = DesktopStrokeFlowPipeline.generate(
            raster, 0.45f, 0.2f, false
        )
        val withUnderlay = DesktopStrokeFlowPipeline.generate(
            raster, 0.45f, 0.2f, true
        )
        assertTrue(noUnderlay.any { it.command == StitchCommand.STITCH })
        assertTrue(withUnderlay.any { it.command == StitchCommand.STITCH })
        assertTrue(withUnderlay.size >= noUnderlay.size)
        assertTrue(withUnderlay != noUnderlay)
    }

    @Test
    fun `densidade e compensacao fora da faixa Android sao recusadas`() {
        val raster = bars()
        for (density in listOf(0f, 2f, Float.NaN)) {
            assertFailsWith<IllegalArgumentException> {
                DesktopStrokeFlowPipeline.generate(raster, density, 0.2f, true)
            }
        }
        for (pull in listOf(-1f, 2f, Float.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> {
                DesktopStrokeFlowPipeline.generate(raster, 0.45f, pull, true)
            }
        }
    }

    @Test
    fun `emissor usa literalmente o codigo Android 1_11_8`() {
        val root = Path.of("").toAbsolutePath().normalize()
        val android = Files.readString(root.resolve(
            "../app/src/main/java/com/timachado/brothermatrizes/" +
                "font/ImportedFontMatrixGenerator.kt"
        )).replace("\r\n", "\n")
        val generated = Files.readString(root.resolve(
            "build/generated/stroke-emitter/kotlin/" +
                "com/timachado/brothermatrizes/desktop/platform/" +
                "DesktopStrokeFlowEmitter.kt"
        ))
        for ((startMarker, endMarker) in listOf(
            "    private data class SkeletonPoint(" to
                "    private fun buildStrokeFlowSatinText(",
            "    private fun orientStrokeFlowRows(" to
                "    internal fun debugStrokeFlowPostOrder()",
            "    private fun appendSplitStitch(" to
                "    private fun repairSatinSequence("
        )) {
            val start = android.indexOf(startMarker)
            val stop = android.indexOf(endMarker, start)
            assertTrue(start > 0 && stop > start)
            assertTrue(generated.contains(android.substring(start, stop)))
        }
    }
}
