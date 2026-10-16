package com.timachado.brothermatrizes.core.embroidery

/**
 * Fail-closed approval policy for unverified raster-to-stitch generation.
 *
 * The original SimulatorScreen is useful for reviewing needle paths but
 * cannot certify physical machine safety. A human-approved stitch-out,
 * algorithm-specific stitch constraints and further tests are required
 * before this pipeline is allowed to create transferable PES files.
 *
 * This object is only invoked by the experimental "Imagem para Bordado"
 * route and does not change legacy imported PES/JEF/DST behavior.
 */
object RasterEmbroideryApproval {
    const val EXPORT_ALLOWED = false

    const val WARNING = "APENAS PRÉVIA — NÃO ENVIAR PARA A MÁQUINA. " +
        "O preenchimento automático da imagem ainda é experimental e " +
        "não foi homologado em bordado real."

    fun canExportFromImageTool(): Boolean = EXPORT_ALLOWED

    fun isReadOnlyPreview(imageToEmbroideryTool: Boolean): Boolean =
        imageToEmbroideryTool && !canExportFromImageTool()
}
