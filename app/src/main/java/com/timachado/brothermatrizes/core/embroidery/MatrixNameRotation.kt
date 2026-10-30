package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Rotação REAL da matriz e do nome vinculado, não apenas da camada de desenho.
 * Todos os comandos/cores mantêm sua ordem e o PES de origem nunca é gravado.
 */
object MatrixNameRotation {
    data class Rotation(
        val background: EmbroideryDesign,
        val nameXUnits: Int,
        val nameYUnits: Int,
        val nameRotationDegrees: Float
    )

    /**
     * Ajusta SOMENTE o tamanho da matriz-base, em torno de seu próprio centro.
     * Não toca nos comandos, nas coordenadas ou nas configurações do nome.
     * O editor recompõe depois a camada do nome a partir dos mesmos parâmetros.
     */
    fun resizeLetter(
        background: EmbroideryDesign,
        factor: Float,
        hoop: HoopProfile
    ): Result<EmbroideryDesign> = runCatching {
        require(factor.isFinite() && factor in 0.8f..1.2f) {
            "Ajuste da letra fora dos limites seguros (80% a 120%)."
        }
        val source = background.editableName?.original ?: background
        require(source.points.any { it.command == StitchCommand.STITCH }) {
            "A letra não contém pontos de bordado."
        }
        val cx = (source.bounds.minXUnits.toLong() +
            source.bounds.maxXUnits.toLong()) / 2.0
        val cy = (source.bounds.minYUnits.toLong() +
            source.bounds.maxYUnits.toLong()) / 2.0
        fun rescalePoint(point: EmbroideryPoint): EmbroideryPoint {
            val x = (cx + (point.xUnits - cx) * factor).roundToInt()
            val y = (cy + (point.yUnits - cy) * factor).roundToInt()
            return point.copy(xUnits = x, yUnits = y)
        }
        val adjusted = MatrixEditor.apply(source,
            EditTransform(scale = factor)).copy(
                guidePoints = source.guidePoints.map(::rescalePoint),
                sourceBytes = ByteArray(0),
                hoopProfile = hoop,
                hoopOrientation = background.hoopOrientation,
                isModified = true,
                editableName = null,
                fileName = source.fileName.substringBeforeLast('.').take(56) +
                    "-letra-ajustada." + source.format.lowercase()
            )
        val result = EmbroideryIntegrity.normalize(adjusted).getOrThrow().design
        val rotatedHoop = result.shouldRotateHoop(hoop)
        val usableWidth = if (rotatedHoop)
            hoop.usableHeightMm else hoop.usableWidthMm
        val usableHeight = if (rotatedHoop)
            hoop.usableWidthMm else hoop.usableHeightMm
        require(result.bounds.widthMm <= usableWidth &&
            result.bounds.heightMm <= usableHeight) {
            "A letra ultrapassa a área segura do bastidor nesta orientação."
        }
        if (result.hoopPositionLocked) HoopLayerPositioning.validateHoop(result, hoop)
        result
    }

    fun normalizeDegrees(degrees: Float): Float {
        require(degrees.isFinite()) { "Ângulo inválido." }
        val result = ((degrees + 180f) % 360f + 360f) % 360f - 180f
        return if (result == -180f && degrees > 0) 180f else result
    }

    fun rotate(
        background: EmbroideryDesign,
        nameXUnits: Int,
        nameYUnits: Int,
        nameRotationDegrees: Float,
        deltaDegrees: Float,
        hoop: HoopProfile
    ): Result<Rotation> = runCatching {
        require(deltaDegrees.isFinite() && deltaDegrees in -360f..360f) {
            "Rotação fora do limite permitido."
        }
        require(background.points.any { it.command == StitchCommand.STITCH }) {
            "A matriz não contém pontos para girar."
        }
        val source = background.editableName?.original ?: background
        val cx = (source.bounds.minXUnits.toLong() +
            source.bounds.maxXUnits.toLong()) / 2.0
        val cy = (source.bounds.minYUnits.toLong() +
            source.bounds.maxYUnits.toLong()) / 2.0
        val radians = Math.toRadians(deltaDegrees.toDouble())
        val cosine = cos(radians)
        val sine = sin(radians)
        fun rotateXY(x: Int, y: Int): Pair<Int, Int> {
            val px = x - cx
            val py = y - cy
            return (cx + px * cosine - py * sine).roundToInt() to
                (cy + px * sine + py * cosine).roundToInt()
        }
        val transformed = MatrixEditor.apply(
            source, EditTransform(rotationDegrees = deltaDegrees)
        )
        // MatrixEditor transforma pontos bordáveis; guias precisam acompanhar
        // o MESMO pivô da matriz para a prévia não ficar desalinhada.
        val guides = source.guidePoints.map { point ->
            val (x, y) = rotateXY(point.xUnits, point.yUnits)
            point.copy(xUnits = x, yUnits = y)
        }
        val result = transformed.copy(
            sourceBytes = ByteArray(0),
            guidePoints = guides,
            // Cada aplicação cria um arquivo NOVO; nunca substituir PES original.
            fileName = source.fileName.substringBeforeLast('.').take(60) +
                "-girada." + source.format.lowercase(),
            hoopProfile = hoop,
            editableName = null,
            isModified = true
        )
        // Bastidor retangular pode ser usado girado, mas a área ÚTIL precisa
        // comportar a matriz já rotacionada com as margens de 5 mm.
        val fits = (result.bounds.widthMm <= hoop.usableWidthMm &&
            result.bounds.heightMm <= hoop.usableHeightMm) ||
            (result.bounds.widthMm <= hoop.usableHeightMm &&
                result.bounds.heightMm <= hoop.usableWidthMm)
        require(fits) {
            "A rotação ultrapassa a área útil do bastidor " +
                hoop.displayName + ". Selecione um bastidor maior."
        }
        if (result.hoopPositionLocked) {
            HoopLayerPositioning.validateHoop(result, hoop)
        }
        val (newX, newY) = rotateXY(nameXUnits, nameYUnits)
        Rotation(
            background = result,
            nameXUnits = newX,
            nameYUnits = newY,
            nameRotationDegrees = normalizeDegrees(
                nameRotationDegrees + deltaDegrees
            )
        )
    }
}
