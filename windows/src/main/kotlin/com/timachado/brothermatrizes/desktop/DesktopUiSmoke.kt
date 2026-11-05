package com.timachado.brothermatrizes.desktop

import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Window
import java.awt.image.BufferedImage
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import javax.imageio.ImageIO

/**
 * Testa a abertura real da interface Compose no desktop do runner Windows.
 * Usado exclusivamente pelo argumento --ui-smoke-test.
 *
 * Captura a janela inicial vazia ou uma matriz SINTÉTICA de QA, sem abrir
 * arquivos de clientes, conectar contas ou acessar serviços remotos.
 * Não substitui inspeção visual manual nem teste em PC físico.
 */
internal object DesktopUiSmoke {
    const val SUCCESS_MARKER = "BROTHER_WINDOWS_UI_SMOKE_OK"
    private const val EXPECTED_TITLE = "Brother Matrizes Desktop"

    fun capture(window: Window, report: Path, syntheticPreview: Boolean = false): Path {
        require(report.isAbsolute && report.fileName.toString().endsWith(".txt", ignoreCase = true)) {
            "Relatório de QA deve ter caminho absoluto e extensão .txt."
        }
        require(Files.isDirectory(report.parent) && !Files.exists(report)) {
            "Relatório deve ser novo, em uma pasta existente."
        }
        require(!GraphicsEnvironment.isHeadless()) {
            "Runner Windows não disponibiliza desktop gráfico."
        }
        val frame = window as? java.awt.Frame
            ?: error("A janela Compose não é um Frame AWT.")
        check(frame.isShowing && frame.isDisplayable && frame.isVisible) {
            "A janela principal do aplicativo não está visível."
        }
        check(frame.title.contains(EXPECTED_TITLE)) {
            "Título da janela Compose não corresponde ao aplicativo QA."
        }
        val bounds: Rectangle = frame.bounds
        check(bounds.width >= 600 && bounds.height >= 450) {
            "Janela aberta com dimensões insuficientes."
        }
        val workArea = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        val visible = bounds.intersection(workArea)
        val visibleArea = visible.width.coerceAtLeast(0).toLong() *
            visible.height.coerceAtLeast(0).toLong()
        val totalArea = bounds.width.toLong() * bounds.height.toLong()
        check(totalArea > 0L && visibleArea.toDouble() / totalArea >= 0.97) {
            "Janela parcialmente fora da área útil do monitor: ${bounds.width}x${bounds.height} " +
                "em workspace ${workArea.width}x${workArea.height}."
        }

        // A captura prova que o app desenhou na superfície do runner.
        // Diferença entre pixels não equivale a aprovação estética ou de UX.
        val screenshot = Robot(frame.graphicsConfiguration.device).createScreenCapture(bounds)
        val colors = HashSet<Int>()
        val stepX = (screenshot.width / 90).coerceAtLeast(1)
        val stepY = (screenshot.height / 60).coerceAtLeast(1)
        for (y in 0 until screenshot.height step stepY) {
            for (x in 0 until screenshot.width step stepX) {
                colors.add(screenshot.getRGB(x, y))
            }
        }
        check(colors.size >= 6) {
            "Captura praticamente uniforme: interface não renderizada ou sem desktop gráfico."
        }
        val screenshotPath = report.resolveSibling(
            report.fileName.toString().removeSuffix(".txt") + ".png"
        )
        require(!Files.exists(screenshotPath)) { "Captura PNG já existe." }
        Files.newOutputStream(screenshotPath, StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE).use { output ->
            check(ImageIO.write(screenshot, "png", output)) {
                "Não foi possível codificar a captura PNG."
            }
        }
        Files.writeString(
            report,
            "$SUCCESS_MARKER\n" +
                "Interface: visível no runner Windows\n" +
                "Dimensões: ${bounds.width}x${bounds.height}\n" +
                "Cores distintas amostradas: ${colors.size}\n" +
                "Captura: ${screenshotPath.fileName}\n" +
                "Modo: " + (if (syntheticPreview)
                    "QA com matriz sintética local\n"
                    else "QA sem autenticação e sem arquivos do usuário\n") +
                    (if (syntheticPreview) "BROTHER_WINDOWS_SYNTHETIC_UI_OK\n" else ""),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE
        )
        return screenshotPath
    }
}
