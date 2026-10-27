package com.timachado.brothermatrizes.core.embroidery

/**
 * Composição aditiva de nome sobre uma matriz já aberta.
 *
 * Recebe sempre a matriz ORIGINAL; a prévia é reconstruída a cada edição, de
 * modo que trocar "Maria" por "Sophia" nunca duplica as letras anteriores.
 * Não tenta adivinhar quais pontos de PES/DST pertencem a um nome preexistente.
 */
object NamePersonalization {
    // O desenho Satin depende de fonte/texto/tamanho, não da posição do dedo.
    // Reutilizar as pontadas enquanto só X/Y muda reduz trabalho e alocações.
    @Volatile private var prepared: Pair<TextMatrixOptions, EmbroideryDesign>? = null

    private fun prepare(
        options: TextMatrixOptions,
        importedGenerator: ((TextMatrixOptions) -> Result<EmbroideryDesign>)?
    ): EmbroideryDesign {
        prepared?.let { if (it.first == options) return it.second }
        synchronized(this) {
            prepared?.let { if (it.first == options) return it.second }
            val newDesign = if (options.importedFontId != null) {
                requireNotNull(importedGenerator) {
                    "Fonte importada indisponível. Selecione ou reimporte o arquivo TTF/OTF."
                }(options).getOrThrow()
            } else {
                TextMatrixGenerator.generate(options).getOrThrow()
            }
            prepared = options to newDesign
            return newDesign
        }
    }

    /**
     * Validação leve para busca automática.
     *
     * NÃO cria nova matriz, copia 12 mil pontos da moldura, calcula hash SHA,
     * normaliza sequência completa ou gera nome de arquivo a cada candidato.
     * Somente quando encontra espaço o autoajuste chama add() UMA vez.
     */
    internal fun canPlace(
        original: EmbroideryDesign,
        options: TextMatrixOptions,
        centerXUnits: Int,
        centerYUnits: Int,
        importedGenerator: ((TextMatrixOptions) -> Result<EmbroideryDesign>)? = null
    ): Result<Unit> = runCatching {
        val source = original.editableName?.original ?: original
        require(options.widthScale.isFinite() && options.heightScale.isFinite() &&
            options.widthScale in 0.5f..2.5f && options.heightScale in 0.5f..2.5f
        ) { "Ajuste horizontal ou vertical fora dos limites seguros." }
        val text = prepare(
            options.copy(
                rotationDegrees = 0f,
                widthScale = 1f,
                heightScale = 1f,
                outputFormat = source.format.uppercase(),
                hoopProfile = null,
                enforceHoop = false
            ),
            importedGenerator
        )
        val positioned = MatrixEditor.apply(
            text,
            EditTransform(
                centerAtOrigin = true,
                offsetXUnits = centerXUnits,
                offsetYUnits = centerYUnits,
                rotationDegrees = options.rotationDegrees,
                scaleX = options.widthScale,
                scaleY = options.heightScale,
                // Geradores BM usam Y para cima; Skia TTF/OTF já emite
                // coordenadas Y para baixo. Espelhar apenas se as
                // convenções de origem forem DIFERENTES.
                mirrorVertical = source.sourceYAxisDown != text.sourceYAxisDown
            )
        )
        NamePlacementSafety.validate(source, positioned).getOrThrow()
        val hoop = source.hoopProfile ?: options.hoopProfile
        if (hoop != null) {
            val widthUnits = maxOf(source.bounds.maxXUnits,
                positioned.bounds.maxXUnits).toLong() -
                minOf(source.bounds.minXUnits, positioned.bounds.minXUnits)
            val heightUnits = maxOf(source.bounds.maxYUnits,
                positioned.bounds.maxYUnits).toLong() -
                minOf(source.bounds.minYUnits, positioned.bounds.minYUnits)
            require(
                (widthUnits <= (hoop.usableWidthMm * 10).toLong() &&
                    heightUnits <= (hoop.usableHeightMm * 10).toLong()) ||
                (widthUnits <= (hoop.usableHeightMm * 10).toLong() &&
                    heightUnits <= (hoop.usableWidthMm * 10).toLong())
            ) { "Nome ultrapassa o bastidor selecionado." }
        }
    }

