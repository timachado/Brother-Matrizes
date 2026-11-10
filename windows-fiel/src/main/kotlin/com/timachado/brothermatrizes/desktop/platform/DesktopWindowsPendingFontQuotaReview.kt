package com.timachado.brothermatrizes.desktop.platform

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * Local-only QA inspection of the EXISTING import_font quota journal.
 *
 * No network, Google token, reservation, finalize, cleanup, file creation,
 * entitlement change, or migration of font files is permitted by this reader.
 * Never infer that a server quota is free just because no local marker exists.
 */
internal object DesktopWindowsPendingFontQuotaReview {
    private const val FOLDER_NAME = "font-import-usage-journal"

    data class Summary(
        val journalFound: Boolean,
        val pendingCount: Int,
        val stages: Map<DesktopArchiveQuotaJournal.Stage, Int>
    ) {
        fun userMessage(): String = when {
            !journalFound ->
                "Nenhum registro local de cotas de fontes foi inicializado neste PC. " +
                    "Isso NÃO confirma direitos ou saldo no servidor."
            pendingCount == 0 ->
                "Nenhuma transação pendente foi encontrada no registro local. " +
                    "Isso NÃO confirma saldo ou licença comercial no servidor."
            else -> buildString {
                append("Há $pendingCount importação(ões) de fontes com confirmação pendente.")
                stages.toList().sortedBy { it.first.name }.forEach { (stage, count) ->
                    append("\n")
                    append(when (stage) {
                        DesktopArchiveQuotaJournal.Stage.RESERVE_UNKNOWN ->
                            "Reserva sem resposta"
                        DesktopArchiveQuotaJournal.Stage.RESERVED ->
                            "Reserva registrada, conclusão desconhecida"
                        DesktopArchiveQuotaJournal.Stage.FINALIZE_UNKNOWN ->
                            "Confirmação final desconhecida"
                        DesktopArchiveQuotaJournal.Stage.CLEANUP_UNKNOWN ->
                            "Limpeza pendente"
                    })
                    append(": $count")
                }
                append("\nNovas importações ficam bloqueadas até reconciliação autorizada.")
                append("\nEste diagnóstico não modifica cotas, arquivos ou registros.")
            }
        }
    }

    fun inspect(userDataFolder: Path): Summary {
        val parent = userDataFolder.toAbsolutePath().normalize()
        require(!Files.isSymbolicLink(parent)) {
            "Diretório privado de usuário inválido."
        }
        if (!Files.exists(parent, LinkOption.NOFOLLOW_LINKS)) {
            return Summary(false, 0, emptyMap())
        }
        require(Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            "Diretório privado de usuário não é pasta regular."
        }
        val journalPath = parent.resolve(FOLDER_NAME)
        if (!Files.exists(journalPath, LinkOption.NOFOLLOW_LINKS)) {
            return Summary(false, 0, emptyMap())
        }
        require(!Files.isSymbolicLink(journalPath) &&
            Files.isDirectory(journalPath, LinkOption.NOFOLLOW_LINKS)) {
            "Registro de cotas inválido; verificação bloqueada."
        }

        val pending = DesktopArchiveQuotaJournal(journalPath).listPending()
        check(pending.all { it.category == DesktopArchiveInventory.Category.FONT }) {
            "Registro de fontes contém categoria inesperada. Revisão manual obrigatória."
        }
        return Summary(
            journalFound = true,
            pendingCount = pending.size,
            stages = pending.groupingBy { it.stage }.eachCount()
        )
    }

    fun inspectWindowsDefault(): Summary =
        inspect(DesktopWindowsDpapiSessionStore.defaultUserDataFolder())
}
