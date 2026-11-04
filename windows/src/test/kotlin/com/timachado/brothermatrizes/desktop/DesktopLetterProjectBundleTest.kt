package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.EditTransform
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopLetterProjectBundleTest {
    private fun layout() = DesktopLetterComposer.update(
        DesktopLetterComposer.start("BOM", 12f, TextStitchStyle.RUNNING),
        1, 4.5f, -2f, 1.2f, 18f
    )

    @Test
    fun `salva FLP v11 intacto mais sidecar e recupera objetos editaveis`() {
        val folder = Files.createTempDirectory("bm-layer-bundle-roundtrip-")
        val flp = folder.resolve("letras.flp")
        val originalLayout = layout()
        val original = DesktopLetterComposer.render(originalLayout)
        val sidecar = DesktopLetterProjectBundle.companion(flp)
        try {
            val saved = DesktopLetterProjectBundle.saveNew(originalLayout, original, flp)
            assertEquals(flp, saved.first)
            assertEquals(sidecar, saved.second)
            assertTrue(Files.size(sidecar) in 1..4096)
            // Continua sendo FLP oficial, aberto independentemente do sidecar.
            val legacy = DesktopProjectService.open(flp)
            assertEquals(original.current.points, legacy.current.points)
            assertEquals(original.current.threadColors, legacy.current.threadColors)

            val reloaded = DesktopLetterProjectBundle.open(flp)
            assertEquals(originalLayout, reloaded.session.current)
            assertFalse(reloaded.session.canUndo)
            assertEquals(original.current.points, reloaded.document.current.points)
            val changed = DesktopLetterComposer.update(
                reloaded.session.current, 2, 8f, 0f, 1.1f, -15f
            )
            val rendered = DesktopLetterComposer.render(changed)
            assertTrue(rendered.current.points != original.current.points)

            // Sidecar opcional: removê-lo não corrompe a leitura FLP.
            Files.delete(sidecar)
            assertEquals(original.current.points, DesktopProjectService.open(flp).current.points)
            assertFailsWith<IllegalArgumentException> { DesktopLetterProjectBundle.open(flp) }
        } finally {
            Files.deleteIfExists(sidecar)
            Files.deleteIfExists(flp)
            Files.deleteIfExists(folder)
        }
    }

    @Test
    fun `extensao FLP maiuscula preserva nome base do sidecar e reabre camadas`() {
        val folder = Files.createTempDirectory("bm-layer-bundle-uppercase-")
        val flp = folder.resolve("Nome.FLP")
        val sidecar = folder.resolve("Nome.bmlayers")
        val params = layout()
        val doc = DesktopLetterComposer.render(params)
        try {
            assertEquals(sidecar, DesktopLetterProjectBundle.companion(flp))
            val saved = DesktopLetterProjectBundle.saveNew(params, doc, flp)
            assertEquals(sidecar, saved.second)
            assertEquals(params, DesktopLetterProjectBundle.open(flp).session.current)
            assertEquals(doc.current.points, DesktopProjectService.open(flp).current.points)
        } finally {
            Files.deleteIfExists(sidecar)
            Files.deleteIfExists(flp)
            Files.deleteIfExists(folder)
        }
    }

    @Test
    fun `recusa sobrescrita de FLP ou sidecar existente sem apagar dados`() {
        val folder = Files.createTempDirectory("bm-layer-bundle-noswap-")
        val flp = folder.resolve("projeto.flp")
        val sidecar = DesktopLetterProjectBundle.companion(flp)
        val params = layout()
        val doc = DesktopLetterComposer.render(params)
        try {
            Files.writeString(sidecar, "sensitive-existing-file")
            assertFailsWith<IllegalArgumentException> {
                DesktopLetterProjectBundle.saveNew(params, doc, flp)
            }
            assertFalse(Files.exists(flp))
            assertEquals("sensitive-existing-file", Files.readString(sidecar))
            Files.delete(sidecar)
            DesktopLetterProjectBundle.saveNew(params, doc, flp)
            val beforeFlp = Files.readAllBytes(flp)
            val beforeLayer = Files.readAllBytes(sidecar)
            assertFailsWith<IllegalArgumentException> {
                DesktopLetterProjectBundle.saveNew(params, doc, flp)
            }
            assertTrue(beforeFlp.contentEquals(Files.readAllBytes(flp)))
            assertTrue(beforeLayer.contentEquals(Files.readAllBytes(sidecar)))
        } finally {
            Files.deleteIfExists(sidecar)
            Files.deleteIfExists(flp)
            Files.deleteIfExists(folder)
        }
    }

    @Test
    fun `detecta FLP ou sidecar adulterados sem regravar projeto`() {
        val folder = Files.createTempDirectory("bm-layer-bundle-damage-")
        val flp = folder.resolve("projeto.flp")
        val sidecar = DesktopLetterProjectBundle.companion(flp)
        val params = layout()
        val doc = DesktopLetterComposer.render(params)
        try {
            DesktopLetterProjectBundle.saveNew(params, doc, flp)
            val originalFlp = Files.readAllBytes(flp)
            val originalCompanion = Files.readAllBytes(sidecar)
            val modifiedFlp = originalFlp.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
            Files.write(flp, modifiedFlp)
            assertFailsWith<IllegalArgumentException> { DesktopLetterProjectBundle.open(flp) }
            assertTrue(modifiedFlp.contentEquals(Files.readAllBytes(flp)))
            Files.write(flp, originalFlp)

            Files.write(sidecar, originalCompanion.copyOfRange(0, 15))
            assertFailsWith<Exception> { DesktopLetterProjectBundle.open(flp) }
            Files.write(sidecar, originalCompanion + byteArrayOf(17))
            assertFailsWith<IllegalArgumentException> { DesktopLetterProjectBundle.open(flp) }
            Files.write(sidecar, originalCompanion)
            assertEquals(params, DesktopLetterProjectBundle.open(flp).session.current)
        } finally {
            Files.deleteIfExists(sidecar)
            Files.deleteIfExists(flp)
            Files.deleteIfExists(folder)
        }
    }

    @Test
    fun `recusa sidecar excessivo e preserva FLP original`() {
        val folder = Files.createTempDirectory("bm-layer-bundle-size-")
        val flp = folder.resolve("camadas.flp")
        val sidecar = DesktopLetterProjectBundle.companion(flp)
        val params = layout()
        try {
            DesktopLetterProjectBundle.saveNew(
                params, DesktopLetterComposer.render(params), flp
            )
            val unchangedFlp = Files.readAllBytes(flp)
            Files.write(sidecar, ByteArray(4097) { 7 })
            assertFailsWith<IllegalArgumentException> {
                DesktopLetterProjectBundle.open(flp)
            }
            assertTrue(unchangedFlp.contentEquals(Files.readAllBytes(flp)))
            assertEquals(4097L, Files.size(sidecar))
        } finally {
            Files.deleteIfExists(sidecar)
            Files.deleteIfExists(flp)
            Files.deleteIfExists(folder)
        }
    }

    @Test
    fun `bloqueia associar camadas a projeto com alteracoes globais`() {
        val folder = Files.createTempDirectory("bm-layer-bundle-global-")
        val flp = folder.resolve("editado.flp")
        val params = layout()
        val base = DesktopLetterComposer.render(params)
        val globallyEdited = DesktopMatrixService.edit(base, EditTransform(offsetXUnits = 40))
        try {
            assertFailsWith<IllegalArgumentException> {
                DesktopLetterProjectBundle.saveNew(params, globallyEdited, flp)
            }
            assertFalse(Files.exists(flp))
            assertFalse(Files.exists(DesktopLetterProjectBundle.companion(flp)))
        } finally {
            Files.deleteIfExists(flp)
            Files.deleteIfExists(folder)
        }
    }

    @Test
    fun `mantem FLP abrivel por codec oficial após gravação de bundle`() {
        val folder = Files.createTempDirectory("bm-layer-bundle-codec-")
        val flp = folder.resolve("nome.flp")
        val sidecar = DesktopLetterProjectBundle.companion(flp)
        val original = DesktopLetterComposer.render(layout())
        try {
            DesktopLetterProjectBundle.saveNew(layout(), original, flp)
            val originalBytes = Files.readAllBytes(flp)
            val officialBytes = DesktopProjectService.encode(original)
            assertTrue(originalBytes.contentEquals(officialBytes))
            assertEquals(original.current.points, DesktopProjectService.decode(originalBytes).points)
        } finally {
            Files.deleteIfExists(sidecar)
            Files.deleteIfExists(flp)
            Files.deleteIfExists(folder)
        }
    }
}
