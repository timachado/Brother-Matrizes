package com.timachado.brothermatrizes.desktop.platform

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.Locale

/**
 * Read-only bridge for fonts recovered from an ORIGINAL Android full backup.
 *
 * Recovery keeps the original TTF/OTF bytes in a separate folder. This
 * inspection does not enroll a font in the Windows commercial library,
 * generate embroidery, obtain a token, or reserve/consume a quota.
 */
internal object DesktopRecoveredAndroidFontPreflight {
    private val safeName = Regex("[A-Za-z0-9._-]{1,100}\\.(ttf|otf)", RegexOption.IGNORE_CASE)

    data class Item(
        val fileName: String,
        val displayName: String,
        val readableOutlines: Boolean
    )

    data class Report(val items: List<Item>) {
        val readableCount: Int get() = items.count { it.readableOutlines }
        val blockedCount: Int get() = items.size - readableCount
    }

    fun inspect(recoveredFontsFolder: Path): Report {
        val root = recoveredFontsFolder.toAbsolutePath().normalize()
        require(!Files.isSymbolicLink(root) &&
            Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            "A pasta recuperada de fontes precisa ser um diretório local real."
        }

        val entries = Files.list(root).use { it.toList() }
        require(entries.size <= 200) { "O backup excede o limite de 200 fontes." }
        val seen = HashSet<String>()
        val report = entries.sortedBy { it.fileName.toString().lowercase(Locale.ROOT) }
            .map { file ->
                val name = file.fileName.toString()
                require(file.normalize().parent == root && safeName.matches(name) &&
                    seen.add(name.lowercase(Locale.ROOT))) {
                    "A pasta recuperada contém nomes inválidos ou duplicados."
                }

                // Reject links, malformed OpenType files and inaccessible glyphs
                // without modifying the recovered bytes or a commercial catalog.
                val metadata = runCatching {
                    require(!Files.isSymbolicLink(file) &&
                        Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                        "Arquivo recuperado não é uma fonte local regular."
                    }
                    DesktopReadOnlyFontAnalyzer.inspect(file)
                }.getOrNull()
                val readable = metadata != null &&
                    metadata.testedGlyphs > 0 && metadata.visibleOutlines > 0
                val nameFromFont = if (readable) {
                    DesktopOpenTypeFontNames.readDisplayName(file.toFile())
                } else null
                Item(
                    fileName = name,
                    displayName = nameFromFont?.takeIf { it.isNotBlank() }
                        ?: metadata?.familyName?.takeIf { it.isNotBlank() }
                        ?: name.substringBeforeLast('.'),
                    readableOutlines = readable
                )
            }
        return Report(report)
    }
}
