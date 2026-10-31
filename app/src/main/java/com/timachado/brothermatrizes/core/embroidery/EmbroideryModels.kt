package com.timachado.brothermatrizes.core.embroidery

enum class StitchCommand {
    STITCH,
    JUMP,
    TRIM,
    STOP,
    COLOR_CHANGE,
    SEQUIN,
    END
}

data class EmbroideryPoint(
    val xUnits: Int,
    val yUnits: Int,
    val command: StitchCommand,
    val colorIndex: Int
)

data class EmbroideryBounds(
    val minXUnits: Int,
    val maxXUnits: Int,
    val minYUnits: Int,
    val maxYUnits: Int
) {
    val widthMm: Float
        get() =
            (
                maxXUnits.toLong() -
                    minXUnits.toLong()
                ) /
                10f

    val heightMm: Float
        get() =
            (
                maxYUnits.toLong() -
                    minYUnits.toLong()
                ) /
                10f
}

data class MachineFinishingInfo(
    val tieInEnabled: Boolean,
    val tieOffEnabled: Boolean,
    val autoTrimLongJumps: Boolean,
    val trimJumpThresholdMm: Float,
    val optimizeTravel: Boolean
)

/**
 * STANDARD exige distância física entre nome e desenho.
 * MONOGRAM_OVERLAY permite sobreposição intencional (ex.: inicial + nome),
 * sem remover as verificações de bastidor, comandos ou integridade.
 */
enum class NameCompositionMode { STANDARD, MONOGRAM_OVERLAY }

/** AUTO mantém o comportamento legado; as demais opções fixam a orientação
 * física do bastidor independente da proporção ou rotação da letra/nome. */
enum class HoopOrientation { AUTO, LANDSCAPE, PORTRAIT }


/** Metadado temporário de edição; PES/DST não guarda texto editável. */
data class NamePersonalizationLayer(
    val original: EmbroideryDesign,
    val options: TextMatrixOptions,
    val centerXUnits: Int,
    val centerYUnits: Int,
    val compositionMode: NameCompositionMode = NameCompositionMode.STANDARD
)

data class EmbroideryDesign(
    val fileName: String,
    val format: String,
    val label: String?,
    val points: List<EmbroideryPoint>,
    val bounds: EmbroideryBounds,
    val stitchCount: Int,
    val jumpCount: Int,
    val colorChanges: Int,
    val endFound: Boolean,
    val sourceBytes: ByteArray,
    val guidePoints: List<EmbroideryPoint> = emptyList(),
    val threadColors: List<Int> = emptyList(),
    val sourceYAxisDown: Boolean = false,
    val isModified: Boolean = false,
    val hoopProfile: HoopProfile? = null,
    val fabricProfile: FabricProfile? = null,
    val machineFinishing:
        MachineFinishingInfo? = null,
    val editableName: NamePersonalizationLayer? = null,
    // v9: marcador explícito de coordenadas físicas vinculadas à origem do
    // bastidor; impede que Viewer recentralize o desenho ao reabrir projeto.
    val hoopPositionLocked: Boolean = false,
    val hoopOrientation: HoopOrientation = HoopOrientation.AUTO
) {
    val colorCount: Int
        get() = maxOf(
            if (points.isEmpty()) 0 else colorChanges + 1,
            threadColors.size
        )
}

/** Mesma orientação usada pelo canvas, posicionamento e validação de saída. */
fun EmbroideryDesign.shouldRotateHoop(hoop: HoopProfile): Boolean {
    if (hoop.widthMm == hoop.heightMm) return false
    val background = editableName?.original?.bounds ?: bounds
    val wantLandscape = when (hoopOrientation) {
        HoopOrientation.LANDSCAPE -> true
        HoopOrientation.PORTRAIT -> false
        HoopOrientation.AUTO -> background.widthMm > background.heightMm
    }
    return wantLandscape != (hoop.widthMm > hoop.heightMm)
}

data class ConvertedMatrix(
    val fileName: String,
    val format: String,
    val bytes: ByteArray
)

data class EditTransform(
    val scale: Float = 1f,
    val rotationDegrees: Float = 0f,
    val offsetXUnits: Int = 0,
    val offsetYUnits: Int = 0,
    val mirrorHorizontal: Boolean = false,
    val mirrorVertical: Boolean = false,
    val centerAtOrigin: Boolean = false,
    val threadColors: List<Int>? = null,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f
)

sealed interface EmbroideryLoadResult {
    data class Success(val design: EmbroideryDesign) : EmbroideryLoadResult

    data class Error(
        val userMessage: String,
        val technicalMessage: String? = null
    ) : EmbroideryLoadResult
}
