package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.EmbroideryIntegrity

/**
 * Editor local de metadados RGB das linhas (não muda a geometria nem cria
 * novos comandos COLOR_CHANGE). Usa o mesmo fallback RGB do MatrixConverter
 * Android oficial e a verificação de integridade oficial após a edição.
 * Não grava arquivos. Máximo de 64 cores para impedir operações de QA
 * acidentais em paletas enormes.
 */
internal object DesktopThreadColorEditor {
    const val MAX_EDITABLE_COLORS = 64

    // RGB 24-bit, alinhados ao fallbackColors oficial do MatrixConverter.
    private val fallbackColors = listOf(
        0xE6BE70, 0xE76F51, 0x2A9D8F, 0x264653,
        0x9B5DE5, 0xF4A261, 0x457B9D, 0xE63946
    )

    fun parseHex(raw: String): Int {
        val text = raw.trim().removePrefix("#")
        require(Regex("[0-9A-Fa-f]{6}").matches(text)) {
            "Informe uma cor hexadecimal RGB, como #457B9D."
        }
        return text.toInt(16)
    }

    fun hex(rgb: Int): String = "#%06X".format(rgb and 0xFFFFFF)

    fun palette(design: EmbroideryDesign): List<Int> {
        val usedIndex = design.points.maxOfOrNull { it.colorIndex } ?: 0
        require(usedIndex >= 0) { "Há um índice de linha inválido." }
        val count = maxOf(design.colorCount, design.threadColors.size, usedIndex + 1, 1)
        require(count <= MAX_EDITABLE_COLORS) {
            "Esta matriz tem $count cores; a edição de cores aceita até $MAX_EDITABLE_COLORS."
        }
        return List(count) { index ->
            (design.threadColors.getOrNull(index)
                ?: fallbackColors[index % fallbackColors.size]) and 0xFFFFFF
        }
    }

    fun recolor(document: DesktopMatrixDocument, index: Int, rgb: Int): DesktopMatrixDocument {
        val colors = palette(document.current)
        require(index in colors.indices) {
            "Número da linha fora do intervalo 1–${colors.size}."
        }
        require(rgb in 0..0xFFFFFF) { "Cor RGB fora do intervalo permitido." }
        if (colors[index] == rgb && document.current.threadColors == colors) {
            return document
        }
        val updated = document.current.copy(
            threadColors = colors.toMutableList().also { it[index] = rgb }.toList(),
            isModified = true
        )
        val normalized = EmbroideryIntegrity.normalize(updated).getOrThrow().design
        require(normalized.stitchCount > 0 && normalized.endFound) {
            "Matriz com pontadas incompletas; alteração de cor recusada."
        }
        return document.copy(current = normalized)
    }
}
