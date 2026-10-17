package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot

/**
 * An OPT-IN QA needle-path candidate for exceptionally simple transparent
 * artwork with only a few original tonal buckets. Unlike the color-family
 * picture (which is just visual), this returns real STITCH/JUMP/TRIM commands
 * only after re-running ALL existing digitizer preflights and comparing every
 * candidate sewn segment with the ORIGINAL photo's opaque silhouette.
 *
 * Never uses an imported PES, never writes PES/JEF/DST bytes, never certifies
 * a physical Brother machine, fabric, density or stitch-out.
 */
internal data class RasterFlatColorStitchReview(
    val design: EmbroideryDesign,
    val sampledSourceSegments: Int,
    val sourceOutOfInkSamples: Int,
    val originalToneBuckets: Int,
    val canExportPes: Boolean = false
)

internal object RasterFlatColorStitchCandidate {
    private const val MAX_TONE_BUCKETS = 12
    private const val MAX_TOTAL_SAMPLES = 250_000
    private const val MAX_OUTSIDE_FRACTION = .035

    fun create(
        original: RasterImagePixels,
        widthMm: Float,
        hoop: HoopProfile
    ): Result<RasterFlatColorStitchReview> = runCatching {
        require(
            original.width in 8..512 && original.height in 8..512 &&
                original.argb.size == original.width * original.height &&
                widthMm.isFinite() && widthMm in 10f..200f &&
                original.argb.any { it ushr 24 < 128 }
        ) { "Revisão QA exige desenho isolado, transparente e escala válida." }

        val study = RasterThreadTextureStudy.inspect(original).getOrThrow()
        require(study.originalAlphaHolesPreserved &&
            study.originalToneBuckets <= MAX_TONE_BUCKETS &&
            study.ambiguousPixels == 0 &&
            study.suggestedColorFamilies in 1..3
        ) {
            "Esta imagem tem relevo, sombras ou cores ambíguas demais " +
                "para gerar pontos automaticamente. Separe as letras, " +
                "o símbolo e os vazados e revise os trilhos de Satin."
        }
        require(!RasterArtworkComplexityAudit.examine(original)
            .requiresMultiTechniqueReview) {
            "Arte multitécnica: reconstrução assistida obrigatória."
        }
        val flat = study.flatPreview
        require(flat.width == original.width && flat.height == original.height &&
            flat.argb.indices.all { i ->
                (flat.argb[i] ushr 24 >= 180) ==
                    (original.argb[i] ushr 24 >= 180)
            }
        ) { "Simplificação alterou uma letra, contraforma ou detalhe da imagem." }

        // Use existing shape-aware Satin, Tatami and RUNNING generators rather
        // than raster fill rows, and keep the existing all-or-nothing outcome.
        val design = RasterAutomaticLogoPreview.create(
            flat, widthMm, hoop
        ).getOrThrow()
        require(design.format == "QA_AUTO_STITCH" &&
            design.sourceBytes.isEmpty() && design.isModified &&
            design.stitchCount > 0 && design.jumpCount > 0 &&
            design.points.lastOrNull()?.command == StitchCommand.END &&
            !RasterEmbroideryApproval.canExportFromImageTool()
        ) { "Candidato não exportável ou incompleto." }

        // Important: the flattened palette cannot be the sole fidelity ground
        // truth. Check EVERY actual STITCH segment against the ORIGINAL alpha
        // mask, independently of the palette segmentation used by the builder.
        val unitsPerPixel = widthMm * 10.0 / original.width
        fun pixelPosition(p: EmbroideryPoint): Pair<Double,Double> =
            (p.xUnits / unitsPerPixel + original.width / 2.0) to
                (original.height / 2.0 - p.yUnits / unitsPerPixel)

        fun isOriginalInk(x: Double, y: Double): Boolean {
            val ix = floor(x).toInt()
            val iy = floor(y).toInt()
            for (dy in -1..1) for (dx in -1..1) {
                val xx = ix + dx
                val yy = iy + dy
                if (xx !in 0 until original.width ||
                    yy !in 0 until original.height) continue
                if (original.argb[yy * original.width + xx] ushr 24 >= 180 &&
                    hypot(xx + .5 - x, yy + .5 - y) <= 1.15) return true
            }
            return false
        }
        var previous: EmbroideryPoint? = null
        var samples = 0
        var outside = 0
        var sewnSegments = 0
        for (p in design.points) {
            when (p.command) {
                StitchCommand.JUMP -> previous = p
                StitchCommand.STITCH -> {
                    val start = previous ?: error(
                        "STITCH sem JUMP ou ponto anterior.")
                    require(start.colorIndex == p.colorIndex) {
                        "Trajetória sem mapeamento de cor consistente."
                    }
                    val (ax, ay) = pixelPosition(start)
                    val (bx, by) = pixelPosition(p)
                    val steps = ceil(hypot(bx-ax, by-ay)).toInt()
                        .coerceAtLeast(1)
                    require(samples + steps + 1 <= MAX_TOTAL_SAMPLES) {
                        "Trajetória excede limite da auditoria da foto original."
                    }
                    for (j in 0..steps) {
                        val t = j.toDouble() / steps
                        val x = ax + (bx - ax) * t
                        val y = ay + (by - ay) * t
                        samples++
                        if (!isOriginalInk(x,y)) outside++
                    }
                    previous = p
                    sewnSegments++
                }
                StitchCommand.TRIM, StitchCommand.COLOR_CHANGE,
                StitchCommand.STOP -> previous = null
                StitchCommand.END -> previous = null
                StitchCommand.SEQUIN -> error("Lantejoulas não suportadas neste QA.")
            }
        }
        require(sewnSegments == design.stitchCount &&
            sewnSegments > 0 && samples > 0 &&
            outside.toDouble() / samples <= MAX_OUTSIDE_FRACTION
        ) {
            "Pontadas experimentais atravessam áreas transparentes da " +
                "imagem original. Revise o contorno antes de simular."
        }

        // Static screening only, never proof of needle or textile safety.
        RasterNeedleRiskAudit.inspect(design, hoop).getOrThrow()
        RasterThreadDensityPreflight.inspect(design).getOrThrow()
        RasterThreadSequenceAudit.inspect(design).getOrThrow()
        require(!RasterEmbroideryApproval.canExportFromImageTool())
        RasterFlatColorStitchReview(
            design = design,
            sampledSourceSegments = samples,
            sourceOutOfInkSamples = outside,
            originalToneBuckets = study.originalToneBuckets
        )
    }
}
