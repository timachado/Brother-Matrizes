package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Test

class MatrixExporterTest {
    @Test
    fun sanitizesUnsafeFileName() {
        assertEquals(
            "cliente_matriz.dst",
            MatrixExporter.safeFileName("cliente/matriz.dst")
        )
    }

    @Test
    fun createsFallbackNameWhenBlank() {
        assertEquals(
            "matriz.dst",
            MatrixExporter.safeFileName("...")
        )
    }
}
