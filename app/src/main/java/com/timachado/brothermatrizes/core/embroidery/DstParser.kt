package com.timachado.brothermatrizes.core.embroidery

object DstParser {
    private const val HEADER_SIZE = 512

    fun parse(fileName: String, bytes: ByteArray): EmbroideryLoadResult {
        if (bytes.size < HEADER_SIZE + 3) {
            return EmbroideryLoadResult.Error(
                "O arquivo DST parece incompleto ou inválido."
            )
        }

        val label = parseLabel(bytes)
        val points = ArrayList<EmbroideryPoint>((bytes.size - HEADER_SIZE) / 3)
        var x = 0
        var y = 0
        var colorIndex = 0
        var stitchCount = 0
        var jumpCount = 0
        var colorChanges = 0
        var endFound = false
        var minX = 0
        var maxX = 0
        var minY = 0
        var maxY = 0

        var offset = HEADER_SIZE
        while (offset + 2 < bytes.size) {
            val b0 = bytes[offset].toInt() and 0xFF
            val b1 = bytes[offset + 1].toInt() and 0xFF
            val b2 = bytes[offset + 2].toInt() and 0xFF
            offset += 3

            if (b2 == 0xF3) {
                points += EmbroideryPoint(
                    x,
                    y,
                    StitchCommand.END,
                    colorIndex
                )
                endFound = true
                break
            }

            x += decodeX(b0, b1, b2)
            y += decodeY(b0, b1, b2)

            val command = when {
                (b2 and 0xC3) == 0xC3 -> StitchCommand.COLOR_CHANGE
                (b2 and 0x43) == 0x43 -> StitchCommand.SEQUIN
                (b2 and 0x83) == 0x83 -> StitchCommand.JUMP
                else -> StitchCommand.STITCH
            }

            when (command) {
                StitchCommand.STITCH -> stitchCount++
                StitchCommand.JUMP -> jumpCount++
                StitchCommand.COLOR_CHANGE -> {
                    colorChanges++
                    colorIndex++
                }
                else -> Unit
            }

            points += EmbroideryPoint(x, y, command, colorIndex)
            minX = minOf(minX, x)
            maxX = maxOf(maxX, x)
            minY = minOf(minY, y)
            maxY = maxOf(maxY, y)
        }

        if (points.isEmpty()) {
            return EmbroideryLoadResult.Error(
                "Nenhuma pontada foi encontrada no arquivo."
            )
        }

        val design =
            EmbroideryDesign(
                fileName = fileName,
                format = "DST",
                label = label,
                points = points,
                bounds = EmbroideryBounds(
                    minX,
                    maxX,
                    minY,
                    maxY
                ),
                stitchCount = stitchCount,
                jumpCount = jumpCount,
                colorChanges = colorChanges,
                endFound = endFound,
                sourceBytes = bytes.copyOf(),
                threadColors = emptyList(),
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
                        "O arquivo DST está corrompido ou inconsistente.",
                        error.message
                    )
                }
            )
    }

    private fun parseLabel(bytes: ByteArray): String? =
        bytes.copyOfRange(0, HEADER_SIZE)
            .toString(Charsets.US_ASCII)
            .lineSequence()
            .firstOrNull { it.startsWith("LA:") }
            ?.removePrefix("LA:")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    private fun decodeX(b0: Int, b1: Int, b2: Int): Int {
        var x = 0
        if ((b0 and 0x01) != 0) x += 1
        if ((b0 and 0x02) != 0) x -= 1
        if ((b0 and 0x04) != 0) x += 9
        if ((b0 and 0x08) != 0) x -= 9
        if ((b1 and 0x01) != 0) x += 3
        if ((b1 and 0x02) != 0) x -= 3
        if ((b1 and 0x04) != 0) x += 27
        if ((b1 and 0x08) != 0) x -= 27
        if ((b2 and 0x04) != 0) x += 81
        if ((b2 and 0x08) != 0) x -= 81
        return x
    }

    private fun decodeY(b0: Int, b1: Int, b2: Int): Int {
        var encodedY = 0
        if ((b0 and 0x40) != 0) encodedY -= 1
        if ((b0 and 0x80) != 0) encodedY += 1
        if ((b0 and 0x10) != 0) encodedY -= 9
        if ((b0 and 0x20) != 0) encodedY += 9
        if ((b1 and 0x40) != 0) encodedY -= 3
        if ((b1 and 0x80) != 0) encodedY += 3
        if ((b1 and 0x10) != 0) encodedY -= 27
        if ((b1 and 0x20) != 0) encodedY += 27
        if ((b2 and 0x10) != 0) encodedY -= 81
        if ((b2 and 0x20) != 0) encodedY += 81

        // DST armazena o eixo Y no sentido oposto ao sistema interno
        // do EmbroideryIO/Brother Matrizes para arquivos importados.
        return -encodedY
    }
}
