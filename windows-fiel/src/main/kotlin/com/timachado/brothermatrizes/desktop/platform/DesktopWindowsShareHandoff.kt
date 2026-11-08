package com.timachado.brothermatrizes.desktop.platform

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.Locale

/**
 * Windows implementation of the Android share intent's FILE HANDOFF only.
 *
 * User explicitly chooses a new export destination in a save dialog.
 * The application then shows that saved file in Windows Explorer, where the
 * user may attach it to WhatsApp Desktop, email or another destination.
 *
 * This is NOT an automatic message/share transmission, USB device transfer,
 * network connection, or completion confirmation by any external application.
 * Nothing is uploaded; no quota, account or Supabase endpoint is accessed.
 */
internal object DesktopWindowsShareHandoff {
    private val extensions = setOf("dst", "pes", "jef", "flp")

    fun revealSavedMatrix(
        saved: Path,
        windowsName: String = System.getProperty("os.name"),
        launchExplorer: (List<String>) -> Unit = { args ->
            ProcessBuilder(args).start()
        }
    ): Path {
        check(windowsName.startsWith("Windows", ignoreCase = true)) {
            "Abrir no Explorer requer Windows."
        }
        val file = saved.toAbsolutePath().normalize()
        val parent = requireNotNull(file.parent) {
            "O arquivo compartilhável não possui pasta."
        }
        val extension = file.fileName.toString()
            .substringAfterLast('.', "").lowercase(Locale.ROOT)
        require(extension in extensions) {
            "Compartilhamento disponível somente para matrizes e projetos."
        }
        require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(file) &&
            Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(parent)) {
            "A exportação deve ser um arquivo local existente, sem link simbólico."
        }
        require(file.toString().none { it == '"' || it == '\r' || it == '\n' }) {
            "Caminho de arquivo inválido para o Explorer."
        }

        // One Explorer argument, no cmd.exe, no shell and no URL handler.
        // The chosen file is selected, not executed or sent to any recipient.
        launchExplorer(listOf("explorer.exe", "/select,${file}"))
        return file
    }
}
