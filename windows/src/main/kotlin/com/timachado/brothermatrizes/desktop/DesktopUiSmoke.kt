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

        // Nesta QA a amostra sintética "QA" usa o fio dourado padrão
        // #E6BE70 do gerador oficial. Detectar pontadas na região esquerda
        // impede falso positivo baseado somente nos rótulos do painel.
        val stitchPixels = if (syntheticPreview) {
            countSyntheticStitchPixels(screenshot)
        } else 0
        if (syntheticPreview) {
            check(stitchPixels >= 80) {
                "A prévia sintética não apresentou pontadas douradas visíveis: " +
                    "$stitchPixels pixels detectados."
            }
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
                (if (syntheticPreview) "Pontadas sintéticas detectadas: $stitchPixels pixels\n" else "") +
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


    /**
     * E2E de evento de mouse real no runner: um passo de roda amplia a
     * geometria visível da matriz sintética. Não simula bordadeira nem
     * inspeciona matrizes privadas.
     */
    fun captureWheelZoom(window: Window, report: Path): Path {
        val beforePath = capture(window, report, syntheticPreview = true)
        val beforeImage = ImageIO.read(beforePath.toFile())
            ?: error("A prévia inicial não pôde ser lida.")
        val before = syntheticStitchBounds(beforeImage)
            ?: error("A prévia sintética inicial não contém pontadas.")

        val frame = window as? java.awt.Frame
            ?: error("Janela Compose indisponível para o teste da roda.")
        val bounds = frame.bounds
        val robot = Robot(frame.graphicsConfiguration.device)
        val mouseX = bounds.x + (bounds.width * 0.34f).toInt()
        val mouseY = bounds.y + (bounds.height * 0.50f).toInt()
        robot.mouseMove(mouseX, mouseY)
        robot.delay(250)
        // Movimento real de mouse no Windows virtual, não chamada direta à
        // função de cálculo. Compose recebe o PointerEventType.Scroll.
        robot.mouseWheel(-1)
        robot.delay(1000)

        val afterImage = robot.createScreenCapture(bounds)
        val after = syntheticStitchBounds(afterImage)
            ?: error("Prévia sem pontadas depois de girar a roda do mouse.")
        check(after.width.toFloat() >= before.width * 1.10f &&
            after.height.toFloat() >= before.height * 1.10f) {
            "Roda não ampliou a matriz: " +
                "${before.width}x${before.height} -> ${after.width}x${after.height}."
        }
        val afterPath = report.resolveSibling(
            report.fileName.toString().removeSuffix(".txt") + "-zoom.png"
        )
        require(!Files.exists(afterPath)) { "Evidência de zoom já existe." }
        Files.newOutputStream(afterPath, StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE).use { output ->
            check(ImageIO.write(afterImage, "png", output)) {
                "Não foi possível escrever evidência do zoom."
            }
        }
        Files.writeString(
            report,
            "Zoom: ${before.width}x${before.height} -> " +
                "${after.width}x${after.height}\n" +
                "Captura ampliada: ${afterPath.fileName}\n" +
                "BROTHER_WINDOWS_UI_WHEEL_ZOOM_OK\n",
            StandardCharsets.UTF_8, StandardOpenOption.APPEND
        )
        return afterPath
    }

    /** Limites visíveis das pontadas sintéticas no painel de prévia. */
    internal fun syntheticStitchBounds(image: BufferedImage): Rectangle? {
        require(image.width > 0 && image.height > 0) { "Captura inválida." }
        val minScanX = (image.width * 0.06f).toInt()
        val maxScanX = (image.width * 0.60f).toInt()
        val minScanY = (image.height * 0.28f).toInt()
        val maxScanY = (image.height * 0.76f).toInt()
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = -1
        var bottom = -1
        for (y in minScanY until maxScanY) {
            for (x in minScanX until maxScanX) {
                val rgb = image.getRGB(x, y)
                val red = (rgb ushr 16) and 0xFF
                val green = (rgb ushr 8) and 0xFF
                val blue = rgb and 0xFF
                if (red in 185..255 && green in 140..220 &&
                    blue in 65..155 && red >= green + 15 &&
                    green >= blue + 25) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        return if (right < 0) null else Rectangle(
            left, top, right - left + 1, bottom - top + 1
        )
    }

    /**
     * Concentra a busca no canvas (lado esquerdo, faixa central).
     * O RGB é um critério de smoke para a amostra oficial "QA";
     * não avalia qualidade de costura nem cores de matrizes externas.
     */
    internal fun countSyntheticStitchPixels(image: BufferedImage): Int {
        require(image.width > 0 && image.height > 0) { "Captura inválida." }
        val minX = (image.width * 0.06f).toInt()
        val maxX = (image.width * 0.60f).toInt()
        val minY = (image.height * 0.28f).toInt()
        val maxY = (image.height * 0.76f).toInt()
        var hits = 0
        for (y in minY until maxY) {
            for (x in minX until maxX) {
                val rgb = image.getRGB(x, y)
                val red = (rgb ushr 16) and 0xFF
                val green = (rgb ushr 8) and 0xFF
                val blue = rgb and 0xFF
                if (red in 185..255 && green in 140..220 &&
                    blue in 65..155 && red >= green + 15 &&
                    green >= blue + 25) {
                    hits++
                }
            }
        }
        return hits
    }
}
