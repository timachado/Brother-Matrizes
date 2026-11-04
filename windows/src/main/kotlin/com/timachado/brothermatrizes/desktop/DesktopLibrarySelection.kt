package com.timachado.brothermatrizes.desktop

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.prefs.Preferences

/**
 * Persiste apenas o caminho da pasta que o usuário selecionou explicitamente.
 * O sistema de preferências do próprio Windows é usado; não armazena matrizes,
 * credenciais, conteúdo de arquivos ou dados em servidores.
 *
 * Por segurança, a pasta é RESTAURADA como sugestão na UI, mas seus arquivos
 * NÃO são lidos automaticamente na inicialização. O usuário clica em Atualizar.
 */
internal class DesktopLibrarySelection(
    private val preferences: Preferences =
        Preferences.userNodeForPackage(DesktopLibrarySelection::class.java)
) {
    fun remember(folder: Path) {
        val absolute = folder.toAbsolutePath().normalize()
        require(Files.isDirectory(absolute, LinkOption.NOFOLLOW_LINKS) &&
                !Files.isSymbolicLink(absolute)) {
            "Selecione uma pasta local válida, sem atalhos."
        }
        preferences.put(KEY, absolute.toString())
        preferences.flush()
    }

    fun recall(): Path? {
        val stored = preferences.get(KEY, null)?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { Path.of(stored).toAbsolutePath().normalize() }
            .getOrNull()
            ?.takeIf { Files.isDirectory(it, LinkOption.NOFOLLOW_LINKS) &&
                    !Files.isSymbolicLink(it) }
    }

    fun forget() {
        preferences.remove(KEY)
        preferences.flush()
    }

    private companion object {
        const val KEY = "selectedLibraryFolder"
    }
}
