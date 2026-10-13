package com.timachado.brothermatrizes.core.account

/** Monthly quotas come exclusively from the server. These are wire identifiers, not entitlements. */
enum class BrotherMeteredAction(val operation: String) {
    RAR_RECEIVE("rar_receive"),
    TELEGRAM_SEARCH("telegram_search"),
    DAFONT_SEARCH("dafont_search");

    companion object {
        fun fromOperation(value: String): BrotherMeteredAction? =
            entries.firstOrNull { it.operation == value }
    }
}
