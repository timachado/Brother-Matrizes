package com.timachado.brothermatrizes.desktop.platform

import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Native dialogs for DaFont / NetFontes font ZIPs and TTF/OTF.
 * AWT modal UI stays on Swing EDT and never blocks Compose's dispatcher.
 * Does not download, install fonts, debit quotas or change licenses.
 */
internal object DesktopNativeFontPicker {
    fun configuredFontChooser(title: String): JFileChooser = JFileChooser().apply {
        require(title.isNotBlank())
        dialogTitle = title
        fileSelectionMode = JFileChooser.FILES_ONLY
        isAcceptAllFileFilterUsed = false
        fileFilter = FileNameExtensionFilter("Fontes TrueType/OpenType (*.ttf, *.otf)", "ttf", "otf")
    }

    fun configuredZipChooser(): JFileChooser = JFileChooser().apply {
        dialogTitle = "Inspecionar ZIP de fontes DaFont/NetFontes — sem importar"
        fileSelectionMode = JFileChooser.FILES_ONLY
        isAcceptAllFileFilterUsed = false
        fileFilter = FileNameExtensionFilter("Arquivos ZIP de fontes (*.zip)", "zip")
    }

    private suspend fun choose(configured: () -> JFileChooser): Path? =
        withContext(Dispatchers.IO) {
            var chosen: Path? = null
            SwingUtilities.invokeAndWait {
                val picker = configured()
                if (picker.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    chosen = picker.selectedFile?.toPath()?.toAbsolutePath()?.normalize()
                }
            }
            chosen
        }

    suspend fun chooseFont(title: String): Path? = choose { configuredFontChooser(title) }

    suspend fun chooseZip(): Path? = choose(::configuredZipChooser)

    /** Confirmation is shown BEFORE the server-authenticated import starts. */
    suspend fun confirmImport(fontName: String): Boolean = withContext(Dispatchers.IO) {
        require(fontName.isNotBlank() && fontName.length <= 300)
        var allowed = false
        SwingUtilities.invokeAndWait {
            val answer = JOptionPane.showConfirmDialog(
                null,
                "Importar a fonte " + fontName + " do ZIP analisado?\n" +
                    "Confirme que você possui autorização de uso.\n" +
                    "Esta ação exigirá conta Google e autorização de cota no servidor.\n" +
                    "Cancelar não consome cota.",
                "Brother Matrizes — confirmar importação de fonte",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            )
            allowed = answer == JOptionPane.YES_OPTION
        }
        allowed
    }
}
