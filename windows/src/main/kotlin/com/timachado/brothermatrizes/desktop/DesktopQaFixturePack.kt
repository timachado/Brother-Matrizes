package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/**
 * Produz somente MATRIZES SINTÉTICAS para QA física, usando os motores oficiais.
 * Não inspeciona pastas de clientes, não acessa rede nem altera arquivos existentes.
 * Gerar este conjunto não equivale a homologação no PE-DESIGN ou na bordadeira.
 */
internal object DesktopQaFixturePack {
    val matrixNames = listOf(
        "QA_NOME_DST.dst",
        "QA_NOME_PES_MAQUINA.pes",
        "QA_NOME_PES_PE_DESIGN_PC_EXPERIMENTAL.pes",
        "QA_NOME_JEF.jef",
        "QA_PROJETO_ANDROID.flp",
        "QA_LETRAS_CAMADAS.flp",
        "QA_LETRAS_CAMADAS.bmlayers"
    )
    private const val CHECKSUMS = "SHA256SUMS.txt"
    private const val INSTRUCTIONS = "LEIA-ME-QA.txt"

    fun generateNew(directory: Path): List<Path> {
        require(directory.isAbsolute && Files.isDirectory(
            directory.parent, LinkOption.NOFOLLOW_LINKS
        ) && !Files.isSymbolicLink(directory.parent)) {
            "Informe uma subpasta nova dentro de um diretório local de QA."
        }
        // Atômico: não reutilizar pasta existente nem misturar com arquivos
        // privados. A saída é exclusivamente destinada à homologação.
        Files.createDirectory(directory)
        val created = mutableListOf<Path>()
        fun register(path: Path): Path {
            created.add(path)
            return path
        }
        try {
            val name = DesktopMatrixService.createName("QA", 12f)
            val targets = listOf(
                Triple("DST", false, matrixNames[0]),
                Triple("PES", false, matrixNames[1]),
                Triple("PES", true, matrixNames[2]),
                Triple("JEF", false, matrixNames[3])
            )
            for ((format, pcProfile, fileName) in targets) {
                val saved = DesktopMatrixService.saveNew(
                    name, format, directory.resolve(fileName), pcProfile
                )
                register(saved)
                val verified = DesktopMatrixService.openFromFile(saved)
                check(verified.current.stitchCount > 0 && verified.current.endFound) {
                    "Matriz de QA não pôde ser relida: $fileName"
                }
            }

            val androidProject = register(
                DesktopProjectService.saveNew(name, directory.resolve(matrixNames[4]))
            )
            check(DesktopProjectService.open(androidProject).current.points ==
                name.current.points) {
                "O FLP sintético não preservou os pontos oficiais."
            }

            val layers = DesktopLetterComposer.update(
                DesktopLetterComposer.start("AB", 12f, TextStitchStyle.RUNNING),
                1, 5f, 2f, 1.2f, 15f
            )
            val layeredDocument = DesktopLetterComposer.render(layers)
            val bundle = DesktopLetterProjectBundle.saveNew(
                layers, layeredDocument, directory.resolve(matrixNames[5])
            )
            register(bundle.first)
            register(bundle.second)
            check(DesktopLetterProjectBundle.open(bundle.first).session.current == layers) {
                "O par FLP + camadas de teste não pôde ser reaberto."
            }

            val instructions = register(directory.resolve(INSTRUCTIONS))
            Files.writeString(
                instructions,
                "BROTHER MATRIZES WINDOWS - AMOSTRAS SINTETICAS DE QA\n" +
                    "Todos os desenhos foram gerados pelo motor oficial a partir de QA e AB.\n" +
                    "Amostras para teste controlado, sem dados de clientes.\n" +
                    "O PES PC e EXPERIMENTAL; abertura no PE-DESIGN 11 NAO homologada.\n" +
                    "Nao distribuir a clientes e nao bordar sem operador autorizado.\n" +
                    "Mantenha QA_LETRAS_CAMADAS.flp e .bmlayers juntos.\n" +
                    "Confirme SHA256SUMS.txt antes de usar os arquivos.\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW
            )
            val sums = matrixNames.plus(INSTRUCTIONS).joinToString("\n") { fileName ->
                val bytes = Files.readAllBytes(directory.resolve(fileName))
                val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
                digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) } +
                    "  " + fileName
            } + "\n"
            Files.writeString(
                register(directory.resolve(CHECKSUMS)), sums,
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW
            )
            return created.toList()
        } catch (failure: Exception) {
            created.asReversed().forEach { path ->
                runCatching { Files.deleteIfExists(path) }
            }
            runCatching { Files.deleteIfExists(directory) }
            throw failure
        }
    }
}
