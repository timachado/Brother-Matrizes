package com.timachado.brothermatrizes.desktop.platform

import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import java.util.prefs.Preferences

/**
 * Adaptadores estritamente locais ao Windows, isolados das preferências
 * e arquivos do APK comercial. Nunca importar Context Android para JVM.
 *
 * ATENÇÃO: fontes TTF/OTF externas ainda não estão portadas. Em vez de
 * fingir que funcionam, somente os presets embutidos do motor Android
 * são habilitados nesta primeira revisão.
 */
object Context {
    const val MODE_PRIVATE = 0
}

object LocalContext {
    val current: DesktopNameContext = DesktopNameContext
}

object DesktopNameContext {
    fun getSharedPreferences(name: String, mode: Int): DesktopNamePreferences {
        require(mode == Context.MODE_PRIVATE) { "Modo de preferências inválido." }
        require(name == "create_name_preferences_v2") {
            "Acesso fora das preferências do Criar Nome recusado."
        }
        return DesktopNamePreferences(
            Preferences.userRoot().node(
                "/com/timachado/brothermatrizes/windows-fiel-qa/create_name"
            )
        )
    }
}

class DesktopNamePreferences internal constructor(
    private val backing: Preferences
) {
    fun getString(key: String, default: String?): String? {
        validateKey(key)
        return backing.get(key, default)
    }

    fun edit(): Editor = Editor(backing)

    class Editor internal constructor(private val backing: Preferences) {
        private val changes = linkedMapOf<String, String?>()
        fun putString(key: String, value: String?): Editor = apply {
            validateKey(key)
            changes[key] = value
        }
        fun remove(key: String): Editor = apply {
            validateKey(key)
            changes[key] = null
        }
        fun apply() {
            changes.forEach { (key, value) ->
                if (value == null) backing.remove(key)
                else backing.put(key, value)
            }
            // Preferences Java persiste sem usar arquivos do Android/produção.
        }
    }
}

private val permittedPreferenceKeys = setOf(
    "display_mode", "imported_font_id", "built_in_font", "selected_hoop"
)
private fun validateKey(key: String) {
    require(key in permittedPreferenceKeys) { "Preferência não reconhecida: $key" }
}

/** Tipagem de fonte necessária apenas para compilar a tela Android sem enganar. */
data class DesktopImportedFont(
    val id: String,
    val displayName: String,
    // Metadado exigido pelo seletor Android ao distinguir nomes duplicados.
    // Não concede acesso à fonte nem produz pontadas TTF/OTF.
    val fileName: String = id
)

object ImportedFontStore {
    /** Sem importação TTF/OTF até implementar o motor de fontes da plataforma. */
    @Suppress("UNUSED_PARAMETER")
    fun list(context: DesktopNameContext): List<DesktopImportedFont> = emptyList()
}

object ImportedFontMatrixGenerator {
    @Suppress("UNUSED_PARAMETER")
    fun generateGlyph(
        font: DesktopImportedFont, char: Char, options: TextMatrixOptions
    ): Result<EmbroideryDesign> = Result.failure(
        UnsupportedOperationException("Fonte TTF/OTF Android ainda não portada ao Windows.")
    )

    @Suppress("UNUSED_PARAMETER")
    fun generateText(
        font: DesktopImportedFont, text: String, options: TextMatrixOptions
    ): Result<EmbroideryDesign> = Result.failure(
        UnsupportedOperationException("Fonte TTF/OTF Android ainda não portada ao Windows.")
    )
}
