package com.timachado.brothermatrizes.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Static checks for platform-specific copy. No Google, cloud, APK or
 * commercial entitlement operation is performed here.
 */
class DesktopWindowsBrandingContractTest {
    private fun source(filename: String): String {
        val suffix = Path.of(
            "src/main/kotlin/com/timachado/brothermatrizes/desktop", filename
        )
        val candidates = listOf(suffix, Path.of("windows-fiel").resolve(suffix))
        val path = candidates.firstOrNull { Files.isRegularFile(it) }
            ?: error("Windows source not found: $filename")
        return Files.readString(path)
    }

    @Test
    fun accountAndCatalogUseBrotherMatrizesWindowsCopy() {
        val account = source("DesktopAccountQaScreen.kt")
        val catalog = source("DesktopFontCatalogScreen.kt")
        val telegram = source("DesktopTelegramMatrixSearchScreen.kt")
        assertTrue(account.contains("Planos e benefícios — Brother Matrizes Windows"))
        assertTrue(account.contains("Brother Matrizes Windows • T.I. Machado"))
        assertFalse(account.contains("Planos e benefícios — versão original Android"))
        assertFalse(catalog.contains("mesmo Supabase do Android"))
        assertFalse(catalog.contains("verificações do Android"))
        assertFalse(telegram.contains("Como no Android"))
    }

    @Test
    fun windowsCommunityUsesOnlyWindowsReleaseFeedAndDoesNotPollAndroid() {
        val community = source("DesktopBrotherCommunityScreen.kt")
        val main = source("FielMain.kt")
        assertTrue(community.contains("Atualizações Brother Matrizes Windows"))
        assertTrue(community.contains("Brother Matrizes Windows: novidades"))
        assertFalse(community.contains("Atualizações Android (APK)"))
        assertFalse(community.contains("Ativar avisos automáticos do Android"))
        assertFalse(community.contains("Consultar avisos oficiais do Android"))
        assertFalse(main.contains("LaunchedEffect(automaticNewsEnabled"))
        assertTrue(main.contains(
            "LaunchedEffect(automaticWindowsReleaseEnabled, normalWindowsSession)"
        ))
    }

    @Test
    fun backupImportCapabilitiesKeepExistingImplementationNames() {
        val main = source("FielMain.kt")
        assertTrue(main.contains("DesktopAndroidBackupInspector.inspect(backup)"))
        assertTrue(main.contains("DesktopAndroidCompleteBackupRecovery.restore("))
        assertTrue(main.contains("Backup completo Brother Matrizes"))
        assertFalse(main.contains("Backup completo (Android/Windows)"))
    }
}
