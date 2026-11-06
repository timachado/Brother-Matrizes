package com.timachado.brothermatrizes.desktop.platform

import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.EmbroideryLoadResult
import com.timachado.brothermatrizes.core.embroidery.EmbroideryLoader
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.Locale

/**
 * Importação real no Windows, com EXATAMENTE os parsers DST/PES/JEF do Android.
 * Também aceita projetos FLP do mesmo ProjectCodec v11.
 *
 * Segurança: seleção de arquivo regular local, máximo 64 MB, sem links
 * simbólicos, sem executar macros/programas, sem sobrescrever nada.
 */
internal object DesktopEmbroideryFiles {
    private const val MAX_BYTES = 64L * 1024 * 1024
    private val SUPPORTED = setOf("dst", "pes", "jef", "flp")

    fun open(fileSelected: Path): EmbroideryDesign {
        val file = fileSelected.toAbsolutePath().normalize()
        require(!Files.isSymbolicLink(file)) {
            "Não é permitido importar por link simbólico."
        }
        require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            "A matriz selecionada não existe ou não é um arquivo regular."
        }
        val extension = file.fileName.toString()
            .substringAfterLast('.', "").lowercase(Locale.ROOT)
        require(extension in SUPPORTED) {
            "Importe somente projetos FLP ou matrizes DST, PES e JEF."
        }
        val length = Files.size(file)
        require(length in 1..MAX_BYTES) {
            "Matriz vazia ou acima do limite de segurança de 64 MB."
        }
        if (extension == "flp") return DesktopFlpProjectFiles.open(file)
        return when (val result = EmbroideryLoader.loadBytes(
            displayName = file.fileName.toString(),
            bytes = Files.readAllBytes(file)
        )) {
            is EmbroideryLoadResult.Success -> result.design
            is EmbroideryLoadResult.Error -> throw IllegalArgumentException(
                result.userMessage
            )
        }
    }
}
