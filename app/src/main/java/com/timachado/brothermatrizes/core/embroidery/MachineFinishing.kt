package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.hypot
import kotlin.math.roundToInt

data class MachineFinishingSettings(
    val tieInEnabled: Boolean = true,
    val tieOffEnabled: Boolean = true,
    val autoTrimLongJumps: Boolean = true,
    val trimJumpThresholdMm: Float = 8f,
    val longStitchWarningMm: Float = 7f,
    val longJumpWarningMm: Float = 12f,
    val lockLengthMm: Float = 0.8f,
    val optimizeTravel: Boolean = true
)

data class MachineFinishingReport(
    val tieInCount: Int = 0,
    val tieOffCount: Int = 0,
    val autoTrimCount: Int = 0,
    val redundantCommandsRemoved: Int = 0,
    val longStitchCount: Int = 0,
    val longJumpCount: Int = 0,
    val longestStitchMm: Float = 0f,
    val longestJumpMm: Float = 0f
) {
    val hasWarnings: Boolean
        get() =
            longStitchCount > 0 ||
                longJumpCount > 0
}

data class MachineFinishingResult(
    val design: EmbroideryDesign,
    val report: MachineFinishingReport
)

object MachineFinishing {

    fun apply(
        design: EmbroideryDesign,
        settings: MachineFinishingSettings =
            MachineFinishingSettings()
    ): MachineFinishingResult {
        require(
            settings.trimJumpThresholdMm in
                1f..50f
        ) {
            "O limite de corte de salto deve ficar entre 1 e 50 mm."
        }

        require(
            settings.longStitchWarningMm in
                1f..30f
        ) {
            "O alerta de ponto longo deve ficar entre 1 e 30 mm."
        }

        require(
            settings.longJumpWarningMm in
                1f..100f
        ) {
            "O alerta de salto longo deve ficar entre 1 e 100 mm."
        }

        require(
            settings.lockLengthMm in
                0.3f..2f
        ) {
            "O arremate deve ficar entre 0,3 e 2 mm."
        }

        val input =
            design.points
                .filter {
                    it.command !=
                        StitchCommand.END
                }

        if (input.isEmpty()) {
            return MachineFinishingResult(
                design = design,
                report =
                    analyze(
                        design,
                        settings
                    )
            )
        }

        val optimized =
            if (
                settings.optimizeTravel
            ) {
                removeRedundantTravel(
                    input
                )
            } else {
                Pair(
                    input,
                    0
                )
            }

        val source =
            optimized.first

        val out =
            mutableListOf<
                EmbroideryPoint
            >()

        var tieIns = 0
        var tieOffs = 0
        var autoTrims = 0

        var currentX = 0
        var currentY = 0
        var previousStitch:
            EmbroideryPoint? =
            null

        var blockHasStitches =
            false

        var pendingTieIn =
            settings.tieInEnabled

        fun addTieOff() {
            if (
                !settings.tieOffEnabled ||
                !blockHasStitches
            ) {
                return
            }

            val last =
                out.lastOrNull {
                    it.command ==
                        StitchCommand.STITCH
                } ?: return

            val before =
                previousStitch
                    ?: return

            val lock =
                lockPair(
                    from =
                        last,
                    toward =
                        before,
                    lengthMm =
                        settings.lockLengthMm
                )

            out +=
                EmbroideryPoint(
                    lock.first,
                    lock.second,
                    StitchCommand.STITCH,
                    last.colorIndex
                )

            out +=
                EmbroideryPoint(
                    last.xUnits,
                    last.yUnits,
                    StitchCommand.STITCH,
                    last.colorIndex
                )

            tieOffs++
            currentX =
                last.xUnits
            currentY =
                last.yUnits
        }

        source.forEachIndexed {
                index,
                point ->
            when (
                point.command
            ) {
                StitchCommand.STITCH -> {
                    if (
                        pendingTieIn
                    ) {
                        val nextStitch =
                            source
                                .drop(
                                    index +
                                        1
                                )
                                .firstOrNull {
                                    it.command ==
                                        StitchCommand.STITCH &&
                                        it.colorIndex ==
                                            point.colorIndex
                                }

                        val anchor =
                            EmbroideryPoint(
                                currentX,
                                currentY,
                                StitchCommand.STITCH,
                                point.colorIndex
                            )

                        val toward =
                            nextStitch
                                ?: point

                        val lock =
                            lockPair(
                                from =
                                    anchor,
                                toward =
                                    toward,
                                lengthMm =
                                    settings.lockLengthMm
                            )

                        if (
                            lock.first !=
                                anchor.xUnits ||
                            lock.second !=
                                anchor.yUnits
                        ) {
                            out +=
                                EmbroideryPoint(
                                    lock.first,
                                    lock.second,
                                    StitchCommand.STITCH,
                                    point.colorIndex
                                )

                            out +=
                                EmbroideryPoint(
                                    anchor.xUnits,
                                    anchor.yUnits,
                                    StitchCommand.STITCH,
                                    point.colorIndex
                                )

                            tieIns++
                        }

                        pendingTieIn =
                            false
                    }

                    previousStitch =
                        out.lastOrNull {
                            it.command ==
                                StitchCommand.STITCH
                        }

                    out += point

                    currentX =
                        point.xUnits
                    currentY =
                        point.yUnits

                    blockHasStitches =
                        true
                }

                StitchCommand.JUMP -> {
                    val distanceMm =
                        distanceMm(
                            currentX,
                            currentY,
                            point.xUnits,
                            point.yUnits
                        )

                    if (
                        settings.autoTrimLongJumps &&
                        distanceMm >=
                            settings
                                .trimJumpThresholdMm &&
                        out.lastOrNull()
                            ?.command !=
                            StitchCommand.TRIM
                    ) {
                        addTieOff()

                        out +=
                            EmbroideryPoint(
                                currentX,
                                currentY,
                                StitchCommand.TRIM,
                                point.colorIndex
                            )

                        autoTrims++

                        blockHasStitches =
                            false

                        pendingTieIn =
                            settings
                                .tieInEnabled
                    }

                    out += point

                    currentX =
                        point.xUnits
                    currentY =
                        point.yUnits
                }

                StitchCommand.COLOR_CHANGE -> {
                    addTieOff()

                    out +=
                        point.copy(
                            xUnits =
                                currentX,
                            yUnits =
                                currentY
                        )

                    previousStitch =
                        null

                    blockHasStitches =
                        false

                    pendingTieIn =
                        settings.tieInEnabled
                }

                StitchCommand.TRIM -> {
                    if (
                        blockHasStitches
                    ) {
                        addTieOff()
                    }

                    if (
                        out.lastOrNull()
                            ?.command !=
                            StitchCommand.TRIM
                    ) {
                        out +=
                            point.copy(
                                xUnits =
                                    currentX,
                                yUnits =
                                    currentY
                            )
                    }

                    previousStitch =
                        null

                    blockHasStitches =
                        false

                    pendingTieIn =
                        settings.tieInEnabled
                }

                StitchCommand.STOP -> {
                    addTieOff()

                    out +=
                        point.copy(
                            xUnits =
                                currentX,
                            yUnits =
                                currentY
                        )

                    previousStitch =
                        null

                    blockHasStitches =
                        false

                    pendingTieIn =
                        settings.tieInEnabled
                }

                StitchCommand.SEQUIN -> {
                    out += point

                    currentX =
                        point.xUnits
                    currentY =
                        point.yUnits
                }

                StitchCommand.END ->
                    Unit
            }
        }

        if (
            blockHasStitches
        ) {
            addTieOff()
        }

        val endX =
            out.lastOrNull()
                ?.xUnits
                ?: currentX

        val endY =
            out.lastOrNull()
                ?.yUnits
                ?: currentY

        out +=
            EmbroideryPoint(
                endX,
                endY,
                StitchCommand.END,
                out.lastOrNull()
                    ?.colorIndex
                    ?: 0
            )

        val rebuilt =
            rebuildDesign(
                design =
                    design,
                points =
                    out,
                finishingInfo =
                    MachineFinishingInfo(
                        tieInEnabled =
                            settings
                                .tieInEnabled,
                        tieOffEnabled =
                            settings
                                .tieOffEnabled,
                        autoTrimLongJumps =
                            settings
                                .autoTrimLongJumps,
                        trimJumpThresholdMm =
                            settings
                                .trimJumpThresholdMm,
                        optimizeTravel =
                            settings
                                .optimizeTravel
                    )
            )

        val analysis =
            analyze(
                rebuilt,
                settings
            )

        return MachineFinishingResult(
            design = rebuilt,
            report =
                analysis.copy(
                    tieInCount =
                        tieIns,
                    tieOffCount =
                        tieOffs,
                    autoTrimCount =
                        autoTrims,
                    redundantCommandsRemoved =
                        optimized.second
                )
        )
    }

