package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.core.embroidery.VectorPoint
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.Locale

/**
 * Acompanhamento Windows do desenho manual: mantém o FLP v11 OFICIAL
 * inalterado e persiste separadamente os traços de mouse normalizados.
 *
 * O .bmdraw vincula-se ao FLP exato por SHA-256 (integridade, não autoria).
 * Reabertura só habilita traços após regeneração e conferência de pontadas,
 * sem ler rede, procurar pastas ou modificar arquivos anteriores.
 */
internal data class DesktopFreehandProject(
    val document: DesktopMatrixDocument,
    val strokes: List<List<VectorPoint>>,
    val widthMm: Float,
    val style: TextStitchStyle,
    val hoop: HoopProfile,
    val rgb: Int
)

internal object DesktopFreehandProjectBundle {
    const val COMPANION_EXTENSION = ".bmdraw"
    const val MAX_COMPANION_BYTES = 64 * 1024
    private const val MAGIC = "BMDRAWQA"
    private const val VERSION = 1
    private const val HASH_LENGTH = 32
    private val noFollow = arrayOf(LinkOption.NOFOLLOW_LINKS)

    fun companion(flp: Path): Path {
        val name = flp.fileName.toString()
        val basename = if (name.endsWith(".flp", ignoreCase = true))
            name.dropLast(4) else name
        return flp.resolveSibling(basename + COMPANION_EXTENSION)
    }

    fun saveNew(
        strokes: List<List<VectorPoint>>,
        widthMm: Float,
        style: TextStitchStyle,
        hoop: HoopProfile,
        flp: Path,
        rgb: Int = 0xE6BE70
    ): Pair<Path, Path> {
        requireDestination(flp)
        val companion = companion(flp)
        require(!Files.exists(flp, LinkOption.NOFOLLOW_LINKS) &&
            !Files.exists(companion, LinkOption.NOFOLLOW_LINKS)) {
            "O FLP ou o arquivo de desenho já existe. Escolha outro nome."
        }
        // generate() valida todos os traços, formatos, bastidor e pontadas.
        val expected = DesktopDrawingService.generate(
            strokes, widthMm, style, hoop, rgb
        )
        val flpBytes = DesktopProjectService.encode(expected)
        val decoded = DesktopProjectService.decode(flpBytes)
        require(sameDesign(expected.current, decoded)) {
            "O FLP não preservou integralmente o desenho livre."
        }
        val auxBytes = encode(strokes, widthMm, style, hoop, rgb, sha256(flpBytes))
        require(auxBytes.size <= MAX_COMPANION_BYTES) {
            "Arquivo auxiliar de desenho excedeu 64 KiB."
        }
        var madeFlp = false
        var madeAux = false
        try {
            Files.newOutputStream(
                flp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE
            ).use {
                madeFlp = true
                it.write(flpBytes)
            }
            Files.newOutputStream(
                companion, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE
            ).use {
                madeAux = true
                it.write(auxBytes)
            }
            return flp to companion
        } catch (failure: Exception) {
            // Nunca apagar arquivos que não foram criados por esta operação.
            if (madeAux) runCatching { Files.deleteIfExists(companion) }
            if (madeFlp) runCatching { Files.deleteIfExists(flp) }
            throw failure
        }
    }

    fun open(flp: Path): DesktopFreehandProject {
        requireSource(flp)
        val aux = companion(flp)
        require(Files.isRegularFile(aux, *noFollow) &&
            !Files.isSymbolicLink(aux) &&
            Files.size(aux) in 1L..MAX_COMPANION_BYTES.toLong()) {
            "Arquivo auxiliar .bmdraw ausente ou acima do limite."
        }
        val flpBytes = Files.newInputStream(flp, LinkOption.NOFOLLOW_LINKS).use {
            it.readNBytes((DesktopProjectService.MAX_PROJECT_BYTES + 1).toInt())
        }
        require(flpBytes.isNotEmpty() &&
            flpBytes.size.toLong() <= DesktopProjectService.MAX_PROJECT_BYTES) {
            "FLP vazio ou acima do limite."
        }
        val auxBytes = Files.newInputStream(aux, LinkOption.NOFOLLOW_LINKS).use {
            it.readNBytes(MAX_COMPANION_BYTES + 1)
        }
        val parsed = decode(auxBytes)
        require(sha256(flpBytes).contentEquals(parsed.hash)) {
            "O desenho manual não corresponde ao FLP: SHA-256 divergente."
        }
        val saved = DesktopProjectService.decode(flpBytes)
        val expected = DesktopDrawingService.generate(
            parsed.strokes, parsed.widthMm, parsed.style, parsed.hoop, parsed.rgb
        ).current
        require(sameDesign(saved, expected)) {
            "Traços recuperados não reproduzem as pontadas oficiais."
        }
        return DesktopFreehandProject(
            document = DesktopMatrixDocument(saved, saved),
            strokes = parsed.strokes,
            widthMm = parsed.widthMm,
            style = parsed.style,
            hoop = parsed.hoop,
            rgb = parsed.rgb
        )
    }

