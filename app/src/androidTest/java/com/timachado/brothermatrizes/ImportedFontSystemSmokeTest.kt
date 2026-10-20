package com.timachado.brothermatrizes

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.timachado.brothermatrizes.core.embroidery.EmbroideryLoadResult
import com.timachado.brothermatrizes.core.embroidery.GeneratedMatrixPipeline
import com.timachado.brothermatrizes.core.embroidery.RasterEmbroideryApproval
import com.timachado.brothermatrizes.core.embroidery.SatinUnderlayMode
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.font.ImportedFont
import com.timachado.brothermatrizes.font.ImportedFontMatrixGenerator
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Required, deterministic Android engine smoke test that does NOT require
 * a separately licensed Great Vibes fixture.
 *
 * System-font Satin production here is independent of image-to-embroidery
 * approval. No claim of physical Brother machine stitch-out is made.
 */
@RunWith(AndroidJUnit4::class)
class ImportedFontSystemSmokeTest {
    private fun installedFont():ImportedFont {
        val file=listOf(
            File("/system/fonts/Roboto-Regular.ttf"),
            File("/system/fonts/NotoSans-Regular.ttf")
        ).firstOrNull { it.isFile && it.length()>0L }
            ?: error("O emulador Android não possui uma TTF de sistema.")
        return ImportedFont(
            id=file.name,displayName="Sistema Android",
            fileName=file.name,extension="ttf",
            absolutePath=file.absolutePath
        )
    }

    @Test
    fun importedSystemTtfSatinProducesAndReopensARealDst() {
        val font=installedFont()
        for(word in listOf("Maria","Priscila")) {
            val result=ImportedFontMatrixGenerator.generateText(
                font=font,text=word,
                options=TextMatrixOptions(
                    text=word,heightMm=18f,
                    style=TextStitchStyle.SATIN,
                    satinUnderlayMode=SatinUnderlayMode.CENTER,
                    outputFormat="DST",enforceHoop=false
                )
            ).getOrThrow()
            assertTrue("$word: Satin precisa de pontos de costura",
                result.points.any { it.command==StitchCommand.STITCH })
            assertTrue("$word: contorno gerado deve ter largura",
                result.bounds.widthMm>0f)
            assertTrue("$word: contorno gerado deve ter altura",
                result.bounds.heightMm>0f)
            val reopened=GeneratedMatrixPipeline.canonicalize(
                design=result,outputSuffix="system-font-qa"
            )
            assertTrue("$word: a matriz DST de teste deve reabrir",
                reopened is EmbroideryLoadResult.Success)
            val data=(reopened as EmbroideryLoadResult.Success).design
            assertTrue("$word: arquivo canônico não pode ficar vazio",
                data.sourceBytes.isNotEmpty() && data.stitchCount>0)
            assertTrue("$word: fonte não pode trocar cores ao reabrir",
                data.threadColors==result.threadColors)
        }
        assertFalse(
            "Imagem para Bordado permanece bloqueada para PES.",
            RasterEmbroideryApproval.canExportFromImageTool()
        )
    }
}
