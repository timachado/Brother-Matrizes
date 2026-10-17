package com.timachado.brothermatrizes.core.embroidery

import java.util.Locale

/**
 * Traceable, non-exportable QA dossier for the ACTUAL quantized needle
 * commands. A "PASS" means one static software audit accepted the data,
 * never that the resulting embroidery is safe on fabric or a Brother.
 *
 * Imported PES/JEF/DST deliberately cannot enter this tool. This report
 * must not call MatrixConverter or GeneratedMatrixPipeline: no PES bytes
 * are generated, persisted or exposed by the image-to-embroidery QA path.
 */
internal enum class RasterQaGateStatus { PASS, FAIL, MISSING }

internal data class RasterQaGate(
    val label: String,
    val status: RasterQaGateStatus,
    val detail: String
)

internal data class RasterQaReadinessReport(
    val format: String,
    val stitchCount: Int,
    val jumpCount: Int,
    val colorChanges: Int,
    val hoop: HoopProfile,
    val gates: List<RasterQaGate>,
    val threadLengthMm: Double?,
    val peakThreadLengthMmPerSquareMm: Double?,
    val sourceCoverage: Double?,
    val sourceOffInkFraction: Double?,
    val sourceWrongColorFraction: Double?,
    val exportPermitted: Boolean = false,
    val machineCertified: Boolean = false
) {
    val hasStaticFailures: Boolean get() =
        gates.any { it.status == RasterQaGateStatus.FAIL }
    val missingEvidence: Boolean get() =
        gates.any { it.status == RasterQaGateStatus.MISSING }

    /** Portable human-readable review note, not a PES/DST/JEF file. */
    fun toMarkdown(): String {
        fun percent(value: Double?): String =
            if (value == null) "Não avaliado"
            else String.format(Locale.US, "%.1f%%", value * 100.0)
        fun mm(value: Double?): String =
            if (value == null) "Não avaliado"
            else String.format(Locale.US, "%.2f mm", value)
        fun safe(value: String): String = value
            .replace("\n", " ").replace("\r", " ").replace("|", "/")
            .take(300)
        val status = if (hasStaticFailures) "BLOQUEADO EM QA"
            else "REVISÃO QA — NÃO HOMOLOGADO"
        return buildString {
            appendLine("# Brother Matrizes — Relatório técnico experimental")
            appendLine()
            appendLine("**Estado:** $status")
            appendLine("**Exportação:** BLOQUEADA (PES/JEF/DST)")
            appendLine("**Máquina certificada:** NÃO")
            appendLine("**Formato interno:** ${safe(format)}")
            appendLine("**Pontos STITCH:** $stitchCount")
            appendLine("**Saltos JUMP:** $jumpCount")
            appendLine("**Trocas de linha:** $colorChanges")
            appendLine("**Bastidor de prévia:** ${safe(hoop.name)}")
            appendLine("**Linha costurada:** ${mm(threadLengthMm)}")
            appendLine("**Pico por 1 mm²:** ${mm(peakThreadLengthMmPerSquareMm)}")
            appendLine("**Cobertura da arte:** ${percent(sourceCoverage)}")
            appendLine("**Pontos fora da arte:** ${percent(sourceOffInkFraction)}")
            appendLine("**Pontos com cor incompatível:** ${percent(sourceWrongColorFraction)}")
            appendLine()
            appendLine("## Verificações")
            appendLine("| Auditoria | Estado | Observação |")
            appendLine("| --- | --- | --- |")
            gates.forEach {
                appendLine("| ${safe(it.label)} | ${it.status} | ${safe(it.detail)} |")
            }
            appendLine()
            appendLine("## Evidências obrigatórias para liberação")
            appendLine("Arte original em resolução integral, validação visual")
            appendLine("PANOBIANCO/Vasos de Bênção, codificação e releitura PES")
            appendLine("independente, modelo Brother documentado e prova física")
            appendLine("supervisionada (agulha, linha, entretela, tecido, densidade).")
            appendLine()
            appendLine("**Este relatório não autoriza envio à bordadeira.**")
        }
    }
}

