package com.timachado.brothermatrizes.desktop.platform

import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.font.CleanRoomSatinEngine
import com.timachado.brothermatrizes.font.ImportedFont as AndroidImportedFont
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Locale
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

/**
 * Ponte das fontes TTF/OTF previamente registradas na biblioteca privada do
 * Windows. Nenhum arquivo de origem é importado aqui; login, reserva de cota,
 * exclusão e persistência de NOVAS fontes continuam com o gate comercial.
 *
 * Igual ao Android, uma fonte que JÁ existe na biblioteca pode ser usada para
 * gerar bordado offline. O identificador de 10 hex é revalidado contra SHA-256
 * a cada seleção/geração, evitando aceitar um arquivo substituído.
 */
data class DesktopImportedFont(
    val id: String,
    val displayName: String,
    val fileName: String = id
)

internal object DesktopSavedFontBridge {
    private const val MAX_FONT_BYTES = 12L * 1024L * 1024L
    private val safeId = Regex("[A-Za-z0-9._-]+-[0-9a-f]{10}\\.(ttf|otf)")

    fun standardFolder(): Path =
        DesktopWindowsDpapiSessionStore.defaultUserDataFolder()
            .resolve("fonts").toAbsolutePath().normalize()

    fun list(folder: Path = standardFolder()): List<DesktopImportedFont> {
        val root = folder.toAbsolutePath().normalize()
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return emptyList()
        // Use the SAME Android-style imported library as DesktopFontCatalog.
        val catalog = DesktopReadOnlyFontCatalog(root).snapshot()
        return catalog.localFontMetadata
            .filter { record -> verify(record, root) }
            .map { DesktopImportedFont(it.id, it.displayName, it.fileName) }
    }

    fun generate(
        font: DesktopImportedFont,
        text: String,
        options: TextMatrixOptions,
        filePrefix: String,
        folder: Path = standardFolder()
    ): Result<EmbroideryDesign> = runCatching {
        require(options.style == TextStitchStyle.SATIN &&
            options.specialStitchMode == null) {
            "Fontes TTF/OTF importadas: Satin original Android disponível; outros estilos ainda não portados."
        }
        require(text.isNotBlank() && text.length <= 24 &&
            text.none { Character.isISOControl(it) || Character.isSurrogate(it) }) {
            "Texto de fonte importada inválido."
        }
        require(font.id == font.fileName && safeId.matches(font.id)) {
            "Identificador da fonte importada inválido."
        }
        val root = folder.toAbsolutePath().normalize()
        val selected = DesktopReadOnlyFontCatalog(root).snapshot()
            .localFontMetadata.firstOrNull { it.id == font.id }
            ?: error("Fonte ausente da biblioteca local.")
        check(verify(selected, root)) {
            "Fonte importada mudou ou está corrompida: geração bloqueada."
        }
        val androidFont = AndroidImportedFont(
            id = selected.id, displayName = selected.displayName,
            fileName = selected.fileName, extension = selected.extension,
            absolutePath = selected.absolutePath
        )
        CleanRoomSatinEngine.generate(
            font = androidFont, sourceText = text,
            options = options, filePrefix = filePrefix
        ).getOrThrow()
    }

    private fun verify(font: DesktopFontRecord, folder: Path): Boolean =
        runCatching {
            if (!safeId.matches(font.id) || font.id != font.fileName) return false
            val file = Path.of(font.absolutePath).toAbsolutePath().normalize()
            if (file.parent != folder || Files.isSymbolicLink(folder) ||
                !Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS) ||
                Files.isSymbolicLink(file) ||
                !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return false
            val size = Files.size(file)
            if (size !in 4..MAX_FONT_BYTES) return false
            val data = Files.readAllBytes(file)
            if (data.size.toLong() != size) return false
            val format = when {
                data.take(4) == listOf(0.toByte(), 1.toByte(), 0.toByte(), 0.toByte()) ||
                    String(data, 0, 4, Charsets.US_ASCII) == "true" -> "ttf"
                String(data, 0, 4, Charsets.US_ASCII) == "OTTO" -> "otf"
                else -> return false
            }
            if (font.extension.lowercase(Locale.ROOT) != format) return false
            val hash = MessageDigest.getInstance("SHA-256").digest(data)
                .take(5).joinToString("") { "%02x".format(it.toInt() and 0xff) }
            font.id.substringBeforeLast('.').endsWith("-$hash", ignoreCase = true)
        }.getOrDefault(false)
}

object ImportedFontStore {
    @Suppress("UNUSED_PARAMETER")
    fun list(context: DesktopNameContext): List<DesktopImportedFont> =
        DesktopSavedFontBridge.list()
}

/**
 * Reuse the ACTIVE Android Satin engine copied byte-for-byte in Gradle.
 * This never imports a font or reserves quota; it only uses validated files
 * already in the local imported-font directory, as Android does.
 */
object ImportedFontMatrixGenerator {
    fun generateGlyph(
        font: DesktopImportedFont, char: Char, options: TextMatrixOptions
    ): Result<EmbroideryDesign> =
        DesktopSavedFontBridge.generate(font, char.toString(), options, "fonte")

    fun generateText(
        font: DesktopImportedFont, text: String, options: TextMatrixOptions
    ): Result<EmbroideryDesign> =
        DesktopSavedFontBridge.generate(font, text, options, "nome")
}