    private data class Decoded(
        val hash: ByteArray,
        val strokes: List<List<VectorPoint>>,
        val widthMm: Float,
        val style: TextStitchStyle,
        val hoop: HoopProfile,
        val rgb: Int
    )

    private fun encode(
        strokes: List<List<VectorPoint>>,
        widthMm: Float,
        style: TextStitchStyle,
        hoop: HoopProfile,
        rgb: Int,
        hash: ByteArray
    ): ByteArray {
        require(hash.size == HASH_LENGTH) { "SHA-256 do FLP inválido." }
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { out ->
            out.write(MAGIC.toByteArray(Charsets.US_ASCII))
            out.writeInt(VERSION)
            out.write(hash)
            out.writeFloat(widthMm)
            out.writeUTF(style.name)
            out.writeUTF(hoop.name)
            out.writeInt(rgb)
            out.writeInt(strokes.size)
            strokes.forEach { stroke ->
                out.writeInt(stroke.size)
                stroke.forEach { point ->
                    out.writeFloat(point.x)
                    out.writeFloat(point.y)
                }
            }
        }
        return output.toByteArray()
    }

    private fun decode(bytes: ByteArray): Decoded {
        require(bytes.isNotEmpty() && bytes.size <= MAX_COMPANION_BYTES) {
            "Arquivo de traços vazio ou muito grande."
        }
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val magic = ByteArray(MAGIC.length)
            input.readFully(magic)
            require(String(magic, Charsets.US_ASCII) == MAGIC) {
                "Cabeçalho do desenho livre não reconhecido."
            }
            require(input.readInt() == VERSION) {
                "Versão do arquivo de desenho livre não suportada."
            }
            val hash = ByteArray(HASH_LENGTH)
            input.readFully(hash)
            val width = input.readFloat()
            val styleName = input.readUTF()
            val style = TextStitchStyle.entries.singleOrNull {
                it.name == styleName
            } ?: throw IllegalArgumentException("Estilo do desenho inválido.")
            val hoopName = input.readUTF()
            val hoop = HoopProfile.entries.singleOrNull {
                it.name == hoopName
            } ?: throw IllegalArgumentException("Bastidor não reconhecido.")
            val rgb = input.readInt()
            require(width.isFinite() && width in 5f..240f &&
                rgb in 0..0xFFFFFF) {
                "Parâmetros de bordado inválidos."
            }
            val count = input.readInt()
            require(count in 1..DesktopDrawingService.MAX_STROKES) {
                "Quantidade de traços fora do limite."
            }
            var total = 0
            val strokes = ArrayList<List<VectorPoint>>(count)
            repeat(count) {
                val length = input.readInt()
                require(length in 2..DesktopDrawingService.MAX_POINTS_PER_STROKE) {
                    "Número de pontos de traço fora do limite."
                }
                total += length
                require(total <= DesktopDrawingService.MAX_TOTAL_POINTS) {
                    "Desenho ultrapassou o limite de coordenadas."
                }
                strokes += List(length) {
                    val x = input.readFloat()
                    val y = input.readFloat()
                    require(x.isFinite() && y.isFinite() &&
                        x in 0f..1f && y in 0f..1f) {
                        "Coordenadas do traço inválidas."
                    }
                    VectorPoint(x, y)
                }
            }
            require(input.available() == 0) {
                "Dados adicionais inesperados no desenho."
            }
            Decoded(hash, strokes, width, style, hoop, rgb)
        }
    }

    private fun requireDestination(flp: Path) {
        require(flp.isAbsolute &&
            flp.fileName.toString().lowercase(Locale.ROOT).endsWith(".flp")) {
            "Destino deve ser arquivo FLP de caminho absoluto."
        }
        require(Files.isDirectory(flp.parent, *noFollow) &&
            !Files.isSymbolicLink(flp.parent) &&
            Files.isWritable(flp.parent)) {
            "Pasta de destino inválida ou sem permissão."
        }
    }

    private fun requireSource(flp: Path) {
        require(flp.isAbsolute &&
            flp.fileName.toString().lowercase(Locale.ROOT).endsWith(".flp")) {
            "Selecione um FLP por caminho absoluto."
        }
        require(Files.isDirectory(flp.parent, *noFollow) &&
            !Files.isSymbolicLink(flp.parent) &&
            Files.isRegularFile(flp, *noFollow) &&
            !Files.isSymbolicLink(flp)) {
            "FLP de desenho inexistente ou redirecionado."
        }
        require(Files.size(flp) in 1L..DesktopProjectService.MAX_PROJECT_BYTES) {
            "FLP acima do limite permitido."
        }
    }

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun sameDesign(a: EmbroideryDesign, b: EmbroideryDesign): Boolean =
        a.points == b.points && a.threadColors == b.threadColors &&
            a.bounds == b.bounds && a.stitchCount == b.stitchCount &&
            a.jumpCount == b.jumpCount && a.colorChanges == b.colorChanges &&
            a.endFound == b.endFound
}
