package com.timachado.brothermatrizes.desktop

/**
 * Leitor local e SOMENTE LEITURA para a primeira QA Windows.
 * O parser oficial Android permanece inalterado; a migração para um núcleo
 * Kotlin compartilhado é requisito antes do lançamento comercial.
 */
internal enum class PreviewCommand { STITCH, JUMP, COLOR_CHANGE, SEQUIN, END }

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
    private const val HEADER_BYTES = 512
    private const val MAX_POINTS = 2_000_000

    fun read(fileName: String, bytes: ByteArray): DstPreview {
        require(fileName.lowercase().endsWith(".dst")) {
            "Esta prévia de homologação abre somente arquivos DST."
        }
        require(bytes.size.toLong() <= MAX_FILE_BYTES) {
            "Arquivo acima do limite de segurança da prévia (8 MiB)."
        }
        require(bytes.size >= HEADER_BYTES + 3) {
            "Arquivo DST incompleto."
        }

        val header = bytes.copyOfRange(0, HEADER_BYTES).toString(Charsets.US_ASCII)
        val label = header.lineSequence()
            .firstOrNull { it.startsWith("LA:") }
            ?.removePrefix("LA:")
            ?.trim()
            ?.take(120)
            ?.takeIf { it.isNotBlank() }

        val points = ArrayList<PreviewPoint>(minOf((bytes.size - HEADER_BYTES) / 3, MAX_POINTS))
        var x = 0
        var y = 0
        var minX = 0
        var maxX = 0
        var minY = 0
        var maxY = 0
        var colorIndex = 0
        var stitches = 0
        var jumps = 0
        var changes = 0
        var endFound = false
        var pos = HEADER_BYTES

        while (pos + 2 < bytes.size) {
            require(points.size < MAX_POINTS) { "Quantidade de comandos acima do limite seguro." }
            val b0 = bytes[pos].toInt() and 0xff
            val b1 = bytes[pos + 1].toInt() and 0xff
            val b2 = bytes[pos + 2].toInt() and 0xff
            pos += 3

            if (b2 == 0xF3) {
                points += PreviewPoint(x, y, PreviewCommand.END, colorIndex)
                endFound = true
                break
            }

            x += decodeX(b0, b1, b2)
            y += decodeY(b0, b1, b2)

            val command = when {
                (b2 and 0xC3) == 0xC3 -> PreviewCommand.COLOR_CHANGE
                (b2 and 0x43) == 0x43 -> PreviewCommand.SEQUIN
                (b2 and 0x83) == 0x83 -> PreviewCommand.JUMP
                else -> PreviewCommand.STITCH
            }
            when (command) {
                PreviewCommand.STITCH -> stitches++
                PreviewCommand.JUMP -> jumps++
                PreviewCommand.COLOR_CHANGE -> {
                    changes++
                    colorIndex++
                }
                else -> Unit
            }
            points += PreviewPoint(x, y, command, colorIndex)
            minX = minOf(minX, x)
            maxX = maxOf(maxX, x)
            minY = minOf(minY, y)
            maxY = maxOf(maxY, y)
        }

        require(endFound) { "Arquivo DST sem marcador de finalização (END)." }
        require(stitches > 0) { "O arquivo não contém pontos de costura." }

        return DstPreview(
            fileName = fileName,
            label = label,
            points = points,
            bounds = PreviewBounds(minX, minY, maxX, maxY),
            stitchCount = stitches,
            jumpCount = jumps,
            colorChanges = changes
        )
    }

    private fun decodeX(b0: Int, b1: Int, b2: Int): Int {
        var x = 0
        if (b0 and 0x01 != 0) x += 1
        if (b0 and 0x02 != 0) x -= 1
        if (b0 and 0x04 != 0) x += 9
        if (b0 and 0x08 != 0) x -= 9
        if (b1 and 0x01 != 0) x += 3
        if (b1 and 0x02 != 0) x -= 3
        if (b1 and 0x04 != 0) x += 27
        if (b1 and 0x08 != 0) x -= 27
        if (b2 and 0x04 != 0) x += 81
        if (b2 and 0x08 != 0) x -= 81
        return x
    }

    private fun decodeY(b0: Int, b1: Int, b2: Int): Int {
        var y = 0
        if (b0 and 0x40 != 0) y -= 1
        if (b0 and 0x80 != 0) y += 1
        if (b0 and 0x10 != 0) y -= 9
        if (b0 and 0x20 != 0) y += 9
        if (b1 and 0x40 != 0) y -= 3
        if (b1 and 0x80 != 0) y += 3
        if (b1 and 0x10 != 0) y -= 27
        if (b1 and 0x20 != 0) y += 27
        if (b2 and 0x10 != 0) y -= 81
        if (b2 and 0x20 != 0) y += 81
        return -y // Mesmo eixo interno adotado pelo leitor DST Android.
    }
}
