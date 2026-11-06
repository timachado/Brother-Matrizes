package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.core.embroidery.VectorPoint
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopFreehandProjectBundleTest {
    private val strokes = listOf(
        listOf(
            VectorPoint(0.1f, 0.1f), VectorPoint(0.9f, 0.1f),
            VectorPoint(0.9f, 0.9f), VectorPoint(0.1f, 0.9f),
            VectorPoint(0.1f, 0.1f)
        ),
        listOf(VectorPoint(0.2f, 0.2f), VectorPoint(0.6f, 0.7f))
    )
    private val hoop = HoopProfile.H100X100
    private val style = TextStitchStyle.RUNNING

    @Test
    fun `cor RGB sobrevive ao salvamento e abertura do projeto desenhado`() {
        val folder = Files.createTempDirectory("brother-draw-color-")
        val flp = folder.resolve("cores.flp")
        val aux = DesktopFreehandProjectBundle.companion(flp)
        try {
            val rgb = 0x2A9D8F
            DesktopFreehandProjectBundle.saveNew(
                strokes, 40f, style, hoop, flp, rgb
            )
            val opened = DesktopFreehandProjectBundle.open(flp)
            assertEquals(rgb, opened.rgb)
            assertEquals(rgb, opened.document.current.threadColors.first())
            assertEquals(
                DesktopProjectService.open(flp).current.threadColors,
                opened.document.current.threadColors
            )
        } finally {
            Files.deleteIfExists(aux)
            Files.deleteIfExists(flp)
            Files.deleteIfExists(folder)
        }
    }

    @Test
    fun `recupera traços reais e FLP oficial sem modificar pontadas`() {
        val folder = Files.createTempDirectory("brother-draw-roundtrip-")
        val flp = folder.resolve("desenho.FLP")
        val sidecar = folder.resolve("desenho.bmdraw")
        try {
            assertEquals(sidecar, DesktopFreehandProjectBundle.companion(flp))
            val output = DesktopFreehandProjectBundle.saveNew(
                strokes, 40f, style, hoop, flp
            )
            assertEquals(flp to sidecar, output)
            val original = DesktopProjectService.open(flp).current
            val project = DesktopFreehandProjectBundle.open(flp)
            assertEquals(strokes, project.strokes)
            assertEquals(40f, project.widthMm)
            assertEquals(style, project.style)
            assertEquals(hoop, project.hoop)
            assertEquals(original.points, project.document.current.points)
            assertEquals(original.threadColors, project.document.current.threadColors)
            assertTrue(project.document.current.endFound)
            // Desenho fica reeditável no canvas após recuperar.
            val changed = project.strokes.dropLast(1)
            assertEquals(1, changed.size)
            assertTrue(changed != project.strokes)
            assertEquals(original.points, DesktopProjectService.open(flp).current.points)
        } finally {
            Files.deleteIfExists(sidecar)
            Files.deleteIfExists(flp)
            Files.deleteIfExists(folder)
        }
    }

    @Test
    fun `FLP sem acompanhante permanece legivel mas sem traços editaveis`() {
        val folder = Files.createTempDirectory("brother-draw-optional-")
        val flp = folder.resolve("desenho.flp")
        val aux = DesktopFreehandProjectBundle.companion(flp)
        try {
            DesktopFreehandProjectBundle.saveNew(strokes, 40f, style, hoop, flp)
            val saved = DesktopProjectService.open(flp).current.points
            Files.delete(aux)
            assertEquals(saved, DesktopProjectService.open(flp).current.points)
            assertFailsWith<IllegalArgumentException> {
                DesktopFreehandProjectBundle.open(flp)
            }
        } finally {
            Files.deleteIfExists(aux)
            Files.deleteIfExists(flp)
            Files.deleteIfExists(folder)
        }
    }

    @Test
    fun `proibe sobrescrever projetos e seus arquivos auxiliares`() {
        val folder = Files.createTempDirectory("brother-draw-noswap-")
        val flp = folder.resolve("desenho.flp")
        val aux = DesktopFreehandProjectBundle.companion(flp)
        try {
            Files.writeString(aux, "nao-apagar")
            assertFailsWith<IllegalArgumentException> {
                DesktopFreehandProjectBundle.saveNew(strokes, 30f, style, hoop, flp)
            }
            assertFalse(Files.exists(flp))
            assertEquals("nao-apagar", Files.readString(aux))
            Files.delete(aux)
            DesktopFreehandProjectBundle.saveNew(strokes, 30f, style, hoop, flp)
            val beforeFlp = Files.readAllBytes(flp)
            val beforeSidecar = Files.readAllBytes(aux)
            assertFailsWith<IllegalArgumentException> {
                DesktopFreehandProjectBundle.saveNew(strokes, 30f, style, hoop, flp)
            }
            assertTrue(beforeFlp.contentEquals(Files.readAllBytes(flp)))
            assertTrue(beforeSidecar.contentEquals(Files.readAllBytes(aux)))
        } finally {
            Files.deleteIfExists(aux)
            Files.deleteIfExists(flp)
            Files.deleteIfExists(folder)
        }
    }

    @Test
    fun `detecta adulteracao dos bytes de FLP e arquivo de desenho`() {
        val folder = Files.createTempDirectory("brother-draw-integrity-")
        val flp = folder.resolve("desenho.flp")
        val aux = DesktopFreehandProjectBundle.companion(flp)
        try {
            DesktopFreehandProjectBundle.saveNew(strokes, 40f, style, hoop, flp)
            val flpOk = Files.readAllBytes(flp)
            val auxOk = Files.readAllBytes(aux)
            val corrupted = flpOk.copyOf().also {
                it[it.lastIndex] = (it.last().toInt() xor 1).toByte()
            }
            Files.write(flp, corrupted)
            assertFailsWith<IllegalArgumentException> {
                DesktopFreehandProjectBundle.open(flp)
            }
            assertTrue(corrupted.contentEquals(Files.readAllBytes(flp)))
            Files.write(flp, flpOk)
            Files.write(aux, auxOk.copyOfRange(0, 16))
            assertFailsWith<Exception> {
                DesktopFreehandProjectBundle.open(flp)
            }
            Files.write(aux, auxOk + byteArrayOf(55))
            assertFailsWith<IllegalArgumentException> {
                DesktopFreehandProjectBundle.open(flp)
            }
            Files.write(aux, auxOk)
            assertEquals(strokes, DesktopFreehandProjectBundle.open(flp).strokes)
        } finally {
            Files.deleteIfExists(aux)
            Files.deleteIfExists(flp)
            Files.deleteIfExists(folder)
        }
    }

    @Test
    fun `aplica limites de tamanho pontos geometria e bastidor`() {
        val folder = Files.createTempDirectory("brother-draw-limits-")
        val flp = folder.resolve("desenho.flp")
        try {
            assertFailsWith<IllegalArgumentException> {
                DesktopFreehandProjectBundle.saveNew(
                    emptyList(), 40f, style, hoop, flp
                )
            }
            assertFailsWith<IllegalArgumentException> {
                DesktopFreehandProjectBundle.saveNew(
                    strokes, Float.NaN, style, hoop, flp
                )
            }
            assertFailsWith<IllegalArgumentException> {
                DesktopFreehandProjectBundle.saveNew(
                    strokes, 140f, style, hoop, flp
                )
            }
            assertFalse(Files.exists(flp))
        } finally {
            Files.deleteIfExists(flp)
            Files.deleteIfExists(folder)
        }
    }
}
