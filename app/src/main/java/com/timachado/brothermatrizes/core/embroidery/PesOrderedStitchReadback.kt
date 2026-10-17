package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.abs

/**
 * QA-only comparison of ordered needle-down positions after PES readback.
 *
 * Matching SETS of positions is insufficient: a machine can visit the same
 * points in the wrong order, or drop repeated passes at one position.
 * PES encoders may legitimately insert intermediate stitches or an initial
 * positioning needle-down, so the reference sequence must be a monotonic
 * subsequence (including multiplicity) of the reopened needle-down stream.
 *
 * This check does NOT approve color-change, trim, underlay or fabric behavior.
 * It NEVER encodes, saves, or enables PES generated from images.
 */
internal data class PesOrderedReadbackReport(
    val expectedStitches: Int,
    val reopenedStitches: Int,
    val matchedStitchesInOrder: Int,
    val toleranceUnits: Int,
    val orderedNeedlePathPreserved: Boolean,
    val externalDecoderVerified: Boolean = false,
    val machineCertified: Boolean = false
)

internal object PesOrderedStitchReadback {
    fun compare(
        reference: EmbroideryDesign,
        reopened: EmbroideryDesign,
        toleranceUnits: Int = 1
    ): Result<PesOrderedReadbackReport> = runCatching {
        require(reference.format == "PES" && reopened.format == "PES" &&
            reference.sourceBytes.isEmpty() &&
            reference.endFound && reopened.endFound &&
            reference.points.lastOrNull()?.command == StitchCommand.END &&
            reopened.points.lastOrNull()?.command == StitchCommand.END &&
            toleranceUnits in 0..1
        ) { "A comparação exige dois fluxos PES completos, sem prévia QA." }

        fun orderedStitches(design: EmbroideryDesign): List<Pair<Int,Int>> {
            require(design.points.size in 3..150_000 &&
                design.stitchCount in 1..150_000 &&
                design.points.count { it.command == StitchCommand.STITCH } ==
                    design.stitchCount
            ) { "Fluxo de pontadas inconsistente ou acima do limite de QA." }
            val positions = design.points.filter {
                it.command == StitchCommand.JUMP ||
                    it.command == StitchCommand.STITCH
            }
            val minX = positions.minOf { it.xUnits }
            fun visibleY(p: EmbroideryPoint): Int =
                if (design.sourceYAxisDown) p.yUnits else -p.yUnits
            val minY = positions.minOf(::visibleY)
            return design.points.asSequence()
                .filter { it.command == StitchCommand.STITCH }
                .map { (it.xUnits-minX) to (visibleY(it)-minY) }
                .toList()
        }
        val original = orderedStitches(reference)
        val decoded = orderedStitches(reopened)

        var cursor = 0
        var matched = 0
        for (expected in original) {
            while (cursor < decoded.size) {
                val next = decoded[cursor++]
                if (abs(expected.first-next.first) <= toleranceUnits &&
                    abs(expected.second-next.second) <= toleranceUnits) {
                    matched++
                    break
                }
            }
            if (matched < original.size && cursor >= decoded.size) break
        }
        require(matched == original.size) {
            "Sequência PES perdeu, reordenou ou deduplicou pontos de agulha " +
                "(preservados $matched de ${original.size})."
        }
        PesOrderedReadbackReport(
            expectedStitches = original.size,
            reopenedStitches = decoded.size,
            matchedStitchesInOrder = matched,
            toleranceUnits = toleranceUnits,
            orderedNeedlePathPreserved = true
        )
    }
}
