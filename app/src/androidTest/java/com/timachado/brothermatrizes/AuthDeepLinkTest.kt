package com.timachado.brothermatrizes

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AuthDeepLinkTest {
    @Test
    fun googleOAuthCallbackResolvesToAuthCallbackActivity() {
        val context =
            ApplicationProvider
                .getApplicationContext<
                    android.content.Context
                >()

        val intent =
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                    "com.timachado.brothermatrizes://login-callback?code=test"
                )
            ).apply {
                setPackage(
                    context.packageName
                )
            }

        val resolved =
            context
                .packageManager
                .resolveActivity(
                    intent,
                    0
                )

        assertNotNull(
            "O callback OAuth precisa resolver dentro do Brother Matrizes.",
            resolved
        )

        assertEquals(
            AuthCallbackActivity::class.java
                .name,
            resolved
                ?.activityInfo
                ?.name
        )
    }
}
