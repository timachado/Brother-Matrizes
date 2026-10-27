package com.timachado.brothermatrizes.core.embroidery

/**
 * Composição aditiva de nome sobre uma matriz já aberta.
 *
 * Recebe sempre a matriz ORIGINAL; a prévia é reconstruída a cada edição, de
 * modo que trocar "Maria" por "Sophia" nunca duplica as letras anteriores.
 * Não tenta adivinhar quais pontos de PES/DST pertencem a um nome preexistente.
 */
object NamePersonalization {
    fun add(
        original: EmbroideryDesign,
        options: TextMatrixOptions,
        centerXUnits: Int,
        centerYUnits: Int
    ): Result<EmbroideryDesign> = runCatching {
        require(original.points.any { it.command == StitchCommand.STITCH }) {
            "A matriz original não contém bordado."
        }
        require(original.format.uppercase() in listOf("PES", "DST", "JEF")) {
            "Esta matriz não possui exportação compatível para nomes."
        }
        require(original.points.lastOrNull()?.command == StitchCommand.END) {
            "A sequência original precisa terminar em END antes de receber nomes."
        }
        require(original.points.dropLast(1).none { it.command == StitchCommand.END }) {
            "A matriz possui um END intermediário; edição bloqueada para proteger o desenho."
        }

        val generated = TextMatrixGenerator.generate(
            options.copy(
                outputFormat = original.format.uppercase(),
                hoopProfile = null,
                enforceHoop = false
            )
        ).getOrThrow()

        val positioned = MatrixEditor.apply(
            generated,
            EditTransform(
                centerAtOrigin = true,
                offsetXUnits = centerXUnits,
                offsetYUnits = centerYUnits
            )
        )

        val preserved = original.points.dropLast(1)
        val finalOriginal = preserved.last()
        val nextColorIndex = (preserved.maxOfOrNull { it.colorIndex } ?: 0) + 1
        require(nextColorIndex < 256) {
            "A matriz atingiu o limite seguro de blocos de cor."
        }
        val personalized = positioned.points.dropLast(1).map {
            it.copy(colorIndex = nextColorIndex)
        }
        require(personalized.any { it.command == StitchCommand.STITCH }) {
            "O nome não produziu pontadas."
        }

        val first = personalized.first()
        val last = personalized.last()
        // TRIM + COLOR_CHANGE + JUMP explícitos: jamais costurar uma linha
        // reta entre a moldura original e a primeira letra.
        val joined = buildList(preserved.size + personalized.size + 4) {
            addAll(preserved)
            add(finalOriginal.copy(command = StitchCommand.TRIM))
            add(finalOriginal.copy(command = StitchCommand.COLOR_CHANGE, colorIndex = nextColorIndex))
            add(first.copy(command = StitchCommand.JUMP))
            addAll(personalized)
            add(last.copy(command = StitchCommand.END))
        }

        val palette = original.threadColors.toMutableList()
        while (palette.size <= nextColorIndex) {
            palette.add(original.threadColors.lastOrNull() ?: 0xE6BE70)
        }
        palette[nextColorIndex] = options.color

        val stem = original.fileName.substringBeforeLast('.').ifBlank { "matriz" }
        val fileName = stem.take(48) + "-nome." + original.format.lowercase()
        val provisional = original.copy(
            fileName = fileName,
            label = original.label,
            points = joined,
            sourceBytes = ByteArray(0),
            guidePoints = emptyList(),
            threadColors = palette,
            isModified = true,
            // A origem e orientação da matriz são preservadas, sem mover flores.
            hoopProfile = original.hoopProfile ?: options.hoopProfile
        )
        val validated = EmbroideryIntegrity.normalize(provisional)
            .getOrThrow().design
        require(validated.points.size <= EmbroideryStressPolicy.MAX_GENERATED_COMMANDS) {
            "A matriz personalizada ultrapassou o limite de comandos seguros."
        }
        val hoop = validated.hoopProfile
        if (hoop != null) {
            require(HoopValidator.validate(validated, hoop).fits) {
                "O nome ultrapassa a área segura do bastidor. Reduza ou mova o texto."
            }
        }
        validated
    }
}
