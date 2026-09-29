package com.timachado.brothermatrizes.core.embroidery

data class EmbroideryIntegrityReport(
    val design: EmbroideryDesign,
    val warnings: List<String>
)

object EmbroideryIntegrity {
    private const val MAX_POINTS =
        2_000_000

    fun normalize(
        design: EmbroideryDesign
    ): Result<EmbroideryIntegrityReport> =
        runCatching {
            require(
                design.points
                    .isNotEmpty()
            ) {
                "A matriz não possui comandos."
            }

            require(
                design.points
                    .size <=
                    MAX_POINTS
            ) {
                "A matriz excede o limite seguro de comandos."
            }

            var stitches =
                0

            var jumps =
                0

            var colorChanges =
                0

            var endFound =
                false

            var hasCoordinate =
                false

            var minX =
                0

            var maxX =
                0

            var minY =
                0

            var maxY =
                0

            design.points
                .forEach {
                        point ->
                    require(
                        point.colorIndex >=
                            0
                    ) {
                        "A matriz possui índice de cor inválido."
                    }

                    when (
                        point.command
                    ) {
                        StitchCommand.STITCH ->
                            stitches++

                        StitchCommand.JUMP ->
                            jumps++

                        StitchCommand.COLOR_CHANGE ->
                            colorChanges++

                        StitchCommand.END ->
                            endFound =
                                true

                        else ->
                            Unit
                    }

                    if (
                        point.command !=
                            StitchCommand.END
                    ) {
                        if (
                            !hasCoordinate
                        ) {
                            minX =
                                point.xUnits

                            maxX =
                                point.xUnits

                            minY =
                                point.yUnits

                            maxY =
                                point.yUnits

                            hasCoordinate =
                                true
                        } else {
                            minX =
                                minOf(
                                    minX,
                                    point.xUnits
                                )

                            maxX =
                                maxOf(
                                    maxX,
                                    point.xUnits
                                )

                            minY =
                                minOf(
                                    minY,
                                    point.yUnits
                                )

                            maxY =
                                maxOf(
                                    maxY,
                                    point.yUnits
                                )
                        }
                    }
                }

            require(
                stitches >
                    0
            ) {
                "A matriz não possui pontos de costura."
            }

            require(
                hasCoordinate
            ) {
                "A matriz não possui coordenadas válidas."
            }

            val bounds =
                EmbroideryBounds(
                    minXUnits =
                        minX,
                    maxXUnits =
                        maxX,
                    minYUnits =
                        minY,
                    maxYUnits =
                        maxY
                )

            val warnings =
                buildList {
                    if (
                        !endFound
                    ) {
                        add(
                            "O arquivo não informou comando END."
                        )
                    }

                    if (
                        design.stitchCount !=
                            stitches
                    ) {
                        add(
                            "A contagem de pontos foi recalculada."
                        )
                    }

                    if (
                        design.jumpCount !=
                            jumps
                    ) {
                        add(
                            "A contagem de saltos foi recalculada."
                        )
                    }

                    if (
                        design.colorChanges !=
                            colorChanges
                    ) {
                        add(
                            "A contagem de trocas de cor foi recalculada."
                        )
                    }

                    if (
                        design.bounds !=
                            bounds
                    ) {
                        add(
                            "Os limites da matriz foram recalculados."
                        )
                    }
                }

            val normalized =
                design.copy(
                    format =
                        design.format
                            .trim()
                            .uppercase(),
                    bounds =
                        bounds,
                    stitchCount =
                        stitches,
                    jumpCount =
                        jumps,
                    colorChanges =
                        colorChanges,
                    endFound =
                        endFound
                )

            EmbroideryIntegrityReport(
                design =
                    normalized,
                warnings =
                    warnings
            )
        }
}
