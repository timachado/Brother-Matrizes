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

class DesktopVectorCollectionRoundtripTest {
    @Test
    fun `duplicar e excluir caminhos preserva FLP oficial nos dois ciclos de reabertura`() {
        val source = DesktopImportedSvg(
            "objetos.svg",
            VectorArtwork(
                label = "Objetos editaveis QA",
                paths = listOf(
                    VectorPath(
                        listOf(
                            VectorPoint(0f, 0f), VectorPoint(95f, 0f),
                            VectorPoint(95f, 65f), VectorPoint(0f, 65f)
                        ),
                        closed = true
                    ),
                    VectorPath(
                        listOf(
                            VectorPoint(30f, 15f), VectorPoint(65f, 15f),
                            VectorPoint(65f, 45f), VectorPoint(30f, 45f)
                        ),
                        closed = true
                    )
                )
            )
        )
        val duplicated = DesktopSvgObjectCollection.duplicate(source.artwork, 1)
        assertEquals(2, duplicated.selectedIndex)
        assertEquals(3, duplicated.artwork.paths.size)

        val folder = Files.createTempDirectory("brother-vector-collection-")
        val duplicatedFlp = folder.resolve("duplicado.flp")
        val editedFlp = folder.resolve("excluido.flp")
        val duplicatedAux = DesktopVectorProjectBundle.companion(duplicatedFlp)
        val editedAux = DesktopVectorProjectBundle.companion(editedFlp)
        val width = 45f
        val style = TextStitchStyle.RUNNING
        val hoop = HoopProfile.H100X100
        try {
            DesktopVectorProjectBundle.saveNew(
                source.copy(artwork = duplicated.artwork),
                width, style, hoop, duplicatedFlp, rgb = 0x598FC2
            )
            val firstFlpBytes = Files.readAllBytes(duplicatedFlp)
            val firstAuxBytes = Files.readAllBytes(duplicatedAux)
            val reopened = DesktopVectorProjectBundle.open(duplicatedFlp)
            assertEquals(duplicated.artwork, reopened.session.current)
            assertEquals(0x598FC2, reopened.rgb)
            assertEquals(
                DesktopProjectService.open(duplicatedFlp).current.points,
                reopened.document.current.points
            )
            assertFalse(reopened.session.canUndo) // sessão anterior não é serializada

            val deleted = DesktopSvgObjectCollection.delete(
                reopened.session.current, duplicated.selectedIndex
            )
            assertEquals(source.artwork, deleted.artwork)
            val session = reopened.session.record(deleted.artwork)
            assertTrue(session.canUndo)
            assertEquals(duplicated.artwork, session.undo().current)
            assertEquals(source.artwork, session.current)

            DesktopVectorProjectBundle.saveNew(
                reopened.imported.copy(artwork = session.current),
                reopened.widthMm, reopened.style, reopened.hoop,
                editedFlp, rgb = reopened.rgb
            )
            val reopenedAgain = DesktopVectorProjectBundle.open(editedFlp)
            assertEquals(source.artwork, reopenedAgain.session.current)
            assertEquals(source.artwork.paths.size, reopenedAgain.session.current.paths.size)
            assertEquals(
                DesktopProjectService.open(editedFlp).current.points,
                reopenedAgain.document.current.points
            )
            // Nunca sobrescrever os projetos pré-existentes da cliente.
            assertTrue(firstFlpBytes.contentEquals(Files.readAllBytes(duplicatedFlp)))
            assertTrue(firstAuxBytes.contentEquals(Files.readAllBytes(duplicatedAux)))
        } finally {
            Files.deleteIfExists(editedAux)
            Files.deleteIfExists(editedFlp)
            Files.deleteIfExists(duplicatedAux)
            Files.deleteIfExists(duplicatedFlp)
            Files.deleteIfExists(folder)
        }
    }
}
