package com.timachado.brothermatrizes.core.account

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Purely local beta quota; never exercises or grants a paid entitlement. */
@RunWith(AndroidJUnit4::class)
class LocalBetaImportQuotaInstrumentedTest {
    @Test
    fun freeBetaReservationsAreBoundedPersistentAndSeparate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences(
            "brother_matrizes_local_beta_quota_v1", Context.MODE_PRIVATE
        )
        prefs.edit().clear().commit()
        try {
            assertEquals(3, LocalBetaImportQuota.limit(true))
            assertEquals(5, LocalBetaImportQuota.limit(false))

            // Unsuccessful extraction should not consume the free preview quota.
            val failed = LocalBetaImportQuota.authorize(context, true)
            LocalBetaImportQuota.finalize(context, true, failed, false)
            repeat(3) {
                val token = LocalBetaImportQuota.authorize(context, true)
                LocalBetaImportQuota.finalize(context, true, token, true)
            }
            assertTrue(runCatching {
                LocalBetaImportQuota.authorize(context, true)
            }.isFailure)

            // Matrix quota is independent of the font quota.
            repeat(5) {
                val token = LocalBetaImportQuota.authorize(context, false)
                LocalBetaImportQuota.finalize(context, false, token, true)
            }
            assertTrue(runCatching {
                LocalBetaImportQuota.authorize(context, false)
            }.isFailure)
            assertTrue(runCatching {
                LocalBetaImportQuota.finalize(
                    context, true, "beta_not-a-valid-request", true
                )
            }.isFailure)
            assertEquals(3, prefs.getInt("used_import_font", -1))
            assertEquals(5, prefs.getInt("used_import_matrix", -1))
        } finally {
            prefs.edit().clear().commit()
        }
    }
}
