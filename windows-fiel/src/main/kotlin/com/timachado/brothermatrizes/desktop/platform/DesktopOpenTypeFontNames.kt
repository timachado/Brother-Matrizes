package com.timachado.brothermatrizes.desktop.platform

import com.timachado.brothermatrizes.font.OpenTypeFontNames
import java.io.File

/**
 * Compatibility façade for existing desktop callers/tests.
 * The parser itself is the ORIGINAL Android 1.11.9 OpenTypeFontNames.kt,
 * copied byte-for-byte by Gradle. No divergent Windows parsing rules.
 */
internal object DesktopOpenTypeFontNames {
    fun readDisplayName(file: File): String? = OpenTypeFontNames.readDisplayName(file)
}
