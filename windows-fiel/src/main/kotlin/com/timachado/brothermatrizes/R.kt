package com.timachado.brothermatrizes

/**
 * IDs estáveis dos drawables OFICIAIS usados pela Home e navegação Android.
 * O mapeamento Desktop usa EXATAMENTE os mesmos nomes e bytes/fontes do app.
 * Não representa Android R gerado: é um adaptador de recursos JVM.
 */
internal object R {
    object drawable {
        const val brother_bm_premium_approved = 1
        const val brother_home_hero = 2
        const val ic_premium_bell = 3
        const val ic_ms_text_fields_rounded = 4
        const val ic_ms_font_download_rounded = 5
        const val ic_ms_folder_open_rounded = 6
        const val ic_ms_play_arrow_rounded = 7
        const val ic_ms_edit_rounded = 8
        const val ic_ms_swap_horiz_rounded = 9
        const val ic_ms_upload_rounded = 10
        const val ic_ms_home_rounded = 11
        const val ic_ms_account_circle_rounded = 12
    }
}

/** Rótulo apenas de interface. O aplicativo comercial Android permanece intacto. */
internal object BuildConfig {
    const val VERSION_NAME = "1.11.8"
}
