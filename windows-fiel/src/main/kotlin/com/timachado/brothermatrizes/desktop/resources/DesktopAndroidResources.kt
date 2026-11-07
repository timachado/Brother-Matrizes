package com.timachado.brothermatrizes.desktop.resources

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.timachado.brothermatrizes.R
import org.jetbrains.skia.Image
import androidx.compose.ui.graphics.toComposeImageBitmap

/**
 * Adaptador Desktop, SEM redesenho: lê o SVG convertido do XML do Android,
 * a fotografia JPEG gerada pelo MESMO processamento Android e o WebP aprovado.
 */
@Composable
fun desktopPainterResource(id: Int): Painter {
    val name = when (id) {
        R.drawable.brother_bm_premium_approved -> "brother_bm_premium_approved.webp"
        R.drawable.brother_home_hero -> "brother_home_hero.jpg"
        R.drawable.ic_premium_bell -> "ic_premium_bell.svg"
        R.drawable.ic_ms_text_fields_rounded -> "ic_ms_text_fields_rounded.svg"
        R.drawable.ic_ms_font_download_rounded -> "ic_ms_font_download_rounded.svg"
        R.drawable.ic_ms_folder_open_rounded -> "ic_ms_folder_open_rounded.svg"
        R.drawable.ic_ms_play_arrow_rounded -> "ic_ms_play_arrow_rounded.svg"
        R.drawable.ic_ms_edit_rounded -> "ic_ms_edit_rounded.svg"
        R.drawable.ic_ms_swap_horiz_rounded -> "ic_ms_swap_horiz_rounded.svg"
        R.drawable.ic_ms_upload_rounded -> "ic_ms_upload_rounded.svg"
        R.drawable.ic_ms_home_rounded -> "ic_ms_home_rounded.svg"
        R.drawable.ic_ms_account_circle_rounded -> "ic_ms_account_circle_rounded.svg"
        else -> error("Drawable Android não mapeado: $id")
    }
    val resource = "drawable/$name"
    return if (name.endsWith(".webp")) {
        remember(id) {
            val bytes = checkNotNull(
                object {}.javaClass.classLoader.getResourceAsStream(resource)
            ) { "Recurso WebP Android ausente: $resource" }.use { it.readBytes() }
            BitmapPainter(Image.makeFromEncoded(bytes).toComposeImageBitmap())
        }
    } else {
        painterResource(resource)
    }
}

/**
 * A Home Android não mostra cartão de atualizações quando não há versão mais
 * nova. Durante o primeiro port visual este adaptador mantém o estado vazio,
 * como na captura de referência. A verificação de atualizações NÃO foi portada
 * e deve ser implementada/testada antes de entregar qualquer Windows funcional.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun BrotherUpdateHomeCard(refreshRequest: Int) {
    // Sem banner de atualização nesta captura de referência OFFLINE.
    // Bloqueio comercial documentado; não oferecer atualização fictícia.
}
