import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.image.ConvolveOp
import java.awt.image.Kernel
import java.awt.image.RescaleOp
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import org.gradle.api.tasks.Sync

plugins {
    kotlin("jvm") version "2.2.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20"
    id("org.jetbrains.compose") version "1.9.3"
}

group = "com.timachado.brothermatrizes"
version = "0.0.1-fidelidade-qa"

kotlin {
    jvmToolchain(17)
}

/**
 * O projeto antigo Windows foi reprovado: NÃO importar fontes de windows/.
 * Copiar exclusivamente código-fonte do Android 1.11.8, sem alterar os arquivos.
 * O HomeScreen é o MESMO Composable do Android, com adaptação de recursos por ID.
 */
val originalAndroidCode = layout.projectDirectory.dir(
    "../app/src/main/java/com/timachado/brothermatrizes"
)
val generatedHome = layout.buildDirectory.dir("generated/android-home/kotlin")
val copyAndroidHome by tasks.registering {
    inputs.file(originalAndroidCode.file("HomeScreen.kt"))
    outputs.dir(generatedHome)
    doLast {
        val source = originalAndroidCode.file("HomeScreen.kt").asFile.readText(Charsets.UTF_8)
        check(source.contains("fun HomeScreen(") &&
            source.contains("fun PremiumHeroCard()") &&
            source.contains("fun ExpressiveFeatureCard(")) {
            "Home Android mudou: reconstrução fiel bloqueada."
        }
        check(source.contains("import androidx.annotation.DrawableRes") &&
            source.contains("import androidx.compose.ui.res.painterResource") &&
            source.contains(
                "import com.timachado.brothermatrizes.ui.updates.BrotherUpdateHomeCard"
            )) {
            "Dependências da Home Android mudaram: rever adaptadores."
        }
        val desktopSource = source
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
        check(!desktopSource.contains("androidx.annotation") &&
            !desktopSource.contains("import androidx.compose.ui.res.painterResource") &&
            desktopSource.contains("desktopPainterResource(iconRes)")) {
            "Transformação de recursos da Home não é segura."
        }
        val out = generatedHome.get().file(
            "com/timachado/brothermatrizes/HomeScreen.kt"
        ).asFile
        out.parentFile.mkdirs()
        out.writeText(desktopSource, Charsets.UTF_8)
    }
}
kotlin.sourceSets.getByName("main").kotlin.srcDir(generatedHome)

// Tema inteiro compilado diretamente do Android original: sem reimplementação
// de cores, tipografia, esquemas light/dark ou cantos arredondados.
val generatedTheme = layout.buildDirectory.dir("generated/android-theme/kotlin")
val copyAndroidTheme by tasks.registering(Sync::class) {
    from("../app/src/main/java") {
        include(
            "com/timachado/brothermatrizes/ui/theme/BrotherMatrizesTheme.kt"
        )
    }
    into(generatedTheme)
}
kotlin.sourceSets.getByName("main").kotlin.srcDir(generatedTheme)

/**
 * Tipos e geradores do Android são compilados DIRETAMENTE da fonte oficial,
 * sem reescrita de algoritmos de pontadas. Mesmo contrato de modelo do APK.
 */
val sharedCoreDir = layout.buildDirectory.dir("generated/core-android/kotlin")
val copyOfficialCore by tasks.registering(Sync::class) {
    from("../app/src/main/java") {
        include("com/timachado/brothermatrizes/core/embroidery/DstParser.kt")
        include("com/timachado/brothermatrizes/core/embroidery/EmbroideryIoParser.kt")
        include("com/timachado/brothermatrizes/core/embroidery/EmbroideryIntegrity.kt")
        include("com/timachado/brothermatrizes/core/embroidery/HoopLayerPositioning.kt")
        include("com/timachado/brothermatrizes/core/embroidery/NamePlacementSafety.kt")
        include("com/timachado/brothermatrizes/core/embroidery/EmbroideryModels.kt")
        include("com/timachado/brothermatrizes/core/embroidery/EmbroideryProfiles.kt")
        include("com/timachado/brothermatrizes/core/embroidery/EmbroideryStats.kt")
        include("com/timachado/brothermatrizes/core/embroidery/EmbroideryStressPolicy.kt")
        include("com/timachado/brothermatrizes/core/embroidery/MachineFinishing.kt")
        include("com/timachado/brothermatrizes/core/embroidery/MachineTransferValidator.kt")
        include("com/timachado/brothermatrizes/core/embroidery/MonogramGenerator.kt")
        include("com/timachado/brothermatrizes/core/embroidery/MatrixConverter.kt")
        include("com/timachado/brothermatrizes/core/embroidery/MatrixEditor.kt")
        include("com/timachado/brothermatrizes/core/embroidery/PesHoopPositionMetadata.kt")
        include("com/timachado/brothermatrizes/core/embroidery/SatinGenerator.kt")
        include("com/timachado/brothermatrizes/core/embroidery/SpecialStitchProcessor.kt")
        include("com/timachado/brothermatrizes/core/embroidery/TextMatrixGenerator.kt")
        include("com/timachado/brothermatrizes/core/embroidery/VectorMatrixGenerator.kt")
    }
    into(sharedCoreDir)
}
kotlin.sourceSets.getByName("main").kotlin.srcDir(sharedCoreDir)

