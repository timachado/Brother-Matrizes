package com.timachado.brothermatrizes.desktop.platform

import java.nio.file.Path
import java.util.Locale

/**
 * Reads the optional file path delivered by the Windows shell.
 *
 * Normal startup has no arguments; the existing QA screenshot flags keep
 * their own handling. No shell commands, URL schemes or arbitrary file
 * formats are accepted. File contents are validated by DesktopEmbroideryFiles
 * on an I/O dispatcher before opening the original Android Viewer.
 *
 * Windows file associations are NOT installed by this class.
 */
internal object DesktopLaunchDocument {
    private val supportedExtensions = setOf("flp", "dst", "pes", "jef")

    fun requestedPath(args: Array<String>): Path? {
        if (args.isEmpty() || args.size != 1) return null
        val raw = args.single()
        if (raw.isBlank() || raw.startsWith("-") || raw.contains('\u0000') ||
            raw.any { it == '\r' || it == '\n' }
        ) return null
        val requested = runCatching { Path.of(raw).toAbsolutePath().normalize() }
            .getOrNull() ?: return null
        val extension = requested.fileName?.toString()
            ?.substringAfterLast('.', "")
            ?.lowercase(Locale.ROOT)
        return requested.takeIf { extension in supportedExtensions }
    }
}