    fun analyze(
        design: EmbroideryDesign,
        settings: MachineFinishingSettings =
            MachineFinishingSettings()
    ): MachineFinishingReport {
        var currentX = 0
        var currentY = 0

        var longStitches = 0
        var longJumps = 0

        var longestStitch = 0f
        var longestJump = 0f

        design.points
            .forEach {
                    point ->
                val distance =
                    distanceMm(
                        currentX,
                        currentY,
                        point.xUnits,
                        point.yUnits
                    )

                when (
                    point.command
                ) {
                    StitchCommand.STITCH -> {
                        longestStitch =
                            maxOf(
                                longestStitch,
                                distance
                            )

                        if (
                            distance >
                                settings
                                    .longStitchWarningMm
                        ) {
                            longStitches++
                        }
                    }

                    StitchCommand.JUMP -> {
                        longestJump =
                            maxOf(
                                longestJump,
                                distance
                            )

                        if (
                            distance >
                                settings
                                    .longJumpWarningMm
                        ) {
                            longJumps++
                        }
                    }

                    else ->
                        Unit
                }

                currentX =
                    point.xUnits
                currentY =
                    point.yUnits
            }

        return MachineFinishingReport(
            longStitchCount =
                longStitches,
            longJumpCount =
                longJumps,
            longestStitchMm =
                longestStitch,
            longestJumpMm =
                longestJump
        )
    }

