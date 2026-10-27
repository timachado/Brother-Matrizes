package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NamePersonalizationTest {
    private fun floralFrame(): EmbroideryDesign {
        val original = listOf(
            EmbroideryPoint(-250, -200, StitchCommand.JUMP, 0),
            EmbroideryPoint(-250, 200, StitchCommand.STITCH, 0),
            EmbroideryPoint(250, 200, StitchCommand.STITCH, 0),
            EmbroideryPoint(250, -200, StitchCommand.STITCH, 0),
            EmbroideryPoint(-250, -200, StitchCommand.STITCH, 0),
            EmbroideryPoint(-250, -200, StitchCommand.END, 0)
        )
        return EmbroideryIntegrity.normalize(
            EmbroideryDesign(
                fileName = "moldura-flores.pes",
                format = "PES",
                label = "MOLDURA FLORES",
                points = original,
                bounds = EmbroideryBounds(-250, 250, -200, 200),
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
}
