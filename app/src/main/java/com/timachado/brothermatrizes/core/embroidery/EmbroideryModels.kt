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
        MachineFinishingInfo? = null
) {
    val colorCount: Int
        get() = maxOf(
            if (points.isEmpty()) 0 else colorChanges + 1,
            threadColors.size
        )
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
    val threadColors: List<Int>? = null
)

sealed interface EmbroideryLoadResult {
    data class Success(val design: EmbroideryDesign) : EmbroideryLoadResult

    data class Error(
        val userMessage: String,
        val technicalMessage: String? = null
    ) : EmbroideryLoadResult
}
