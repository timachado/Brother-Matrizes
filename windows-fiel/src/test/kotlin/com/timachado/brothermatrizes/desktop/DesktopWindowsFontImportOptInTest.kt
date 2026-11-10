package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopWindowsAccountQaOptIn
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Default portable build must never offer real import_font transactions. */
class DesktopWindowsFontImportOptInTest {
    private val publishable = "sb_publishable_offline_test_only_123456789012345"
    private val enabled = mapOf(
        "BROTHER_WINDOWS_QA_GOOGLE_AUTH" to "enabled",
        "BROTHER_WINDOWS_QA_SUPABASE_PUBLISHABLE_KEY" to publishable,
        "BROTHER_WINDOWS_QA_FONT_IMPORT" to "enabled"
    )

    @Test
    fun realFontImportRequiresExplicitGoogleRedirectV2AndImportOptIns() {
        val reviewer = "BROTHER_WINDOWS_QA_REDIRECT_REVIEWED"
        val backend = "BROTHER_WINDOWS_QA_FONT_V2_BACKEND_REVIEWED"
        val qaReady = enabled + (reviewer to "enabled") +
            (backend to "enabled")
        assertFalse(DesktopWindowsAccountQaOptIn.fontImportEnabled(emptyMap()))
        assertFalse(DesktopWindowsAccountQaOptIn.fontImportEnabled(enabled))
        for (missing in listOf(
            "BROTHER_WINDOWS_QA_FONT_IMPORT",
            "BROTHER_WINDOWS_QA_GOOGLE_AUTH",
            "BROTHER_WINDOWS_QA_SUPABASE_PUBLISHABLE_KEY",
            reviewer,
            backend
        )) {
            assertFalse(DesktopWindowsAccountQaOptIn.fontImportEnabled(
                qaReady - missing
            ))
        }
        assertFalse(DesktopWindowsAccountQaOptIn.fontImportEnabled(
            qaReady + (backend to "true")
        ))
        assertFalse(DesktopWindowsAccountQaOptIn.fontImportEnabled(
            qaReady + (reviewer to "true")
        ))
        assertTrue(DesktopWindowsAccountQaOptIn.fontImportEnabled(qaReady))
        // An operator configuration alone NEVER invents a Google session.
        assertNull(DesktopWindowsAccountQaOptIn.fontImportGateForQa(null, qaReady))
        assertNull(DesktopWindowsAccountQaOptIn.fontImportGateForQa(null, enabled))
        assertNull(DesktopWindowsAccountQaOptIn.fontImportGateForQa(null, emptyMap()))
    }

    @Test
    fun archiveImportRequiresSeparateReviewedV2ConsentAndVerifiedGoogle() {
        val import = "BROTHER_WINDOWS_QA_ARCHIVE_IMPORT"
        val backend = "BROTHER_WINDOWS_QA_ARCHIVE_V2_BACKEND_REVIEWED"
        val redirect = "BROTHER_WINDOWS_QA_REDIRECT_REVIEWED"
        val approved = enabled + (import to "enabled") +
            (backend to "enabled") + (redirect to "enabled")
        assertFalse(DesktopWindowsAccountQaOptIn.archiveImportEnabled(emptyMap()))
        assertFalse(DesktopWindowsAccountQaOptIn.archiveImportEnabled(enabled))
        for (missing in listOf(
            import, backend, redirect,
            "BROTHER_WINDOWS_QA_GOOGLE_AUTH",
            "BROTHER_WINDOWS_QA_SUPABASE_PUBLISHABLE_KEY"
        )) {
            assertFalse(DesktopWindowsAccountQaOptIn.archiveImportEnabled(
                approved - missing
            ))
        }
        assertFalse(DesktopWindowsAccountQaOptIn.archiveImportEnabled(
            approved + (backend to "true")
        ))
        assertFalse(DesktopWindowsAccountQaOptIn.archiveImportEnabled(
            approved + (import to "true")
        ))
        assertTrue(DesktopWindowsAccountQaOptIn.archiveImportEnabled(approved))
        assertNull(DesktopWindowsAccountQaOptIn.archiveImportForQa(
            null, null, approved
        ))

        val host = Files.readString(Path.of(
            "src/main/kotlin/com/timachado/brothermatrizes/desktop/FielMain.kt"
        ))
        assertTrue(host.contains("DesktopWindowsAccountQaOptIn.archiveImportEnabled()"))
        assertTrue(host.contains("DesktopWindowsAccountQaOptIn.archiveImportForQa("))
        val script = Files.readString(Path.of("build.gradle.kts"))
        assertTrue(script.contains(
            "onAuthorizedImport: (suspend (ArchiveInventory, ArchiveItem, CharArray) -> Unit)? = null"
        ))
        assertTrue(script.contains(
            "IMPORTAR ITEM COM CONTA E COTA"
        ))
    }

