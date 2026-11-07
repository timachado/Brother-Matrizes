package com.timachado.brothermatrizes.desktop.platform

/**
 * Local-only review of URLs manually copied from Supabase Auth dashboard.
 *
 * Does NOT fetch remote configuration, save user input, log URL contents or
 * confirm that the pasted entries are active. Only an exact allowlist
 * candidate is accepted; broad wildcards and lookalike patterns fail closed.
 */
internal object DesktopRedirectAllowlistReview {
    internal data class Result(
        val candidatePresentInPastedText: Boolean,
        val checkedLineCount: Int
    )

    fun examine(pastedLines: String): Result {
        require(pastedLines.length <= 8_192) {
            "Lista de redirecionamentos extensa demais para QA."
        }
        val entries = pastedLines.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        return Result(
            candidatePresentInPastedText = entries.any {
                it == DesktopPkceRedirectPreflight.SUPABASE_ALLOWLIST_CANDIDATE
            },
            checkedLineCount = entries.size
        )
    }
}
