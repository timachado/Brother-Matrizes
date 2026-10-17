package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterAssistedSatinDraftPlannerTest {
    private val orange = 0xffdb761a.toInt()
    private val neutral = 0xffeeeeee.toInt()

    private fun photo(
        w: Int = 160, h: Int = 100,
        color: (Int, Int) -> Int
    ): RasterImagePixels = RasterImagePixels(
        w, h, IntArray(w * h) { i -> color(i % w, i / w) },
        "teste editavel de foto"
    )

    private fun plan(source: RasterImagePixels): RasterAssistedElementPlan =
        RasterAssistedElementPlanner.propose(
            source, RasterThreadTextureStudy.inspect(source).getOrThrow()
        ).getOrThrow()

    @Test fun selectedHorizontalSatinRemainsCenteredInFullOriginalHoop() {
        val image = photo { x, y ->
            if (x in 18..119 && y in 32..45) orange else 0
        }
        val sourcePlan = plan(image)
        val part = sourcePlan.elements.single()
        val draft = RasterAssistedSatinDraftPlanner.prepare(
            image, sourcePlan, part.id, 90f, HoopProfile.H130X180
        ).getOrThrow()
        assertEquals(part.id, draft.item.id)
        assertEquals(part.pixelCount, draft.item.pixelCount)
        assertEquals(part.minX, draft.item.mask.minX)
        assertEquals(part.maxX, draft.item.mask.maxX)
        assertEquals(image.argb.size, draft.item.mask.pixels.size)
        assertTrue(draft.sections.isNotEmpty())
        assertEquals(0, draft.counterCount)
        assertTrue(draft.manualTouchRequired)
        assertFalse(draft.exportPermitted)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun ringWithCounterCannotReceiveUnsafeStraightRailFallback() {
        val source = photo { x,y ->
            if (x in 35..89 && y in 14..75 &&
                !(x in 49..76 && y in 29..61)) orange else 0
        }
        val sourcePlan = plan(source)
        val element = sourcePlan.elements.single()
        assertEquals(1,element.enclosedHoles)
        val draft = RasterAssistedSatinDraftPlanner.prepare(
            source, sourcePlan, element.id, 90f, HoopProfile.H130X180
        )
        if (draft.isSuccess) {
            val result = draft.getOrThrow()
            assertEquals(1,result.counterCount)
            val preview = RasterSatinObjectEditor.preview(
                source,result.item,result.sections,90f,
                HoopProfile.H130X180,minimumCoverage=.90f
            ).getOrThrow()
            assertTrue(RasterAssistedSatinDraftPlanner
                .requireSafeCounterPath(
                    source,result.item,preview,90f
                ).isSuccess)
            assertTrue(result.manualTouchRequired)
            assertFalse(result.exportPermitted)
        } else {
            assertTrue(draft.exceptionOrNull()?.message.orEmpty().isNotEmpty())
        }
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun tinyCounterMustNeverBeSilentlyPaintedOver() {
        val source = photo { x, y ->
            if (x in 40..99 && y in 20..79 &&
                !(x in 65..66 && y in 49..50)) neutral else 0
        }
        val sourcePlan = plan(source)
        val element = sourcePlan.elements.single()
        assertEquals(1,element.enclosedHoles)
        val attempt = RasterAssistedSatinDraftPlanner.prepare(
            source, sourcePlan, element.id, 90f, HoopProfile.H130X180
        )
        assertTrue(attempt.isFailure)
        assertTrue(attempt.exceptionOrNull()?.message.orEmpty()
            .contains("contraforma", ignoreCase = true))
    }

    @Test fun originalMaskMustNotBeReplacedOrEditedBehindTheReviewersBack() {
        val source = photo { x,y ->
            if (x in 20..89 && y in 25..48) orange else 0
        }
        val sourcePlan = plan(source)
        val changed = source.argb.copyOf()
        changed[30 * source.width + 40] = 0
        val tamperedSource = RasterImagePixels(
            source.width,source.height,changed,"origem alterada"
        )
        val result = RasterAssistedSatinDraftPlanner.prepare(
            tamperedSource,sourcePlan,sourcePlan.elements.single().id,
            90f,HoopProfile.H130X180
        )
        assertTrue(result.isFailure)
    }

    @Test fun unknownElementAndOverlargeHoopRequestFailClosed() {
        val source = photo { x,y ->
            if (x in 20..89 && y in 25..48) orange else 0
        }
        val sourcePlan = plan(source)
        assertTrue(RasterAssistedSatinDraftPlanner.prepare(
            source,sourcePlan,500,90f,HoopProfile.H130X180
        ).isFailure)
        assertTrue(RasterAssistedSatinDraftPlanner.prepare(
            source,sourcePlan,sourcePlan.elements.single().id,
            180f,HoopProfile.H100X100
        ).isFailure)
    }

    @Test fun editedSatinStitchCrossingLetterHoleFailsSecondAudit() {
        val source = photo { x,y ->
            if (x in 35..89 && y in 14..75 &&
                !(x in 49..76 && y in 29..61)) orange else 0
        }
        val element = plan(source).elements.single()
        val sourcePlan = plan(source)
        val isolated = sourcePlan.isolate(element.id).getOrThrow()
        val pixels = IntArray(source.argb.size) { index ->
            if (isolated.argb[index] ushr 24 >= 180) 0 else -1
        }
        val item = RasterSatinObject(
            id = element.id, color = orange, pixelCount = element.pixelCount,
            mask = SeparatedColorArtwork(
                colors = listOf(orange), pixels = pixels,
                minX = element.minX, maxX = element.maxX,
                minY = element.minY, maxY = element.maxY
            )
        )
        // A single physically plausible 7.3 mm needle move that enters the
        // letter's transparent core MUST be rejected independently of
        // how the draft rails were generated.
        val points = listOf(
            EmbroideryPoint(-186, 28, StitchCommand.JUMP, 0),
            EmbroideryPoint(-113, 28, StitchCommand.STITCH, 0),
            EmbroideryPoint(-113, 28, StitchCommand.END, 0)
        )
        val unsafe = EmbroideryDesign(
            fileName = "crossed-counter.qa",
            format = "QA_SATIN_REVIEW",
            label = "malicious stitch through empty letter",
            points = points,
            bounds = EmbroideryBounds(-186,-113,28,28),
            stitchCount = 1,
            jumpCount = 1,
            colorChanges = 0,
            endFound = true,
            sourceBytes = ByteArray(0),
            threadColors = listOf(orange),
            isModified = true,
            hoopProfile = HoopProfile.H130X180
        )
        val checked = RasterAssistedSatinDraftPlanner.requireSafeCounterPath(
            source,item,unsafe,90f
        )
        assertTrue(checked.isFailure)
        assertTrue(checked.exceptionOrNull()?.message.orEmpty().contains(
            "contraforma", ignoreCase = true
        ) || checked.exceptionOrNull()?.message.orEmpty().contains(
            "atravessa", ignoreCase = true
        ))
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun noStitchFileIsProducedByPreparingEditableRails() {
        val source = photo { x,y ->
            if (x in 20..89 && y in 25..48) orange else 0
        }
        val result = RasterAssistedSatinDraftPlanner.prepare(
            source,plan(source),1,90f,HoopProfile.H130X180
        ).getOrThrow()
        assertTrue(result.sections.all { it.rails.size >= 2 })
        assertFalse(result.exportPermitted)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }
}
