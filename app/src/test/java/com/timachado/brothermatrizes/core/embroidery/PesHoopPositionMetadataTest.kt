package com.timachado.brothermatrizes.core.embroidery

import com.timachado.brothermatrizes.core.project.ProjectCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Regressão: exportar PES e reabri-lo NÃO pode recentralizar a composição
 * deslocada no bastidor. As pontadas da máquina permanecem no bloco PEC;
 * somente o rótulo de 16 bytes registra a posição para o aplicativo.
 */
class PesHoopPositionMetadataTest {
    private fun layout(): EmbroideryDesign {
        return EmbroideryIntegrity.normalize(
            EmbroideryDesign(
                fileName = "MF_arranjo_de_flores_nome_maria.pes",
                format = "PES",
                label = "Arranjo Flores",
                points = listOf(
                    EmbroideryPoint(-300, -600, StitchCommand.JUMP, 0),
                    EmbroideryPoint(-250, -550, StitchCommand.STITCH, 0),
                    EmbroideryPoint(-200, 550, StitchCommand.STITCH, 0),
                    EmbroideryPoint(50, 620, StitchCommand.STITCH, 1),
                    EmbroideryPoint(90, 620, StitchCommand.END, 1)
                ),
                bounds = EmbroideryBounds(0, 0, 0, 0),
                stitchCount = 0, jumpCount = 0, colorChanges = 0,
                endFound = false,
                sourceBytes = ByteArray(0),
                threadColors = listOf(0x208029, 0xA02020),
                hoopPositionLocked = true,
                hoopProfile = HoopProfile.H130X180,
                sourceYAxisDown = true,
                isModified = true
            )
        ).getOrThrow().design
    }

    @Test fun savePesReopenRestoresExactHoopPositionAndProjectCodec() {
        val source = layout()
        val converted = MatrixConverter.convert(source, "PES", "criado").getOrThrow()
        val reopened = EmbroideryIoParser.parse(
            converted.fileName, converted.bytes
        ) as EmbroideryLoadResult.Success
        val actual = reopened.design
        assertTrue("PES reconhecido como posição fixa", actual.hoopPositionLocked)
        assertEquals(HoopProfile.H130X180, actual.hoopProfile)
        assertTrue(abs(source.bounds.minXUnits - actual.bounds.minXUnits) <= 2)
        assertTrue(abs(source.bounds.maxXUnits - actual.bounds.maxXUnits) <= 2)
        assertTrue(abs(source.bounds.minYUnits - actual.bounds.minYUnits) <= 2)
        assertTrue(abs(source.bounds.maxYUnits - actual.bounds.maxYUnits) <= 2)
        assertTrue(actual.points.count { it.command == StitchCommand.STITCH } >=
            source.points.count { it.command == StitchCommand.STITCH })
        assertTrue(actual.threadColors.isNotEmpty())
        val project = ProjectCodec.decode(ProjectCodec.encode(actual))
        assertTrue(project.hoopPositionLocked)
        assertEquals(actual.points, project.points)
        assertEquals(actual.bounds, project.bounds)
        assertTrue(HoopLayerPositioning.prepare(
            project, HoopProfile.H130X180
        ).getOrThrow() === project)
    }

    @Test fun positioningChangesOnlyThePecTextLabelNotMachineStitchData() {
        val locked = layout()
        val standard = MatrixConverter.convert(
            locked.copy(hoopPositionLocked = false), "PES", "criado"
        ).getOrThrow().bytes
        val positioned = MatrixConverter.convert(
            locked, "PES", "criado"
        ).getOrThrow().bytes
        assertEquals(standard.size, positioned.size)
        assertEquals(standard.copyOfRange(0, 8).toList(),
            positioned.copyOfRange(0, 8).toList())
        val pec = (positioned[8].toInt() and 255) or
            ((positioned[9].toInt() and 255) shl 8)
        assertTrue(positioned.copyOfRange(pec + 3, pec + 19)
            .toString(Charsets.US_ASCII).startsWith("BM9"))
        for (i in standard.indices) {
            if (i in (pec + 3) until (pec + 19)) continue
            assertEquals("Byte PES de máquina modificado em $i",
                standard[i], positioned[i])
        }
    }

    @Test fun oldPesAndInvalidMarkerRemainReadableWithoutFalsePositions() {
        val raw = MatrixConverter.convert(
            layout().copy(hoopPositionLocked = false), "PES", "criado"
        ).getOrThrow()
        val previous = EmbroideryIoParser.parse(raw.fileName, raw.bytes)
            as EmbroideryLoadResult.Success
        assertFalse(previous.design.hoopPositionLocked)
        val stamped = MatrixConverter.convert(
            layout(), "PES", "criado"
        ).getOrThrow()
        val bytes = stamped.bytes.copyOf()
        val pec = (bytes[8].toInt() and 255) or
            ((bytes[9].toInt() and 255) shl 8)
        bytes[pec + 8] = if (bytes[pec + 8] != 'X'.code.toByte())
            'X'.code.toByte() else 'Y'.code.toByte()
        val altered = EmbroideryIoParser.parse(stamped.fileName, bytes)
            as EmbroideryLoadResult.Success
        assertFalse("Marcador adulterado nunca é aplicado",
            altered.design.hoopPositionLocked)
    }

    @Test fun afterMovingFrameDownAndNameUpPesKeepsTheOffset() {
        val source = layout()
        val newSource = HoopLayerPositioning.move(
            source, 0, 25, HoopLayerPositioning.Target.MATRIX,
            HoopProfile.H130X180
        ).getOrThrow()
        val bytes = MatrixConverter.convert(newSource, "PES").getOrThrow()
        val restored = (EmbroideryIoParser.parse(bytes.fileName, bytes.bytes)
            as EmbroideryLoadResult.Success).design
        assertTrue(abs(newSource.bounds.minYUnits - restored.bounds.minYUnits) <= 2)
        assertTrue(abs(restored.bounds.minYUnits - source.bounds.minYUnits) > 10)
    }
}
