package com.timachado.brothermatrizes.core.updates

/**
 * Comparação por versionCode do Android, não pelo texto da versão.
 */
internal object UpdateVersionRules {
    fun isUpdateAvailable(installedCode: Long, publishedCode: Long): Boolean =
        installedCode >= 1L && publishedCode > installedCode

    fun isCritical(installedCode: Long, minimumSupportedCode: Long): Boolean =
        minimumSupportedCode > 0L && installedCode < minimumSupportedCode
}