    fun add(
        original: EmbroideryDesign,
        options: TextMatrixOptions,
        centerXUnits: Int,
        centerYUnits: Int,
        importedGenerator: ((TextMatrixOptions) -> Result<EmbroideryDesign>)? = null
    ): Result<EmbroideryDesign> = runCatching {
        val source = original.editableName?.original ?: original
        require(options.widthScale.isFinite() && options.heightScale.isFinite() &&
            options.widthScale in 0.5f..2.5f && options.heightScale in 0.5f..2.5f
        ) { "Ajuste horizontal ou vertical fora dos limites seguros." }
        require(source.points.any { it.command == StitchCommand.STITCH }) {
            "A matriz original não contém bordado."
        }
        require(source.format.uppercase() in listOf("PES", "DST", "JEF")) {
            "Esta matriz não possui exportação compatível para nomes."
        }
        require(source.points.lastOrNull()?.command == StitchCommand.END) {
            "A sequência original precisa terminar em END antes de receber nomes."
        }
        require(source.points.dropLast(1).none { it.command == StitchCommand.END }) {
            "A matriz possui um END intermediário; edição bloqueada para proteger o desenho."
        }

        val generated = prepare(
            options.copy(
                // Rotation is applied AFTER generation, around the text center.
                // Never rotate the source floral matrix.
                rotationDegrees = 0f,
                widthScale = 1f,
                heightScale = 1f,
                outputFormat = source.format.uppercase(),
                hoopProfile = null,
                enforceHoop = false
            ),
            importedGenerator = importedGenerator
        )

        val positioned = MatrixEditor.apply(
            generated,
            EditTransform(
                centerAtOrigin = true,
                offsetXUnits = centerXUnits,
                offsetYUnits = centerYUnits,
                rotationDegrees = options.rotationDegrees,
                scaleX = options.widthScale,
                scaleY = options.heightScale,
                // Arquivos PES/JEF usam eixo Y de tela, oposto às letras nativas.
                // Geradores BM usam Y para cima; Skia TTF/OTF já emite
                // coordenadas Y para baixo. Espelhar apenas se as
                // convenções de origem forem DIFERENTES.
                mirrorVertical = source.sourceYAxisDown != generated.sourceYAxisDown
            )
        )

        // Bloqueio geométrico: nem o nome nem o percurso Satin podem
        // tocar as flores, borboletas ou as linhas já existentes.
        NamePlacementSafety.validate(source, positioned).getOrThrow()

        val preserved = source.points.dropLast(1)
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

        val palette = source.threadColors.toMutableList()
        while (palette.size <= nextColorIndex) {
            palette.add(source.threadColors.lastOrNull() ?: 0xE6BE70)
        }
        palette[nextColorIndex] = options.color

        val stem = source.fileName.substringBeforeLast('.').ifBlank { "matriz" }
        val slug = java.text.Normalizer.normalize(
            options.text.trim(), java.text.Normalizer.Form.NFD
        ).replace(Regex("\\p{M}+"), "")
            .lowercase(java.util.Locale.ROOT)
            .replace(Regex("[^a-z0-9_-]+"), "-")
            .trim('-').take(18).ifBlank { "nome" }
        val identity = listOf(
            slug, options.font.name.lowercase(java.util.Locale.ROOT),
            options.heightMm.toString(), options.spacingMm.toString(),
            options.satinDensityMm.toString(), options.color.toString(),
            centerXUnits.toString(), centerYUnits.toString(),
            options.rotationDegrees.toString(), options.importedFontId.orEmpty(),
            options.widthScale.toString(), options.heightScale.toString()
        ).joinToString("|")
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8))
            .take(4)
            .joinToString("") { "%02x".format(it) }
        val fileName = stem.take(35) + "-nome-" + slug + "-" + digest +
            "." + source.format.lowercase(java.util.Locale.ROOT)
        val provisional = source.copy(
            fileName = fileName,
            label = source.label,
            points = joined,
            sourceBytes = ByteArray(0),
            guidePoints = source.guidePoints,
            threadColors = palette,
            isModified = true,
            // A origem e orientação da matriz são preservadas, sem mover flores.
            hoopProfile = source.hoopProfile ?: options.hoopProfile,
            editableName = NamePersonalizationLayer(
                original = source,
                options = options,
                centerXUnits = centerXUnits,
                centerYUnits = centerYUnits
            )
        )
        val validated = EmbroideryIntegrity.normalize(provisional)
            .getOrThrow().design
        require(validated.points.size <= EmbroideryStressPolicy.MAX_GENERATED_COMMANDS) {
            "A matriz personalizada ultrapassou o limite de comandos seguros."
        }
        val hoop = validated.hoopProfile
        if (hoop != null) {
            // Bastidores retangulares Brother podem ser montados em retrato.
            // H130X180, por exemplo, é armazenado como 180x130 no perfil,
            // enquanto a matriz de borboletas mede ~105,4x157,6 mm.
            val directFit = HoopValidator.validate(validated, hoop).fits
            val rotatedFit =
                validated.bounds.widthMm <= hoop.usableHeightMm &&
                validated.bounds.heightMm <= hoop.usableWidthMm
            require(directFit || rotatedFit) {
                "O nome ultrapassa a área segura do bastidor nas duas orientações. Reduza ou mova o texto."
            }
        }
        validated
    }
}
