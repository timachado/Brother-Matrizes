package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.EmbroideryIntegrity
import com.timachado.brothermatrizes.core.project.ProjectCodec
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Locale

/**
 * FLP nativo compartilhado com o Android, sem criar variante proprietária Desktop.
 * Preserva metadados de nome editável, cor e bastidor presentes no ProjectCodec.
 *
 * O arquivo original importado nunca é sobrescrito. A carga de um FLP é um novo
 * ponto de restauração (original=current), não a recuperação dos bytes da
 * matriz fonte se eles não constarem do projeto Android.
 */
internal object DesktopProjectService {
    const val EXTENSION = ".flp"
    const val MAX_PROJECT_BYTES: Long = 32L * 1024 * 1024

    fun encode(document: DesktopMatrixDocument): ByteArray {
        val normalized = validate(document.current)
        val bytes = ProjectCodec.encode(normalized)
        require(bytes.isNotEmpty() && bytes.size.toLong() <= MAX_PROJECT_BYTES) {
            "O projeto ultrapassou o limite de segurança de 32 MiB."
        }
        val reopened = decode(bytes)
        require(reopened.stitchCount == normalized.stitchCount &&
            reopened.points == normalized.points &&
            reopened.threadColors == normalized.threadColors) {
            "Os pontos ou as cores do projeto não foram preservados."
        }
        return bytes
    }

    fun decode(bytes: ByteArray): EmbroideryDesign {
        require(bytes.isNotEmpty() && bytes.size.toLong() <= MAX_PROJECT_BYTES) {
            "Arquivo de projeto vazio ou acima do limite de 32 MiB."
        }
        return validate(ProjectCodec.decode(bytes))
    }

    fun open(file: Path): DesktopMatrixDocument {
        require(file.fileName.toString().lowercase(Locale.ROOT).endsWith(EXTENSION)) {
            "Selecione um projeto Brother Matrizes .flp."
        }
        require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(file)) {
            "Selecione um arquivo de projeto regular, sem atalhos."
        }
        require(Files.size(file) in 1..MAX_PROJECT_BYTES) {
            "Projeto acima do limite de segurança de 32 MiB."
        }
        // Leitura efetivamente limitada: o arquivo pode crescer depois de
        // Files.size(). NOFOLLOW_LINKS também bloqueia troca por atalho.
        val bytes = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS).use { stream ->
            stream.readNBytes((MAX_PROJECT_BYTES + 1).toInt())
        }
        val loaded = decode(bytes)
        return DesktopMatrixDocument(original = loaded, current = loaded)
    }

    /**
     * Permite criar somente arquivos NOVOS e remove um arquivo parcial criado
     * exclusivamente nesta operação em caso de falha de gravação.
     */
    fun saveNew(document: DesktopMatrixDocument, destination: Path): Path {
        require(destination.isAbsolute &&
            destination.fileName.toString().lowercase(Locale.ROOT).endsWith(EXTENSION)) {
            "Escolha um caminho absoluto terminado em .flp."
        }
        require(Files.isDirectory(destination.parent, LinkOption.NOFOLLOW_LINKS)) {
            "Diretório de destino indisponível."
        }
        val bytes = encode(document)
        var created = false
        try {
            Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
                .use { output ->
                    created = true
                    output.write(bytes)
                    output.flush()
                }
            return destination
        } catch (error: Exception) {
            if (created) runCatching { Files.deleteIfExists(destination) }
            throw error
        }
    }

    private fun validate(design: EmbroideryDesign): EmbroideryDesign {
        val normalized = EmbroideryIntegrity.normalize(design).getOrThrow().design
        require(normalized.stitchCount > 0 && normalized.endFound &&
            normalized.points.size <= 2_000_000) {
            "Projeto incompleto ou com quantidade de pontos inválida."
        }
        return normalized
    }
}
