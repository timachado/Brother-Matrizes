package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.core.embroidery.VectorArtwork
import com.timachado.brothermatrizes.core.embroidery.VectorPath
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

/**
 * Projeto vetorial Windows com dois arquivos NOVOS:
 *
 * 1. .flp: ProjectCodec v11 OFICIAL do Android, somente pontadas finais.
 * 2. .bmvector: caminhos SVG editáveis, parâmetros e SHA-256 exato do FLP.
 *
 * SHA-256 vincula arquivos; não autentica a autoria. Não regrava SVG original
 * nem altera o codec do Android. Os dois arquivos são obrigatórios para
 * recuperar os objetos; um FLP isolado continua legível normalmente.
 */
internal data class DesktopVectorProject(
    val document: DesktopMatrixDocument,
    val imported: DesktopImportedSvg,
    val session: DesktopSvgEditSession,
    val widthMm: Float,
    val style: TextStitchStyle,
    val hoop: HoopProfile,
    val rgb: Int
)

internal object DesktopVectorProjectBundle {
    const val COMPANION_EXTENSION = ".bmvector"
    const val MAX_COMPANION_BYTES = 96 * 1024
    private const val MAGIC = "BMVECTOR"
    private const val VERSION = 1
    private const val SHA256_BYTES = 32
    private const val MAX_LABEL_CHARS = 120
    private val noFollow = arrayOf(LinkOption.NOFOLLOW_LINKS)

    fun companion(flp: Path): Path {
        val filename = flp.fileName.toString()
        val stem = if (filename.endsWith(".flp", ignoreCase = true)) {
            filename.dropLast(4)
        } else filename
        return flp.resolveSibling(stem + COMPANION_EXTENSION)
    }

    fun saveNew(
        imported: DesktopImportedSvg,
        widthMm: Float,
        style: TextStitchStyle,
        hoop: HoopProfile,
        flp: Path,
        rgb: Int = 0xE6BE70
    ): Pair<Path, Path> {
        requireDestination(flp)
        val aux = companion(flp)
        require(!Files.exists(flp, LinkOption.NOFOLLOW_LINKS) &&
            !Files.exists(aux, LinkOption.NOFOLLOW_LINKS)) {
            "Projeto FLP ou arquivo vetorial já existe. Escolha outro nome."
        }
        validateNames(imported)
        DesktopSvgImport.validate(imported.artwork)
        // Gera a matriz na memória pelo MESMO motor usado no Android.
        val generated = DesktopDrawingService.generateSvg(
            imported, widthMm, style, hoop, rgb
        )
        val bytes = DesktopProjectService.encode(generated)
        require(sameDesign(generated.current, DesktopProjectService.decode(bytes))) {
            "O FLP gerado não preservou as pontadas vetoriais."
        }
        val companionBytes = encode(
            imported, widthMm, style, hoop, rgb, sha256(bytes)
        )
        require(companionBytes.size <= MAX_COMPANION_BYTES) {
            "Arquivo vetorial ultrapassou limite de 96 KiB."
        }

        var createdFlp = false
        var createdCompanion = false
        try {
            Files.newOutputStream(
                flp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE
            ).use {
                createdFlp = true
                it.write(bytes)
            }
            Files.newOutputStream(
                aux, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE
            ).use {
                createdCompanion = true
                it.write(companionBytes)
            }
            return flp to aux
        } catch (error: Exception) {
            // Rollback exclusivamente dos arquivos novos desta tentativa.
            if (createdCompanion) runCatching { Files.deleteIfExists(aux) }
            if (createdFlp) runCatching { Files.deleteIfExists(flp) }
            throw error
        }
    }

