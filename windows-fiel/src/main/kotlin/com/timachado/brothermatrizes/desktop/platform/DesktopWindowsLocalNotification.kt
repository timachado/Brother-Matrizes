package com.timachado.brothermatrizes.desktop.platform

import java.awt.Color
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage

/**
 * Opt-in Windows balloon, initiated exclusively by a user's click.
 * This is NOT Firebase push, a resident Windows service or periodic polling.
 * The temporary tray icon is removed by a daemon thread to avoid leaving
 * the app running in the background after the Compose window closes.
 */
internal object DesktopWindowsLocalNotification {
    internal interface Transport {
        fun supported(): Boolean
        fun display(title: String, message: String): Boolean
    }

    private object AwtTransport : Transport {
        override fun supported(): Boolean =
            !GraphicsEnvironment.isHeadless() && SystemTray.isSupported()

        override fun display(title: String, message: String): Boolean {
            val image = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
            val graphics = image.createGraphics()
            try {
                graphics.color = Color(12, 42, 69)
                graphics.fillRoundRect(0, 0, 32, 32, 7, 7)
                graphics.color = Color(67, 225, 238)
                graphics.font = Font(Font.SANS_SERIF, Font.BOLD, 15)
                graphics.drawString("BM", 3, 22)
            } finally {
                graphics.dispose()
            }
            val tray = SystemTray.getSystemTray()
            val icon = TrayIcon(image, "Brother Matrizes").apply {
                isImageAutoSize = true
            }
            tray.add(icon)
            try {
                icon.displayMessage(title, message, TrayIcon.MessageType.INFO)
                Thread({
                    try {
                        Thread.sleep(8_000)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    } finally {
                        tray.remove(icon)
                    }
                }, "brother-windows-tray-cleanup").apply {
                    isDaemon = true
                    start()
                }
                return true
            } catch (error: Exception) {
                tray.remove(icon)
                throw error
            }
        }
    }

    fun showAfterUserClick(
        title: String,
        message: String,
        windowsName: String = System.getProperty("os.name"),
        transport: Transport = AwtTransport
    ): Boolean {
        require(title.isNotBlank() && title.length <= 90 &&
            message.isNotBlank() && message.length <= 240 &&
            title.none { it.code < 32 } &&
            message.none { it.code < 32 && it != '\n' }) {
            "Aviso local inválido."
        }
        if (!windowsName.startsWith("Windows", ignoreCase = true) ||
            !transport.supported()) return false
        return transport.display(title, message)
    }
}