internal object RasterQaReadinessReportBuilder {
    fun collect(
        design: EmbroideryDesign,
        hoop: HoopProfile,
        sourceImage: RasterImagePixels? = null,
        sourceWidthMm: Float? = null,
        machine: PesTargetProfile = PesTargetProfile.unknown(),
        pesVersion: Int? = null
    ): Result<RasterQaReadinessReport> = runCatching {
        require(
            design.format in setOf(
                "QA_AUTO_STITCH", "QA_SATIN_REVIEW",
                "QA_RUNNING_REVIEW", "QA_TATAMI_REVIEW"
            ) &&
            design.isModified && design.sourceBytes.isEmpty() &&
            design.hoopProfile == hoop && design.points.size in 3..90_000 &&
            design.points.last().command == StitchCommand.END
        ) {
            "Relatório somente para prévia experimental, nunca PES importado."
        }
        require((sourceImage == null) == (sourceWidthMm == null)) {
            "Imagem original e escala devem ser informadas juntas."
        }
        if (sourceImage != null) {
            require(sourceWidthMm!!.isFinite() && sourceWidthMm in 10f..200f &&
                sourceImage.width in 8..512 &&
                sourceImage.height in 8..512 &&
                sourceImage.argb.size == sourceImage.width * sourceImage.height
            ) { "Arte original inválida para auditoria." }
        }

        val gates = ArrayList<RasterQaGate>()
        fun audit(name: String, block: () -> Unit) {
            val check = runCatching(block)
            gates += RasterQaGate(
                name,
                if (check.isSuccess) RasterQaGateStatus.PASS
                else RasterQaGateStatus.FAIL,
                if (check.isSuccess) "Trajetória passou nesta verificação estática."
                else check.exceptionOrNull()?.message ?: "Falha na auditoria."
            )
        }
        val risk = RasterNeedleRiskAudit.inspect(design,hoop)
        gates += RasterQaGate(
            "Comprimento e concentração de agulhadas",
            if (risk.isSuccess) RasterQaGateStatus.PASS else RasterQaGateStatus.FAIL,
            risk.exceptionOrNull()?.message ?: "Limites preliminares QA satisfeitos."
        )
        val density = RasterThreadDensityPreflight.inspect(design)
        gates += RasterQaGate(
            "Comprimento de fio por região de tecido",
            if (density.isSuccess) RasterQaGateStatus.PASS else RasterQaGateStatus.FAIL,
            density.exceptionOrNull()?.message ?: "Acúmulo preliminar QA aceito."
        )
        if (design.format == "QA_AUTO_STITCH") {
            audit("Sobreposição entre cores") {
                RasterColorLayerOverlapAudit.inspect(design).getOrThrow()
            }
        } else gates += RasterQaGate(
            "Sobreposição entre cores",
            RasterQaGateStatus.MISSING,
            "Exige composição do logotipo inteiro."
        )

        var coverage: Double? = null
        var offInk: Double? = null
        var wrongColor: Double? = null
        if (sourceImage != null && design.format == "QA_AUTO_STITCH") {
            audit("Retenção dos detalhes da imagem original") {
                RasterSourceRetentionAudit.requirePreserved(sourceImage)
                    .getOrThrow()
            }
            val check = RasterWholeArtworkFidelity.inspect(
                sourceImage,design,sourceWidthMm!!
            )
            if (check.isSuccess) {
                val report = check.getOrThrow()
                coverage = report.coverage
                offInk = report.offInkFraction
                wrongColor = report.wrongColorFraction
            }
            gates += RasterQaGate(
                "Fidelidade global dos STITCH",
                if(check.isFailure) RasterQaGateStatus.FAIL
                else if(coverage!!>=.60 &&
                    offInk!!<=.18 &&
                    wrongColor!!<=.12 &&
                    check.getOrThrow().significantColorMinimumCoverage>=.50
                ) RasterQaGateStatus.PASS else RasterQaGateStatus.FAIL,
                check.exceptionOrNull()?.message ?: "Cobertura e cores aferidas contra o desenho."
            )
        } else {
            gates += RasterQaGate(
                "Fidelidade global dos STITCH",
                RasterQaGateStatus.MISSING,
                "Precisa da arte inteira original e de composição QA_AUTO_STITCH."
            )
            gates += RasterQaGate(
                "Retenção dos detalhes da imagem original",
                RasterQaGateStatus.MISSING,
                "Sem imagem original completa e comparável."
            )
        }

        // PES target assessment accepts AUTO/SATIN/RUNNING; Tatami standalone
        // remains unsupported. NEVER turn confirmed metadata into approval.
        if (design.format == "QA_TATAMI_REVIEW") {
            gates += RasterQaGate(
                "Perfil PES e bastidor da máquina",
                RasterQaGateStatus.MISSING,
                "Análise PES individual de Tatami ainda não disponibilizada."
            )
        } else {
            val target = RasterPesTargetCompatibility.assess(
                design,hoop,machine,pesVersion
            )
            gates += RasterQaGate(
                "Perfil PES e bastidor da máquina",
                if (target.isFailure) RasterQaGateStatus.FAIL
                else if (target.getOrThrow().machineProfileConfirmed &&
                    target.getOrThrow().pesVersionConfirmed
                ) RasterQaGateStatus.PASS else RasterQaGateStatus.MISSING,
                target.exceptionOrNull()?.message
                    ?: target.getOrThrow().warnings.joinToString(" ")
            )
        }
        // No 8..512px processing bitmap can, by itself, prove faithful
        // reproduction of the originally uploaded full-resolution artwork.
        // In particular the Vasos 96x96 fixture is only a QA miniature.
        val originalEvidence = if(sourceImage==null) {
            "A imagem fonte original completa não foi fornecida."
        } else if(sourceImage.width<=128 || sourceImage.height<=128) {
            "Referência ${sourceImage.width} × ${sourceImage.height} " +
                "pixels: miniatura ou baixa resolução, insuficiente para " +
                "homologar letras, curvas e detalhes da arte original."
        } else {
            "Imagem processada de ${sourceImage.width} × " +
                "${sourceImage.height} pixels, sem prova de resolução " +
                "integral, checksum da fonte ou comparação visual certificada."
        }
        gates += RasterQaGate(
            "Referência da arte em resolução integral",
            RasterQaGateStatus.MISSING,
            originalEvidence
        )
        gates += RasterQaGate(
            "Codificação PES e roundtrip independente",
            RasterQaGateStatus.MISSING,
            "Nenhuma codificação PES de imagem experimental foi liberada."
        )
        gates += RasterQaGate(
            "Prova de costura real e calibração de tecido",
            RasterQaGateStatus.MISSING,
            "Não existe comprovação física de agulha, linha, entretela e tecido."
        )
        RasterQaReadinessReport(
            format=design.format,
            stitchCount=design.stitchCount,
            jumpCount=design.jumpCount,
            colorChanges=design.colorChanges,
            hoop=hoop,
            gates=gates,
            threadLengthMm=density.getOrNull()?.sewnThreadLengthMm,
            peakThreadLengthMmPerSquareMm=
                density.getOrNull()?.peakThreadLengthMmPerCell,
            sourceCoverage=coverage,
            sourceOffInkFraction=offInk,
            sourceWrongColorFraction=wrongColor,
            exportPermitted=false,
            machineCertified=false
        )
    }
}
