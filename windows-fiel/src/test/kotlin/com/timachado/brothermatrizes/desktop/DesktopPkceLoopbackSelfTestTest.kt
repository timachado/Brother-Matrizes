package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopPkceLoopbackSelfTest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Real local TCP loopback only; no browser, Google, Supabase, JWT, quota,
 * user data, account session or production filesystem side effects.
 */
class DesktopPkceLoopbackSelfTestTest {
    @Test
    fun testsRealLocalCallbackWithForgedStateRejectedAndValidStateAccepted() =
        runBlocking {
            val result = withTimeout(20_000) {
                DesktopPkceLoopbackSelfTest.verify()
            }
            assertTrue(result.localListenerAvailable)
            assertTrue(result.forgedStateRejected)
            assertTrue(result.validLocalRoundTrip)
        }

    @Test
    fun repeatedTestsCloseEphemeralPortAndDoNotPersistSessions() = runBlocking {
        repeat(3) {
            val result = withTimeout(20_000) {
                DesktopPkceLoopbackSelfTest.verify()
            }
            assertTrue(result.validLocalRoundTrip)
        }
    }
}
