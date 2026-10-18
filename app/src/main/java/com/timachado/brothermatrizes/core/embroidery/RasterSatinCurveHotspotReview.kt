package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Only points the operator to difficult Satin rails; never edits stitches. */
internal enum class RasterSatinHotspotType { SHARP_CURVE, WIDTH_TRANSITION }

internal data class RasterSatinCurveHotspot(
    val sectionIndex: Int,
    val pairIndex: Int,
    val type: RasterSatinHotspotType,
    val turnDegrees: Double,
    val adjacentWidthRatio: Double,
    val priority: Double
) {
    fun explanation(): String =
        "Seção " + (sectionIndex + 1) + ", par " + (pairIndex + 1) +
            ": " + when(type) {
                RasterSatinHotspotType.SHARP_CURVE -> "curva acentuada"
                RasterSatinHotspotType.WIDTH_TRANSITION -> "mudança de espessura"
            } + " (" +
            "%.0f".format(java.util.Locale.ROOT,turnDegrees) + "°, razão " +
            "%.2f".format(java.util.Locale.ROOT,adjacentWidthRatio) +
            "). Revise o trecho e suas pontadas."

}

internal data class RasterSatinCurveHotspotReport(
    val sectionCount: Int,
    val reviewedPairs: Int,
    val important: List<RasterSatinCurveHotspot>,
    val sourceStitchCount: Int,
    val exportPermitted: Boolean = false,
    val requiresOperatorReview: Boolean = true
) {
    fun message(): String =
        important.size.toString() + " trecho(s) críticos entre " +
            reviewedPairs + " pares Satin. Nenhum ajuste foi aplicado; " +
            "não é aprovação de máquina ou PES."
}

internal object RasterSatinCurveHotspotReview {
    private const val TURN_THRESHOLD_DEGREES = 26.0
    private const val WIDTH_RATIO_THRESHOLD = 1.32

    /** Geometry-only diagnosis is NOT a stitch-safe approval. */
    fun rankGeometry(
        sections: List<ManualSatinSection>,
        localWidthMm: Double,
        localHeightMm: Double
    ): Result<List<RasterSatinCurveHotspot>> = runCatching {
        require(sections.size in 1..24 &&
            localWidthMm.isFinite() && localWidthMm>.1 &&
            localHeightMm.isFinite() && localHeightMm>.1 &&
            sections.sumOf { it.rails.size } in 2..1500
        ) { "Trilhos fora dos limites QA." }
        data class Point(val x:Double,val y:Double)
        fun distance(a:Point,b:Point):Double=hypot(a.x-b.x,a.y-b.y)
        val found=ArrayList<RasterSatinCurveHotspot>()
        for ((sectionIndex,section) in sections.withIndex()) {
            val rails=section.rails
            require(rails.size in 2..100)
            val center=ArrayList<Point>(rails.size)
            val widths=ArrayList<Double>(rails.size)
            for (r in rails) {
                require(listOf(r.ax,r.ay,r.bx,r.by).all {
                    it.isFinite() && it in 0f..1f
                }) { "Coordenada de trilho fora do componente." }
                val ax=r.ax.toDouble()*localWidthMm
                val ay=r.ay.toDouble()*localHeightMm
                val bx=r.bx.toDouble()*localWidthMm
                val by=r.by.toDouble()*localHeightMm
                center+=Point((ax+bx)/2,(ay+by)/2)
                widths+=hypot(ax-bx,ay-by)
            }
            for (i in rails.indices) {
                val earlier=max(0,i-1)
                val later=min(rails.lastIndex,i+1)
                val ratio=max(widths[earlier],widths[later])/
                    min(widths[earlier],widths[later]).coerceAtLeast(.001)
                var turn=0.0
                if (i>0 && i<rails.lastIndex) {
                    val a=center[i-1];val b=center[i];val c=center[i+1]
                    val ab=distance(a,b);val bc=distance(b,c)
                    require(ab>.001 && bc>.001) { "Eixo Satin com pares repetidos." }
                    val cos=(((b.x-a.x)*(c.x-b.x)+
                        (b.y-a.y)*(c.y-b.y))/(ab*bc)).coerceIn(-1.0,1.0)
                    turn=Math.toDegrees(acos(cos))
                }
                if(turn>=TURN_THRESHOLD_DEGREES) {
                    found+=RasterSatinCurveHotspot(
                        sectionIndex,i,RasterSatinHotspotType.SHARP_CURVE,
                        turn,ratio,turn
                    )
                } else if(ratio>=WIDTH_RATIO_THRESHOLD) {
                    found+=RasterSatinCurveHotspot(
                        sectionIndex,i,RasterSatinHotspotType.WIDTH_TRANSITION,
                        turn,ratio,ratio*20.0
                    )
                }
            }
        }
        found.sortedWith(
            compareByDescending<RasterSatinCurveHotspot> {it.priority}
                .thenBy {it.sectionIndex}.thenBy {it.pairIndex}
        ).take(24)
    }

    /** The UI can show hotspots only after independent needle/ink audit. */
    fun inspect(
        source: RasterImagePixels,
        part: RasterSatinObject,
        draft: RasterLetterSatinDraft,
        widthMm: Float,
        hoop: HoopProfile
    ): Result<RasterSatinCurveHotspotReport> = runCatching {
        val topology=RasterLetterTopologyReview.inspect(source,part)
            .getOrThrow()
        require(topology==draft.diagnosis &&
            draft.reviewRequired && !draft.exportPermitted &&
            !RasterEmbroideryApproval.canExportFromImageTool()
        ) { "Prévia anterior alterada ou não revisada." }
        RasterSatinRailGeometryGate.inspect(
            source,part,draft.sections,widthMm,topology.enclosedCounters
        ).getOrThrow()
        val verified=RasterSatinObjectEditor.preview(
            source,part,draft.sections,widthMm,hoop,minimumCoverage=.68f
        ).getOrThrow()
        require(verified.points==draft.preview.points &&
            verified.threadColors==draft.preview.threadColors &&
            verified.format=="QA_SATIN_REVIEW" &&
            verified.sourceBytes.isEmpty() && verified.endFound
        ) { "Os STITCH já não pertencem aos trilhos indicados." }
        if(topology.enclosedCounters>0) {
            RasterCounterSatinGate.inspect(
                source,part,verified,widthMm
            ).getOrThrow()
        }
        RasterNeedleRiskAudit.inspect(verified,hoop).getOrThrow()
        RasterThreadDensityPreflight.inspect(verified).getOrThrow()
        val fidelity=RasterStitchFidelityAudit.compare(
            source,part,verified,widthMm
        ).getOrThrow()
        require(fidelity.coverage>=.78 &&
            fidelity.offInkFraction<=.10 &&
            fidelity.coverage==draft.coverage &&
            fidelity.offInkFraction==draft.offInkFraction
        ) { "Cobertura Satin diverge do rascunho original." }
        val mmPixel=widthMm.toDouble()/source.width
        RasterSatinCurveHotspotReport(
            sectionCount=draft.sections.size,
            reviewedPairs=draft.sections.sumOf {it.rails.size},
            important=rankGeometry(
                draft.sections,
                (part.mask.maxX-part.mask.minX+1)*mmPixel,
                (part.mask.maxY-part.mask.minY+1)*mmPixel
            ).getOrThrow(),
            sourceStitchCount=verified.stitchCount
        )
    }
}