    fun open(flp: Path): DesktopVectorProject {
        requireSource(flp)
        val aux = companion(flp)
        require(Files.isRegularFile(aux, *noFollow) &&
            !Files.isSymbolicLink(aux) &&
            Files.size(aux) in 1L..MAX_COMPANION_BYTES.toLong()) {
            "Arquivo de vetores .bmvector ausente ou acima do limite."
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
        val decoded = decode(auxBytes)
        require(sha256(flpBytes).contentEquals(decoded.hash)) {
            "O arquivo de vetores não corresponde ao FLP selecionado (SHA-256)."
        }
        val saved = DesktopProjectService.decode(flpBytes)
        // Regenera as pontadas pelo engine compartilhado, depois compara
        // conteúdo efetivo ANTES de liberar qualquer caminho editável.
        val expected = DesktopDrawingService.generateSvg(
            decoded.imported, decoded.widthMm,
            decoded.style, decoded.hoop, decoded.rgb
        ).current
        require(sameDesign(expected, saved)) {
            "Os vetores não reproduzem as pontadas salvas no FLP."
        }
        return DesktopVectorProject(
            document = DesktopMatrixDocument(saved, saved),
            imported = decoded.imported,
            session = DesktopSvgEditSession.start(decoded.imported.artwork),
            widthMm = decoded.widthMm,
            style = decoded.style,
            hoop = decoded.hoop,
            rgb = decoded.rgb
        )
    }

    private data class RawCompanion(
        val hash: ByteArray,
        val imported: DesktopImportedSvg,
        val widthMm: Float,
        val style: TextStitchStyle,
        val hoop: HoopProfile,
        val rgb: Int
    )

    private fun encode(
        imported: DesktopImportedSvg,
        widthMm: Float,
        style: TextStitchStyle,
        hoop: HoopProfile,
        rgb: Int,
        hash: ByteArray
    ): ByteArray {
        require(hash.size == SHA256_BYTES) { "SHA-256 inválido." }
        validateNames(imported)
        DesktopSvgImport.validate(imported.artwork)
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { out ->
            out.write(MAGIC.toByteArray(Charsets.US_ASCII))
            out.writeInt(VERSION)
            out.write(hash)
            out.writeUTF(imported.sourceName)
            out.writeUTF(imported.artwork.label)
            out.writeFloat(widthMm)
            out.writeUTF(style.name)
            out.writeUTF(hoop.name)
            out.writeInt(rgb)
            out.writeInt(imported.artwork.paths.size)
            imported.artwork.paths.forEach { path ->
                out.writeBoolean(path.closed)
                out.writeInt(path.points.size)
                path.points.forEach { point ->
                    out.writeFloat(point.x)
                    out.writeFloat(point.y)
                }
            }
        }
        return output.toByteArray()
    }

    private fun decode(bytes: ByteArray): RawCompanion {
        require(bytes.isNotEmpty() && bytes.size <= MAX_COMPANION_BYTES) {
            "Arquivo vetorial vazio ou fora do limite."
        }
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val magic = ByteArray(MAGIC.length)
            input.readFully(magic)
            require(String(magic, Charsets.US_ASCII) == MAGIC) {
                "Cabeçalho do arquivo vetorial inválido."
            }
            require(input.readInt() == VERSION) {
                "Versão do arquivo vetorial incompatível."
            }
            val hash = ByteArray(SHA256_BYTES)
            input.readFully(hash)
            val sourceName = input.readUTF()
            val label = input.readUTF()
            require(sourceName.length in 1..MAX_LABEL_CHARS &&
                label.length in 1..MAX_LABEL_CHARS) {
                "Nome SVG inválido."
            }
            val widthMm = input.readFloat()
            val styleName = input.readUTF()
            val style = TextStitchStyle.entries.singleOrNull {
                it.name == styleName
            } ?: throw IllegalArgumentException("Estilo de ponto desconhecido.")
            val hoopName = input.readUTF()
            val hoop = HoopProfile.entries.singleOrNull {
                it.name == hoopName
            } ?: throw IllegalArgumentException("Bastidor não reconhecido.")
            val rgb = input.readInt()
            require(widthMm.isFinite() && widthMm in 5f..240f &&
                rgb in 0..0xFFFFFF) {
                "Parâmetros do bordado vetorial inválidos."
            }
            val count = input.readInt()
            require(count in 1..DesktopSvgImport.MAX_PATHS) {
                "Quantidade de caminhos vetoriais inválida."
            }
            var total = 0
            val paths = ArrayList<VectorPath>(count)
            repeat(count) {
                val closed = input.readBoolean()
                val n = input.readInt()
                require(n in 2..DesktopSvgImport.MAX_POINTS_PER_PATH) {
                    "Quantidade de pontos do vetor inválida."
                }
                total += n
                require(total <= DesktopSvgImport.MAX_POINTS) {
                    "Desenho ultrapassou limite de coordenadas."
                }
                val points = ArrayList<VectorPoint>(n)
                repeat(n) {
                    val x = input.readFloat()
                    val y = input.readFloat()
                    require(x.isFinite() && y.isFinite() &&
                        x in -1_000_000f..1_000_000f &&
                        y in -1_000_000f..1_000_000f) {
                        "Coordenadas vetoriais inválidas."
                    }
                    points += VectorPoint(x, y)
                }
                paths += VectorPath(points, closed)
            }
            require(input.available() == 0) {
                "Conteúdo adicional inesperado no arquivo vetorial."
            }
            val imported = DesktopImportedSvg(
                sourceName, VectorArtwork(paths, label)
            )
            DesktopSvgImport.validate(imported.artwork)
            RawCompanion(hash, imported, widthMm, style, hoop, rgb)
        }
    }

    private fun validateNames(imported: DesktopImportedSvg) {
        require(imported.sourceName.length in 1..MAX_LABEL_CHARS &&
            imported.artwork.label.length in 1..MAX_LABEL_CHARS) {
            "Nome do arquivo ou do desenho excedeu 120 caracteres."
        }
    }

    private fun requireDestination(flp: Path) {
        require(flp.isAbsolute && flp.fileName.toString()
            .endsWith(".flp", ignoreCase = true)) {
            "Selecione um destino absoluto terminado em .flp."
        }
        require(Files.isDirectory(flp.parent, *noFollow) &&
            !Files.isSymbolicLink(flp.parent) &&
            Files.isWritable(flp.parent)) {
            "Pasta de destino inválida, redirecionada ou sem permissão."
        }
    }

    private fun requireSource(flp: Path) {
        require(flp.isAbsolute && flp.fileName.toString()
            .endsWith(".flp", ignoreCase = true)) {
            "Selecione um FLP por caminho absoluto."
        }
        require(Files.isDirectory(flp.parent, *noFollow) &&
            !Files.isSymbolicLink(flp.parent) &&
            Files.isRegularFile(flp, *noFollow) &&
            !Files.isSymbolicLink(flp)) {
            "FLP de origem ausente ou redirecionado."
        }
        require(Files.size(flp) in 1L..DesktopProjectService.MAX_PROJECT_BYTES) {
            "FLP acima do limite permitido."
        }
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun sameDesign(
        a: com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign,
        b: com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
    ): Boolean =
        a.points == b.points &&
            a.threadColors == b.threadColors &&
            a.bounds == b.bounds &&
            a.stitchCount == b.stitchCount &&
            a.jumpCount == b.jumpCount &&
            a.colorChanges == b.colorChanges &&
            a.endFound == b.endFound
}
