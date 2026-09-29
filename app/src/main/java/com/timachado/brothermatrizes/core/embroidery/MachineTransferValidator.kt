package com.timachado.brothermatrizes.core.embroidery

enum class TransferIssueLevel {
    BLOCKING,
    WARNING
}

data class TransferIssue(
    val level: TransferIssueLevel,
    val message: String
)

data class MachineTransferValidation(
    val format: String,
    val hoop: HoopProfile,
    val issues: List<TransferIssue>
) {
    val blockingIssues: List<TransferIssue>
        get() =
            issues.filter {
                it.level ==
                    TransferIssueLevel.BLOCKING
            }

    val warnings: List<TransferIssue>
        get() =
            issues.filter {
                it.level ==
                    TransferIssueLevel.WARNING
            }

    val ready: Boolean
        get() =
            blockingIssues.isEmpty()
}

object MachineTransferValidator {
    fun recommendedHoop(
        design: EmbroideryDesign
    ): HoopProfile =
        design.hoopProfile
            ?.takeIf {
                fitsTransferHoop(
                    design = design,
                    hoop = it
                )
            }
            ?: HoopProfile
                .entries
                .firstOrNull {
                    fitsTransferHoop(
                        design = design,
                        hoop = it
                    )
                }
            ?: HoopProfile
                .entries
                .last()

    /*
     * A tela "Enviar para a máquina" deve aceitar o bastidor físico
     * nas duas orientações. Os perfis retangulares são armazenados
     * internamente como largura x altura, mas a máquina permite girar
     * o bastidor 90 graus. Mantemos o HoopValidator global intacto e
     * aplicamos esta exceção somente no fluxo de transferência.
     */
    private fun fitsTransferHoop(
        design: EmbroideryDesign,
        hoop: HoopProfile
    ): Boolean {
        val designWidth =
            design.bounds.widthMm

        val designHeight =
            design.bounds.heightMm

        /*
         * Para envio à máquina usamos a dimensão física nominal do
         * bastidor. A margem segura continua existindo nas validações
         * globais de criação/auto-fit, mas não deve reduzir novamente
         * a capacidade física nesta tela de transferência.
         */
        val hoopWidth =
            hoop.widthMm

        val hoopHeight =
            hoop.heightMm

        val fitsDirect =
            designWidth <=
                hoopWidth &&
                designHeight <=
                    hoopHeight

        val fitsRotated =
            designWidth <=
                hoopHeight &&
                designHeight <=
                    hoopWidth

        return fitsDirect ||
            fitsRotated
    }

    fun validate(
        design: EmbroideryDesign,
        format: String,
        hoop: HoopProfile
    ): MachineTransferValidation {
        val normalizedFormat =
            format
                .trim()
                .uppercase()

        val issues =
            buildList {
                if (
                    normalizedFormat !in
                        MatrixConverter
                            .supportedFormats
                ) {
                    add(
                        TransferIssue(
                            level =
                                TransferIssueLevel
                                    .BLOCKING,
                            message =
                                "Formato $normalizedFormat não é suportado para envio."
                        )
                    )
                }

                if (
                    design.stitchCount <=
                        0
                ) {
                    add(
                        TransferIssue(
                            level =
                                TransferIssueLevel
                                    .BLOCKING,
                            message =
                                "A matriz não possui pontos de bordado."
                        )
                    )
                }

                if (
                    design.bounds
                        .widthMm <=
                        0f ||
                    design.bounds
                        .heightMm <=
                        0f
                ) {
                    add(
                        TransferIssue(
                            level =
                                TransferIssueLevel
                                    .BLOCKING,
                            message =
                                "As dimensões da matriz são inválidas."
                        )
                    )
                }

                val fits =
                    fitsTransferHoop(
                        design = design,
                        hoop = hoop
                    )

                if (
                    !fits
                ) {
                    add(
                        TransferIssue(
                            level =
                                TransferIssueLevel
                                    .BLOCKING,
                            message =
                                "A matriz não cabe na área útil do bastidor ${hoop.displayName}."
                        )
                    )
                }

                if (
                    !design.endFound
                ) {
                    add(
                        TransferIssue(
                            level =
                                TransferIssueLevel
                                    .WARNING,
                            message =
                                "O arquivo original não informou comando END; o conversor preparará o arquivo de saída."
                        )
                    )
                }

                if (
                    design.colorCount >
                        64
                ) {
                    add(
                        TransferIssue(
                            level =
                                TransferIssueLevel
                                    .WARNING,
                            message =
                                "A matriz possui muitas trocas/blocos de cor. Confirme o limite da sua bordadeira."
                        )
                    )
                }
            }

        return MachineTransferValidation(
            format =
                normalizedFormat,
            hoop =
                hoop,
            issues =
                issues
        )
    }
}
