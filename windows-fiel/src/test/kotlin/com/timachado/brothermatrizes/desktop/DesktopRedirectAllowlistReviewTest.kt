package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopPkceRedirectPreflight
import com.timachado.brothermatrizes.desktop.platform.DesktopRedirectAllowlistReview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** In-memory, offline review; never reads Supabase Auth settings. */
class DesktopRedirectAllowlistReviewTest {
    @Test
    fun findsOnlyExactEscapedCandidateInMultilineDashboardCopy() {
        val expected = DesktopPkceRedirectPreflight.SUPABASE_ALLOWLIST_CANDIDATE
        val copied = "https://timachado.com.br/login\n   $expected   \nhttps://example.org/callback"
        val review = DesktopRedirectAllowlistReview.examine(copied)
        assertTrue(review.candidatePresentInPastedText)
        assertEquals(3, review.checkedLineCount)
    }

    @Test
    fun rejectsLookalikesAndUnsafeBroaderWildcards() {
        val candidate = DesktopPkceRedirectPreflight.SUPABASE_ALLOWLIST_CANDIDATE
        val rejected = listOf(
            candidate.replace("\\?", "?"),
            candidate.replace("127.0.0.1", "localhost"),
            candidate.replace("127.0.0.1", "127.0.0.2"),
            candidate.replace("/brother-matrizes/auth", "/**"),
            "http://127.0.0.1:*/**",
            candidate.replace("http://", "https://"),
            candidate + " ",
            "https://example.com/**"
        )
        // The pasted value is explicitly trimmed per line; whitespace alone
        // is not semantically different, but every structural mutation is.
        rejected.dropLast(2).forEach { altered ->
            assertFalse(DesktopRedirectAllowlistReview.examine(altered)
                .candidatePresentInPastedText)
        }
        assertFalse(DesktopRedirectAllowlistReview.examine("https://example.com/**")
            .candidatePresentInPastedText)
        assertTrue(DesktopRedirectAllowlistReview.examine(" $candidate ")
            .candidatePresentInPastedText)
    }

    @Test
    fun refusesOversizedPastedTextAndDoesNotInferRemoteApproval() {
        assertFailsWith<IllegalArgumentException> {
            DesktopRedirectAllowlistReview.examine("x".repeat(8_193))
        }
        val empty = DesktopRedirectAllowlistReview.examine("  \n\t")
        assertFalse(empty.candidatePresentInPastedText)
        assertEquals(0, empty.checkedLineCount)
    }
}
