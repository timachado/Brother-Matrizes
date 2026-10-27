package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NamePersonalizationTest {
    private fun floralFrame(): EmbroideryDesign {
        val original = listOf(
            EmbroideryPoint(-380, -380, StitchCommand.JUMP, 0),
            EmbroideryPoint(-380, 380, StitchCommand.STITCH, 0),
            EmbroideryPoint(380, 380, StitchCommand.STITCH, 0),
            EmbroideryPoint(380, -380, StitchCommand.STITCH, 0),
            EmbroideryPoint(-380, -380, StitchCommand.STITCH, 0),
            EmbroideryPoint(-380, -380, StitchCommand.END, 0)
        )
        return EmbroideryIntegrity.normalize(
            EmbroideryDesign(
                fileName = "moldura-flores.pes",
                format = "PES",
                label = "MOLDURA FLORES",
                points = original,
                bounds = EmbroideryBounds(-380, 380, -380, 380),
                stitchCount = 4,
                jumpCount = 1,
                colorChanges = 0,
                endFound = true,
                sourceBytes = byteArrayOf(1, 2, 3),
                threadColors = listOf(0x123456),
                hoopProfile = HoopProfile.H100X100
            )
        ).getOrThrow().design
    }

    @Test fun mariaAndSophiaAreRealStitchesWithoutChangingFrame() {
        val frame = floralFrame()
        for (name in listOf("Maria", "Sophia")) {
            val result = NamePersonalization.add(
                frame, TextMatrixOptions(
                    text = name, heightMm = 10f,
                    style = TextStitchStyle.SATIN,
                    outputFormat = "PES",
                    color = 0xB22222
                ), 0, 0
            ).getOrThrow()
            assertEquals(frame.points.dropLast(1), result.points.take(frame.points.size - 1))
            assertEquals(StitchCommand.END, result.points.last().command)
            assertEquals(1, result.points.count { it.command == StitchCommand.END })
            assertTrue(result.points.count { it.command == StitchCommand.STITCH } > frame.stitchCount)
            assertTrue(result.points.any { it.command == StitchCommand.TRIM })
            assertTrue(result.points.any { it.command == StitchCommand.COLOR_CHANGE })
            assertEquals(0xB22222, result.threadColors[1])
            assertTrue(result.isModified)
            assertTrue(result.sourceBytes.isEmpty())
            assertEquals(frame.bounds, EmbroideryIntegrity.normalize(frame).getOrThrow().design.bounds)
        }
    }

    @Test fun textChangeRebuildsFromOriginalAndDoesNotAccumulateNames() {
        val original = floralFrame()
        val maria = NamePersonalization.add(original, TextMatrixOptions("Maria"), 0, 0).getOrThrow()
        val sophia = NamePersonalization.add(original, TextMatrixOptions("Sophia"), 0, 0).getOrThrow()
        assertEquals(1, maria.points.count { it.command == StitchCommand.COLOR_CHANGE })
        assertEquals(1, sophia.points.count { it.command == StitchCommand.COLOR_CHANGE })
        assertEquals(original.points, floralFrame().points)
        assertFalse(sophia.points === maria.points)
    }

    @Test fun reopeningEditableNameReplacesMariaWithSophiaWithoutDuplicatingStitches() {
        val frame = floralFrame()
        val maria = NamePersonalization.add(
            frame, TextMatrixOptions("Maria", style = TextStitchStyle.SATIN), 5, 0
        ).getOrThrow()
        val sophia = NamePersonalization.add(
            maria, TextMatrixOptions("Sophia", style = TextStitchStyle.SATIN), 5, 0
        ).getOrThrow()
        assertEquals(frame.points.dropLast(1), sophia.points.take(frame.points.size - 1))
        assertEquals(1, sophia.points.count { it.command == StitchCommand.COLOR_CHANGE })
        assertEquals(frame.points, sophia.editableName?.original?.points)
        assertEquals("Sophia", sophia.editableName?.options?.text)
        assertTrue(sophia.points.any { it.command == StitchCommand.STITCH })
    }

    @Test fun namingCreatesIndependentFilesForMariaAndSophia() {
        val base = floralFrame()
        val maria = NamePersonalization.add(base, TextMatrixOptions("Maria"), 0, 0).getOrThrow()
        val sophia = NamePersonalization.add(base, TextMatrixOptions("Sophia"), 0, 0).getOrThrow()
        val mariaAlternate = NamePersonalization.add(base, TextMatrixOptions("Maria"), 15, 0).getOrThrow()
        assertTrue(maria.fileName != base.fileName)
        assertTrue(maria.fileName != sophia.fileName)
        assertTrue(maria.fileName != mariaAlternate.fileName)
        assertTrue(maria.fileName.contains("maria"))
        assertTrue(sophia.fileName.contains("sophia"))
    }

    /**
     * Regressão com as DIMENSÕES medidas do arquivo MOLDURA FLORES
     * BORBOLETAS 13X18.pes (105,4 x 157,6 mm).
     * A geometria do teste é sintética: não armazenamos o arquivo privado
     * do usuário no repositório público.
     */
    @Test fun portraitMoldura13x18AcceptsRotatedBrotherHoop() {
        val framePoints = listOf(
            EmbroideryPoint(-820, -691, StitchCommand.JUMP, 0),
            EmbroideryPoint(-820, 885, StitchCommand.STITCH, 0),
            EmbroideryPoint(234, 885, StitchCommand.STITCH, 0),
            EmbroideryPoint(234, -691, StitchCommand.STITCH, 0),
            EmbroideryPoint(-820, -691, StitchCommand.STITCH, 0),
            EmbroideryPoint(-820, -691, StitchCommand.END, 0)
        )
        val frame = EmbroideryIntegrity.normalize(
            floralFrame().copy(
                fileName = "moldura-borboletas-13x18.pes",
                points = framePoints,
                hoopProfile = HoopProfile.H130X180,
                sourceYAxisDown = true
            )
        ).getOrThrow().design

        assertEquals(105.4f, frame.bounds.widthMm, 0.01f)
        assertEquals(157.6f, frame.bounds.heightMm, 0.01f)
        assertFalse(HoopValidator.validate(frame, HoopProfile.H130X180).fits)

        val edited = NamePersonalization.add(
            frame,
            TextMatrixOptions(
                text = "Maria", heightMm = 10f,
                style = TextStitchStyle.SATIN,
                outputFormat = "PES"
            ),
            centerXUnits = -293,
            centerYUnits = 97
        ).getOrThrow()
        assertEquals(frame.points.dropLast(1), edited.points.take(frame.points.size - 1))
        assertEquals(HoopProfile.H130X180, edited.hoopProfile)
        assertEquals(1, edited.points.count { it.command == StitchCommand.END })
        assertTrue(edited.bounds.widthMm <= HoopProfile.H130X180.usableHeightMm)
        assertTrue(edited.bounds.heightMm <= HoopProfile.H130X180.usableWidthMm)

        val outside = NamePersonalization.add(
            frame, TextMatrixOptions("Maria", heightMm = 10f),
            centerXUnits = 3000, centerYUnits = 97
        )
        assertTrue(outside.isFailure)
    }

    @Test fun rotatedNamePreservesBaseAndSavedRotation() {
        val original = floralFrame()
        val options = TextMatrixOptions(
            text = "Maria", heightMm = 10f,
            style = TextStitchStyle.SATIN,
            rotationDegrees = 30f
        )
        val rotated = NamePersonalization.add(original, options, 0, 0)
            .getOrThrow()
        val default = NamePersonalization.add(
            original, options.copy(rotationDegrees = 0f), 0, 0
        ).getOrThrow()
        assertEquals(original.points.dropLast(1),
            rotated.points.take(original.points.size - 1))
        assertEquals(30f, rotated.editableName?.options?.rotationDegrees ?: -1f)
        assertTrue(rotated.points != default.points)
    }

    @Test fun importedFontNeverFallsBackToBrotherGlyphs() {
        val original = floralFrame()
        val options = TextMatrixOptions(
            text = "Maria", importedFontId = "fonte-ba9de2fa01.ttf",
            heightMm = 10f, style = TextStitchStyle.SATIN
        )
        val withoutSource = NamePersonalization.add(original, options, 0, 0)
        assertTrue(withoutSource.isFailure)
        assertTrue(withoutSource.exceptionOrNull()?.message
            ?.contains("Fonte importada") == true)

        var observed: TextMatrixOptions? = null
        val withSource = NamePersonalization.add(
            original, options, 0, 0,
            importedGenerator = { current ->
                observed = current
                TextMatrixGenerator.generate(
                    current.copy(importedFontId = null)
                )
            }
        ).getOrThrow()
        assertEquals(options.importedFontId, observed?.importedFontId)
        assertEquals(options.importedFontId,
            withSource.editableName?.options?.importedFontId)
        assertEquals(original.points.dropLast(1),
            withSource.points.take(original.points.size - 1))
    }

    @Test fun skiaFontUsesOriginalYDirectionInPortraitPes() {
        // Contorno assimétrico: parte superior em -40 e base em +10.
        // Se ocorrer espelhamento vertical indevido, os pontos trocam
        // de sinal e a caligrafia cursiva aparece invertida.
        val base = floralFrame().copy(sourceYAxisDown = true)
        val glyphPoints = listOf(
            EmbroideryPoint(-20, -40, StitchCommand.JUMP, 0),
            EmbroideryPoint(-10, -25, StitchCommand.STITCH, 0),
            EmbroideryPoint(20, 10, StitchCommand.STITCH, 0),
            EmbroideryPoint(20, 10, StitchCommand.END, 0)
        )
        val skiaGlyph = base.copy(
            fileName = "fonte-teste.pes", label = "Maria",
            points = glyphPoints, sourceYAxisDown = true,
            bounds = EmbroideryBounds(-20, 20, -40, 10),
            stitchCount = 2, jumpCount = 1,
            hoopProfile = null, editableName = null
        )
        val options = TextMatrixOptions(
            text = "Maria", importedFontId = "bertilda.ttf",
            heightMm = 15f, style = TextStitchStyle.SATIN
        )
        val expected = MatrixEditor.apply(
            skiaGlyph,
            EditTransform(centerAtOrigin = true, offsetXUnits = 0,
                offsetYUnits = 0, mirrorVertical = false)
        )
        val actual = NamePersonalization.add(
            base, options, 0, 0, importedGenerator = { Result.success(skiaGlyph) }
        ).getOrThrow()
        val appendix = actual.points.drop(base.points.size - 1)
            .filter { it.command == StitchCommand.STITCH }
        val expectedStitches = expected.points.filter {
            it.command == StitchCommand.STITCH
        }
        assertEquals(expectedStitches.map { it.yUnits },
            appendix.take(expectedStitches.size).map { it.yUnits })
        assertEquals(base.points.dropLast(1),
            actual.points.take(base.points.size - 1))
    }

    @Test fun lightweightPreflightMatchesFullCompositionAndDoesNotChangeSource() {
        val source = floralFrame()
        val before = source.points.toList()
        val options = TextMatrixOptions("Maria", heightMm = 10f)
        assertTrue(NamePersonalization.canPlace(source, options, 0, 0).isSuccess)
        val result = NamePersonalization.add(source, options, 0, 0).getOrThrow()
        assertEquals(before, source.points)
        assertEquals(before.dropLast(1),
            result.points.take(before.size - 1))
        assertTrue(NamePersonalization.canPlace(source, options, 3000, 0).isFailure)
        assertTrue(NamePersonalization.add(source, options, 3000, 0).isFailure)
    }

    @Test fun rejectsInvalidSequenceWithoutModifyingOriginal() {
        val original = floralFrame()
        val corrupt = original.copy(points = original.points.dropLast(1) +
            EmbroideryPoint(0, 0, StitchCommand.STITCH, 0))
        assertTrue(NamePersonalization.add(corrupt, TextMatrixOptions("Maria"), 0, 0).isFailure)
        assertEquals(StitchCommand.END, original.points.last().command)
    }

    @Test fun exportPesDstAndReopenWhenSupported() {
        val original = floralFrame()
        for (format in listOf("PES", "DST")) {
            val framed = original.copy(
                format = format,
                fileName = "moldura-flores." + format.lowercase()
            )
            val edited = NamePersonalization.add(
                framed, TextMatrixOptions("Maria", style = TextStitchStyle.SATIN),
                0, 0
            ).getOrThrow()
            val converted = MatrixConverter.convert(edited, format, "nome").getOrThrow()
            val reopened = EmbroideryLoader.loadBytes(converted.fileName, converted.bytes)
            assertTrue("Falha ao reabrir $format", reopened is EmbroideryLoadResult.Success)
            assertTrue((reopened as EmbroideryLoadResult.Success).design.stitchCount > original.stitchCount)
        }
    }

    @Test fun independentPinchScalesStitchesNotOnlyVisualPreview() {
        val base = floralFrame()
        val before = base.points.toList()
        val normal = NamePersonalization.add(
            base, TextMatrixOptions("Maria", heightMm = 8f), 0, 0
        ).getOrThrow()
        val options = TextMatrixOptions(
            text = "Maria", heightMm = 8f,
            widthScale = 1.25f, heightScale = 1.1f
        )
        val stretched = NamePersonalization.add(base, options, 0, 0)
            .getOrThrow()
        assertTrue(stretched.points != normal.points)
        assertTrue(stretched.fileName != normal.fileName)
        assertEquals(1.25f, stretched.editableName!!.options.widthScale, .001f)
        assertEquals(1.1f, stretched.editableName!!.options.heightScale, .001f)
        assertEquals(before, base.points)
        assertEquals(before.dropLast(1),
            stretched.points.take(before.size - 1))
        assertTrue(NamePersonalization.canPlace(base, options, 0, 0).isSuccess)
        assertTrue(NamePersonalization.canPlace(
            base, options.copy(widthScale = 2.6f), 0, 0
        ).isFailure)
    }
}
