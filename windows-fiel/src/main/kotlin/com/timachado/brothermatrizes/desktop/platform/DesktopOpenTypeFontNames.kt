package com.timachado.brothermatrizes.desktop.platform

import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset

/**
 * Reads a human-readable font name from the OpenType name table.
 *
 * Download sites and ZIP archives can give font files UUIDs or technical names.
 * The file and its stable identifier are never modified by this reader.
 */
internal object DesktopOpenTypeFontNames {
    private const val SFNT_TRUE_TYPE = 0x00010000
    private const val SFNT_OPEN_TYPE = 0x4F54544F // OTTO
    private const val SFNT_APPLE_TRUE = 0x74727565 // true
    private const val NAME_TABLE = 0x6E616D65 // name
    private const val MAX_TABLES = 512
    private const val MAX_NAMES = 4096
    private const val MAX_NAME_BYTES = 1024

    fun readDisplayName(file: File): String? = runCatching {
        RandomAccessFile(file, "r").use { reader ->
            val fileSize = reader.length()
            if (fileSize < 12L) return@use null

            val signature = reader.readInt()
            if (signature != SFNT_TRUE_TYPE &&
                signature != SFNT_OPEN_TYPE &&
                signature != SFNT_APPLE_TRUE
            ) return@use null

            val tables = reader.readUnsignedShort()
            if (tables !in 1..MAX_TABLES || 12L + tables * 16L > fileSize) {
                return@use null
            }

            var nameOffset = -1L
            var nameLength = 0L
            for (index in 0 until tables) {
                reader.seek(12L + index * 16L)
                val tag = reader.readInt()
                reader.readInt() // checksum
                val offset = reader.readInt().toLong() and 0xffffffffL
                val length = reader.readInt().toLong() and 0xffffffffL
                if (tag == NAME_TABLE && offset <= fileSize &&
                    length <= fileSize - offset
                ) {
                    nameOffset = offset
                    nameLength = length
                    break
                }
            }
            if (nameOffset < 0L || nameLength < 6L) return@use null

            reader.seek(nameOffset)
            val format = reader.readUnsignedShort()
            val count = reader.readUnsignedShort()
            val storageOffset = reader.readUnsignedShort().toLong()
            if (format !in 0..1 || count > MAX_NAMES ||
                6L + count * 12L > nameLength || storageOffset > nameLength
            ) return@use null

            val storageStart = nameOffset + storageOffset
            val tableEnd = nameOffset + nameLength
            var bestName: String? = null
            var bestScore = Int.MIN_VALUE

            for (index in 0 until count) {
                reader.seek(nameOffset + 6L + index * 12L)
                val platform = reader.readUnsignedShort()
                val encoding = reader.readUnsignedShort()
                val language = reader.readUnsignedShort()
                val nameId = reader.readUnsignedShort()
                val byteLength = reader.readUnsignedShort()
                val stringOffset = reader.readUnsignedShort().toLong()

                if (nameId != 4 && nameId != 16 && nameId != 1) continue
                if (byteLength !in 1..MAX_NAME_BYTES) continue
                val from = storageStart + stringOffset
                if (from < storageStart || from > tableEnd ||
                    byteLength.toLong() > tableEnd - from
                ) continue

                val charset = when (platform) {
                    0, 3 -> if (byteLength % 2 == 0) Charsets.UTF_16BE else null
                    1 -> if (encoding == 0) macRoman else null
                    else -> null
                } ?: continue

                reader.seek(from)
                val bytes = ByteArray(byteLength)
                reader.readFully(bytes)
                val name = String(bytes, charset)
                    .replace(Regex("[\\p{Cntrl}]"), "")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .takeIf { it.isNotBlank() && it.length <= 150 }
                    ?: continue

                // Full Font Name (4), Typographic Family (16), then Family (1).
                val typeScore = when (nameId) {
                    4 -> 300
                    16 -> 200
                    else -> 100
                }
                val platformScore = when (platform) {
                    3 -> 30
                    0 -> 25
                    else -> 10
                }
                val languageScore = when (language) {
                    0x0416 -> 5 // pt-BR
                    0x0409 -> 4 // en-US
                    0 -> 3
                    else -> 0
                }
                val score = typeScore + platformScore + languageScore
                if (score > bestScore) {
                    bestName = name
                    bestScore = score
                }
            }
            bestName
        }
    }.getOrNull()

    private val macRoman: Charset by lazy {
        runCatching { Charset.forName("x-MacRoman") }
            .getOrDefault(Charsets.ISO_8859_1)
    }
}
