package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.HoopValidator

/**
 * Pré-avaliação geométrica dos bastidores oficiais do Android.
 *
 * Não altera a matriz, não seleciona bastidor na bordadeira e não valida
 * limites mecânicos, centralização real ou compatibilidade do equipamento.
 * A orientação alternativa apenas sinaliza uma opção a verificar fisicamente.
 */
internal enum class DesktopHoopFit {
    DIRECT,
    ALTERNATE_ORIENTATION,
    EXCEEDS
}

internal data class DesktopHoopAssessment(
    val hoop: HoopProfile,
    val status: DesktopHoopFit,
    val usableWidthMm: Float,
    val usableHeightMm: Float,
    val directOverflowWidthMm: Float,
    val directOverflowHeightMm: Float
)

internal object DesktopHoopAdvisor {
    fun analyze(design: EmbroideryDesign): List<DesktopHoopAssessment> {
        val width = design.bounds.widthMm
        val height = design.bounds.heightMm
        require(width.isFinite() && height.isFinite() && width >= 0f && height >= 0f) {
            "A matriz possui limites geométricos inválidos."
        }
        return HoopProfile.entries.map { hoop ->
            // Utiliza o mesmo validador e margens internas do motor Android.
            val direct = HoopValidator.validate(design, hoop)
            // O bastidor físico pode NÃO admitir esta orientação. Nunca
            // aplicar rotação de pontos automaticamente por este resultado.
            val alternateFits = width <= hoop.usableHeightMm &&
                height <= hoop.usableWidthMm
            DesktopHoopAssessment(
                hoop = hoop,
                status = when {
                    direct.fits -> DesktopHoopFit.DIRECT
                    alternateFits -> DesktopHoopFit.ALTERNATE_ORIENTATION
                    else -> DesktopHoopFit.EXCEEDS
                },
                usableWidthMm = direct.usableWidthMm,
                usableHeightMm = direct.usableHeightMm,
                directOverflowWidthMm = direct.widthOverflowMm,
                directOverflowHeightMm = direct.heightOverflowMm
            )
        }
    }
}
