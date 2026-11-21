package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VectorSvgExporterTest {
    @Test
    fun freehandSvgCanBeImportedAgainWithClosedPath() {
        val artwork = VectorArtwork(
            label = "Logo & Teste<svg>",
            paths = listOf(
                VectorPath(
                    points = listOf(
                        VectorPoint(5f, 7f),
                        VectorPoint(30f, 7f),
                        VectorPoint(30f, 40f)
                    ),
                    closed = true
                )
            )
        )

        val bytes = VectorSvgExporter.export(artwork, 0x12AB34).getOrThrow()
        val xml = bytes.toString(Charsets.UTF_8)

        assertTrue(xml.contains("<title>Logo &amp; Teste&lt;svg&gt;</title>"))
        assertTrue(xml.contains("stroke=\"#12AB34\""))
        assertTrue(xml.contains("viewBox=\"5.000 7.000 25.000 33.000\""))

        val parsed = SimpleSvgParser.parse("desenho.svg", bytes).getOrThrow()
        assertEquals(1, parsed.paths.size)
        assertTrue(parsed.paths.single().closed)
        assertEquals(VectorPoint(5f, 7f), parsed.paths.single().points.first())
    }

    @Test
    fun drawingWithoutPathsCannotExport() {
        val empty = VectorArtwork(paths = emptyList())
        assertTrue(VectorSvgExporter.export(empty).isFailure)
    }

    @Test
    fun nonFiniteCoordinatesAreRejected() {
        val broken = VectorArtwork(
            paths = listOf(
                VectorPath(listOf(VectorPoint(0f, 0f), VectorPoint(Float.NaN, 1f)))
            )
        )
        assertTrue(VectorSvgExporter.export(broken).isFailure)
    }
}
