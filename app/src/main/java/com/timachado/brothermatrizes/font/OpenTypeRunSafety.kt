package com.timachado.brothermatrizes.font

/**
 * A prévia só deve oferecer alternativas OpenType que o extrator Satin
 * consegue digitalizar como um único contorno. O texto padrão continua
 * disponível pelo caminho tradicional, inclusive para nomes mais longos.
 *
 * A razão avanço / altura da letra H é independente da escala escolhida
 * pelo usuário e igual à usada pelo AndroidSkiaOutlineExtractor.
 */
internal object OpenTypeRunSafety {
    private const val MAX_WIDTH_IN_CAP_HEIGHTS = 6f

    fun allowsShaping(codePointCount: Int): Boolean = codePointCount in 2..9

    fun allowsAdvance(advance: Float, capHeight: Float): Boolean =
        advance.isFinite() && capHeight.isFinite() &&
            advance > 0f && capHeight > 0f &&
            advance <= capHeight * MAX_WIDTH_IN_CAP_HEIGHTS
}
