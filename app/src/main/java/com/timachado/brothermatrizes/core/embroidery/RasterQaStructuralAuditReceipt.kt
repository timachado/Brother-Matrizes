package com.timachado.brothermatrizes.core.embroidery

/**
 * Explicit, versioned, copy-only QA receipt that cannot silently fall back
 * to the old alpha histogram if the spatial audit is missing.
 *
 * The fingerprint binds the receipt to the actual original pixels analyzed
 * on-device. This is NOT stitch geometry or an exportable PES.
 */
internal object RasterQaStructuralAuditReceipt {
    const val BUILD_MARKER = "QA-ABERTURA-DE-VAZADOS-V3"

    fun generate(
        report: RasterAlphaThresholdSweepReport
    ): Result<String> = runCatching {
        val audit = report.structuralAudit ?:
            error("Auditoria espacial ausente: atualize para o APK QA V2.")
        val leaks = report.counterLeakEvidence ?:
            error("Rota dos vazados ausente: atualize para o APK QA V3.")
        val source = report.source
        require(audit.source === source && leaks.source === source &&
            source.argb.size == source.width * source.height &&
            report.componentLineage?.source === source &&
            report.requiresOperatorReview &&
            !report.exportPermitted && !audit.exportPermitted &&
            !leaks.exportPermitted && leaks.requiresManualReview &&
            !RasterEmbroideryApproval.canExportFromImageTool()
        ) {
            "Relatório estrutural não corresponde à imagem original revisada."
        }
        val requested=listOf(
            160 to 180,180 to 208,208 to 224,224 to 240
        )
        require(audit.intervals.map { it.fromThreshold to it.toThreshold } ==
            requested && leaks.intervals.map {
                it.fromThreshold to it.toThreshold
            } == requested) {
            "A auditoria espacial QA V3 está incompleta."
        }
        require(leaks.intervals.indices.all { i ->
            leaks.intervals[i].openedCounters.size <=
                audit.intervals[i].holesBefore
        }) { "Cavidades incompatíveis entre relatórios QA." }

        var fingerprint=1469598103934665603L
        for(pixel in source.argb) {
            fingerprint=(fingerprint xor (pixel.toLong() and 0xffffffffL)) *
                1099511628211L
        }
        val totalConnectors=audit.intervals.sumOf {
            it.splitConnectorCandidates.size
        }
        val counterChanges=audit.intervals.count {
            it.holesBefore!=it.holesAfter
        }
        val tracedHoles=leaks.intervals.sumOf { it.openedCounters.size }
        BUILD_MARKER + " | " +
            "imagem=${source.width}x${source.height} | " +
            "fingerprint=" + java.lang.Long.toHexString(fingerprint) +
            " | conectores-frágeis=" + totalConnectors +
            " | intervalos-com-mudança-de-vazados=" + counterChanges +
            " | vazados-com-rota-de-abertura=" + tracedHoles +
            " | " + audit.summary() +
            " | " + leaks.summary() +
            " | Diagnóstico somente dos pixels originais; não é PES, " +
            "nem aprovação de bordado físico."
    }
}
