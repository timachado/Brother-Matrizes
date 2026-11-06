package com.timachado.brothermatrizes.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Gate de paridade de CÓDIGO da Home; NÃO substitui screenshot/uso físico. */
class AndroidHomeSourceParityTest {
    private val root = Path.of("").toAbsolutePath().normalize()

    @Test
    fun `Home Desktop e fonte Android com somente tres adaptacoes de compilacao`() {
        val android = Files.readString(
            root.resolve("../app/src/main/java/com/timachado/brothermatrizes/HomeScreen.kt")
        )
        val generated = Files.readString(
            root.resolve("build/generated/android-home/kotlin/com/timachado/brothermatrizes/HomeScreen.kt")
        )
        val expected = android
            .replace("import androidx.annotation.DrawableRes\n", "")
            .replace("@DrawableRes iconRes: Int", "iconRes: Int")
            .replace(
                "import androidx.compose.ui.res.painterResource",
                "import com.timachado.brothermatrizes.desktop.resources.desktopPainterResource"
            )
            .replace(
                "import com.timachado.brothermatrizes.ui.updates.BrotherUpdateHomeCard",
                "import com.timachado.brothermatrizes.desktop.resources.BrotherUpdateHomeCard"
            )
            .replace("painterResource(", "desktopPainterResource(")
        assertEquals(expected, generated, "Tela Android foi redesenhada ou alterada no Desktop")
        assertTrue(generated.contains("PremiumHeroCard()"))
        assertTrue(generated.contains("GridCells.Fixed(2)"))
    }

    @Test
    fun `logo WebP e extraido sem alteracao de um byte`() {
        val original = Base64.getMimeDecoder().decode(
            Files.readString(root.resolve("../branding/brother_bm_premium_approved.webp.base64"))
        )
        val embedded = checkNotNull(
            javaClass.classLoader.getResourceAsStream(
                "drawable/brother_bm_premium_approved.webp"
            )
        ) { "WebP oficial não está empacotado" }.use { it.readBytes() }
        assertContentEquals(original, embedded)
    }

    @Test
    fun `banner possui o mesmo crop e o mesmo upscale geometrico do Android`() {
        val originalBytes = Base64.getMimeDecoder().decode(
            Files.readString(root.resolve("../branding/brother_home_hero.base64"))
        )
        val original = ImageIO.read(originalBytes.inputStream())
        val result = checkNotNull(javaClass.classLoader.getResourceAsStream(
            "drawable/brother_home_hero.jpg"
        )).use { ImageIO.read(it) }
        val cropX = (original.width * 0.35f).toInt()
        assertEquals((original.width - cropX) * 3, result.width)
        assertEquals(original.height * 3, result.height)
    }
}
