package com.timachado.brothermatrizes.desktop

import androidx.compose.ui.geometry.Offset
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.core.embroidery.VectorArtwork
import com.timachado.brothermatrizes.core.embroidery.VectorPath
import com.timachado.brothermatrizes.core.embroidery.VectorPoint
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopVectorProjectBundleTest {
    private val shape = DesktopImportedSvg(
        "desenho.svg",
        VectorArtwork(
            label = "Vetores QA",
            paths = listOf(
                VectorPath(listOf(
                    VectorPoint(0f, 0f), VectorPoint(100f, 0f),
                    VectorPoint(100f, 100f), VectorPoint(0f, 100f),
                    VectorPoint(0f, 0f)
                )),
                VectorPath(listOf(
                    VectorPoint(20f, 20f), VectorPoint(80f, 80f)
                ))
            )
        )
    )
    private val style = TextStitchStyle.RUNNING
    private val hoop = HoopProfile.H100X100

    @Test
    fun `roundtrip FLP oficial e vetores reeditaveis preserva caminhos e parametros`() {
        val root = Files.createTempDirectory("brother-vector-roundtrip-")
        val flp = root.resolve("vetores.FLP")
        val sidecar = root.resolve("vetores.bmvector")
        try {
            val moved = DesktopSvgPathEditor.move(
                shape.artwork, 1, 440f, 320f, Offset(18f, 4f)
            )
            val edited = shape.copy(artwork = moved)
            assertEquals(sidecar, DesktopVectorProjectBundle.companion(flp))
            assertEquals(
                flp to sidecar,
                DesktopVectorProjectBundle.saveNew(edited, 40f, style, hoop, flp)
            )
            assertTrue(Files.size(sidecar) in 1..DesktopVectorProjectBundle.MAX_COMPANION_BYTES)
            val normal = DesktopProjectService.open(flp)
            val expected = DesktopDrawingService.generateSvg(edited, 40f, style, hoop)
            assertEquals(expected.current.points, normal.current.points)
            assertEquals(expected.current.threadColors, normal.current.threadColors)

            val opened = DesktopVectorProjectBundle.open(flp)
            assertEquals(edited, opened.imported)
            assertEquals(edited.artwork, opened.session.current)
            assertFalse(opened.session.canUndo)
            assertEquals(40f, opened.widthMm)
            assertEquals(style, opened.style)
            assertEquals(hoop, opened.hoop)
            assertEquals(expected.current.points, opened.document.current.points)

            val after = DesktopSvgPathEditor.move(
                opened.session.current, 0, 440f, 320f, Offset(-15f, 8f)
            )
            assertTrue(opened.session.record(after).canUndo)
            // A reedição altera a composição de vetores sem sobrescrever FLP.
            assertTrue(after != edited.artwork)
        } finally {
            Files.deleteIfExists(sidecar)
            Files.deleteIfExists(flp)
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun `FLP isolado abre mas sem sidecar nao ha reabertura de vetores`() {
        val root = Files.createTempDirectory("brother-vector-optional-")
        val flp = root.resolve("vetores.flp")
        val aux = DesktopVectorProjectBundle.companion(flp)
        try {
            DesktopVectorProjectBundle.saveNew(shape, 35f, style, hoop, flp)
            val before = DesktopProjectService.open(flp).current.points
            Files.delete(aux)
            assertEquals(before, DesktopProjectService.open(flp).current.points)
            assertFailsWith<IllegalArgumentException> {
                DesktopVectorProjectBundle.open(flp)
            }
        } finally {
            Files.deleteIfExists(aux)
            Files.deleteIfExists(flp)
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun `recusa sobrescrever qualquer arquivo existente`() {
        val root = Files.createTempDirectory("brother-vector-noswap-")
        val flp = root.resolve("projeto.flp")
        val aux = DesktopVectorProjectBundle.companion(flp)
        try {
            Files.writeString(aux, "arquivo existente importante")
            assertFailsWith<IllegalArgumentException> {
                DesktopVectorProjectBundle.saveNew(shape, 35f, style, hoop, flp)
            }
            assertFalse(Files.exists(flp))
            assertEquals("arquivo existente importante", Files.readString(aux))
            Files.delete(aux)
            DesktopVectorProjectBundle.saveNew(shape, 35f, style, hoop, flp)
            val originalFlp = Files.readAllBytes(flp)
            val originalSidecar = Files.readAllBytes(aux)
            assertFailsWith<IllegalArgumentException> {
                DesktopVectorProjectBundle.saveNew(shape, 35f, style, hoop, flp)
            }
            assertTrue(originalFlp.contentEquals(Files.readAllBytes(flp)))
            assertTrue(originalSidecar.contentEquals(Files.readAllBytes(aux)))
        } finally {
            Files.deleteIfExists(aux)
            Files.deleteIfExists(flp)
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun `tampering de FLP e auxiliar bloqueia reabertura sem alterar disco`() {
        val root = Files.createTempDirectory("brother-vector-tamper-")
        val flp = root.resolve("projeto.flp")
        val aux = DesktopVectorProjectBundle.companion(flp)
        try {
            DesktopVectorProjectBundle.saveNew(shape, 35f, style, hoop, flp)
            val goodFlp = Files.readAllBytes(flp)
            val goodAux = Files.readAllBytes(aux)
            val changed = goodFlp.copyOf().also {
                it[it.lastIndex] = (it.last().toInt() xor 1).toByte()
            }
            Files.write(flp, changed)
            assertFailsWith<IllegalArgumentException> {
                DesktopVectorProjectBundle.open(flp)
            }
            assertTrue(changed.contentEquals(Files.readAllBytes(flp)))
            Files.write(flp, goodFlp)

            Files.write(aux, goodAux.copyOfRange(0, 20))
            assertFailsWith<Exception> { DesktopVectorProjectBundle.open(flp) }
            Files.write(aux, goodAux + byteArrayOf(22))
            assertFailsWith<IllegalArgumentException> {
                DesktopVectorProjectBundle.open(flp)
            }
            Files.write(aux, goodAux)
            assertEquals(shape.artwork, DesktopVectorProjectBundle.open(flp).session.current)
        } finally {
            Files.deleteIfExists(aux)
            Files.deleteIfExists(flp)
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun `rejeita carga lateral acima do limite sem danificar FLP`() {
        val root = Files.createTempDirectory("brother-vector-size-")
        val flp = root.resolve("projeto.flp")
        val aux = DesktopVectorProjectBundle.companion(flp)
        try {
            DesktopVectorProjectBundle.saveNew(shape, 35f, style, hoop, flp)
            val before = Files.readAllBytes(flp)
            Files.write(aux, ByteArray(DesktopVectorProjectBundle.MAX_COMPANION_BYTES + 1))
            assertFailsWith<IllegalArgumentException> {
                DesktopVectorProjectBundle.open(flp)
            }
            assertTrue(before.contentEquals(Files.readAllBytes(flp)))
        } finally {
            Files.deleteIfExists(aux)
            Files.deleteIfExists(flp)
            Files.deleteIfExists(root)
        }
    }
}
