package com.timachado.brothermatrizes.core.embroidery

/**
 * A PES extension is a FORMAT, not a universal machine profile. Brother models
 * differ in accepted hoop dimensions, transfer limits and PES generations.
 *
 * Never invent model limits or conflate a valid QA stitch sequence with a PES
 * file accepted by every machine. Target values must come from a specific
 * machine's manual or a documented source, not an assumed "universal" model.
 *
 * Does NOT parse/export PES or touch imported PES/JEF/DST designs.
 */
internal data class PesTargetProfile(
    val model: String?,
    val usableWidthMm: Float?,
    val usableHeightMm: Float?,
    val maxStitches: Int?,
    val maxColorChanges: Int?,
    val acceptedPesVersions: Set<Int>?,
    val limitsVerifiedAgainstManual: Boolean
) {
    companion object {
        fun unknown(): PesTargetProfile = PesTargetProfile(
            model = null,
            usableWidthMm = null,
            usableHeightMm = null,
            maxStitches = null,
            maxColorChanges = null,
            acceptedPesVersions = null,
            limitsVerifiedAgainstManual = false
        )
    }
}

internal data class PesTargetAssessment(
    val stitchCount: Int,
    val colorChanges: Int,
    val widthMm: Float,
    val heightMm: Float,
    val machineProfileConfirmed: Boolean,
    val pesVersionConfirmed: Boolean,
    val exportPermitted: Boolean,
    val warnings: List<String>
)

/**
 * Checks the commands and workspace against a DESCRIBED target, and records
 * missing evidence. Even full metadata cannot release an experimental PES
 * automatically: physical QA, file encoding and a model-specific stitch-out
 * are independent gates.
 */
