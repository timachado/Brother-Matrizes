package com.timachado.brothermatrizes.desktop

import java.awt.Robot
import java.awt.Window
import java.awt.event.InputEvent
import java.awt.image.BufferedImage
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import javax.imageio.ImageIO

/**
 * E2E gráfico restrito ao runner Windows de QA, sem documentos de clientes.
 * Não substitui homologação visual/manual nem costura em bordadeira.
 */
internal object DesktopDrawingUiSmoke {
    private const val VERIFIED = "BROTHER_WINDOWS_UI_DRAWING_VERIFIED"

    fun captureSvgPreview(window: Window, report: Path): Path {
        val initial = DesktopUiSmoke.capture(window, report)
        val image = ImageIO.read(initial.toFile()) ?: error("PNG SVG QA ilegível.")
        val pixels = coloredCanvasPixels(image)
        writeMeasurement(report, "SVG sintetico vetorial: $pixels pixels\n")
        check(pixels >= 75) {
            "O canvas de SVG não exibiu traços coloridos na região de desenho: $pixels."
        }
        writeMeasurement(report, "$VERIFIED\n")
        return initial
    }

    fun captureDrawGesture(window: Window, report: Path): Path {
        val initial = DesktopUiSmoke.capture(window, report)
        val before = ImageIO.read(initial.toFile()) ?: error("PNG inicial de desenho ilegível.")
        val beforePixels = coloredCanvasPixels(before)
        val frame = window as? java.awt.Frame
            ?: error("Janela Compose ausente no teste de desenho.")
        val b = frame.bounds
        val robot = Robot(frame.graphicsConfiguration.device)
        // Cada gesto usa coordenadas relativas ao espaço útil aproximado
        // do canvas esquerdo: funciona também nas janelas compactas.
        val xs = listOf(0.18f, 0.22f, 0.27f, 0.32f, 0.37f)
        val ys = listOf(0.45f, 0.54f, 0.48f, 0.60f, 0.53f)
        val firstX = b.x + (b.width * xs[0]).toInt()
        val firstY = b.y + (b.height * ys[0]).toInt()
        robot.mouseMove(firstX, firstY)
        robot.delay(120)
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
        try {
            // Interpola pontos para que detectDragGestures receba eventos reais.
            for (i in 0 until xs.lastIndex) {
                for (part in 1..12) {
                    val alpha = part / 12f
                    val x = xs[i] + (xs[i + 1] - xs[i]) * alpha
                    val y = ys[i] + (ys[i + 1] - ys[i]) * alpha
                    robot.mouseMove(
                        b.x + (b.width * x).toInt(),
                        b.y + (b.height * y).toInt()
                    )
                    robot.delay(24)
                }
            }
        } finally {
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
        }
        robot.delay(900)
        val after = robot.createScreenCapture(b)
        val afterPixels = coloredCanvasPixels(after)
        val afterPath = report.resolveSibling(
            report.fileName.toString().removeSuffix(".txt") + "-after.png"
        )
        require(!Files.exists(afterPath)) { "Captura posterior já existe." }
        Files.newOutputStream(
            afterPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE
        ).use { output ->
            check(ImageIO.write(after, "png", output)) {
                "Não foi possível gravar PNG após arrasto."
            }
        }
        writeMeasurement(
            report,
            "Canvas antes: $beforePixels pixels; depois: $afterPixels pixels\n" +
                "Captura após mouse: ${afterPath.fileName}\n"
        )
        check(afterPixels >= beforePixels + 90 && afterPixels >= 110) {
            "Gesto de mouse não produziu traço visível: $beforePixels -> $afterPixels."
        }
        writeMeasurement(report, "$VERIFIED\nBROTHER_WINDOWS_UI_MOUSE_DRAW_OK\n")
        return afterPath
    }

    /**
     * Conta somente pixels verde-azulados da linha #2A9D8F, no canvas
     * esquerdo; textos e swatches no painel direito não contam.
     */
    internal fun coloredCanvasPixels(image: BufferedImage): Int {
        require(image.width > 0 && image.height > 0) { "Captura inválida." }
        var result = 0
        val fromX = (image.width * 0.06f).toInt()
        val toX = (image.width * 0.56f).toInt()
        val fromY = (image.height * 0.29f).toInt()
        val toY = (image.height * 0.82f).toInt()
        for (y in fromY until toY) {
            for (x in fromX until toX) {
                val rgb = image.getRGB(x, y)
                val red = (rgb ushr 16) and 0xff
                val green = (rgb ushr 8) and 0xff
                val blue = rgb and 0xff
                if (red in 25..85 && green in 115..200 &&
                    blue in 100..192 && green > red + 50 &&
                    blue > red + 40) result++
            }
        }
        return result
    }

    private fun writeMeasurement(report: Path, content: String) {
        Files.writeString(
            report, content, StandardCharsets.UTF_8,
            StandardOpenOption.APPEND
        )
    }
}
