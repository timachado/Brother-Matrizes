package com.timachado.brothermatrizes.desktop.platform

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.Locale
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Swing dialog runs on EDT, while Compose awaits from Dispatchers.IO. */
internal object DesktopNativeMatrixPicker {
    const val TITLE = "Abrir matriz DST, PES, JEF ou projeto FLP"
    private val extensions = setOf("dst", "pes", "jef", "flp")

    fun configuredChooser(qaFile: Path? = null): JFileChooser = JFileChooser().apply {
        dialogTitle = TITLE
        fileSelectionMode = JFileChooser.FILES_ONLY
        isAcceptAllFileFilterUsed = false
        fileFilter = FileNameExtensionFilter(
            "Matrizes e projetos Brother Matrizes (*.dst, *.pes, *.jef, *.flp)",
            "dst", "pes", "jef", "flp"
        )
        if (qaFile != null) {
            currentDirectory = qaFile.parent.toFile()
            selectedFile = qaFile.toFile()
        }
    }

    suspend fun choose(qaFile: Path? = null): Path? = withContext(Dispatchers.IO) {
        var result: Path? = null
        // Do not call a blocking Swing modal dialog on Compose's coroutine UI
        // dispatcher. QA still sees the same JFileChooser via AWT Robot.
        SwingUtilities.invokeAndWait {
            val chooser = configuredChooser(qaFile)
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                result = chooser.selectedFile?.toPath()?.toAbsolutePath()?.normalize()
            }
        }
        result
    }

    fun validateSelectedFile(selected: Path): Path {
        val path = selected.toAbsolutePath().normalize()
        val ext = path.fileName.toString().substringAfterLast('.', "")
            .lowercase(Locale.ROOT)
        require(ext in extensions && !Files.isSymbolicLink(path) &&
            !Files.isSymbolicLink(path.parent) &&
            Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) &&
            Files.size(path) in 1..(128L * 1024L * 1024L)) {
            "Selecione uma matriz local DST, PES, JEF ou projeto FLP válido."
        }
        return path
    }
}
