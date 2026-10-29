package com.timachado.brothermatrizes.core.embroidery

import java.util.Locale

/**
 * Metadado de posicionamento do BROTHER MATRIZES no rótulo ASCII LA: do
 * cabeçalho PEC de um PES v1 truncado. NÃO altera pontos, comandos, origem
 * PEC, deslocamentos ou tamanho do bloco de costura enviado à máquina.
 *
 * Arquivos PES comuns continuam sem esse marcador. A posição portátil
 * refere-se à prévia do aplicativo; a posição física em diferentes modelos
 * de máquina deve ser verificada no bastidor antes de executar o bordado.
 */
internal object PesHoopPositionMetadata {
    private const val PREFIX = "BM9"
    private const val FIELD_WIDTH = 16
    private const val CENTER = 839808 // 36^4/2, deslocamentos assinados de 0,1 mm
    private const val BASE = 36
    private const val CRC_MODULUS = 1296

    private data class Layout(
        val minX: Int,
        val minY: Int,
        val hoop: HoopProfile
    )

    private fun pecLabelOffset(bytes: ByteArray): Int? {
        if (bytes.size < 64 ||
            bytes.copyOfRange(0, 8).toString(Charsets.US_ASCII) != "#PES0001"
        ) return null
        val offset = (bytes[8].toInt() and 255) or
            ((bytes[9].toInt() and 255) shl 8) or
            ((bytes[10].toInt() and 255) shl 16) or
            ((bytes[11].toInt() and 255) shl 24)
        if (offset < 12 || offset.toLong() + 20L > bytes.size) return null
        if (bytes[offset] != 'L'.code.toByte() ||
            bytes[offset + 1] != 'A'.code.toByte() ||
            bytes[offset + 2] != ':'.code.toByte()
        ) return null
        return offset + 3
    }

    private fun base36(value: Int, length: Int): String {
        require(value >= 0 && value < Math.pow(BASE.toDouble(), length.toDouble()))
        return value.toString(BASE).uppercase(Locale.ROOT).padStart(length, '0')
    }

    private fun crc(text: String): String {
        var value = 0
        text.forEach { value = (value * 33 + it.code) % CRC_MODULUS }
        return base36(value, 2)
    }

    /** Modifica só os 16 caracteres do rótulo PEC, nunca a geometria de costura. */
    fun write(bytes: ByteArray, design: EmbroideryDesign): ByteArray {
        if (!design.hoopPositionLocked || !design.sourceYAxisDown) return bytes
        val hoop = design.hoopProfile ?: return bytes
        val x = design.bounds.minXUnits.toLong() + CENTER
        val y = design.bounds.minYUnits.toLong() + CENTER
        if (x !in 0L until 1679616L || y !in 0L until 1679616L) {
            throw IllegalArgumentException("Posição incompatível com o marcador PES.")
        }
        val start = requireNotNull(pecLabelOffset(bytes)) {
            "PES sem cabeçalho PEC compatível com posição editável."
        }
        val payload = PREFIX + base36(x.toInt(), 4) +
            base36(y.toInt(), 4) + base36(hoop.ordinal, 2) + "D"
        val encoded = payload + crc(payload)
        require(encoded.length == FIELD_WIDTH)
        val result = bytes.copyOf()
        encoded.toByteArray(Charsets.US_ASCII).copyInto(result, start)
        return result
    }

    private fun read(bytes: ByteArray): Layout? {
        val at = pecLabelOffset(bytes) ?: return null
        val value = bytes.copyOfRange(at, at + FIELD_WIDTH)
            .toString(Charsets.US_ASCII)
        if (!value.startsWith(PREFIX) || value[13] != 'D' ||
            crc(value.substring(0, 14)) != value.substring(14, 16)
        ) return null
        val x = value.substring(3, 7).toIntOrNull(BASE) ?: return null
        val y = value.substring(7, 11).toIntOrNull(BASE) ?: return null
        val index = value.substring(11, 13).toIntOrNull(BASE) ?: return null
        val hoop = HoopProfile.entries.getOrNull(index) ?: return null
        return Layout(x - CENTER, y - CENTER, hoop)
    }

    /** Restaura a posição ABSOLUTA, não recalcule o centro da nova bounding box. */
    fun restore(bytes: ByteArray, design: EmbroideryDesign): EmbroideryDesign {
        val saved = read(bytes) ?: return design
        if (!design.sourceYAxisDown) return design
        val dx = saved.minX.toLong() - design.bounds.minXUnits.toLong()
        val dy = saved.minY.toLong() - design.bounds.minYUnits.toLong()
        fun moved(point: EmbroideryPoint): EmbroideryPoint {
            val x = point.xUnits.toLong() + dx
            val y = point.yUnits.toLong() + dy
            require(x in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() &&
                y in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
            ) { "Posição PES fora do intervalo de bordado." }
            return point.copy(xUnits = x.toInt(), yUnits = y.toInt())
        }
        val normalized = EmbroideryIntegrity.normalize(
            design.copy(
                points = design.points.map(::moved),
                guidePoints = design.guidePoints.map(::moved),
                sourceBytes = bytes,
                hoopProfile = saved.hoop,
                hoopPositionLocked = true,
                isModified = true,
                label = design.fileName.substringBeforeLast('.')
            )
        ).getOrThrow().design
        HoopLayerPositioning.validateHoop(normalized, saved.hoop)
        return normalized
    }
}
