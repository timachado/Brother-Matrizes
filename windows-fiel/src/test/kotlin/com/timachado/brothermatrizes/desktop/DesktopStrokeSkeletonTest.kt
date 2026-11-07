package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopStrokeSkeleton
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Etapa principal stroke-flow: usa o thinMask/traceSkeleton literalmente
 * do Android oficial. Não implica matriz comercial pronta.
 */
class DesktopStrokeSkeletonTest {
    private fun twoBars(width: Int = 64, height: Int = 60): BooleanArray {
        val mask = BooleanArray(width * height)
        for (y in 8..48) {
            for (x in 8..15) mask[y * width + x] = true
            for (x in 38..47) mask[y * width + x] = true
        }
        return mask
    }

    @Test
    fun `duas colunas de satin distintas mantêm duas linhas de esqueleto`() {
        val input = twoBars()
        val initial = input.copyOf()
        val traced = DesktopStrokeSkeleton.analyze(input, 64, 60)
        assertTrue(traced.size >= 2,
            "As duas colunas isoladas devem gerar linhas independentes.")
        assertTrue(traced.any { line -> line.any { it.x in 8..15 } })
        assertTrue(traced.any { line -> line.any { it.x in 38..47 } })
        assertTrue(traced.all { line ->
            line.size >= 2 && line.all { it.x in 1..62 && it.y in 1..58 }
        })
        assertTrue(traced.all { line ->
            line.zipWithNext().all { (a, b) ->
                abs(a.x - b.x) <= 1 && abs(a.y - b.y) <= 1
            }
        })
        assertContentEquals(initial, input,
            "Afinamento original não pode alterar a máscara de entrada.")
        assertEquals(traced, DesktopStrokeSkeleton.analyze(input, 64, 60),
            "O algoritmo de bordado precisa gerar esqueleto determinístico.")
    }

    @Test
    fun `anel mantém topologia não preenchida no centro`() {
        val w = 70
        val h = 70
        val ring = BooleanArray(w*h)
        for (y in 10..58) for (x in 10..58) {
            if (x !in 22..46 || y !in 22..46) ring[y*w+x] = true
        }
        val traced = DesktopStrokeSkeleton.analyze(ring, w, h)
        assertTrue(traced.isNotEmpty())
        assertTrue(traced.flatten().any { it.x < 22 })
        assertTrue(traced.flatten().any { it.x > 46 })
        assertTrue(traced.flatten().all { p ->
            p.x !in 23..45 || p.y !in 23..45
        }, "Afinamento não pode preencher o vazado interior.")
    }

    @Test
    fun `rejeita máscara vazia dimensões falsas bordas preenchidas e estouro`() {
        val valid = twoBars()
        assertFailsWith<IllegalArgumentException> {
            DesktopStrokeSkeleton.analyze(valid, 64, 59)
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopStrokeSkeleton.analyze(BooleanArray(64*60), 64, 60)
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopStrokeSkeleton.analyze(BooleanArray(64*60) { it == 0 }, 64, 60)
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopStrokeSkeleton.analyze(valid, 1_800, 1_800)
        }
    }

    @Test
    fun `thinning e trace devem permanecer literalmente idênticos à fonte Android`() {
        val root = Path.of("").toAbsolutePath().normalize()
        val original = Files.readString(root.resolve(
            "../app/src/main/java/com/timachado/brothermatrizes/" +
                "font/ImportedFontMatrixGenerator.kt"
        )).replace("\r\n", "\n")
        val start = original.indexOf("    private fun thinMask(")
        val end = original.indexOf("    private fun orientSkeletonLine(", start)
        assertTrue(start > 0 && end > start)
        val exact = original.substring(start, end)
        val generated = Files.readString(root.resolve(
            "build/generated/stroke-skeleton/kotlin/" +
                "com/timachado/brothermatrizes/desktop/platform/DesktopStrokeSkeleton.kt"
        ))
        assertTrue(generated.contains(exact),
            "O algoritmo de esqueleto foi reescrito e não corresponde ao Android.")
    }
}
