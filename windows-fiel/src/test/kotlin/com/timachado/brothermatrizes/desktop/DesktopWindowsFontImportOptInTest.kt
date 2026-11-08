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
    fun newFontImportRequiresTwoIndependentOptInsAndAnExistingQaSession() {
        assertFalse(DesktopWindowsAccountQaOptIn.fontImportEnabled(emptyMap()))
        assertFalse(DesktopWindowsAccountQaOptIn.fontImportEnabled(
            enabled - "BROTHER_WINDOWS_QA_FONT_IMPORT"
        ))
        assertFalse(DesktopWindowsAccountQaOptIn.fontImportEnabled(
            enabled - "BROTHER_WINDOWS_QA_GOOGLE_AUTH"
        ))
        assertFalse(DesktopWindowsAccountQaOptIn.fontImportEnabled(
            enabled - "BROTHER_WINDOWS_QA_SUPABASE_PUBLISHABLE_KEY"
        ))
        assertTrue(DesktopWindowsAccountQaOptIn.fontImportEnabled(enabled))
        assertNull(DesktopWindowsAccountQaOptIn.fontImportGateForQa(null, enabled))
        assertNull(DesktopWindowsAccountQaOptIn.fontImportGateForQa(null, emptyMap()))
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
