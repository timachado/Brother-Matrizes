package com.timachado.brothermatrizes.core.account

/**
 * Ações externas controladas por conta Google verificada.
 * Não substitui import_font/import_matrix: qualquer arquivo importado
 * continua consumindo sua cota compartilhada de importação.
 */
internal enum class BrotherMeteredAction(val operation: String, val freeMonthlyLimit: Int) {
    RAR_OPEN("rar_open", 5),
    RAR_SEND("rar_send", 5),
    TELEGRAM_SEARCH("telegram_search", 5),
    DAFONT_SEARCH("dafont_search", 10);

    companion object {
        fun fromOperation(value: String): BrotherMeteredAction? =
            entries.firstOrNull { it.operation == value }
    }
}
