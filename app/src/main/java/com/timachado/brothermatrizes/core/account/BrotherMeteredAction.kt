package com.timachado.brothermatrizes.core.account

/** Quotas come exclusively from the server. These are wire identifiers, not entitlements. */
internal enum class BrotherMeteredAction(val operation: String) {
    OPEN_MATRIX("open_matrix"),
    RAR_RECEIVE("rar_receive"),
    TELEGRAM_SEARCH("telegram_search"),
    TELEGRAM_IMPORT("telegram_import"),
    DAFONT_SEARCH("dafont_search"),
    DAFONT_IMPORT("dafont_import");

    companion object {
        fun fromOperation(value: String): BrotherMeteredAction? =
            entries.firstOrNull { it.operation == value }
    }
}
