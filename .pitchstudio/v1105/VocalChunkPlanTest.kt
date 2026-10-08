package br.com.timachado.pitchstudio

import org.junit.Assert.*
import org.junit.Test

class VocalChunkPlanTest {
    @Test fun defaultsAndLongSegmentsHaveContinuousCoverage() {
        for ((seconds, expectedBlocks) in listOf(6 to 2, 12 to 3, 18 to 4)) {
            val frames = VocalChunkPlan.targetFrames(seconds, 100L * 44100, 44100)
            assertEquals(seconds * 44100, frames)
            val offsets = VocalChunkPlan.chunkOffsets(frames)
            assertEquals(expectedBlocks, offsets.size)
            assertEquals(0, offsets.first())
            offsets.forEachIndexed { index, start ->
                if (index > 0) assertEquals(VocalChunkPlan.STEP_FRAMES, start - offsets[index - 1])
            }
            assertTrue(offsets.last() + VocalChunkPlan.OUTPUT_FRAMES >= frames)
        }
    }

    @Test fun shortTrackNeverFabricatesAudio() {
        assertEquals(7 * 44100,
            VocalChunkPlan.targetFrames(18, 7L * 48000, 48000))
        assertEquals(1, VocalChunkPlan.chunkOffsets(120000).size)
    }

    @Test fun overlappingSamplesAreCrossfaded() {
        val start=VocalChunkPlan.STEP_FRAMES
        val target=FloatArray(start + VocalChunkPlan.OUTPUT_FRAMES)
        val previous=FloatArray(VocalChunkPlan.OUTPUT_FRAMES) { 1f }
        val next=FloatArray(VocalChunkPlan.OUTPUT_FRAMES) { -1f }
        VocalChunkPlan.mixInto(target, previous, 0)
        VocalChunkPlan.mixInto(target, next, start)
        assertTrue(target[start] > 0.9f)
        assertTrue(kotlin.math.abs(target[start + VocalChunkPlan.OVERLAP_FRAMES / 2]) < 0.01f)
        assertTrue(target[start + VocalChunkPlan.OVERLAP_FRAMES - 1] < -0.9f)
        assertEquals(-1f, target[start + VocalChunkPlan.OVERLAP_FRAMES], 0f)
    }

    @Test fun truncatedFinalChunkDoesNotWritePastEnd() {
        val length=12*44100
        val combined=FloatArray(length)
        for (offset in VocalChunkPlan.chunkOffsets(length))
            VocalChunkPlan.mixInto(combined, FloatArray(VocalChunkPlan.OUTPUT_FRAMES) { .25f }, offset)
        assertEquals(.25f, combined.first(), 0f)
        assertEquals(.25f, combined.last(), 0f)
    }

    @Test fun invalidDurationIsRejected() {
        try {
            VocalChunkPlan.targetFrames(60, 44100L * 60, 44100)
            fail("Should reject 60s on limited memory device")
        } catch (_: IllegalArgumentException) { }
    }
}
