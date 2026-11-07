package com.timachado.brothermatrizes.desktop.platform

import com.timachado.brothermatrizes.core.embroidery.EmbroideryPoint
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import java.nio.file.Path

/**
 * Integra as fases experimentais do stroke-flow numa cadeia de pontadas.
 *
 * A geometria Skia ainda não foi comparada com o raster Android, e a
 * seleção de segmentos utilizáveis antes do planejamento final pode
 * alterar a ordem. Não usar o resultado em UI, exportação ou clientes.
 */
internal object DesktopStrokeFlowPipeline {
    fun generate(
        raster: DesktopStrokeRaster,
        densityMm: Float,
        pullCompensationMm: Float,
        includeUnderlay: Boolean
    ): List<EmbroideryPoint> {
        require(densityMm.isFinite() && densityMm in 0.3f..1.2f) {
            "Densidade Satin fora dos limites Android."
        }
        require(pullCompensationMm.isFinite() &&
            pullCompensationMm in 0f..1f) {
            "Compensação de repuxo fora dos limites Android."
        }
        val skeleton = raster.trace()
        require(skeleton.isNotEmpty()) {
            "O contorno vetorial não produziu esqueleto costurável."
        }
        val fullPlan = DesktopStrokeFlowPlanner.plan(skeleton)
        require(fullPlan.isNotEmpty()) {
            "Nenhum segmento válido encontrado para costura."
        }
        val rows = fullPlan.filter { it.phase == "CENTER_RUN" }
            .associate { step ->
                step.segmentId to DesktopStrokeFlowRows.build(
                    step.geometry, raster, densityMm, pullCompensationMm
                )
            }.filterValues { it.size >= 2 }
        require(rows.isNotEmpty()) {
            "Nenhum segmento do esqueleto produziu colunas Satin."
        }
        // Ainda não equivale à ordem oficial quando há segmentos
        // descartados antes da escolha de entrada. Isto será validado
        // pela comparação Android x Windows e ajustado se necessário.
        val ordered = fullPlan.filter { it.segmentId in rows.keys }
        val stitches = DesktopStrokeFlowEmitter.emit(
            plan = ordered,
            rowsBySegment = rows,
            includeUnderlay = includeUnderlay,
            densityMm = densityMm
        )
        require(stitches.any { it.command == StitchCommand.STITCH }) {
            "Nenhuma pontada foi emitida."
        }
        return stitches
    }

    fun generateFromFont(
        fontFile: Path,
        text: String,
        heightUnits: Float,
        spacingUnits: Float,
        densityMm: Float,
        pullCompensationMm: Float,
        includeUnderlay: Boolean
    ): List<EmbroideryPoint> {
        val geometry = DesktopSkiaGlyphGeometry.sample(
            fontFile, text, heightUnits, spacingUnits
        )
        val output = mutableListOf<EmbroideryPoint>()
        geometry.contoursByGlyph.forEach { glyph ->
            val raster = DesktopStrokeRasterizer.rasterize(glyph)
            val points = generate(
                raster, densityMm, pullCompensationMm, includeUnderlay
            )
            if (output.isNotEmpty() && points.isNotEmpty()) {
                val last = output.last()
                if (last.command != StitchCommand.TRIM) {
                    output += EmbroideryPoint(
                        last.xUnits, last.yUnits, StitchCommand.TRIM, last.colorIndex
                    )
                }
            }
            output.addAll(points)
            require(output.size <= 100_000) {
                "O texto inteiro excedeu o limite de pontadas QA."
            }
        }
        return output
    }
}
