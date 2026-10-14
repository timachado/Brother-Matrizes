package com.timachado.brothermatrizes.core.embroidery

enum class HoopProfile(
    val displayName: String,
    val widthMm: Float,
    val heightMm: Float,
    val safeMarginMm: Float = 5f
) {
    H100X100(
        "100 × 100 mm",
        100f,
        100f
    ),
    H130X180(
        "130 × 180 mm",
        180f,
        130f
    ),
    H140X200(
        "140 × 200 mm",
        200f,
        140f
    ),
    H160X260(
        "160 × 260 mm",
        260f,
        160f
    ),
    H200X300(
        "200 × 300 mm",
        300f,
        200f
    );

    val usableWidthMm: Float
        get() =
            (
                widthMm -
                    safeMarginMm *
                        2f
                ).coerceAtLeast(
                    1f
                )

    val usableHeightMm: Float
        get() =
            (
                heightMm -
                    safeMarginMm *
                        2f
                ).coerceAtLeast(
                    1f
                )
}

data class HoopFitResult(
    val fits: Boolean,
    val widthOverflowMm: Float,
    val heightOverflowMm: Float,
    val usableWidthMm: Float,
    val usableHeightMm: Float
)

object HoopValidator {
    fun validate(
        design: EmbroideryDesign,
        hoop: HoopProfile
    ): HoopFitResult {
        val widthOverflow =
            (
                design.bounds.widthMm -
                    hoop.usableWidthMm
                ).coerceAtLeast(
                    0f
                )

        val heightOverflow =
            (
                design.bounds.heightMm -
                    hoop.usableHeightMm
                ).coerceAtLeast(
                    0f
                )

        return HoopFitResult(
            fits =
                widthOverflow <=
                    0f &&
                    heightOverflow <=
                        0f,
            widthOverflowMm =
                widthOverflow,
            heightOverflowMm =
                heightOverflow,
            usableWidthMm =
                hoop.usableWidthMm,
            usableHeightMm =
                hoop.usableHeightMm
        )
    }
}

enum class FabricProfile(
    val displayName: String,
    val helperText: String,
    val satinDensityMm: Float,
    val pullCompensationMm: Float,
    val underlayMode: SatinUnderlayMode,
    val shortStitches: Boolean
) {
    COTTON(
        displayName =
            "Algodão",
        helperText =
            "Tecido estável • ponto de partida equilibrado",
        satinDensityMm =
            0.45f,
        pullCompensationMm =
            0.20f,
        underlayMode =
            SatinUnderlayMode.CENTER,
        shortStitches =
            true
    ),
    KNIT(
        displayName =
            "Malha / camiseta",
        helperText =
            "Mais elástico • reforço e compensação maiores",
        satinDensityMm =
            0.50f,
        pullCompensationMm =
            0.35f,
        underlayMode =
            SatinUnderlayMode.BOTH,
        shortStitches =
            true
    ),
    TOWEL(
        displayName =
            "Toalha / felpudo",
        helperText =
            "Superfície alta • underlay forte para levantar o bordado",
        satinDensityMm =
            0.50f,
        pullCompensationMm =
            0.40f,
        underlayMode =
            SatinUnderlayMode.BOTH,
        shortStitches =
            true
    ),
    DENIM(
        displayName =
            "Jeans / sarja",
        helperText =
            "Tecido rígido • evita excesso de massa de pontos",
        satinDensityMm =
            0.52f,
        pullCompensationMm =
            0.20f,
        underlayMode =
            SatinUnderlayMode.CENTER,
        shortStitches =
            true
    ),
    CAP(
        displayName =
            "Boné estruturado",
        helperText =
            "Base rígida/curva • underlay firme e cobertura mais fechada",
        satinDensityMm =
            0.42f,
        pullCompensationMm =
            0.30f,
        underlayMode =
            SatinUnderlayMode.BOTH,
        shortStitches =
            true
    )
}
