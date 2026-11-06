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
        // Windows checkout pode usar CRLF; normalizar só no diretório gerado.
        val source = originalAndroidCode.file("HomeScreen.kt").asFile
            .readText(Charsets.UTF_8).replace("\r\n", "\n")
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
        check(
            !desktopSource.contains("import androidx.annotation.DrawableRes") &&
                !desktopSource.contains("import androidx.compose.ui.res.painterResource") &&
                desktopSource.contains("desktopPainterResource(")
        ) {
            "Transformação de imports e recursos da Home não é segura."
        }
        val out = generatedHome.get().file(
            "com/timachado/brothermatrizes/HomeScreen.kt"
        ).asFile
        out.parentFile.mkdirs()
        out.writeText(desktopSource, Charsets.UTF_8)
    }
}
kotlin.sourceSets.getByName("main").kotlin.srcDir(generatedHome)

// Criar Nome: portar a hierarquia e controles Compose DIRETAMENTE do APK
// Android 1.11.8; adaptar apenas LocalContext (preferências Windows)
// e as fontes TTF/OTF Android ainda indisponíveis no desktop.
// Nenhuma implementação anterior da tela Windows pode entrar aqui.
val generatedCreator = layout.buildDirectory.dir("generated/android-create-name/kotlin")
val copyAndroidCreateName by tasks.registering {
    inputs.file(originalAndroidCode.file("CreateNameScreen.kt"))
    outputs.dir(generatedCreator)
    doLast {
        val source = originalAndroidCode.file("CreateNameScreen.kt").asFile
            .readText(Charsets.UTF_8).replace("\r\n", "\n")
        check(
            source.contains("fun CreateNameScreen(") &&
            source.contains("TextLayoutGenerator") &&
            source.contains("fun generateAt(") &&
            source.contains("\"Criar matriz\"") &&
            source.contains("\"▶ Simular\"") &&
            source.contains("EmbroideryCanvas(")
        ) { "A tela oficial Criar Nome mudou: revisar contrato e bloquear QA." }
        val desktop = source
            .replace(
                "import android.content.Context",
                "import com.timachado.brothermatrizes.desktop.platform.Context"
            )
            .replace(
                "import androidx.compose.ui.platform.LocalContext",
                "import com.timachado.brothermatrizes.desktop.platform.LocalContext"
            )
            .replace(
                "import com.timachado.brothermatrizes.font.ImportedFontMatrixGenerator",
                "import com.timachado.brothermatrizes.desktop.platform.ImportedFontMatrixGenerator"
            )
            .replace(
                "import com.timachado.brothermatrizes.font.ImportedFontStore",
                "import com.timachado.brothermatrizes.desktop.platform.ImportedFontStore"
            )
        check(
            !desktop.contains("import android.") &&
            !desktop.contains("import androidx.compose.ui.platform.LocalContext") &&
            !desktop.contains("import com.timachado.brothermatrizes.font.")
        ) { "Dependência Android não adaptada no Criar Nome." }
        val out = generatedCreator.get().file(
            "com/timachado/brothermatrizes/CreateNameScreen.kt"
        ).asFile
        out.parentFile.mkdirs()
        out.writeText(desktop, Charsets.UTF_8)
    }
}
kotlin.sourceSets.getByName("main").kotlin.srcDir(generatedCreator)

// Preview, Viewer e Simulador: importar os composables originais, inclusive
// controles reais; adaptadores de plataforma ficam fora dos fontes copiados.
val generatedViewer = layout.buildDirectory.dir("generated/android-viewer/kotlin")
val copyAndroidVisualization by tasks.registering(Sync::class) {
    from("../app/src/main/java") {
        for (name in listOf(
            "EmbroideryCanvas.kt", "EmbroideryDisplay.kt",
            "EmbroideryRenderCoordinates.kt", "StitchThreadSegments.kt",
            "SimulatorScreen.kt", "MachineSimulationCanvas.kt",
            "ViewerScreen.kt", "NameEditorPlacement.kt",
            "NameEditorStitchLayer.kt"
        )) include("com/timachado/brothermatrizes/$name")
    }
    into(generatedViewer)
}
kotlin.sourceSets.getByName("main").kotlin.srcDir(generatedViewer)

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
        include("com/timachado/brothermatrizes/core/embroidery/TextLayoutGenerator.kt")
        include("com/timachado/brothermatrizes/core/embroidery/TextHoopAutoFit.kt")
        include("com/timachado/brothermatrizes/core/embroidery/EmbroideryPreviewOptimizer.kt")
        include("com/timachado/brothermatrizes/core/embroidery/SimulationTiming.kt")
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

