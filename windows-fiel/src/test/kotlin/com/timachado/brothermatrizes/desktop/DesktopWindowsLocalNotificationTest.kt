package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopWindowsLocalNotification
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Fake OS transport. Does not display any actual notification on CI. */
class DesktopWindowsLocalNotificationTest {
    private class FakeTransport(
        val available: Boolean
    ) : DesktopWindowsLocalNotification.Transport {
        var supportsCalls = 0
        var sends = 0
        var capturedTitle: String? = null
        var capturedMessage: String? = null

        override fun supported(): Boolean {
            supportsCalls++
            return available
        }
        override fun display(title: String, message: String): Boolean {
            sends++
            capturedTitle = title
            capturedMessage = message
            return true
        }
    }

    @Test
    fun nonWindowsAndUnavailableTrayDoNotSendAnything() {
        val fake = FakeTransport(true)
        assertFalse(DesktopWindowsLocalNotification.showAfterUserClick(
            "Brother Matrizes", "Aviso local de teste", "Linux", fake
        ))
        assertEquals(0, fake.sends)
        assertEquals(0, fake.supportsCalls)
        val blocked = FakeTransport(false)
        assertFalse(DesktopWindowsLocalNotification.showAfterUserClick(
            "Brother Matrizes", "Aviso local de teste", "Windows 11", blocked
        ))
        assertEquals(0, blocked.sends)
    }

    @Test
    fun explicitWindowsUserActionUsesOnlySuppliedPublicText() {
        val fake = FakeTransport(true)
        val title = "Brother Matrizes — novidades Android"
        val message = "Versão Android 1.11.8. Windows ainda em homologação."
        assertTrue(DesktopWindowsLocalNotification.showAfterUserClick(
            title, message, "Windows 11", fake
        ))
        assertEquals(1, fake.sends)
        assertEquals(title, fake.capturedTitle)
        assertEquals(message, fake.capturedMessage)
    }

    @Test
    fun longOrControlCharacterPayloadsAreRejectedBeforeTouchingOs() {
        val fake = FakeTransport(true)
        for ((title, message) in listOf(
            "" to "Ok",
            "A".repeat(91) to "Ok",
            "Ok" to "",
            "Ok" to "B".repeat(241),
            "abc\rdef" to "Ok",
            "Ok" to "msg\u0000bad"
        )) {
            assertFailsWith<IllegalArgumentException> {
                DesktopWindowsLocalNotification.showAfterUserClick(
                    title, message, "Windows 11", fake
                )
            }
        }
        assertEquals(0, fake.sends)
        assertEquals(0, fake.supportsCalls)
    }
}
