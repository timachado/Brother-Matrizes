package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.abs

/**
 * Independent consumer-side checks of already-encoded PES bytes. This
 * utility NEVER invokes MatrixConverter, creates a PES, writes to disk,
 * enables image-tool export, or asserts machine compatibility.
 *
 * The readback uses the existing app parser (EmbroideryLoader), and is
 * therefore NOT an independent third-party decoder or physical stitch-out.
 * The producer must be verified separately by regression tests.
 */
internal data class PesReadbackEvidenceReport(
    val fileBytes: Int,
    val originalStitches: Int,
    val reopenedStitches: Int,
    val originalWidthMm: Float,
    val reopenedWidthMm: Float,
    val originalHeightMm: Float,
    val reopenedHeightMm: Float,
    val geometryPreserved: Boolean,
    val stitchOrderPreserved: Boolean,
    val externalDecoderVerified: Boolean = false,
    val machineCertified: Boolean = false,
    val authorizesImagePesExport: Boolean = false
)

internal object PesReadbackEvidence {
    /**
     * Only tests a preexisting production-like source in PES representation;
     * QA_* raster designs must never enter a PES workflow via this helper.
     */
    fun inspect(
        source: EmbroideryDesign,
        fileName: String,
        encodedBytes: ByteArray
    ): Result<PesReadbackEvidenceReport> = runCatching {
        require(source.format=="PES" &&
            source.sourceBytes.isEmpty() &&
            source.points.isNotEmpty() && source.stitchCount>0 &&
            source.endFound && source.colorChanges>=0 &&
            source.points.last().command==StitchCommand.END
        ) {
            "Releitura não habilitada para prévia experimental QA/Imagem para Bordado."
        }
        require(fileName.lowercase().endsWith(".pes") &&
            encodedBytes.size in 512..(16*1024*1024) &&
            encodedBytes.copyOfRange(0,8).toString(Charsets.US_ASCII)
                .startsWith("#PES")
        ) { "Cabeçalho, nome ou tamanho do arquivo PES inválido." }
        val parsed=EmbroideryLoader.loadBytes(
            displayName=fileName,bytes=encodedBytes
        )
        val reopened=(parsed as? EmbroideryLoadResult.Success)?.design
            ?: error("O arquivo PES não pôde ser reaberto pelo leitor do aplicativo.")
        require(reopened.format=="PES" && reopened.stitchCount>0) {
            "Releitura retornou formato ou pontos incompatíveis."
        }
        val oldPoints=source.points.filter { it.command==StitchCommand.STITCH }
        val newPoints=reopened.points.filter { it.command==StitchCommand.STITCH }
        require(oldPoints.isNotEmpty() && newPoints.isNotEmpty())
        fun normalized(design:EmbroideryDesign):List<Pair<Int,Int>> {
            // Machine encoder can insert a STITCH at the initial JUMP
            // position. Both source and parsed machine path must therefore
            // use the origin of ALL needle/travel locations, not the first
            // STITCH alone. Otherwise legitimate PEC origin shifts look like
            // missing embroidery even when the actual stitches are intact.
            val positions=design.points.filter {
                it.command==StitchCommand.JUMP ||
                    it.command==StitchCommand.STITCH
            }
            val xmin=positions.minOf { it.xUnits }
            fun visualY(p:EmbroideryPoint)=
                if(design.sourceYAxisDown) p.yUnits else -p.yUnits
            val ymin=positions.minOf(::visualY)
            return design.points.filter {
                it.command==StitchCommand.STITCH
            }.map { p ->
                (p.xUnits-xmin) to (visualY(p)-ymin)
            }
        }
        val oldGeometry=normalized(source)
        val newGeometry=normalized(reopened)
        require(oldGeometry.size<=90_000 && newGeometry.size<=120_000) {
            "Sequência de pontos PES excede limite de auditoria."
        }
        fun similar(a:Pair<Int,Int>,b:Pair<Int,Int>) =
            abs(a.first-b.first)<=1 && abs(a.second-b.second)<=1
        // Important: a set comparison accepts reversed stitch order,
        // missing duplicate passages, and a different needle route through
        // the SAME positions. For PES readback we require an ordered
        // subsequence of the actual needle-down commands instead.
        //
        // The encoder is still allowed to INSERT extra intermediate points
        // to split long movements. Every original needle-down visit,
        // including repeated visits, must appear in the same order.
        var cursor=0
        var matched=0
        for (expected in oldGeometry) {
            while(cursor<newGeometry.size &&
                !similar(expected,newGeometry[cursor])) cursor++
            if (cursor==newGeometry.size) break
            matched++
            cursor++
        }
        val preserved=matched==oldGeometry.size
        val widthDiff=abs(
            source.bounds.widthMm-reopened.bounds.widthMm
        )
        val heightDiff=abs(
            source.bounds.heightMm-reopened.bounds.heightMm
        )
        require(preserved && widthDiff<=.3f && heightDiff<=.3f) {
            "PES reabriu com diferença de ordem, trajetória ou dimensões: " +
                "stitchesMatched=$matched/${oldGeometry.size}; " +
                "widthDeltaMm=$widthDiff; heightDeltaMm=$heightDiff."
        }
        // The PES container is offset to positive PEC coordinates by the
        // release converter; this check detects recentering to negatives.
        require(reopened.bounds.minXUnits>=-1 &&
            reopened.bounds.minYUnits>=-1
        ) { "Releitura PES apresenta origem negativa inesperada." }
        PesReadbackEvidenceReport(
            fileBytes=encodedBytes.size,
            originalStitches=source.stitchCount,
            reopenedStitches=reopened.stitchCount,
            originalWidthMm=source.bounds.widthMm,
            reopenedWidthMm=reopened.bounds.widthMm,
            originalHeightMm=source.bounds.heightMm,
            reopenedHeightMm=reopened.bounds.heightMm,
            geometryPreserved=true,
            stitchOrderPreserved=true
        )
    }
}
