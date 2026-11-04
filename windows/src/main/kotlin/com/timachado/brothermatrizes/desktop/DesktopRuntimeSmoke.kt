package com.timachado.brothermatrizes.desktop

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Diagnóstico offline da instalação empacotada Windows, SEM inicializar Compose.
 * Usa somente uma pasta temporária descartável: não abre contas, não lê matrizes
 * da cliente, não altera preferências, e jamais toca diretórios de produção.
 *
 * O relatório só é escrito no fim se TODOS os testes tiverem sido aprovados.
 */
internal object DesktopRuntimeSmoke {
    const val SUCCESS_MARKER = "BROTHER_WINDOWS_QA_SMOKE_OK"

    fun execute(reportPath: Path) {
        require(reportPath.isAbsolute &&
            reportPath.fileName.toString().endsWith(".txt", ignoreCase = true)) {
            "Informe um relatório .txt com caminho absoluto."
        }
        require(Files.isDirectory(reportPath.parent) &&
            !Files.exists(reportPath)) {
            "O relatório deve ser novo, em pasta existente."
        }
        val temporary = Files.createTempDirectory("brother-windows-qa-smoke-")
        try {
            val document = DesktopMatrixService.createName("QA", 12f)
            check(document.current.stitchCount > 0 && document.current.endFound) {
                "O motor de nomes não produziu uma matriz completa."
            }

            val converted = DesktopMatrixService.convert(document, "DST")
            check(converted.bytes.size > 512) { "Conversão DST incompleta." }
            val reloadedDst = DesktopMatrixService.open(converted.fileName, converted.bytes)
            check(reloadedDst.current.stitchCount > 0 && reloadedDst.current.endFound) {
                "A releitura da matriz DST falhou."
            }

            val savedFlp = temporary.resolve("diagnostico.flp")
            DesktopProjectService.saveNew(document, savedFlp)
            val restoredFlp = DesktopProjectService.open(savedFlp)
            check(restoredFlp.current.points == document.current.points) {
                "O codec FLP não preservou os pontos."
            }
            // Proteção contra sobrescrita deve funcionar também no executável.
            val refused = runCatching {
                DesktopProjectService.saveNew(document, savedFlp)
            }.isFailure
            check(refused) { "A proteção contra sobrescrita de projeto falhou." }

            Files.writeString(
                reportPath,
                "$SUCCESS_MARKER\n" +
                    "DST: validado\n" +
                    "FLP: validado\n" +
                    "Sobrescrita: bloqueada\n" +
                    "Modo: offline / somente QA\n",
                StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE_NEW
            )
        } finally {
            // A pasta só contém o FLP temporário que este teste criou.
            Files.deleteIfExists(temporary.resolve("diagnostico.flp"))
            Files.deleteIfExists(temporary)
        }
    }
}