    @Test
    fun telegramMatrixImportRequiresItsOwnOptInAndSharesArchiveJournal() {
        val telegram = "BROTHER_WINDOWS_QA_TELEGRAM_IMPORT"
        val archive = "BROTHER_WINDOWS_QA_ARCHIVE_IMPORT"
        val backend = "BROTHER_WINDOWS_QA_ARCHIVE_V2_BACKEND_REVIEWED"
        val redirect = "BROTHER_WINDOWS_QA_REDIRECT_REVIEWED"
        val approved = enabled + (telegram to "enabled") +
            (archive to "enabled") + (backend to "enabled") +
            (redirect to "enabled")
        assertFalse(DesktopWindowsAccountQaOptIn.telegramImportEnabled(emptyMap()))
        assertFalse(DesktopWindowsAccountQaOptIn.telegramImportEnabled(enabled))
        for (missing in listOf(
            telegram, archive, backend, redirect,
            "BROTHER_WINDOWS_QA_GOOGLE_AUTH",
            "BROTHER_WINDOWS_QA_SUPABASE_PUBLISHABLE_KEY"
        )) {
            assertFalse(DesktopWindowsAccountQaOptIn.telegramImportEnabled(
                approved - missing
            ))
        }
        assertFalse(DesktopWindowsAccountQaOptIn.telegramImportEnabled(
            approved + (telegram to "true")
        ))
        assertTrue(DesktopWindowsAccountQaOptIn.telegramImportEnabled(approved))
        assertNull(DesktopWindowsAccountQaOptIn.telegramImportForQa(
            null, null, approved
        ))
        val host = Files.readString(Path.of(
            "src/main/kotlin/com/timachado/brothermatrizes/desktop/FielMain.kt"
        ))
        assertTrue(host.contains("DesktopWindowsAccountQaOptIn.telegramImportEnabled()"))
        assertTrue(host.contains("DesktopWindowsAccountQaOptIn.telegramImportForQa("))
        assertTrue(host.contains("gate.importStandaloneMatrix(selected)"))
        val screen = Files.readString(Path.of(
            "src/main/kotlin/com/timachado/brothermatrizes/desktop/" +
                "DesktopTelegramMatrixSearchScreen.kt"
        ))
        assertTrue(screen.contains("onImportLocalFile: (() -> Unit)? = null"))
        assertTrue(screen.contains("if (onImportLocalFile != null)"))
        val shared = Files.readString(Path.of(
            "src/main/kotlin/com/timachado/brothermatrizes/desktop/platform/" +
                "DesktopWindowsArchiveAuthorizedImport.kt"
        ))
        assertTrue(shared.contains("DesktopWindowsStandaloneMatrixAuthorizedImport("))
        assertTrue(shared.contains("journal = journal"))
    }

    @Test
    fun remoteReservationInspectionRequiresSeparateReviewedBackendRollout() {
        val v2 = "BROTHER_WINDOWS_QA_KNOWN_QUOTA_STATUS_V2"
        // The current production Edge Function supports legacy aggregate
        // status only. Merely enabling Google or font import cannot enable
        // a per-request status endpoint which is not deployed.
        assertFalse(DesktopWindowsAccountQaOptIn.knownQuotaStatusV2Enabled(
            emptyMap()
        ))
        assertFalse(DesktopWindowsAccountQaOptIn.knownQuotaStatusV2Enabled(
            enabled
        ))
        assertFalse(DesktopWindowsAccountQaOptIn.knownQuotaStatusV2Enabled(
            mapOf(v2 to "enabled")
        ))
        assertFalse(DesktopWindowsAccountQaOptIn.knownQuotaStatusV2Enabled(
            (enabled - "BROTHER_WINDOWS_QA_GOOGLE_AUTH") + (v2 to "enabled")
        ))
        assertFalse(DesktopWindowsAccountQaOptIn.knownQuotaStatusV2Enabled(
            (enabled - "BROTHER_WINDOWS_QA_SUPABASE_PUBLISHABLE_KEY") +
                (v2 to "enabled")
        ))
        assertFalse(DesktopWindowsAccountQaOptIn.knownQuotaStatusV2Enabled(
            enabled + (v2 to "true")
        ))
        val qaReady = enabled + (v2 to "enabled")
        assertTrue(DesktopWindowsAccountQaOptIn.knownQuotaStatusV2Enabled(
            qaReady
        ))
        assertNull(DesktopWindowsAccountQaOptIn.remoteFontQuotaReviewForQa(
            null, qaReady
        ))
        assertNull(DesktopWindowsAccountQaOptIn.remoteFontQuotaReviewForQa(
            null, enabled
        ))

        val screen = Files.readString(Path.of(
            "src/main/kotlin/com/timachado/brothermatrizes/desktop/" +
                "DesktopAccountQaScreen.kt"
        ))
        assertTrue(screen.contains(
            "DesktopWindowsAccountQaOptIn.knownQuotaStatusV2Enabled("
        ))
        assertTrue(screen.contains("Consulta remota desabilitada"))
    }

    @Test
    fun standardUiCannotExposeCommercialImportWithoutOptIn() {
        val source = Files.readString(Path.of(
            "src/main/kotlin/com/timachado/brothermatrizes/desktop/" +
                "DesktopFontCatalogScreen.kt"
        ))
        assertTrue(source.contains("importGate: DesktopCommercialFontImportGate? = null"))
        assertTrue(source.contains("if (importGate != null)"))
        assertTrue(source.contains("Confirmar importação e possível desconto da cota"))
        assertTrue(source.contains("Cancelar sem consumir cota"))
        assertTrue(source.contains("DesktopReadOnlyFontAnalyzer.inspect(selected)"))
        val host = Files.readString(Path.of(
            "src/main/kotlin/com/timachado/brothermatrizes/desktop/FielMain.kt"
        ))
        assertTrue(host.contains("DesktopWindowsAccountQaOptIn.fontImportGateForQa"))
    }
}
