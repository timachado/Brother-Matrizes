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
