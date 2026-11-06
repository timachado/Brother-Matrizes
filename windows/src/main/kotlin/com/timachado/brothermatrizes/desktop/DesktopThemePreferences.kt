package com.timachado.brothermatrizes.desktop

import java.util.prefs.Preferences

/**
 * Preferência visual local do Windows.
 *
 * Persiste somente o modo claro/escuro no perfil do usuário do PC,
 * sem sincronizar dados, alterar o Android ou inicializar serviços de rede.
 * Se a preferência estiver ausente ou inválida, mantém o modo escuro oficial.
 */
internal class DesktopThemePreferences(
    private val preferences: Preferences =
        Preferences.userNodeForPackage(DesktopThemePreferences::class.java)
) {
    companion object {
        const val KEY = "themeDarkV1"
    }

    fun loadDark(): Boolean = when (preferences.get(KEY, "")) {
        "false" -> false
        else -> true
    }

    fun saveDark(enabled: Boolean) {
        preferences.put(KEY, enabled.toString())
        preferences.flush()
    }
}
