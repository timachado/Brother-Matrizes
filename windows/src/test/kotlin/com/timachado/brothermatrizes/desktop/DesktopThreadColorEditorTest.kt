package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.EmbroideryBounds
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.EmbroideryPoint
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopThreadColorEditorTest {
    private fun name() = DesktopMatrixService.createName("QA", 12f)

    @Test
    fun `aceita hex RGB e rejeita caracteres e canais alfa`() {
        assertEquals(0x457B9D, DesktopThreadColorEditor.parseHex("#457b9d"))
        assertEquals(0xAABBCC, DesktopThreadColorEditor.parseHex(" AaBbCc "))
        assertEquals("#0044AA", DesktopThreadColorEditor.hex(0x0044AA))
        for (invalid in listOf("", "#FFF", "#12345678", "12345G", "red", "#-11111")) {
            assertFailsWith<IllegalArgumentException> {
                DesktopThreadColorEditor.parseHex(invalid)
            }
        }
    }

    @Test
    fun `recolorir preserva pontos, origem, limites e comandos`() {
        val base = name()
        val beforePoints = base.current.points
        val beforeBounds = base.current.bounds
        val recolored = DesktopThreadColorEditor.recolor(base, 0, 0x224466)
        assertEquals(0x224466, recolored.current.threadColors[0])
        assertEquals(beforePoints, recolored.current.points)
        assertEquals(beforeBounds, recolored.current.bounds)
        assertEquals(base.original, recolored.original)
        assertTrue(recolored.current.isModified)
        assertEquals(base.current.points, base.original.points)
    }

    @Test
    fun `desfazer refazer de cor conserva edicao geometrica anterior`() {
        val base = name()
        val shift = com.timachado.brothermatrizes.core.embroidery.EditTransform(offsetXUnits = 20)
        val moved = DesktopMatrixService.edit(base, shift)
        val recolored = DesktopThreadColorEditor.recolor(moved, 0, 0x551199)
        val timeline = DesktopEditTimeline.start(base)
            .record(DesktopEditAction.Transform(shift))
            .record(DesktopEditAction.RecolorThread(0, 0x551199))
        val undo = timeline.undo()
        assertEquals(moved.current.points, undo.document.current.points)
        assertEquals(moved.current.threadColors, undo.document.current.threadColors)
        val redo = undo.timeline.redo()
        assertEquals(recolored.current.points, redo.document.current.points)
        assertEquals(recolored.current.threadColors, redo.document.current.threadColors)
        assertFalse(redo.timeline.canRedo)
    }

    @Test
    fun `rejeita numero de cor inexistente sem alterar original`() {
        val base = name()
        val count = DesktopThreadColorEditor.palette(base.current).size
        assertFailsWith<IllegalArgumentException> {
            DesktopThreadColorEditor.recolor(base, -1, 0x112233)
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopThreadColorEditor.recolor(base, count, 0x112233)
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopThreadColorEditor.recolor(base, 0, -1)
        }
        assertEquals(base.original, base.current)
    }

    @Test
    fun `recusa paleta excessiva`() {
        val base = name()
        val excessive = base.current.copy(threadColors = List(65) { 0x123456 })
        assertFailsWith<IllegalArgumentException> {
            DesktopThreadColorEditor.palette(excessive)
        }
    }

    @Test
    fun `cores persistem no projeto FLP novo e nao substituem arquivo existente`() {
        val base = name()
        val colored = DesktopThreadColorEditor.recolor(base, 0, 0x1A7A4B)
        val dir = Files.createTempDirectory("brother-threadcolor-qa-")
        val file = dir.resolve("nova-cor.flp")
        try {
            DesktopProjectService.saveNew(colored, file)
            val loaded = DesktopProjectService.open(file)
            assertEquals(colored.current.threadColors, loaded.current.threadColors)
            assertEquals(colored.current.points, loaded.current.points)
            assertFailsWith<Exception> {
                DesktopProjectService.saveNew(colored, file)
            }
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(dir)
        }
    }
}
