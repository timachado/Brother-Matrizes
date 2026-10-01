package com.timachado.brothermatrizes.core.embroidery

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PesBinaryDiagnosticTest {
    @Test
    fun writeMariaPesFixtureForExternalDecoder() {
        val design =
            TextMatrixGenerator
                .generate(
                    TextMatrixOptions(
                        text = "MARIA",
                        heightMm = 29.9f,
                        spacingMm = 0f,
                        style = TextStitchStyle.SATIN,
                        outputFormat = "PES",
                        hoopProfile = HoopProfile.H100X100,
                        enforceHoop = false
                    )
                )
                .getOrThrow()

        val converted =
            MatrixConverter
                .convert(
                    design,
                    "PES",
                    "binary-diagnostic"
                )
                .getOrThrow()

        val out = File("build/diagnostics/maria-app.pes")
        out.parentFile?.mkdirs()
        out.writeBytes(converted.bytes)

        val meta = File("build/diagnostics/maria-app.txt")
        meta.writeText(
            "sourceWidthMm=" + design.bounds.widthMm + "
" +
                "sourceHeightMm=" + design.bounds.heightMm + "
" +
                "points=" + design.points.size + "
" +
                "stitches=" + design.stitchCount + "
"
        )

        assertTrue(out.length() > 600)
    }
}