/**
 * Converter ícones Android XML em SVG mantendo o pathData ORIGINAL, e extrair
 * a arte aprovada do Android. Copiar a transformação fotográfica de
 * app/build.gradle.kts (crop 35%, upscale bicúbico 3x, contraste e sharpen).
 *
 * Requer Python 3 SOMENTE na build; não há dependência de Python no cliente.
 */
val generatedRes = layout.buildDirectory.dir("generated/resources")
val prepareOriginalAssets by tasks.registering {
    inputs.files(
        fileTree("../app/src/main/res/drawable") { include("ic_ms_*.xml", "ic_premium_bell.xml") },
        file("../branding/brother_home_hero.base64"),
        file("../branding/brother_bm_premium_approved.webp.base64")
    )
    inputs.file("qa/preparar_recursos_android.py")
    outputs.dir(generatedRes)
    doLast {
        val out = generatedRes.get().asFile
        out.mkdirs()
        val python = System.getenv("PYTHON")?.takeIf { it.isNotBlank() }
            ?: if (System.getProperty("os.name").contains("Windows", true)) "python" else "python3"
        val command = ProcessBuilder(
            python, file("qa/preparar_recursos_android.py").absolutePath,
            "--out", out.absolutePath
        ).directory(rootDir).redirectErrorStream(true).start()
        val log = command.inputStream.bufferedReader().readText()
        check(command.waitFor() == 0) { "Falha extraindo recursos oficiais: $log" }

        val drawable = out.resolve("drawable")
        val originalHero = ImageIO.read(drawable.resolve("brother_home_hero-original.jpg"))
        check(originalHero != null && originalHero.width >= 150 && originalHero.height >= 100) {
            "Fotografia original Android ausente ou inválida."
        }
        val cropX = (originalHero.width * 0.35f).toInt()
        val heroNoText = originalHero.getSubimage(
            cropX, 0, originalHero.width - cropX, originalHero.height
        )
        val w = heroNoText.width * 3
        val h = heroNoText.height * 3
        val resized = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val graphics = resized.createGraphics()
        try {
            graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC
            )
            graphics.drawImage(heroNoText, 0, 0, w, h, null)
        } finally {
            graphics.dispose()
        }
        val contrasted = RescaleOp(1.07f, 3f, null).filter(resized, null)
        val sharpened = ConvolveOp(
            Kernel(3, 3, floatArrayOf(
                0f, -0.22f, 0f, -0.22f, 1.88f, -0.22f, 0f, -0.22f, 0f
            )), ConvolveOp.EDGE_NO_OP, null
        ).filter(contrasted, null)
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        ImageIO.createImageOutputStream(
            drawable.resolve("brother_home_hero.jpg")
        ).use { stream ->
            try {
                writer.output = stream
                val options = writer.defaultWriteParam
                options.compressionMode = ImageWriteParam.MODE_EXPLICIT
                options.compressionQuality = 0.92f
                writer.write(null, IIOImage(sharpened, null, null), options)
            } finally {
                writer.dispose()
            }
        }
    }
}
sourceSets.getByName("main").resources.srcDir(generatedRes)

tasks.named("processResources") { dependsOn(prepareOriginalAssets) }
tasks.named("compileKotlin") {
    dependsOn(copyAndroidHome, copyAndroidTheme, copyOfficialCore)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("com.github.EmbroidePy.EmbroideryIO:embroideryio-jvm:0.1.23")
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks.test { useJUnitPlatform() }

compose.desktop {
    application {
        mainClass = "com.timachado.brothermatrizes.desktop.FielMainKt"
        // Sem targetFormats, jpackage, release ou instaladores nesta etapa.
        // A Home deve ser comparada visualmente antes de empacotar Windows.
    }
}
