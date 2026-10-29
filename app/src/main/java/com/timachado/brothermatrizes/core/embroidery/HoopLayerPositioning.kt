package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.roundToInt

/**
 * Posicionamento físico de matriz/nome no bastidor. Nunca confundir com o
 * zoom/pan da visualização: aqui são modificadas as coordenadas de pontos.
 *
 * Em projetos com nome, a matriz-base é o prefixo da sequência; o nome é o
 * sufixo separado por TRIM + COLOR_CHANGE + JUMP. A ordem é preservada.
 */
object HoopLayerPositioning {
    enum class Target { MATRIX, NAME, BOTH }

    /**
     * A primeira entrada no modo de posicionamento mantém o aspecto da
     * visualização anterior (que centralizava um PES importado), convertendo
     * esse enquadramento para coordenadas físicas em torno da origem (0, 0).
     * Uma matriz já posicionada NUNCA é recentralizada novamente.
     */
    fun prepare(design: EmbroideryDesign, hoop: HoopProfile): Result<EmbroideryDesign> =
        runCatching {
            if (design.hoopPositionLocked) {
                validateHoop(design, hoop)
                return@runCatching design
            }
            val bx = design.bounds
            val x = -((bx.minXUnits.toLong() + bx.maxXUnits.toLong()) / 2L)
            val y = -((bx.minYUnits.toLong() + bx.maxYUnits.toLong()) / 2L)
            require(x in Int.MIN_VALUE..Int.MAX_VALUE &&
                y in Int.MIN_VALUE..Int.MAX_VALUE) { "Coordenadas fora do limite." }
            translate(design, x.toInt(), y.toInt(), Target.BOTH, hoop)
                .getOrThrow()
        }

    fun move(
        design: EmbroideryDesign,
        dxUnits: Int,
        dyUnits: Int,
        target: Target,
        hoop: HoopProfile
    ): Result<EmbroideryDesign> = runCatching {
        require(design.hoopPositionLocked) {
            "Ative primeiro o modo de posicionamento no bastidor."
        }
        translate(design, dxUnits, dyUnits, target, hoop).getOrThrow()
    }

    fun validateHoop(design: EmbroideryDesign, hoop: HoopProfile) {
        // A orientação do bastidor é a mesma usada pelo EmbroideryCanvas.
        val bounds = design.bounds
        val designLandscape = bounds.widthMm > bounds.heightMm
        val hoopLandscape = hoop.widthMm > hoop.heightMm
        val rotate = hoop.widthMm != hoop.heightMm &&
            designLandscape != hoopLandscape
        val width = (if (rotate) hoop.usableHeightMm else
            hoop.usableWidthMm) * 10f
        val height = (if (rotate) hoop.usableWidthMm else
            hoop.usableHeightMm) * 10f
        val halfW = width / 2f
        val halfH = height / 2f
        require(bounds.minXUnits >= -halfW - 1f &&
            bounds.maxXUnits <= halfW + 1f &&
            bounds.minYUnits >= -halfH - 1f &&
            bounds.maxYUnits <= halfH + 1f) {
            "A matriz ou o nome ultrapassa a área útil do bastidor. " +
                "A posição anterior foi mantida."
        }
    }

    private fun translate(
        design: EmbroideryDesign, dx: Int, dy: Int, target: Target,
        hoop: HoopProfile
    ): Result<EmbroideryDesign> = runCatching {
        val layer = design.editableName
        require(target != Target.NAME || layer != null) {
            "Adicione um nome editável antes de movê-lo separadamente."
        }
        require(design.points.isNotEmpty()) { "A matriz está vazia." }
        if (target != Target.BOTH && layer != null) {
            val prefix = layer.original.points.dropLast(1)
            require(design.points.size > prefix.size + 3 &&
                design.points.take(prefix.size) == prefix) {
                "A separação entre matriz e nome não está íntegra."
            }
        }
        fun safeAdd(a: Int, b: Int): Int {
            val sum = a.toLong() + b
            require(sum in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
                "A posição excede os limites numéricos seguros."
            }
            return sum.toInt()
        }
        fun point(p: EmbroideryPoint) = p.copy(
            xUnits = safeAdd(p.xUnits, dx),
            yUnits = safeAdd(p.yUnits, dy)
        )
        val nameBoundary = layer?.original?.points?.size?.minus(1) ?: -1
        val nextPoints = design.points.mapIndexed { i, p ->
            when (target) {
                Target.BOTH -> point(p)
                Target.MATRIX -> if (layer == null || i <= nameBoundary + 1)
                    point(p) else p
                Target.NAME -> if (i > nameBoundary + 1) point(p) else p
            }
        }
        val shiftedOriginal = layer?.original?.let { original ->
            if (target == Target.NAME) original else original.copy(
                points = original.points.map(::point),
                guidePoints = original.guidePoints.map(::point),
                sourceBytes = ByteArray(0),
                isModified = true,
                hoopProfile = hoop,
                hoopPositionLocked = true
            ).let { EmbroideryIntegrity.normalize(it).getOrThrow().design }
        }
        val shiftedLayer = layer?.copy(
            original = requireNotNull(shiftedOriginal),
            centerXUnits = if (target == Target.MATRIX)
                layer.centerXUnits else safeAdd(layer.centerXUnits, dx),
            centerYUnits = if (target == Target.MATRIX)
                layer.centerYUnits else safeAdd(layer.centerYUnits, dy)
        )
        val moved = EmbroideryIntegrity.normalize(
            design.copy(
                points = nextPoints,
                guidePoints = if (target == Target.NAME)
                    design.guidePoints else design.guidePoints.map(::point),
                editableName = shiftedLayer,
                sourceBytes = ByteArray(0),
                isModified = true,
                hoopProfile = hoop,
                hoopPositionLocked = true
            )
        ).getOrThrow().design

        validateHoop(moved, hoop)
        if (shiftedLayer != null) {
            val namePoints = moved.points.drop(nameBoundary + 2)
            val nameOnly = moved.copy(
                points = namePoints,
                editableName = null
            )
            NamePlacementSafety.validate(shiftedLayer.original, nameOnly)
                .getOrThrow()
        }
        moved
    }
}
