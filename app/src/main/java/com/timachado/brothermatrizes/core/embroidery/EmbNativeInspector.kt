package com.timachado.brothermatrizes.core.embroidery

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.zip.Inflater

/**
 * Leitor OFFLINE de contêineres OLE/CFB EMB conhecidos.
 * Usa somente as especificações genéricas MS-CFB/MS-OLEPS.
 * Não interpreta DesignDocument como pontos e nunca cria PES a partir de imagem.
 */
data class EmbThreadStep(
    val order: Int,
    val declaredStitches: Int,
    val colorName: String
)

data class EmbNativePreview(
    val fileName: String,
    val sha256: String,
    val declaredStitches: Int?,
    val declaredColors: Int?,
    val declaredObjects: Int?,
    val declaredTrims: Int?,
    val declaredColorChanges: Int?,
    val declaredStops: Int?,
    val declaredThreadSteps: List<EmbThreadStep>,
    val imageJpeg: ByteArray?,
    val isStitchDataDecoded: Boolean = false
)

object EmbNativeInspector {
    const val MAX_EMB_BYTES = 10 * 1024 * 1024
    private val signature = byteArrayOf(
        0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(),
        0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte()
    )

    fun hasCfbSignature(bytes: ByteArray): Boolean =
        bytes.size >= 8 && signature.indices.all { bytes[it] == signature[it] }

    fun looksLikeEmbName(name: String): Boolean {
        val lower = name.lowercase(java.util.Locale.ROOT)
        return lower.endsWith(".emb") ||
            (lower.endsWith(".doc") && lower.contains(".emb"))
    }

    /**
     * Retorna null quando o arquivo não é desta família reconhecida de EMB.
     * Rejeita amostras corrompidas; bytes e prévias nunca são publicados.
     */
    fun inspect(name: String, bytes: ByteArray): EmbNativePreview? {
        require(bytes.isNotEmpty() && bytes.size <= MAX_EMB_BYTES) {
            "Arquivo EMB vazio ou maior que 10 MB."
        }
        if (!hasCfbSignature(bytes)) return null
        val cfb = CfbContainer(bytes)
        if (!cfb.has("DesignDocument") ||
            !cfb.has("\u0005WilcomDesignInformationDDD")) return null
        val props = oleProperties(cfb.read("\u0005WilcomDesignInformationDDD"))
        val preview = runCatching {
            jpegPreview(cfb.read("TRUEVIEW_ICON"))
        }.getOrNull()
        val stepText = props["threads"] as? String
        return EmbNativePreview(
            fileName = name,
            sha256 = MessageDigest.getInstance("SHA-256")
                .digest(bytes).joinToString("") { "%02x".format(it) },
            declaredStitches = props["number of stitches"] as? Int,
            declaredColors = props["number of colours"] as? Int,
            declaredObjects = props["number of objects"] as? Int,
            declaredTrims = props["number of trims"] as? Int,
            declaredColorChanges = props["number of colour changes"] as? Int,
            declaredStops = props["number of stops"] as? Int,
            declaredThreadSteps = parseThreadSteps(stepText),
            imageJpeg = preview
        )
    }

    private fun parseThreadSteps(raw: String?): List<EmbThreadStep> {
        if (raw.isNullOrBlank() || raw.length > 32768) return emptyList()
        val lines = raw.lines().filter { it.isNotBlank() }
        if (lines.size > 256) return emptyList()
        val items = ArrayList<EmbThreadStep>()
        for (line in lines) {
            val parts = line.split('\t')
            if (parts.size < 7) return emptyList()
            val order = parts[0].toIntOrNull() ?: return emptyList()
            val stitches = parts[3].toIntOrNull() ?: return emptyList()
            val color = parts[6].trim()
            if (order != items.size + 1 || stitches !in 0..10_000_000 ||
                color.length !in 1..48 || color.any { it.code < 32 }) return emptyList()
            items += EmbThreadStep(order, stitches, color)
        }
        return items
    }

    private fun u16(data: ByteArray, at: Int): Int {
        require(at >= 0 && at.toLong() + 2 <= data.size) { "Cabeçalho truncado." }
        return (data[at].toInt() and 255) or ((data[at + 1].toInt() and 255) shl 8)
    }

    private fun i32(data: ByteArray, at: Int): Int {
        require(at >= 0 && at.toLong() + 4 <= data.size) { "Estrutura truncada." }
        return (data[at].toInt() and 255) or
            ((data[at + 1].toInt() and 255) shl 8) or
            ((data[at + 2].toInt() and 255) shl 16) or
            (data[at + 3].toInt() shl 24)
    }

    private data class Entry(val name: String, val type: Int, val start: Int, val size: Int)

