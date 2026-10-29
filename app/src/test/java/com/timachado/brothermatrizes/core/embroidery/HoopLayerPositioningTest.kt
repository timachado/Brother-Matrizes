package com.timachado.brothermatrizes.core.embroidery

import com.timachado.brothermatrizes.core.project.ProjectCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoopLayerPositioningTest {
    private fun flowers(): EmbroideryDesign {
        val points = listOf(
            EmbroideryPoint(-220, -600, StitchCommand.JUMP, 0),
            EmbroideryPoint(-220, 600, StitchCommand.STITCH, 0),
            EmbroideryPoint(-195, 600, StitchCommand.STITCH, 0),
            EmbroideryPoint(-195, -600, StitchCommand.STITCH, 0),
            EmbroideryPoint(-220, -600, StitchCommand.END, 0)
        )
        return EmbroideryIntegrity.normalize(EmbroideryDesign(
            fileName = "MF arranjo de flores.pes",
            format = "PES",
            label = "Moldura Flores",
            points = points,
            bounds = EmbroideryBounds(0, 0, 0, 0),
            stitchCount = 0,
            jumpCount = 0,
            colorChanges = 0,
            endFound = false,
            sourceBytes = byteArrayOf(1, 2, 3),
            threadColors = listOf(0x339933),
            sourceYAxisDown = true,
            hoopProfile = HoopProfile.H130X180
        )).getOrThrow().design
    }

    private fun withName(): EmbroideryDesign {
        return NamePersonalization.add(
            original = flowers(),
            options = TextMatrixOptions(
                text = "Maria", heightMm = 8f, spacingMm = 1.2f,
                style = TextStitchStyle.SATIN,
                hoopProfile = HoopProfile.H130X180,
                outputFormat = "PES"
            ),
            centerXUnits = 175,
            centerYUnits = 40
        ).getOrThrow()
    }

    @Test fun importedDesignBecomesHoopAnchoredOnlyOnce() {
        val raw = flowers()
        val aligned = HoopLayerPositioning.prepare(raw, HoopProfile.H130X180)
            .getOrThrow()
        assertTrue(aligned.hoopPositionLocked)
        assertTrue(aligned.isModified)
        assertTrue(aligned.sourceBytes.isEmpty())
        assertEquals(0L, (aligned.bounds.minYUnits.toLong() +
            aligned.bounds.maxYUnits.toLong()) / 2L)
        val reopened = ProjectCodec.decode(ProjectCodec.encode(aligned))
        assertTrue(reopened.hoopPositionLocked)
        assertEquals(aligned.points, reopened.points)
        assertEquals(reopened, HoopLayerPositioning.prepare(
            reopened, HoopProfile.H130X180
        ).getOrThrow())
    }

    @Test fun matrixMovesDownWhileNameMovesUpWithoutFollowingMatrix() {
        val first = HoopLayerPositioning.prepare(
            withName(), HoopProfile.H130X180
        ).getOrThrow()
        val original = requireNotNull(first.editableName)
        val baseCount = original.original.points.size
        val basePoint = first.points[1]
        val namePoint = first.points[baseCount + 2]
        val down = HoopLayerPositioning.move(
            first, 0, 40, HoopLayerPositioning.Target.MATRIX,
            HoopProfile.H130X180
        ).getOrThrow()
        assertEquals(basePoint.yUnits + 40, down.points[1].yUnits)
        assertEquals(namePoint, down.points[baseCount + 2])
        assertEquals(original.centerYUnits, down.editableName!!.centerYUnits)
        assertEquals(original.original.points[1].yUnits + 40,
            down.editableName!!.original.points[1].yUnits)
        val up = HoopLayerPositioning.move(
            down, 0, -50, HoopLayerPositioning.Target.NAME,
            HoopProfile.H130X180
        ).getOrThrow()
        assertEquals(basePoint.yUnits + 40, up.points[1].yUnits)
        assertEquals(namePoint.yUnits - 50,
            up.points[baseCount + 2].yUnits)
        assertEquals(original.centerYUnits - 50,
            up.editableName!!.centerYUnits)
        assertEquals(first.points.map { it.command },
            up.points.map { it.command })
        assertEquals(first.points.map { it.colorIndex },
            up.points.map { it.colorIndex })

        val reopened = ProjectCodec.decode(ProjectCodec.encode(up))
        assertTrue(reopened.hoopPositionLocked)
        assertEquals(up.points, reopened.points)
        assertEquals(up.editableName!!.centerYUnits,
            reopened.editableName!!.centerYUnits)
        assertEquals(up.editableName!!.original.points,
            reopened.editableName!!.original.points)
        assertTrue(reopened.editableName!!.original.hoopPositionLocked)
    }

    @Test fun exceedingSafeHoopMarginDoesNotMutatePreviousPosition() {
        val valid = HoopLayerPositioning.prepare(
            flowers(), HoopProfile.H130X180
        ).getOrThrow()
        val snapshot = valid.points.toList()
        val result = HoopLayerPositioning.move(
            valid, 0, 400, HoopLayerPositioning.Target.MATRIX,
            HoopProfile.H130X180
        )
        assertTrue(result.isFailure)
        assertEquals(snapshot, valid.points)
        assertTrue(valid.sourceBytes.isEmpty())
    }

    @Test fun movingNameRequiresAnEditableLayer() {
        val original = HoopLayerPositioning.prepare(
            flowers(), HoopProfile.H130X180
        ).getOrThrow()
        assertFalse(HoopLayerPositioning.move(
            original, 0, -25, HoopLayerPositioning.Target.NAME,
            HoopProfile.H130X180
        ).isSuccess)
    }
}
