package com.timachado.brothermatrizes.font

import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.util.Locale

/**
 * Lê SOMENTE a tabela "name" SFNT (TTF/OTF) para identificar o nome real
 * gravado pela fundição da fonte, sem depender do nome técnico no ZIP.
 * Segurança: limites explícitos de offsets, contagem de records e bytes lidos.
 * Não edita o arquivo, não modifica os IDs usados pela biblioteca.
 */
internal object SfntFontNameReader {
    private const val MAX_NAME_TABLE = 1_048_576L
    private const val MAX_RECORDS = 2048
    private const val MAX_STRING_BYTES = 8192
    private const val MAX_FILE_BYTES = 12L * 1024L * 1024L

    fun familyName(file: File): String? = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            val length = raf.length()
            if (length !in 12L..MAX_FILE_BYTES) return@use null
            val version = raf.readInt()
            if (version != 0x00010000 && version != 0x4F54544F && version != 0x74727565) {
                return@use null
            }
            val numTables = raf.readUnsignedShort()
            raf.skipBytes(6)
            if (numTables !in 1..128 || 12L + numTables * 16L > length) return@use null

            var tableOffset = -1L
            var tableLength = -1L
            repeat(numTables) {
                val tag = raf.readInt()
                raf.skipBytes(4) // checksum
                val offset = raf.readInt().toLong() and 0xffffffffL
                val size = raf.readInt().toLong() and 0xffffffffL
                if (tag == 0x6e616d65) { // 'name'
                    tableOffset = offset
                    tableLength = size
                }
            }
            if (tableOffset < 0 || tableLength !in 6L..MAX_NAME_TABLE ||
                tableOffset > length || tableLength > length - tableOffset) return@use null

            raf.seek(tableOffset)
            val format = raf.readUnsignedShort()
            val count = raf.readUnsignedShort()
            val storage = raf.readUnsignedShort()
            if (format !in 0..1 || count !in 1..MAX_RECORDS ||
                6L + count * 12L > tableLength ||
                storage < 6 + count * 12 || storage.toLong() > tableLength) return@use null

            data class Candidate(val name: String, val priority: Int)
            var chosen: Candidate? = null
            repeat(count) {
                val platform = raf.readUnsignedShort()
                val encoding = raf.readUnsignedShort()
                val language = raf.readUnsignedShort()
                val nameId = raf.readUnsignedShort()
                val bytes = raf.readUnsignedShort()
                val nameOffset = raf.readUnsignedShort()
                val nextRecord = raf.filePointer
                if ((nameId == 1 || nameId == 4 || nameId == 16) &&
                    bytes in 2..MAX_STRING_BYTES &&
                    storage.toLong() + nameOffset + bytes <= tableLength &&
                    (platform == 0 || platform == 3 || platform == 1)) {
                    val pos = tableOffset + storage + nameOffset
                    raf.seek(pos)
                    val data = ByteArray(bytes)
                    raf.readFully(data)
                    val decoded = when {
                        platform == 0 || platform == 3 ->
                            if (bytes % 2 == 0) String(data, Charsets.UTF_16BE) else ""
                        platform == 1 && encoding == 0 ->
                            String(data, Charset.forName("ISO-8859-1"))
                        else -> ""
                    }
                    val clean = cleanName(decoded)
                    if (clean != null) {
                        val priority = (if (nameId == 16) 0 else if (nameId == 1) 10 else 20) +
                            (if (platform == 3 && language == 0x0409) 0 else if (platform == 0) 1 else 2)
                        if (chosen == null || priority < chosen!!.priority)
                            chosen = Candidate(clean, priority)
                    }
                }
                raf.seek(nextRecord)
            }
            chosen?.name
        }
    }.getOrNull()

    internal fun cleanName(value: String): String? {
        val name = value.replace(Regex("[\\p{Cc}\\p{Cf}]+"), " ")
            .replace(Regex("\\s+"), " ").trim().take(72)
        if (name.length !in 2..72 || name.none { it.isLetter() } ||
            name.contains('\uFFFD') || name.contains('\u0000')) return null
        // Nomes como "a59e4c8e" ou "00009173" não são nomes comerciais.
        val compact = name.replace(" ", "")
        if (compact.length in 8..32 && compact.all { it.isDigit() ||
                it.lowercaseChar() in 'a'..'f' } && compact.any { it.isDigit() }) return null
        return name
    }
}
