package com.timachado.brothermatrizes.core.embroidery

import java.io.ByteArrayInputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.math.roundToInt
import org.embroideryio.embroideryio.EmbConstant
import org.embroideryio.embroideryio.EmbroideryIO

internal fun mapEmbroideryIoCommand(
    rawCommand: Int
): StitchCommand? =
    when (
        rawCommand
    ) {
        EmbConstant.STITCH,
        EmbConstant.SEW_TO,
        EmbConstant.NEEDLE_AT ->
            StitchCommand.STITCH

        EmbConstant.JUMP,
        EmbConstant.STITCH_BREAK ->
            StitchCommand.JUMP

        EmbConstant.TRIM,
        EmbConstant.SEQUENCE_BREAK,
        EmbConstant.TIE_OFF ->
            StitchCommand.TRIM

        EmbConstant.STOP,
        EmbConstant.FRAME_EJECT ->
            StitchCommand.STOP

        EmbConstant.COLOR_CHANGE,
        EmbConstant.NEEDLE_SET,
        EmbConstant.COLOR_BREAK ->
            StitchCommand.COLOR_CHANGE

        EmbConstant.SEQUIN_MODE,
        EmbConstant.SEQUIN_EJECT ->
            StitchCommand.SEQUIN

        EmbConstant.END ->
            StitchCommand.END

        EmbConstant.TIE_ON ->
            StitchCommand.STITCH

        else ->
            null
    }

object EmbroideryIoParser {
    private const val PARSE_TIMEOUT_SECONDS =
        12L

    private val enabledFormats = setOf("jef", "pes")

    fun parse(fileName: String, bytes: ByteArray): EmbroideryLoadResult {
        val extension = fileName
            .substringAfterLast('.', "")
            .lowercase()

        if (extension !in enabledFormats) {
            return EmbroideryLoadResult.Error(
                "Formato ainda não habilitado nesta camada."
            )
        }

        val executor =
            Executors
                .newSingleThreadExecutor {
                        runnable ->
                    Thread(
                        runnable,
                        "brother-matrix-parser"
                    ).apply {
                        isDaemon =
                            true
                    }
                }

        val future =
            executor.submit<
                org.embroideryio.embroideryio.EmbPattern?
            > {
                ByteArrayInputStream(
                    bytes
                ).use {
                        input ->
                    EmbroideryIO
                        .readStream(
                            fileName,
                            input
                        )
                }
            }

        val pattern =
            try {
                future.get(
                    PARSE_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
                )
            } catch (
                error:
                    TimeoutException
            ) {
                future.cancel(
                    true
                )

                return EmbroideryLoadResult.Error(
                    "Esta matriz demorou demais para ser interpretada e foi interrompida para evitar travar o aplicativo.",
                    "Tempo limite de leitura: " +
                        PARSE_TIMEOUT_SECONDS +
                        " s"
                )
            } catch (
                error:
                    Exception
            ) {
                return EmbroideryLoadResult.Error(
                    "Não foi possível interpretar a matriz " +
                        extension.uppercase() +
                        ".",
                    error.cause
                        ?.message
                        ?: error.message
                )
            } finally {
                executor.shutdownNow()
            } ?: return EmbroideryLoadResult.Error(
                "O arquivo " +
                    extension.uppercase() +
                    " não contém uma matriz reconhecível."
            )

        if (pattern.size() <= 0) {
            return EmbroideryLoadResult.Error(
                "Nenhuma pontada foi encontrada no arquivo."
            )
        }

        val points = ArrayList<EmbroideryPoint>(pattern.size())
        var colorIndex = 0
        var stitchCount = 0
        var jumpCount = 0
        var colorChanges = 0
        var endFound = false
        var minX = 0
        var maxX = 0
        var minY = 0
        var maxY = 0
        var hasCoordinate = false

        for (index in 0 until pattern.size()) {
            val rawCommand =
                pattern.getData(index) and EmbConstant.COMMAND_MASK

            val x = pattern.getX(index).roundToInt()
            val y = pattern.getY(index).roundToInt()

            val command =
                mapEmbroideryIoCommand(
                    rawCommand
                ) ?: continue

            when (command) {
                StitchCommand.STITCH -> stitchCount++
                StitchCommand.JUMP -> jumpCount++
                StitchCommand.COLOR_CHANGE -> {
                    colorChanges++
                    colorIndex++
                }
                StitchCommand.END -> endFound = true
                else -> Unit
            }

            points += EmbroideryPoint(
                xUnits = x,
                yUnits = y,
                command = command,
                colorIndex = colorIndex
            )

            if (command != StitchCommand.END) {
                if (!hasCoordinate) {
                    minX = x
                    maxX = x
                    minY = y
                    maxY = y
                    hasCoordinate = true
                } else {
                    minX = minOf(minX, x)
                    maxX = maxOf(maxX, x)
                    minY = minOf(minY, y)
                    maxY = maxOf(maxY, y)
                }
            }
        }

        if (points.none { it.command == StitchCommand.STITCH }) {
            return EmbroideryLoadResult.Error(
                "A matriz foi reconhecida, mas nenhuma pontada de costura válida foi encontrada."
            )
        }

        if (
            !endFound
        ) {
            return EmbroideryLoadResult.Error(
                "A matriz foi reconhecida, mas a leitura terminou antes do comando final. O arquivo não será aberto incompleto.",
                "Comando END ausente após a interpretação " +
                    extension.uppercase()
            )
        }

        val colors = pattern.threadlist.map { it.color }

        val design =
            EmbroideryDesign(
                fileName = fileName,
                format = extension.uppercase(),
                label = pattern.name,
                points = points,
                bounds = EmbroideryBounds(
                    minXUnits = minX,
                    maxXUnits = maxX,
                    minYUnits = minY,
                    maxYUnits = maxY
                ),
                stitchCount = stitchCount,
                jumpCount = jumpCount,
                colorChanges = colorChanges,
                endFound = endFound,
                sourceBytes = bytes,
                threadColors = colors,
                sourceYAxisDown = true
            )

        return EmbroideryIntegrity
            .normalize(
                design
            )
            .fold(
                onSuccess = {
                    EmbroideryLoadResult.Success(
                        it.design
                    )
                },
                onFailure = {
                        error ->
                    EmbroideryLoadResult.Error(
                        "A matriz " +
                            extension.uppercase() +
                            " está corrompida ou inconsistente.",
                        error.message
                    )
                }
            )
    }
}
