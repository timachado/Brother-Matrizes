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

class DesktopVectorTransformRoundtripTest {
    @Test
    fun `escala rotacao e espelhamento sobrevivem a dois ciclos FLP mais bmvector`() {
        val source = DesktopImportedSvg(
            "formas.svg",
            VectorArtwork(
                label = "Projeto QA de formas",
                paths = listOf(
                    VectorPath(
                        points = listOf(
                            VectorPoint(0f, 0f), VectorPoint(100f, 0f),
                            VectorPoint(100f, 70f), VectorPoint(0f, 70f)
                        ),
                        closed = true
                    ),
                    VectorPath(
                        points = listOf(
                            VectorPoint(20f, 20f), VectorPoint(60f, 35f)
                        )
                    )
                )
            )
        )
        val originalPath = source.artwork.paths.first()
        val scaled = DesktopSvgObjectTransform.apply(
            source.artwork, 1, DesktopSvgObjectAction.SCALE_UP
        )
        val rotated = DesktopSvgObjectTransform.apply(
            scaled, 1, DesktopSvgObjectAction.ROTATE_RIGHT
        )
        assertEquals(originalPath, rotated.paths[0])
        val session = DesktopSvgEditSession.start(source.artwork)
            .record(scaled).record(rotated)
        assertTrue(session.canUndo)
        assertEquals(scaled, session.undo().current)
        assertEquals(rotated, session.undo().redo().current)

        val root = Files.createTempDirectory("brother-vector-transform-")
        val firstFlp = root.resolve("vetor-1.flp")
        val nextFlp = root.resolve("vetor-2.flp")
        val firstSidecar = DesktopVectorProjectBundle.companion(firstFlp)
        val nextSidecar = DesktopVectorProjectBundle.companion(nextFlp)
        val width = 45f
        val style = TextStitchStyle.RUNNING
        val hoop = HoopProfile.H100X100
        try {
            val edited = source.copy(artwork = session.current)
            DesktopVectorProjectBundle.saveNew(
                edited, width, style, hoop, firstFlp, 0xAABBCC
            )
            val firstFlpBytes = Files.readAllBytes(firstFlp)
            val firstAuxBytes = Files.readAllBytes(firstSidecar)
            val opened = DesktopVectorProjectBundle.open(firstFlp)
            assertEquals(rotated, opened.session.current)
            assertEquals(0xAABBCC, opened.rgb)
            assertEquals(
                DesktopProjectService.open(firstFlp).current.points,
                opened.document.current.points
            )
            assertFalse(opened.session.canUndo) // histórico fica só na sessão
            val turnedBack = DesktopSvgObjectTransform.apply(
                opened.session.current, 1, DesktopSvgObjectAction.ROTATE_LEFT
            )
            val mirroredH = DesktopSvgObjectTransform.apply(
                turnedBack, 1, DesktopSvgObjectAction.MIRROR_HORIZONTAL
            )
            val mirroredBoth = DesktopSvgObjectTransform.apply(
                mirroredH, 1, DesktopSvgObjectAction.MIRROR_VERTICAL
            )
            // As transformações do segundo salvamento não alteram o primeiro
            // nem a ordem dos caminhos ou os pontos da primeira camada vetorial.
            assertEquals(originalPath, mirroredBoth.paths[0])
            DesktopVectorProjectBundle.saveNew(
                opened.imported.copy(artwork = mirroredBoth),
                opened.widthMm, opened.style, opened.hoop, nextFlp, opened.rgb
            )
            val reopenedNext = DesktopVectorProjectBundle.open(nextFlp)
            assertEquals(mirroredBoth, reopenedNext.session.current)
            assertEquals(opened.rgb, reopenedNext.rgb)
            assertEquals(
                DesktopProjectService.open(nextFlp).current.points,
                reopenedNext.document.current.points
            )
            // O novo salvamento nunca substitui nem edita o par original.
            assertTrue(firstFlpBytes.contentEquals(Files.readAllBytes(firstFlp)))
            assertTrue(firstAuxBytes.contentEquals(Files.readAllBytes(firstSidecar)))
        } finally {
            Files.deleteIfExists(nextSidecar)
            Files.deleteIfExists(nextFlp)
            Files.deleteIfExists(firstSidecar)
            Files.deleteIfExists(firstFlp)
            Files.deleteIfExists(root)
        }
    }
}
