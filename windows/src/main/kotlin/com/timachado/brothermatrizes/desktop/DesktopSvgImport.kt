package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.SimpleSvgParser
import com.timachado.brothermatrizes.core.embroidery.VectorArtwork
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.Locale
import kotlin.math.max

/** SVG externo: leitura local sem resolver URLs, imagens, DTDs ou entidades. */
internal data class DesktopImportedSvg(
    val sourceName: String,
    val artwork: VectorArtwork
)

internal object DesktopSvgImport {
    const val MAX_BYTES = 2_000_000
    const val MAX_PATHS = 96
    const val MAX_POINTS_PER_PATH = 800
    const val MAX_POINTS = 6_000

    // O parser do Android aceita apenas subconjunto vetorial. O Windows
    // recusa antecipadamente recursos externos/ativos e SVG com entidades.
    private val forbidden = Regex(
        """(?is)<!\s*(?:doctype|entity)|<\s*(?:script|style|foreignobject|image|use|iframe|object|embed|xi:include)\b|<\?xml-stylesheet\b|\b(?:href|xlink:href)\s*=|\bon[a-z]+\s*=|url\s*\("""
    )

    fun open(file: Path): DesktopImportedSvg {
        val name = file.fileName?.toString() ?: throw IllegalArgumentException("Arquivo sem nome.")
        require(name.lowercase(Locale.ROOT).endsWith(".svg")) {
            "Selecione um arquivo .svg."
        }
        require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            "SVG deve ser um arquivo local regular, sem link simbólico."
        }
        require(Files.size(file) in 1..MAX_BYTES.toLong()) {
            "SVG vazio ou maior que 2 MB."
        }
        val bytes = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS).use {
            it.readNBytes(MAX_BYTES + 1)
        }
        return parse(name, bytes)
    }

    /** Parser oficial Android com guarda adicional estrita para arquivos locais. */
    fun parse(name: String, bytes: ByteArray): DesktopImportedSvg {
        require(name.lowercase(Locale.ROOT).endsWith(".svg")) {
            "Somente SVG é aceito."
        }
        require(bytes.isNotEmpty() && bytes.size <= MAX_BYTES) {
            "SVG vazio ou acima de 2 MB."
        }
        val markup = bytes.toString(Charsets.UTF_8)
        require(!forbidden.containsMatchIn(markup)) {
            "SVG contém recursos externos ou ativos não permitidos nesta QA."
        }
        val artwork = SimpleSvgParser.parse(name, bytes).getOrThrow()
        validate(artwork)
        return DesktopImportedSvg(name, artwork)
    }

    fun validate(artwork: VectorArtwork) {
        require(artwork.paths.size in 1..MAX_PATHS) {
            "SVG excede o limite de $MAX_PATHS caminhos."
        }
        var count = 0
        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (path in artwork.paths) {
            require(path.points.size in 2..MAX_POINTS_PER_PATH) {
                "Caminho SVG inválido ou com pontos demais."
            }
            count += path.points.size
            require(count <= MAX_POINTS) {
                "SVG excede $MAX_POINTS coordenadas."
            }
            for (p in path.points) {
                require(p.x.isFinite() && p.y.isFinite() &&
                    p.x in -1_000_000f..1_000_000f &&
                    p.y in -1_000_000f..1_000_000f) {
                    "SVG contém coordenadas inválidas."
                }
                minX = minOf(minX, p.x)
                maxX = maxOf(maxX, p.x)
                minY = minOf(minY, p.y)
                maxY = maxOf(maxY, p.y)
            }
        }
        require(maxX - minX >= 0.005f && maxY - minY >= 0.005f) {
            "O desenho SVG precisa ter largura e altura válidas."
        }
    }
}
