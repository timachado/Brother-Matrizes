package com.timachado.brothermatrizes.desktop.platform

import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.project.ProjectCodec
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Adaptador nativo de arquivos Windows para o codec FLP v11 ORIGINAL Android.
 *
 * Nunca atualiza projetos em produção, não utiliza banco, login ou rede.
 * A escrita é CREATE_NEW, logo nenhum FLP existente pode ser sobrescrito.
 * O seletor gráfico é responsabilidade do host Desktop.
 */
internal object DesktopFlpProjectFiles {
    private const val MAX_PROJECT_BYTES = 64L * 1024 * 1024

    fun saveNew(design: EmbroideryDesign, chosenFile: Path): Path {
        val file = chosenFile.toAbsolutePath().normalize()
        require(file.fileName.toString().endsWith(".flp", ignoreCase = true)) {
            "Selecione um nome de projeto .flp."
        }
        require(!Files.isSymbolicLink(file)) {
            "Não é permitido salvar por link simbólico."
        }
        val bytes = ProjectCodec.encode(design)
        require(bytes.isNotEmpty() && bytes.size <= MAX_PROJECT_BYTES) {
            "Projeto FLP excede o limite de 64 MB ou está vazio."
        }
        // Verificar integridade do FLP antes de persistir bytes no disco.
        val verified = ProjectCodec.decode(bytes)
        require(verified.points == design.points) {
            "O FLP não preservou a sequência original de pontadas."
        }
        Files.write(file, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        return file
    }

    fun open(chosenFile: Path): EmbroideryDesign {
        val file = chosenFile.toAbsolutePath().normalize()
        require(file.fileName.toString().endsWith(".flp", ignoreCase = true)) {
            "O arquivo selecionado não é um projeto .flp."
        }
        require(!Files.isSymbolicLink(file)) {
            "Não é permitido abrir links simbólicos como projetos."
        }
        require(Files.isRegularFile(file)) {
            "Projeto FLP não encontrado."
        }
        val length = Files.size(file)
        require(length in 1..MAX_PROJECT_BYTES) {
            "Projeto inválido ou acima do limite de 64 MB."
        }
        val decoded = ProjectCodec.decode(Files.readAllBytes(file))
        require(decoded.points.isNotEmpty()) {
            "O projeto não possui pontadas válidas."
        }
        return decoded
    }
}