// Codec FLP v11: extraído SEM MODIFICAR métodos do ProjectCodec Android.
// O ProjectStore Android usa Context; não deve ser copiado para Windows.
val generatedProjectCodec = layout.buildDirectory.dir("generated/project-codec/kotlin")
val copyAndroidProjectCodec by tasks.registering {
    val source = layout.projectDirectory.file(
        "../app/src/main/java/com/timachado/brothermatrizes/core/project/ProjectStore.kt"
    )
    inputs.file(source)
    outputs.dir(generatedProjectCodec)
    doLast {
        val original = source.asFile.readText(Charsets.UTF_8).replace("\r\n", "\n")
        val boundary = "\nobject ProjectStore {"
        require(original.contains("object ProjectCodec {") &&
            original.contains("fun encodeTo(") &&
            original.contains("fun decode(") &&
            original.contains(boundary)) {
            "Projeto FLP oficial mudou; revisar codec antes de compilar."
        }
        val codec = original.substringBefore(boundary)
            .replace("import android.content.Context\n", "")
            .replace(
                "import com.timachado.brothermatrizes.core.storage.AtomicFileWriter\n", ""
            )
        check(!codec.contains("import android.") &&
            codec.contains("private const val VERSION =") &&
            codec.contains("11") && codec.trimEnd().endsWith("}")) {
            "Codec FLP Android não pôde ser isolado com segurança."
        }
        val out = generatedProjectCodec.get().file(
            "com/timachado/brothermatrizes/core/project/ProjectCodec.kt"
        ).asFile
        out.parentFile.mkdirs()
        out.writeText(codec + "\n", Charsets.UTF_8)
    }
}
kotlin.sourceSets.getByName("main").kotlin.srcDir(generatedProjectCodec)

// Importação DST/PES/JEF: copiar a implementação REAL de EmbroideryLoader
// (loadBytes, sniff de formato e parsers originais); retirar APENAS o método
// Android ContentResolver/Uri e os helpers de streams Android.
val generatedLoader = layout.buildDirectory.dir("generated/android-embroidery-loader/kotlin")
val copyAndroidLoader by tasks.registering {
    val source = layout.projectDirectory.file(
        "../app/src/main/java/com/timachado/brothermatrizes/core/embroidery/EmbroideryLoader.kt"
    )
    inputs.file(source)
    outputs.dir(generatedLoader)
    doLast {
        val original = source.asFile.readText(Charsets.UTF_8).replace("\r\n", "\n")
        val start = original.indexOf("    fun load(")
        val fromBytes = original.indexOf("    fun loadBytes(")
        val endBytes = original.indexOf("    private class FileTooLargeException")
        require(start > 0 && fromBytes > start && endBytes > fromBytes &&
            original.contains("private fun parseByExtension(") &&
            original.contains("fun loadBytes(")) {
            "Loader Android mudou: revisão obrigatória da adaptação."
        }
        val shared = original.substring(0, start)
            .replace("import android.content.ContentResolver\n", "")
            .replace("import android.net.Uri\n", "")
            .replace("import android.provider.OpenableColumns\n", "") +
                original.substring(fromBytes, endBytes) + "}\n"
        check(!shared.contains("import android.") &&
            !shared.contains("ContentResolver") &&
            shared.contains("DstParser.parse(") &&
            shared.contains("EmbroideryIoParser") &&
            shared.contains("fun loadBytes(")) {
            "Loader JVM não corresponde ao parse original Android."
        }
        val out = generatedLoader.get().file(
            "com/timachado/brothermatrizes/core/embroidery/EmbroideryLoader.kt"
        ).asFile
        out.parentFile.mkdirs()
        out.writeText(shared, Charsets.UTF_8)
    }
}
kotlin.sourceSets.getByName("main").kotlin.srcDir(generatedLoader)

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
    dependsOn(
        copyAndroidHome, copyAndroidCreateName, copyAndroidVisualization,
        copyAndroidTheme, copyOfficialCore, copyAndroidProjectCodec,
        copyAndroidLoader
    )
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