internal object RasterPesTargetCompatibility {
    fun assess(
        design: EmbroideryDesign,
        hoop: HoopProfile,
        target: PesTargetProfile = PesTargetProfile.unknown(),
        intendedPesVersion: Int? = null
    ): Result<PesTargetAssessment> = runCatching {
        require(
            design.format in setOf(
                "QA_AUTO_STITCH", "QA_SATIN_REVIEW", "QA_RUNNING_REVIEW"
            ) && design.isModified && design.sourceBytes.isEmpty() &&
                design.hoopProfile == hoop && design.points.size in 3..90_000 &&
                design.points.last().command == StitchCommand.END &&
                design.points.count { it.command == StitchCommand.END } == 1
        ) {
            "Compatibilidade só pode ser analisada sobre a prévia QA não exportável."
        }
        require(target.model == null || target.model.isNotBlank()) {
            "Informe um modelo válido da máquina."
        }
        require(!target.limitsVerifiedAgainstManual ||
            (!target.model.isNullOrBlank() &&
                target.usableWidthMm != null &&
                target.usableHeightMm != null &&
                target.maxStitches != null &&
                target.maxColorChanges != null &&
                !target.acceptedPesVersions.isNullOrEmpty())
        ) {
            "Perfil confirmado exige modelo, bastidor, limites e versões PES documentadas."
        }
        require(target.usableWidthMm == null ||
            (target.usableWidthMm.isFinite() && target.usableWidthMm > 0f)
        )
        require(target.usableHeightMm == null ||
            (target.usableHeightMm.isFinite() && target.usableHeightMm > 0f)
        )
        require(target.maxStitches == null || target.maxStitches > 0)
        require(target.maxColorChanges == null || target.maxColorChanges >= 0)
        require(target.acceptedPesVersions == null ||
            target.acceptedPesVersions.all { it > 0 })
        require(intendedPesVersion == null || intendedPesVersion > 0)

        val stitches = design.points.count { it.command == StitchCommand.STITCH }
        val jumps = design.points.count { it.command == StitchCommand.JUMP }
        val changes = design.points.count {
            it.command == StitchCommand.COLOR_CHANGE
        }
        require(stitches > 0 && jumps > 0 &&
            design.stitchCount == stitches &&
            design.jumpCount == jumps &&
            design.colorChanges == changes && design.endFound
        ) {
            "Contagem de pontos, saltos ou trocas de cor inconsistente."
        }
        require(design.threadColors.isNotEmpty() &&
            design.points.all { it.colorIndex in design.threadColors.indices } &&
            design.points.none {
                it.command == StitchCommand.SEQUIN ||
                    it.command == StitchCommand.STOP
            }
        ) {
            "Mapeamento de cores ou comando não suportado."
        }
        val sewnAndTravel = design.points.filter {
            it.command == StitchCommand.STITCH ||
                it.command == StitchCommand.JUMP
        }
        require(sewnAndTravel.isNotEmpty() &&
            design.bounds.minXUnits == sewnAndTravel.minOf { it.xUnits } &&
            design.bounds.maxXUnits == sewnAndTravel.maxOf { it.xUnits } &&
            design.bounds.minYUnits == sewnAndTravel.minOf { it.yUnits } &&
            design.bounds.maxYUnits == sewnAndTravel.maxOf { it.yUnits }
        ) {
            "Limites declarados não correspondem às coordenadas dos pontos."
        }
        require(design.bounds.widthMm >= 0f &&
            design.bounds.heightMm >= 0f &&
            design.bounds.widthMm <= hoop.usableWidthMm &&
            design.bounds.heightMm <= hoop.usableHeightMm
        ) {
            "O desenho ultrapassa a área útil do bastidor selecionado."
        }
        val limitX = hoop.usableWidthMm * 5f
        val limitY = hoop.usableHeightMm * 5f
        require(design.points.all {
            kotlin.math.abs(it.xUnits.toFloat()) <= limitX &&
                kotlin.math.abs(it.yUnits.toFloat()) <= limitY
        }) {
            "Há pontos fora do bastidor, mesmo com dimensões totais reduzidas."
        }
        target.usableWidthMm?.let { w ->
            require(design.bounds.widthMm <= w &&
                design.points.all { kotlin.math.abs(it.xUnits.toFloat()) <= w * 5f }
            ) {
                "A matriz ultrapassa a largura confirmada da máquina de destino."
            }
        }
        target.usableHeightMm?.let { h ->
            require(design.bounds.heightMm <= h &&
                design.points.all { kotlin.math.abs(it.yUnits.toFloat()) <= h * 5f }
            ) {
                "A matriz ultrapassa a altura confirmada da máquina de destino."
            }
        }
        target.maxStitches?.let { maximum ->
            require(stitches <= maximum) {
                "Quantidade de pontos excede a capacidade informada do equipamento."
            }
        }
        target.maxColorChanges?.let { maximum ->
            require(changes <= maximum) {
                "Quantidade de mudanças de cor excede a capacidade informada."
            }
        }
        if (intendedPesVersion != null && target.acceptedPesVersions != null) {
            require(intendedPesVersion in target.acceptedPesVersions) {
                "Esta versão PES não foi confirmada como aceita pelo equipamento."
            }
        }

        val profileConfirmed = target.limitsVerifiedAgainstManual
        val versionConfirmed = profileConfirmed &&
            intendedPesVersion != null &&
            intendedPesVersion in target.acceptedPesVersions.orEmpty()
        val warnings = buildList {
            if (!profileConfirmed) {
                add("Modelo e limites físicos de máquina PES ainda não confirmados.")
            }
            if (!versionConfirmed) {
                add("Versão de gravação PES compatível ainda não confirmada.")
            }
            add("Arquivo PES não foi gerado nem testado no equipamento.")
            add("Agulha, linha, entretela e tecido exigem teste físico supervisionado.")
        }
        // There is deliberately no "ready" path in QA, even if target metadata
        // is complete; validating PES bytes and the stitch-out is separate.
        PesTargetAssessment(
            stitchCount = stitches,
            colorChanges = changes,
            widthMm = design.bounds.widthMm,
            heightMm = design.bounds.heightMm,
            machineProfileConfirmed = profileConfirmed,
            pesVersionConfirmed = versionConfirmed,
            exportPermitted = false,
            warnings = warnings
        )
    }
}
