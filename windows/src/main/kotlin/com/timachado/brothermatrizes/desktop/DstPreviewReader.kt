package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.DstParser
import com.timachado.brothermatrizes.core.embroidery.EmbroideryLoadResult
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import java.util.Locale

/**
 * Adaptador de visualização Desktop. A decodificação é feita pelo parser
 * REAL do aplicativo Android, compilado a partir dos mesmos fontes.
 * Sem edição ou gravação do arquivo original.
 */
internal enum class PreviewCommand {
    STITCH, JUMP, TRIM, STOP, COLOR_CHANGE, SEQUIN, END
}

internal data class PreviewPoint(
    val x: Int,
    val y: Int,
    val command: PreviewCommand,
    val colorIndex: Int
)

internal data class PreviewBounds(
    val minX: Int,
    val minY: Int,
    val maxX: Int,
    val maxY: Int
) {
    val widthMm: Double get() = (maxX.toLong() - minX.toLong()) / 10.0
    val heightMm: Double get() = (maxY.toLong() - minY.toLong()) / 10.0
}

internal data class DstPreview(
    val fileName: String,
    val label: String?,
    val points: List<PreviewPoint>,
    val bounds: PreviewBounds,
    val stitchCount: Int,
    val jumpCount: Int,
    val colorChanges: Int
)

internal object DstPreviewReader {
    const val MAX_FILE_BYTES: Long = 8L * 1024 * 1024
    private const val MIN_FILE_BYTES: Int = 512 + 3

    fun read(fileName: String, bytes: ByteArray): DstPreview {
        require(fileName.lowercase(Locale.ROOT).endsWith(".dst")) {
            "Esta prévia de homologação abre somente arquivos DST."
        }
        require(bytes.size.toLong() <= MAX_FILE_BYTES) {
            "Arquivo acima do limite de segurança da prévia (8 MiB)."
        }
        require(bytes.size >= MIN_FILE_BYTES) {
            "Arquivo DST incompleto."
        }

        val design = when (val result = DstParser.parse(fileName, bytes)) {
            is EmbroideryLoadResult.Success -> result.design
            is EmbroideryLoadResult.Error ->
                throw IllegalArgumentException(result.userMessage)
        }
        // A QA Windows é intencionalmente estrita: se não tiver END, não exibe.
        require(design.endFound) {
            "Arquivo DST sem marcador de finalização (END)."
        }
        require(design.stitchCount > 0) {
            "O arquivo não contém pontos de costura."
        }

        return DstPreview(
            fileName = design.fileName,
            label = design.label,
            points = design.points.map { point ->
                PreviewPoint(
                    x = point.xUnits,
                    y = point.yUnits,
                    command = when (point.command) {
                        StitchCommand.STITCH -> PreviewCommand.STITCH
                        StitchCommand.JUMP -> PreviewCommand.JUMP
                        StitchCommand.TRIM -> PreviewCommand.TRIM
                        StitchCommand.STOP -> PreviewCommand.STOP
                        StitchCommand.COLOR_CHANGE -> PreviewCommand.COLOR_CHANGE
                        StitchCommand.SEQUIN -> PreviewCommand.SEQUIN
                        StitchCommand.END -> PreviewCommand.END
                    },
                    colorIndex = point.colorIndex
                )
            },
            bounds = PreviewBounds(
                design.bounds.minXUnits,
                design.bounds.minYUnits,
                design.bounds.maxXUnits,
                design.bounds.maxYUnits
            ),
            stitchCount = design.stitchCount,
            jumpCount = design.jumpCount,
            colorChanges = design.colorChanges
        )
    }
}
