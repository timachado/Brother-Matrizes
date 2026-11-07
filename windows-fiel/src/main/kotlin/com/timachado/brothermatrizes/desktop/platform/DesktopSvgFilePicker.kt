package com.timachado.brothermatrizes.desktop.platform

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.Locale
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Native Windows adapter for the official Android CreateDrawingScreen SVG
 * import action. It only reads a local SVG chosen explicitly by the user.
 * This adapter never writes, uploads or opens a network connection.
 */
internal object DesktopSvgFilePicker {
    private const val MAX_BYTES = 2_000_000L

    data class Selected(val name: String, val bytes: ByteArray)

    fun chooseFromDialog(): Result<Selected>? {
        val chooser = JFileChooser().apply {
            dialogTitle = "Selecionar desenho vetorial SVG — Brother Matrizes"
            fileSelectionMode = JFileChooser.FILES_ONLY
            isAcceptAllFileFilterUsed = false
            fileFilter = FileNameExtensionFilter("Desenhos SVG (*.svg)", "svg")
        }
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return null
        return runCatching { readVerified(chooser.selectedFile.toPath()) }
    }

    /** Separate pure-file adapter allows QA without Swing or user documents. */
    fun readVerified(candidate: Path): Selected {
        val file = candidate.toAbsolutePath().normalize()
        require(file.fileName.toString().lowercase(Locale.ROOT).endsWith(".svg")) {
            "Selecione um arquivo com extensão .svg."
        }
        require(!Files.isSymbolicLink(file) &&
            !Files.isSymbolicLink(file.parent) &&
            Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            "O SVG precisa ser um arquivo local regular, não um link simbólico."
        }
        require(Files.size(file) in 1..MAX_BYTES) {
            "O SVG deve ter entre 1 byte e 2 MB."
        }
        val bytes = Files.readAllBytes(file)
        require(bytes.size.toLong() in 1..MAX_BYTES) {
            "O SVG mudou durante a leitura ou excede 2 MB."
        }
        return Selected(file.fileName.toString(), bytes)
    }
}
