package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopSkeletonPoint
import com.timachado.brothermatrizes.desktop.platform.DesktopStrokeFlowPlanner
import com.timachado.brothermatrizes.desktop.platform.DesktopStrokeSkeleton
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Planejamento de ramificações/entrada do motor Satin oficial Android.
 * Ainda NÃO conecta rows/colunas e NÃO valida pontos de bordado físicos.
 */
class DesktopStrokeFlowPlannerTest {
    @Test
    fun `segmentos desconectados geram ida de underlay e volta satin por segmento`() {
        val width = 85
        val height = 70
        val mask = BooleanArray(width * height)
        for (y in 12..58) {
            for (x in 11..18) mask[y * width + x] = true
            for (x in 45..52) mask[y * width + x] = true
        }
        val lines = DesktopStrokeSkeleton.analyze(mask, width, height)
        val snapshot = lines.map { it.toList() }
        val plan = DesktopStrokeFlowPlanner.plan(lines)
        assertTrue(plan.size >= 4)
        assertEquals(plan.size, plan.distinctBy {
            it.segmentId to it.phase
        }.size)
        assertTrue(plan.chunked(2).all { pair ->
            pair.size == 2 &&
                pair[0].segmentId == pair[1].segmentId &&
                pair[0].phase == "CENTER_RUN" &&
                pair[1].phase == "SATIN_RETURN" &&
                pair[0].from == pair[1].to &&
                pair[0].to == pair[1].from
        }, "Cada segmento precisa costurar ida/volta na ordem Android.")
        assertEquals(snapshot, lines)
        assertEquals(plan, DesktopStrokeFlowPlanner.plan(lines))
    }

    @Test
    fun `ciclo inicial preserva cada aresta sem escolher ponto fora do contorno`() {
        val perimeter = buildList {
            for (x in 10..30) add(DesktopSkeletonPoint(x, 10))
            for (y in 11..30) add(DesktopSkeletonPoint(30, y))
            for (x in 29 downTo 10) add(DesktopSkeletonPoint(x, 30))
            for (y in 29 downTo 11) add(DesktopSkeletonPoint(10, y))
            add(DesktopSkeletonPoint(10, 10))
        }
        val plan = DesktopStrokeFlowPlanner.plan(listOf(perimeter), preferInitialStroke = true)
        assertEquals(2, plan.size)
        assertEquals("CENTER_RUN", plan[0].phase)
        assertEquals("SATIN_RETURN", plan[1].phase)
        assertEquals(plan[0].from, plan[1].to)
        assertEquals(plan[0].to, plan[1].from)
        val approvedPoints = perimeter.toSet()
        assertTrue(plan.flatMap { it.geometry }.all { it in approvedPoints })
        assertEquals(plan[0].geometry.size, perimeter.size)
    }

    @Test
    fun `entrada invalida ou complexidade excessiva rejeitadas`() {
        assertFailsWith<IllegalArgumentException> {
            DesktopStrokeFlowPlanner.plan(emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopStrokeFlowPlanner.plan(
                listOf(listOf(DesktopSkeletonPoint(-1, 1), DesktopSkeletonPoint(3, 3)))
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopStrokeFlowPlanner.plan(List(257) {
                listOf(DesktopSkeletonPoint(2, 2), DesktopSkeletonPoint(3, 3))
            })
        }
    }

    @Test
    fun `funcoes de planejador sao extraidas literalmente do Android`() {
        val root = Path.of("").toAbsolutePath().normalize()
        val android = Files.readString(root.resolve(
            "../app/src/main/java/com/timachado/brothermatrizes/font/" +
                "ImportedFontMatrixGenerator.kt"
        )).replace("\r\n", "\n")
        val output = Files.readString(root.resolve(
            "build/generated/stroke-planner/kotlin/" +
                "com/timachado/brothermatrizes/desktop/platform/DesktopStrokeFlowPlanner.kt"
        ))
        val pairs = listOf(
            "    private data class SkeletonPoint(" to
                "    private fun buildStrokeFlowSatinText(",
            "    private fun strokeFlowNodeKey(" to
                "    private fun buildStrokeFlowRows(",
            "    private fun pointDistance(" to
                "    private fun appendSplitStitch(",
            "    private fun skeletonLineLength(" to
                "    private fun smoothAndResampleSkeleton("
        )
        pairs.forEach { (begin, end) ->
            val start = android.indexOf(begin)
            val finish = android.indexOf(end, start)
            assertTrue(start > 0 && finish > start)
            assertTrue(output.contains(android.substring(start, finish)),
                "A versão Windows não deve divergir dos métodos de costura Android.")
        }
    }
}
