package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.core.embroidery.VectorArtwork
import com.timachado.brothermatrizes.core.embroidery.VectorPath
import com.timachado.brothermatrizes.core.embroidery.VectorPoint
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopVectorNodesRoundtripTest {
    @Test
    fun `editar nos e reordenar caminhos sobrevivem a reabertura de dois pares FLP`() {
        val original = DesktopImportedSvg(
            "tracos.svg",
            VectorArtwork(
                label = "Nós SVG Windows",
                paths = listOf(
                    VectorPath(
                        listOf(
                            VectorPoint(0f, 0f), VectorPoint(100f, 0f),
                            VectorPoint(100f, 70f), VectorPoint(0f, 70f)
                        ), closed = true
                    ),
                    VectorPath(
                        listOf(
                            VectorPoint(15f, 15f), VectorPoint(55f, 40f),
                            VectorPoint(70f, 60f)
                        )
                    )
                )
            )
        )
        val inserted = DesktopSvgNodeEditor.apply(
            original.artwork, 1, 0, DesktopSvgNodeAction.INSERT_AFTER
        )
        val moved = DesktopSvgNodeEditor.apply(
            inserted.artwork, 1, inserted.selectedNodeIndex,
            DesktopSvgNodeAction.RIGHT
        )
        val reordered = DesktopSvgObjectCollection.reorder(moved.artwork, 1, -1)
        assertEquals(0, reordered.selectedIndex)
        assertEquals(moved.artwork.paths[1], reordered.artwork.paths[0])
        assertEquals(original.artwork.paths[0], reordered.artwork.paths[1])
        val session = DesktopSvgEditSession.start(original.artwork)
            .record(inserted.artwork).record(moved.artwork).record(reordered.artwork)
        assertEquals(moved.artwork, session.undo().current)

        val root = Files.createTempDirectory("brother-svg-node-project-")
        val firstFlp = root.resolve("nos-1.flp")
        val secondFlp = root.resolve("nos-2.flp")
        val firstAux = DesktopVectorProjectBundle.companion(firstFlp)
        val secondAux = DesktopVectorProjectBundle.companion(secondFlp)
        try {
            DesktopVectorProjectBundle.saveNew(
                original.copy(artwork = session.current),
                42f, TextStitchStyle.RUNNING,
                HoopProfile.H100X100, firstFlp, rgb = 0x3355AA
            )
            val firstFlpBytes = Files.readAllBytes(firstFlp)
            val firstAuxBytes = Files.readAllBytes(firstAux)
            val opened = DesktopVectorProjectBundle.open(firstFlp)
            assertEquals(reordered.artwork, opened.session.current)
            assertEquals(0x3355AA, opened.rgb)
            assertFalse(opened.session.canUndo)
            assertEquals(
                DesktopProjectService.open(firstFlp).current.points,
                opened.document.current.points
            )
            val deletedNode = DesktopSvgNodeEditor.apply(
                opened.session.current, 0, 1, DesktopSvgNodeAction.DELETE
            )
            assertEquals(original.artwork.paths[1].points.size,
                deletedNode.artwork.paths[0].points.size)
            DesktopVectorProjectBundle.saveNew(
                opened.imported.copy(artwork = deletedNode.artwork),
                opened.widthMm, opened.style, opened.hoop,
                secondFlp, opened.rgb
            )
            val again = DesktopVectorProjectBundle.open(secondFlp)
            assertEquals(deletedNode.artwork, again.session.current)
            assertEquals(
                DesktopProjectService.open(secondFlp).current.points,
                again.document.current.points
            )
            // Ambos novos pares são independentes; salvar outro não sobrescreve.
            assertTrue(firstFlpBytes.contentEquals(Files.readAllBytes(firstFlp)))
            assertTrue(firstAuxBytes.contentEquals(Files.readAllBytes(firstAux)))
        } finally {
            Files.deleteIfExists(secondAux)
            Files.deleteIfExists(secondFlp)
            Files.deleteIfExists(firstAux)
            Files.deleteIfExists(firstFlp)
            Files.deleteIfExists(root)
        }
    }
}