    private fun rebuildDesign(
        design: EmbroideryDesign,
        points: List<EmbroideryPoint>,
        finishingInfo:
            MachineFinishingInfo
    ): EmbroideryDesign {
        val coordinates =
            points.filter {
                it.command !=
                    StitchCommand.END
            }

        val bounds =
            if (
                coordinates.isEmpty()
            ) {
                design.bounds
            } else {
                EmbroideryBounds(
                    minXUnits =
                        coordinates
                            .minOf {
                                it.xUnits
                            },
                    maxXUnits =
                        coordinates
                            .maxOf {
                                it.xUnits
                            },
                    minYUnits =
                        coordinates
                            .minOf {
                                it.yUnits
                            },
                    maxYUnits =
                        coordinates
                            .maxOf {
                                it.yUnits
                            }
                )
            }

        return design.copy(
            points =
                points,
            bounds =
                bounds,
            stitchCount =
                points.count {
                    it.command ==
                        StitchCommand.STITCH
                },
            jumpCount =
                points.count {
                    it.command ==
                        StitchCommand.JUMP
                },
            colorChanges =
                points.count {
                    it.command ==
                        StitchCommand.COLOR_CHANGE
                },
            endFound =
                points.any {
                    it.command ==
                        StitchCommand.END
                },
            isModified = true,
            machineFinishing =
                finishingInfo
        )
    }

    private fun removeRedundantTravel(
        points: List<EmbroideryPoint>
    ): Pair<List<EmbroideryPoint>, Int> {
        val out =
            mutableListOf<
                EmbroideryPoint
            >()

        var removed = 0

        points.forEach {
                point ->
            val previous =
                out.lastOrNull()

            val duplicateTravel =
                previous != null &&
                    point.xUnits ==
                        previous.xUnits &&
                    point.yUnits ==
                        previous.yUnits &&
                    (
                        point.command ==
                            StitchCommand.JUMP ||
                        point.command ==
                            StitchCommand.TRIM
                        ) &&
                    point.command ==
                        previous.command

            val duplicateTrim =
                previous
                    ?.command ==
                    StitchCommand.TRIM &&
                    point.command ==
                        StitchCommand.TRIM

            if (
                duplicateTravel ||
                duplicateTrim
            ) {
                removed++
            } else {
                out += point
            }
        }

        return Pair(
            out,
            removed
        )
    }

    private fun lockPair(
        from: EmbroideryPoint,
        toward: EmbroideryPoint,
        lengthMm: Float
    ): Pair<Int, Int> {
        val dx =
            toward.xUnits -
                from.xUnits

        val dy =
            toward.yUnits -
                from.yUnits

        val distance =
            hypot(
                dx.toDouble(),
                dy.toDouble()
            )

        if (
            distance <
                0.001
        ) {
            return Pair(
                from.xUnits,
                from.yUnits
            )
        }

        val wantedUnits =
            lengthMm *
                10f

        val scale =
            (
                wantedUnits /
                    distance
                ).coerceAtMost(
                    1.0
                )

        return Pair(
            (
                from.xUnits +
                    dx *
                        scale
                ).roundToInt(),
            (
                from.yUnits +
                    dy *
                        scale
                ).roundToInt()
        )
    }

    private fun distanceMm(
        fromX: Int,
        fromY: Int,
        toX: Int,
        toY: Int
    ): Float =
        (
            hypot(
                (
                    toX -
                        fromX
                    ).toDouble(),
                (
                    toY -
                        fromY
                    ).toDouble()
            ) /
                10.0
            ).toFloat()
}
