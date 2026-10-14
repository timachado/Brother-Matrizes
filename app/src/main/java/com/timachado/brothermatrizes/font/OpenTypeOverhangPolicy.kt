package com.timachado.brothermatrizes.font

import kotlin.math.max

/**
 * Uma feature OpenType pode substituir letras por floreios gigantes que
 * avançam para as letras vizinhas sem aumentar o advance tipográfico.
 *
 * Esse formato é apropriado para aplicação SELETIVA (início ou fim) e não
 * como um estilo global para cada letra do nome. Nunca deformar o glifo:
 * apenas impedir que a variante seja oferecida para a palavra inteira.
 *
 * Números vêm dos bounds reais do contorno Android, na mesma unidade do Paint.
 */
internal object OpenTypeOverhangPolicy {
    data class GlyphExtent(val advance: Float, val left: Float, val right: Float)

    fun isUnsafeForWholeWord(
        extent: GlyphExtent,
        position: Int,
        totalCharacters: Int
    ): Boolean {
        if (totalCharacters <= 1 || position !in 0 until totalCharacters ||
            !extent.advance.isFinite() || extent.advance <= 0f ||
            !extent.left.isFinite() || !extent.right.isFinite()) return false

        // Um floreio na primeira letra pode sair para a esquerda;
        // um floreio na última pode sair para a direita. Só impedir
        // extrapolação grande NA DIREÇÃO de letras vizinhas.
        val extreme = max(extent.advance * 1.25f, 7.5f)
        val leftOverhang = max(0f, -extent.left)
        val rightOverhang = max(0f, extent.right - extent.advance)
        return (position > 0 && leftOverhang > extreme) ||
            (position < totalCharacters - 1 && rightOverhang > extreme)
    }
}