    private class CfbContainer(private val bytes: ByteArray) {
        private val sectorSize: Int
        private val totalSectors: Int
        private val fat: IntArray
        private val miniFat: IntArray
        private val miniStream: ByteArray
        private val entries: List<Entry>

        init {
            require(hasCfbSignature(bytes) && bytes.size >= 512) { "Não é um CFB válido." }
            val major = u16(bytes, 26)
            val shift = u16(bytes, 30)
            require(major == 3 || major == 4) { "Versão CFB não suportada." }
            require(u16(bytes, 28) == 0xFFFE && shift == if (major == 3) 9 else 12) {
                "Cabeçalho CFB incompatível."
            }
            require(u16(bytes, 32) == 6 && i32(bytes, 56) == 4096) {
                "MiniFAT CFB incompatível."
            }
            sectorSize = 1 shl shift
            require(bytes.size >= sectorSize) { "Contêiner truncado." }
            totalSectors = (bytes.size - sectorSize) / sectorSize
            val fatCount = i32(bytes, 44)
            require(fatCount in 0..totalSectors && fatCount <= 8192) { "FAT inválida." }
            require(i32(bytes, 68) == -2 && i32(bytes, 72) == 0) {
                "Contêiner com DIFAT externa ainda não suportado."
            }
            val sectors = (0 until 109).map { i32(bytes, 76 + it * 4) }
                .filter { it != -1 }
            require(sectors.size == fatCount) { "FAT inconsistente." }
            val fatOut = ArrayList<Int>()
            for (index in sectors) {
                val raw = sector(index)
                for (i in raw.indices step 4) fatOut += i32(raw, i)
            }
            fat = fatOut.toIntArray()
            require(fat.size >= totalSectors) { "FAT incompleta." }
            val directory = readChain(i32(bytes, 48), fat, null, sectorSize, null)
            val dir = ArrayList<Entry>()
            for (offset in 0 until directory.size - 127 step 128) {
                val kind = directory[offset + 66].toInt() and 255
                if (kind !in listOf(1, 2, 5)) continue
                val sizeName = u16(directory, offset + 64)
                require(sizeName in 2..64 && sizeName % 2 == 0) { "Nome CFB inválido." }
                val end = offset + sizeName
                require(directory[end - 2] == 0.toByte() && directory[end - 1] == 0.toByte()) {
                    "Nome CFB não terminado."
                }
                val name = String(
                    directory, offset, sizeName - 2, Charsets.UTF_16LE
                )
                val start = i32(directory, offset + 116)
                val size = i32(directory, offset + 120)
                require(size in 0..MAX_EMB_BYTES) { "Stream CFB excessivo." }
                dir += Entry(name, kind, start, size)
            }
            entries = dir
            val root = entries.firstOrNull { it.type == 5 }
                ?: error("Diretório raiz CFB ausente.")
            val miniCount = i32(bytes, 64)
            require(miniCount in 0..totalSectors) { "MiniFAT inconsistente." }
            miniFat = if (miniCount > 0) {
                val raw = readChain(
                    i32(bytes, 60), fat, miniCount * sectorSize, sectorSize, null
                )
                IntArray(raw.size / 4) { i32(raw, it * 4) }
            } else IntArray(0)
            miniStream = if (root.size > 0) {
                readChain(root.start, fat, root.size, sectorSize, null)
            } else byteArrayOf()
        }

        fun has(name: String): Boolean = entries.any { it.type == 2 && it.name == name }

        fun read(name: String): ByteArray {
            val entry = entries.firstOrNull { it.type == 2 && it.name == name }
                ?: error("Stream EMB ausente: $name")
            if (entry.size == 0) return byteArrayOf()
            return if (entry.size < 4096) {
                require(miniFat.isNotEmpty()) { "MiniFAT ausente." }
                readChain(entry.start, miniFat, entry.size, 64, miniStream)
            } else readChain(entry.start, fat, entry.size, sectorSize, null)
        }

        private fun sector(index: Int): ByteArray {
            require(index in 0 until totalSectors) { "Setor fora do arquivo." }
            val offset = (index + 1) * sectorSize
            return bytes.copyOfRange(offset, offset + sectorSize)
        }

