package com.timachado.brothermatrizes.font

import java.io.File
import java.io.RandomAccessFile

/**
 * Leitura local, limitada e somente de metadados GSUB de uma fonte OpenType.
 *
 * Não modela os glifos, não transforma em pontos e não infere que um recurso
 * muda o texto (esta etapa pertence ao motor de shaping).
 * Nunca envia fontes para rede, não altera o arquivo e não usa licenças.
 */
internal object OpenTypeFeatureCatalog {
    private const val MAX_FONT_BYTES = 12L * 1024L * 1024L
    private const val MAX_GSUB_BYTES = 1024L * 1024L
    private const val MAX_FEATURES = 1024
    private const val TAG_GSUB = 0x47535542
    private val OPTIONAL_FEATURES = setOf(
        "aalt", "salt", "swsh", "cswh", "dlig", "hlig", "calt", "clig", "liga"
    )

    fun readOptionalFeatures(file: File): Set<String> = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            val total = raf.length()
            if (total !in 28L..MAX_FONT_BYTES) return@use emptySet()
            val signature = raf.readInt()
            if (signature !in intArrayOf(0x00010000, 0x4f54544f, 0x74727565)) {
                return@use emptySet()
            }
            val count = raf.readUnsignedShort()
            raf.skipBytes(6)
            if (count !in 1..128 || 12L + count * 16L > total) {
                return@use emptySet()
            }
            var offset = -1L
            var size = -1L
            repeat(count) {
                val tag = raf.readInt()
                raf.skipBytes(4)
                val tableOffset = raf.readInt().toLong() and 0xffffffffL
                val tableSize = raf.readInt().toLong() and 0xffffffffL
                if (tag == TAG_GSUB) {
                    offset = tableOffset
                    size = tableSize
                }
            }
            if (offset < 0 || size !in 12L..MAX_GSUB_BYTES ||
                offset > total || size > total - offset) return@use emptySet()

            raf.seek(offset)
            val major = raf.readUnsignedShort()
            val minor = raf.readUnsignedShort()
            raf.skipBytes(2) // ScriptListOffset
            val featuresOffset = raf.readUnsignedShort()
            raf.skipBytes(2) // LookupListOffset
            if (major != 1 || minor !in 0..1 ||
                featuresOffset < 10 || featuresOffset.toLong() > size - 2L)
                return@use emptySet()
            raf.seek(offset + featuresOffset)
            val featureCount = raf.readUnsignedShort()
            if (featureCount > MAX_FEATURES ||
                2L + featureCount * 6L > size - featuresOffset)
                return@use emptySet()
            val result = mutableSetOf<String>()
            repeat(featureCount) {
                val tag = raf.readInt()
                raf.readUnsignedShort() // Feature table offset; parsing not needed
                val name = String(byteArrayOf(
                    (tag ushr 24).toByte(), (tag ushr 16).toByte(),
                    (tag ushr 8).toByte(), tag.toByte()), Charsets.US_ASCII)
                if (name in OPTIONAL_FEATURES ||
                    (name.length == 4 && name.startsWith("ss") &&
                     name.substring(2).all { it.isDigit() } &&
                     name.substring(2) != "00")) result.add(name)
            }
            result.toSortedSet()
        }
    }.getOrDefault(emptySet())
}
