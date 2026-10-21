package com.timachado.brothermatrizes.core.embroidery

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.hypot

/**
 * Raster -> ordered outline waypoints in physical millimetres.
 *
 * This is an inspectable, per-contour running-path CANDIDATE, not an
 * embroidery design. Each outline stays separate: there are NO machine
 * STITCH/JUMP/TRIM/END commands, tie-ins, underlay, stitch-file bytes,
 * counter approval or PES. Connection planning and physical review are
 * prerequisites to turning these waypoint candidates into stitches.
 */
internal data class RasterQaWaypointMm(val x: Float, val y: Float)

internal data class RasterQaRunningContour(
    val sourceLoopIndex: Int,
    val isCounter: Boolean,
    val perimeterMm: Float,
    val waypoints: List<RasterQaWaypointMm>,
    val thinFeatureWarning: Boolean
) {
    val isClosed: Boolean get() =
        waypoints.size >= 3 && waypoints.first() == waypoints.last()
}

internal data class RasterQaRunningPathReport(
    val source: RasterImagePixels,
    val sourceFingerprint: String,
    val chosenWidthMm: Float,
    val maxWaypointSpacingMm: Float,
    val outlines: List<RasterQaRunningContour>,
    val needsConnectionPlanning: Boolean = true,
    val needsOperatorReview: Boolean = true,
    val exportPermitted: Boolean = false
) {
    fun summary(): String {
        fun fmt(value: Float) = String.format(Locale.US, "%.3f", value)
        return "QA-TRAJETOS-CORRIDOS-V1 | " +
            "fingerprint=$sourceFingerprint | largura=${fmt(chosenWidthMm)} mm" +
            " | espacamento-max=${fmt(maxWaypointSpacingMm)} mm" +
            " | contornos=${outlines.size}" +
            " | vazados=${outlines.count { it.isCounter }}" +
            " | contornos-finos-para-revisar=${outlines.count { it.thinFeatureWarning }}" +
            " | coordenadas-candidatas=${outlines.sumOf { it.waypoints.size }}" +
            " | SEM STITCH/JUMP/TRIM/PES: trajetos separados, não conectados " +
            "nem aprovados para bordado físico."
    }
}

internal object RasterQaRunningPathStudy {
    fun prepare(
        report: RasterQaContourGeometryReport,
        maxWaypointSpacingMm: Float = 2f
    ): Result<RasterQaRunningPathReport> = runCatching {
        val image = report.source
        require(
            image.width in 8..512 && image.height in 8..512 &&
            image.argb.size == image.width*image.height &&
            !report.exportPermitted && report.requiresOperatorReview &&
            !RasterEmbroideryApproval.canExportFromImageTool() &&
            report.chosenWidthMm.isFinite() &&
            report.chosenWidthMm in 10f..300f &&
            report.mmPerPixel.isFinite() && report.mmPerPixel>0f &&
            maxWaypointSpacingMm.isFinite() &&
            maxWaypointSpacingMm in 0.5f..5f &&
            report.inkPixels>0 && report.loops.isNotEmpty()
        ) { "Fonte ou parâmetros fora da análise de trajetos QA." }

        // Recheck source identity instead of trusting a copied geometry.
        var hash=1469598103934665603L
        for (pixel in image.argb) {
            hash=(hash xor (pixel.toLong() and 0xffffffffL))*
                1099511628211L
        }
        require(java.lang.Long.toHexString(hash)==report.fingerprint) {
            "Contornos pertencem a uma versão diferente da imagem."
        }
        require(kotlin.math.abs(
            report.mmPerPixel*image.width-report.chosenWidthMm
        )<0.0002f) { "Escala física dos contornos inconsistente." }

        var totalSamples=0
        val outlines=report.loops.mapIndexed { loopIndex,loop ->
            val vertices=loop.vertices
            require(vertices.size in 5..200_000 &&
                vertices.first()==vertices.last() &&
                loop.perimeterPixels>0 &&
                loop.signedTwiceAreaPixels!=0L
            ) { "Contorno aberto ou degenerado." }
            val edges=ArrayList<Double>(vertices.size-1)
            var perimeter=0.0
            var signedTwiceArea=0L
            for(i in 0 until vertices.size-1) {
                val a=vertices[i]
                val b=vertices[i+1]
                require(a.x in 0..image.width &&
                    a.y in 0..image.height &&
                    b.x in 0..image.width &&
                    b.y in 0..image.height
                ) { "Coordenadas de contorno fora da imagem." }
                val dx=b.x-a.x
                val dy=b.y-a.y
                require((dx==0) != (dy==0)) {
                    "Contorno contém segmento diagonal ou de comprimento zero."
                }
                val delta=hypot(dx.toDouble(),dy.toDouble())
                edges.add(delta)
                perimeter+=delta
                signedTwiceArea+=a.x.toLong()*b.y-b.x.toLong()*a.y
            }
            require(perimeter>0.0 &&
                signedTwiceArea==loop.signedTwiceAreaPixels &&
                kotlin.math.abs(perimeter-loop.perimeterPixels)<0.0001
            ) { "Área ou perímetro do contorno QA foi alterado." }

            val perimeterMm=perimeter*report.mmPerPixel
            val steps=ceil(perimeterMm/maxWaypointSpacingMm).toInt()
            require(steps in 2..4096) {
                "Rota muito curta ou extensa para amostragem QA automática."
            }
            totalSamples+=steps+1
            require(totalSamples<=16_384) {
                "Excesso de coordenadas de QA, requer simplificação supervisionada."
            }
            val cumulative=DoubleArray(edges.size+1)
            for(i in edges.indices)cumulative[i+1]=cumulative[i]+edges[i]
            val path=ArrayList<RasterQaWaypointMm>(steps+1)
            var edge=0
            for(k in 0 until steps) {
                val at=k.toDouble()*perimeter/steps
                while(edge<edges.size-1 && cumulative[edge+1]<=at)
                    edge++
                val a=vertices[edge]
                val b=vertices[edge+1]
                val ratio=((at-cumulative[edge])/edges[edge]).toFloat()
                path+=RasterQaWaypointMm(
                    (a.x+(b.x-a.x)*ratio)*report.mmPerPixel,
                    (a.y+(b.y-a.y)*ratio)*report.mmPerPixel
                )
            }
            path+=path.first()
            val minX=vertices.minOf { it.x }
            val maxX=vertices.maxOf { it.x }
            val minY=vertices.minOf { it.y }
            val maxY=vertices.maxOf { it.y }
            val minDimensionMm=minOf(
                maxX-minX,maxY-minY
            )*report.mmPerPixel
            RasterQaRunningContour(
                sourceLoopIndex=loopIndex,isCounter=loop.isCounter,
                perimeterMm=perimeterMm.toFloat(),waypoints=path,
                // Descriptive flag, NOT a universal machine stitch rule.
                thinFeatureWarning=minDimensionMm<1f
            )
        }
        require(outlines.all { it.isClosed }) {
            "Trajeto QA não retornou à origem."
        }
        RasterQaRunningPathReport(
            source=image,sourceFingerprint=report.fingerprint,
            chosenWidthMm=report.chosenWidthMm,
            maxWaypointSpacingMm=maxWaypointSpacingMm,
            outlines=outlines
        )
    }
}