        private fun readChain(
            first: Int, allocation: IntArray, length: Int?,
            blockSize: Int, mini: ByteArray?
        ): ByteArray {
            val seen = HashSet<Int>()
            val output = ByteArrayOutputStream()
            var index = first
            val allowed = if (mini != null) mini.size / 64 else totalSectors
            if (length == 0 && first == -2) return byteArrayOf()
            while (index != -2 && (length == null || output.size() < length)) {
                require(index >= 0 && index < allowed && index < allocation.size &&
                    seen.add(index)) { "Cadeia CFB inválida, cíclica ou truncada." }
                val chunk = if (mini == null) {
                    sector(index)
                } else {
                    mini.copyOfRange(index * 64, index * 64 + blockSize)
                }
                val remaining = length?.let { it - output.size() } ?: blockSize
                output.write(chunk, 0, minOf(blockSize, remaining))
                require(output.size() <= MAX_EMB_BYTES) { "Stream CFB excessivo." }
                index = allocation[index]
            }
            require(length == null || output.size() == length) { "Stream CFB truncado." }
            return output.toByteArray()
        }
    }

    private fun oleProperties(raw: ByteArray): Map<String, Any> {
        require(raw.size >= 48 && (raw[0].toInt() and 255) == 254 &&
            (raw[1].toInt() and 255) == 255 && i32(raw, 24) == 1) {
            "Metadados OLEPS inválidos."
        }
        val begin = i32(raw, 44)
        require(begin in 0..raw.size - 8) { "Seção OLEPS inválida." }
        val length = i32(raw, begin)
        val count = i32(raw, begin + 4)
        require(length >= 8 && begin.toLong() + length <= raw.size &&
            count in 0..256 && 8L + count * 8L <= length) {
            "Tabela de propriedades inválida."
        }
        val end = begin + length
        val indexes = linkedMapOf<Int, Int>()
        for (i in 0 until count) {
            val id = i32(raw, begin + 8 + 8 * i)
            val at = begin + i32(raw, begin + 8 + 8 * i + 4)
            require(at >= begin && at.toLong() + 4 <= end) {
                "Propriedade OLEPS fora dos limites."
            }
            indexes[id] = at
        }
        val page = indexes[1]?.let { at ->
            if ((i32(raw, at) and 0xFFFF) == 2) u16(raw, at + 4) else 1252
        } ?: 1252
        val charset = if (page == 1200) Charsets.UTF_16LE else Charset.forName("windows-1252")
        val names = hashMapOf<Int, String>()
        indexes[0]?.let { start ->
            val dictionaryCount = i32(raw, start)
            require(dictionaryCount in 0..256) { "Dicionário OLEPS inválido." }
            var at = start + 4
            repeat(dictionaryCount) {
                val id = i32(raw, at)
                val nameBytes = i32(raw, at + 4)
                at += 8
                require(nameBytes in 1..512 && at.toLong() + nameBytes <= end) {
                    "Nome OLEPS inválido."
                }
                names[id] = String(raw, at, nameBytes, charset).trimEnd('\u0000')
                at += nameBytes
            }
        }
        val output = hashMapOf<String, Any>()
        for ((id, at) in indexes) {
            if (id == 0) continue
            val type = i32(raw, at) and 0xFFFF
            val p = at + 4
            val value: Any = when (type) {
                2 -> if (p.toLong() + 2 <= end) u16(raw, p) else continue
                3 -> if (p.toLong() + 4 <= end) i32(raw, p) else continue
                30, 31 -> {
                    if (p.toLong() + 4 > end) continue
                    val chars = i32(raw, p)
                    val unit = if (type == 31) 2 else 1
                    val size = chars.toLong() * unit
                    if (chars < 0 || size > 8192 || p.toLong() + 4 + size > end) continue
                    String(raw, p + 4, size.toInt(),
                        if (type == 31) Charsets.UTF_16LE else charset).trimEnd('\u0000')
                }
                else -> continue
            }
            output[names[id] ?: "property_$id"] = value
        }
        return output
    }

    private fun jpegPreview(stream: ByteArray): ByteArray {
        require(stream.size >= 8) { "Prévia ausente." }
        val length = i32(stream, 0)
        require(length in 4..1_048_576) { "Prévia excessiva." }
        val inflater = Inflater()
        return try {
            inflater.setInput(stream, 4, stream.size - 4)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16384)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count <= 0) {
                    require(!inflater.needsInput() && !inflater.needsDictionary()) {
                        "Imagem incompleta."
                    }
                    error("Prévia compactada inválida.")
                }
                require(out.size() + count <= length) { "Prévia excede tamanho informado." }
                out.write(buffer, 0, count)
            }
            val jpeg = out.toByteArray()
            require(jpeg.size == length && inflater.remaining == 0 &&
                jpeg.size >= 4 &&
                (jpeg[0].toInt() and 255) == 255 &&
                (jpeg[1].toInt() and 255) == 216 &&
                (jpeg[jpeg.size - 2].toInt() and 255) == 255 &&
                (jpeg.last().toInt() and 255) == 217) {
                "Miniatura JPEG inválida."
            }
            jpeg
        } finally {
            inflater.end()
        }
    }
}
