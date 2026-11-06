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
            "ViewerScreen.kt", "ConverterScreen.kt", "NameEditorPlacement.kt",
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
 * Gerador puro Satin do Android: extrai a fonte Kotlin ORIGINAL
 * `ImportedFontMatrixGenerator.kt` SEM copiar APIs de Canvas, Bitmap,
 * PathMeasure ou Context. Nenhuma regra de pontos é reimplementada.
 *
 * A rasterização e os contornos TTF/OTF do Windows ainda precisam ser
 * comparados numericamente com Android no mesmo arquivo de fonte.
 */
val generatedReferenceSatin = layout.buildDirectory.dir("generated/satin-android/kotlin")
val copyAndroidReferenceSatin by tasks.registering {
    val input = layout.projectDirectory.file(
        "../app/src/main/java/com/timachado/brothermatrizes/font/ImportedFontMatrixGenerator.kt"
    )
    inputs.file(input)
    outputs.dir(generatedReferenceSatin)
    doLast {
        val source = input.asFile.readText(Charsets.UTF_8).replace("\r\n", "\n")
        val begin = source.indexOf("    private data class ReferenceSatinState(")
        val densify = source.indexOf("    private fun densifyReferenceContours(", begin)
        val scan = source.indexOf("    private fun scanReferenceSpans(", densify)
        val after = source.indexOf("    private fun buildCenterlineRunning(", scan)
        check(begin > 0 && densify > begin && scan > densify && after > scan &&
            source.substring(begin, after).contains("private fun appendReferenceSatinGlyph(") &&
            source.substring(begin, after).contains("private fun emitReferenceColumn(") &&
            source.substring(begin, after).contains("private fun emitReferenceCenterUnderlay(") &&
            source.substring(begin, after).contains("private fun emitReferenceLock(") &&
            source.substring(begin, after).contains("private fun emitReferenceTravel(") &&
            source.substring(begin, after).contains("private fun emitReferenceStitchTo(")
        ) { "Código Satin oficial Android mudou: revisar porta, NÃO executar versão divergente." }
        // Exclui somente densifyReferenceContours(SampledContour), que
        // depende de rasterização Android. Não é utilizado pelo caminho
        // Satin de polígonos; este já chega do amostrador Skia Desktop.
        val pure = source.substring(begin, densify) + source.substring(scan, after)
        check(!pure.contains("android.graphics.") &&
            !pure.contains("SampledContour") &&
            !pure.contains("PathMeasure(") &&
            !pure.contains("Bitmap(") && !pure.contains("Canvas(")) {
            "Trecho não é puro Kotlin: adaptação abortada."
        }
        val wrapper = """
package com.timachado.brothermatrizes.desktop.platform

import com.timachado.brothermatrizes.core.embroidery.EmbroideryPoint
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import kotlin.math.*

/** Métodos Satin, underlay, fixação e travel extraídos do Android 1.11.8. */
internal object DesktopReferenceSatinKernel {
    fun generate(
        contoursByGlyph: List<List<List<DesktopContourPoint>>>,
        options: TextMatrixOptions
    ): List<EmbroideryPoint> {
        require(contoursByGlyph.size in 1..64) { "Quantidade de glifos inválida." }
        require(options.style == TextStitchStyle.SATIN) {
            "Este kernel é exclusivo da configuração Satin."
        }
        require(options.satinDensityMm.isFinite() &&
            options.satinDensityMm in 0.3f..1.2f) {
            "Densidade Satin fora do intervalo oficial Android."
        }
        require(options.satinPullCompensationMm.isFinite() &&
            options.satinPullCompensationMm in 0f..1f) {
            "Compensação de repuxo fora do intervalo Android."
        }
        var total = 0
        contoursByGlyph.forEach { glyph ->
            require(glyph.size in 1..64) { "Contornos da fonte inválidos." }
            glyph.forEach { contour ->
                require(contour.size in 3..20_000) { "Tamanho de contorno inválido." }
                total += contour.size
                require(total <= 80_000) { "Muitos pontos vetoriais." }
                require(contour.all { it.x.isFinite() && it.y.isFinite() &&
                    kotlin.math.abs(it.x) <= 1_000_000f &&
                    kotlin.math.abs(it.y) <= 1_000_000f }) {
                    "Coordenadas de contorno inválidas."
                }
            }
        }
        val output = mutableListOf<EmbroideryPoint>()
        val state = ReferenceSatinState()
        contoursByGlyph.forEach { glyph ->
            val polygons = glyph.map { contour ->
                contour.map { it.x to it.y }
            }
            appendReferenceSatinGlyph(output, polygons, options, state)
            require(output.size <= 100_000) {
                "A geração Satin excedeu o limite de pontadas de QA."
            }
        }
        require(output.any { it.command == StitchCommand.STITCH }) {
            "O gerador Satin original não gerou pontadas."
        }
        return output
    }

""" + pure + "\n}\n"
        val target = generatedReferenceSatin.get().file(
            "com/timachado/brothermatrizes/desktop/platform/DesktopReferenceSatinKernel.kt"
        ).asFile
        target.parentFile.mkdirs()
        target.writeText(wrapper, Charsets.UTF_8)
    }
}
kotlin.sourceSets.getByName("main").kotlin.srcDir(generatedReferenceSatin)

/**
 * Stroke-flow principal: extração LITERAL da fase de thinning/skeleton
 * (Zhang–Suen em dois passes + trace de arestas) do Android 1.11.8.
 * Continua sem tocar no pipeline Android. O compositor Skia será
 * implementado como adaptador isolado.
 */
val generatedStrokeSkeleton = layout.buildDirectory.dir("generated/stroke-skeleton/kotlin")
val copyAndroidStrokeSkeleton by tasks.registering {
    val originalFile = layout.projectDirectory.file(
        "../app/src/main/java/com/timachado/brothermatrizes/font/ImportedFontMatrixGenerator.kt"
    )
    inputs.file(originalFile)
    outputs.dir(generatedStrokeSkeleton)
    doLast {
        val source = originalFile.asFile.readText(Charsets.UTF_8).replace("\r\n", "\n")
        val begin = source.indexOf("    private fun thinMask(")
        val end = source.indexOf("    private fun orientSkeletonLine(", begin)
        require(begin > 0 && end > begin &&
            source.substring(begin, end).contains("private fun traceSkeleton(") &&
            source.substring(begin, end).contains("pass <") &&
            source.substring(begin, end).contains("120")) {
            "Algoritmo de esqueleto Android mudou: bloquear porta automática."
        }
        val pure = source.substring(begin, end)
        require(!pure.contains("android.") &&
            !pure.contains("Bitmap(") && !pure.contains("Canvas(") &&
            !pure.contains("Path(")) {
            "Fase de skeleton Android requer adaptação específica; QA abortada."
        }
        val wrapper = """
package com.timachado.brothermatrizes.desktop.platform

import kotlin.math.hypot

/** Linhas do esqueleto devolvidas sem reordenar ou suavizar. */
internal data class DesktopSkeletonPoint(val x: Int, val y: Int)

/** Algoritmo original Android extraído diretamente em tempo de compilação. */
internal object DesktopStrokeSkeleton {
    private data class SkeletonPoint(val x: Int, val y: Int)

    fun analyze(mask: BooleanArray, width: Int, height: Int): List<List<DesktopSkeletonPoint>> {
        require(width in 5..1800 && height in 5..1800 &&
            width.toLong() * height.toLong() <= 1_500_000L &&
            mask.size.toLong() == width.toLong() * height.toLong()) {
            "Máscara de rasterização possui dimensões inválidas."
        }
        require(mask.any { it }) { "A máscara não tem pixels preenchidos." }
        // Defesa: algoritmos de afinamento são chamados só em máscaras
        // com borda exterior livre, conforme o padding do Android.
        require((0 until width).all { !mask[it] && !mask[(height-1)*width + it] } &&
            (0 until height).all { !mask[it*width] && !mask[it*width+width-1] }) {
            "A máscara precisa ter uma margem vazia de um pixel."
        }
        val thinned = thinMask(mask, width, height)
        return traceSkeleton(thinned, width, height)
            .map { line -> line.map { DesktopSkeletonPoint(it.x, it.y) } }
    }

""" + pure + "\n}\n"
        val out = generatedStrokeSkeleton.get().file(
            "com/timachado/brothermatrizes/desktop/platform/DesktopStrokeSkeleton.kt"
        ).asFile
        out.parentFile.mkdirs()
        out.writeText(wrapper, Charsets.UTF_8)
    }
}
kotlin.sourceSets.getByName("main").kotlin.srcDir(generatedStrokeSkeleton)

/**
 * Planejamento do percurso stroke-flow: usa a implementação Android
 * literalmente (split, rebases, raiz e planejamento ida/volta).
 * A exportação Windows ainda NÃO usa este motor experimental.
 */
val generatedStrokePlanner = layout.buildDirectory.dir("generated/stroke-planner/kotlin")
val copyAndroidStrokePlanner by tasks.registering {
    val originalFile = layout.projectDirectory.file(
        "../app/src/main/java/com/timachado/brothermatrizes/font/ImportedFontMatrixGenerator.kt"
    )
    inputs.file(originalFile)
    outputs.dir(generatedStrokePlanner)
    doLast {
        val source = originalFile.asFile.readText(Charsets.UTF_8).replace("\r\n", "\n")
        val typesStart = source.indexOf("    private data class SkeletonPoint(")
        val typesEnd = source.indexOf("    private fun buildStrokeFlowSatinText(", typesStart)
        val plannerStart = source.indexOf("    private fun strokeFlowNodeKey(", typesEnd)
        val plannerEnd = source.indexOf("    private fun buildStrokeFlowRows(", plannerStart)
        val distanceStart = source.indexOf("    private fun pointDistance(", plannerEnd)
        val distanceEnd = source.indexOf("    private fun appendSplitStitch(", distanceStart)
        val lengthStart = source.indexOf("    private fun skeletonLineLength(", distanceEnd)
        val lengthEnd = source.indexOf("    private fun smoothAndResampleSkeleton(", lengthStart)
        check(typesStart > 0 && typesEnd > typesStart &&
            plannerStart > typesEnd && plannerEnd > plannerStart &&
            distanceStart > plannerEnd && distanceEnd > distanceStart &&
            lengthStart > distanceEnd && lengthEnd > lengthStart) {
            "Mudança na localização do planner Android: revisar antes de integrar."
        }
        val types = source.substring(typesStart, typesEnd)
        val planner = source.substring(plannerStart, plannerEnd)
        val helpers = source.substring(distanceStart, distanceEnd) +
            source.substring(lengthStart, lengthEnd)
        check(types.contains("data class StrokeFlowSegment(") &&
            types.contains("data class StrokeFlowTraversalStep(") &&
            planner.contains("private fun splitStrokeFlowSegments(") &&
            planner.contains("private fun planStrokeFlowTraversal(") &&
            planner.contains("private fun rebaseLeadingClosedSkeletonLine(") &&
            planner.contains("private fun rebaseLeadingClosedStrokeFlow(") &&
            helpers.contains("private fun pointDistance(") &&
            helpers.contains("private fun skeletonLineLength(") &&
            !planner.contains("Bitmap(") && !planner.contains("Canvas(") &&
            !planner.contains("android.")) {
            "O planner não corresponde ao algoritmo puro oficial Android."
        }
        val wrapper = """
package com.timachado.brothermatrizes.desktop.platform

import kotlin.math.*

/** Plano QA das fases reais de cada segmento, sem emitir pontos comerciais. */
internal data class DesktopStrokeFlowPlanStep(
    val segmentId: Int,
    val phase: String,
    val from: DesktopSkeletonPoint,
    val to: DesktopSkeletonPoint,
    val geometry: List<DesktopSkeletonPoint>
)

/** Extrai o planejamento do motor Android, sem alterações nos métodos. */
internal object DesktopStrokeFlowPlanner {
    fun plan(
        source: List<List<DesktopSkeletonPoint>>,
        preferInitialStroke: Boolean = false
    ): List<DesktopStrokeFlowPlanStep> {
        require(source.size in 1..256 && source.sumOf { it.size } <= 100_000) {
            "Quantidade de linhas ou pixels do esqueleto fora do limite."
        }
        require(source.all { line ->
            line.size in 2..100_000 && line.all {
                it.x in 0..1800 && it.y in 0..1800
            }
        }) { "Segmento do esqueleto inválido." }
        val originalLines = source.map { line ->
            line.map { SkeletonPoint(it.x, it.y) }
        }.filter { line ->
            line.size >= 5 && skeletonLineLength(line) >= 8f
        }
        if (originalLines.isEmpty()) return emptyList()
        // Exatamente a ordem da implementação Android em buildSatinByAxis.
        val normalized = rebaseLeadingClosedSkeletonLine(originalLines)
        val loopEntry = normalized.filter { line ->
            line.size >= 6 && line.first() == line.last()
        }.minByOrNull { line -> line.minOf { it.x } }?.first()
        val segments = rebaseLeadingClosedStrokeFlow(
            splitStrokeFlowSegments(normalized)
        )
        if (segments.isEmpty()) return emptyList()
        // Esta etapa não testa buildStrokeFlowRows; todos os segmentos
        // geometricamente válidos são planejados. Quando o row builder
        // for portado, filtrar segmentos por 2 ou mais linhas Satin.
        val hint = loopEntry ?: if (preferInitialStroke)
            preferredOpenSwashEntry(segments)
        else preferredStrokeFlowEntry(segments)
        val byId = segments.associateBy { it.id }
        return planStrokeFlowTraversal(segments, hint).map { step ->
            val seg = checkNotNull(byId[step.segmentId])
            DesktopStrokeFlowPlanStep(
                segmentId = step.segmentId,
                phase = step.phase.name,
                from = strokeFlowNodePoint(step.fromNode).let {
                    DesktopSkeletonPoint(it.x, it.y)
                },
                to = strokeFlowNodePoint(step.toNode).let {
                    DesktopSkeletonPoint(it.x, it.y)
                },
                geometry = orientedStrokeFlowPoints(seg, step.fromNode).map {
                    DesktopSkeletonPoint(it.x, it.y)
                }
            )
        }
    }

""" + types + planner + helpers + "\n}\n"
        val out = generatedStrokePlanner.get().file(
            "com/timachado/brothermatrizes/desktop/platform/DesktopStrokeFlowPlanner.kt"
        ).asFile
        out.parentFile.mkdirs()
        out.writeText(wrapper, Charsets.UTF_8)
    }
}
kotlin.sourceSets.getByName("main").kotlin.srcDir(generatedStrokePlanner)

/**
 * Construtor das linhas Satin do stroke-flow oficial Android: suavização,
 * reamostragem do esqueleto, busca perpendicular às bordas e coordenadas.
 * Faz uso EXATO das funções de buildStrokeFlowRows e auxiliares da fonte
 * Android, não um algoritmo alternativo de pontos.
 */
val generatedStrokeRows = layout.buildDirectory.dir("generated/stroke-rows/kotlin")
val copyAndroidStrokeRows by tasks.registering {
    val originalFile = layout.projectDirectory.file(
        "../app/src/main/java/com/timachado/brothermatrizes/font/ImportedFontMatrixGenerator.kt"
    )
    inputs.file(originalFile)
    outputs.dir(generatedStrokeRows)
    doLast {
        val source = originalFile.asFile.readText(Charsets.UTF_8).replace("\r\n", "\n")
        val typesStart = source.indexOf("    private data class SkeletonPoint(")
        val typesEnd = source.indexOf("    private fun buildStrokeFlowSatinText(", typesStart)
        val rowsStart = source.indexOf("    private fun buildStrokeFlowRows(", typesEnd)
        val rowsEnd = source.indexOf("    private fun orientStrokeFlowRows(", rowsStart)
        val smoothStart = source.indexOf("    private fun smoothAndResampleSkeleton(", rowsEnd)
        val boundaryStart = source.indexOf("    private fun boundaryDistance(", smoothStart)
        val end = source.indexOf("    private fun buildFilledText(", boundaryStart)
        check(typesStart > 0 && typesEnd > typesStart &&
            rowsStart > typesEnd && rowsEnd > rowsStart &&
            smoothStart > rowsEnd && boundaryStart > smoothStart &&
            end > boundaryStart &&
            source.substring(rowsStart, rowsEnd).contains("private fun buildStrokeFlowRows(") &&
            source.substring(smoothStart, boundaryStart).contains("private fun smoothSkeletonLine(") &&
            source.substring(smoothStart, boundaryStart).contains("private fun sampleSkeletonLine(") &&
            source.substring(boundaryStart, end).contains("private fun skeletonXToUnits(") &&
            source.substring(boundaryStart, end).contains("private fun skeletonYToUnits(")
        ) { "Funções oficiais de linhas Satin mudaram; a porta deve ser revista." }
        val types = source.substring(typesStart, typesEnd)
        val rows = source.substring(rowsStart, rowsEnd)
        val smooth = source.substring(smoothStart, boundaryStart)
        val boundary = source.substring(boundaryStart, end)
        require((types + rows + smooth + boundary).let {
            !it.contains("Bitmap(") && !it.contains("Canvas(") &&
                !it.contains("android.graphics.")
        }) { "Funções selecionadas deixaram de ser Kotlin independente." }
        val wrapper = """
package com.timachado.brothermatrizes.desktop.platform

import kotlin.math.*

/** Linhas geométricas do Satin original, antes de gerar pontadas. */
internal data class DesktopStrokeRow(
    val centerXUnits: Int,
    val centerYUnits: Int,
    val aXUnits: Int,
    val aYUnits: Int,
    val bXUnits: Int,
    val bYUnits: Int
)

internal object DesktopStrokeFlowRows {
    fun build(
        skeleton: List<DesktopSkeletonPoint>,
        raster: DesktopStrokeRaster,
        densityMm: Float,
        pullCompensationMm: Float
    ): List<DesktopStrokeRow> {
        require(skeleton.size in 2..100_000 && skeleton.all {
            it.x in 0 until raster.width && it.y in 0 until raster.height
        }) { "Pontos de esqueleto inválidos para esta máscara." }
        require(densityMm.isFinite() && densityMm in 0.3f..1.2f) {
            "Densidade Satin fora do intervalo oficial Android."
        }
        require(pullCompensationMm.isFinite() &&
            pullCompensationMm in 0f..1f) {
            "Repuxo Satin fora do intervalo oficial Android."
        }
        val densityUnits = (densityMm * 10f).coerceIn(2.5f, 12f)
        val stepPixels = (densityUnits / raster.unitsPerPixel).coerceIn(2f, 8f)
        val pullPixels = (
            pullCompensationMm * 10f / raster.unitsPerPixel
        ).coerceAtLeast(0f)
        val rows = buildStrokeFlowRows(
            rawLine = skeleton.map { SkeletonPoint(it.x, it.y) },
            mask = raster.mask,
            width = raster.width,
            height = raster.height,
            stepPixels = stepPixels,
            pullPixels = pullPixels,
            minX = raster.minX,
            maxY = raster.maxY,
            padding = raster.padding,
            unitsPerPixel = raster.unitsPerPixel
        )
        require(rows.size <= 50_000) { "Muitas linhas de cobertura Satin." }
        return rows.map { row ->
            DesktopStrokeRow(
                row.centerXUnits, row.centerYUnits,
                row.aXUnits, row.aYUnits, row.bXUnits, row.bYUnits
            )
        }
    }

""" + types + rows + smooth + boundary + "\n}\n"
        val out = generatedStrokeRows.get().file(
            "com/timachado/brothermatrizes/desktop/platform/DesktopStrokeFlowRows.kt"
        ).asFile
        out.parentFile.mkdirs()
        out.writeText(wrapper, Charsets.UTF_8)
    }
}
kotlin.sourceSets.getByName("main").kotlin.srcDir(generatedStrokeRows)

/**
 * Emitter do stroke-flow Satin principal: extrai literalmente do Android
 * as rotinas que emitem JUMP/TRIM/STITCH, center-run, underlay e cobertura.
 * NÃO altera o motor comercial Android; teste isolado em JVM Windows.
 */
val generatedStrokeEmitter = layout.buildDirectory.dir("generated/stroke-emitter/kotlin")
val copyAndroidStrokeEmitter by tasks.registering {
    val originalFile = layout.projectDirectory.file(
        "../app/src/main/java/com/timachado/brothermatrizes/font/ImportedFontMatrixGenerator.kt"
    )
    inputs.file(originalFile)
    outputs.dir(generatedStrokeEmitter)
    doLast {
        val source = originalFile.asFile.readText(Charsets.UTF_8).replace("\r\n", "\n")
        val typesStart = source.indexOf("    private data class SkeletonPoint(")
        val typesEnd = source.indexOf("    private fun buildStrokeFlowSatinText(", typesStart)
        val emissionStart = source.indexOf("    private fun orientStrokeFlowRows(", typesEnd)
        val emissionEnd = source.indexOf("    internal fun debugStrokeFlowPostOrder()", emissionStart)
        val splitStart = source.indexOf("    private fun appendSplitStitch(", emissionEnd)
        val splitEnd = source.indexOf("    private fun repairSatinSequence(", splitStart)
        require(typesStart > 0 && typesEnd > typesStart &&
            emissionStart > typesEnd && emissionEnd > emissionStart &&
            splitStart > emissionEnd && splitEnd > splitStart) {
            "Métodos de costura stroke-flow mudaram: revisar extração."
        }
        val types = source.substring(typesStart, typesEnd)
        val emit = source.substring(emissionStart, emissionEnd)
        val split = source.substring(splitStart, splitEnd)
        require(emit.contains("private fun moveStrokeFlowTo(") &&
            emit.contains("private fun emitStrokeFlowCenterRun(") &&
            emit.contains("private fun emitStrokeFlowCoverage(") &&
            emit.contains("private fun orientStrokeFlowRows(") &&
            split.contains("private fun appendSplitStitch(") &&
            !(emit + split).contains("android.graphics.") &&
            !(emit + split).contains("Bitmap(") &&
            !(emit + split).contains("Canvas(")) {
            "Emissor oficial não é Kotlin puro seguro para Windows."
        }
        val wrapper = """
package com.timachado.brothermatrizes.desktop.platform

import com.timachado.brothermatrizes.core.embroidery.EmbroideryPoint
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import kotlin.math.*

/**
 * Resultado real em pontos das fases CENTER_RUN / SATIN_RETURN.
 * Alimentado por planejamento e rows oficiais. Não é release TTF/OTF.
 */
internal object DesktopStrokeFlowEmitter {
    fun emit(
        plan: List<DesktopStrokeFlowPlanStep>,
        rowsBySegment: Map<Int, List<DesktopStrokeRow>>,
        includeUnderlay: Boolean,
        densityMm: Float
    ): List<EmbroideryPoint> {
        require(plan.isNotEmpty() && plan.size <= 512 && plan.size % 2 == 0) {
            "Plano Satin inválido ou excessivo."
        }
        require(densityMm.isFinite() && densityMm in 0.3f..1.2f) {
            "Densidade do motor Android fora do intervalo aprovado."
        }
        val normalized = mutableMapOf<Int, Pair<StrokeFlowSegment, List<StrokeFlowSatinRow>>>()
        for (pair in plan.chunked(2)) {
            val center = pair[0]
            val satin = pair[1]
            require(center.phase == "CENTER_RUN" && satin.phase == "SATIN_RETURN" &&
                center.segmentId == satin.segmentId &&
                center.from == satin.to && center.to == satin.from &&
                center.geometry.size in 2..100_000) {
                "Plano de fases não corresponde à ordem Android."
            }
            require(center.segmentId !in normalized) {
                "Um segmento não pode ser costurado duas vezes."
            }
            val rows = checkNotNull(rowsBySegment[center.segmentId]) {
                "Segmento sem colunas de cobertura Satin."
            }
            require(rows.size in 2..50_000) {
                "Um segmento precisa de 2 ou mais linhas de cobertura."
            }
            require(rows.all {
                kotlin.math.abs(it.centerXUnits) <= 1_000_000 &&
                kotlin.math.abs(it.centerYUnits) <= 1_000_000 &&
                kotlin.math.abs(it.aXUnits) <= 1_000_000 &&
                kotlin.math.abs(it.aYUnits) <= 1_000_000 &&
                kotlin.math.abs(it.bXUnits) <= 1_000_000 &&
                kotlin.math.abs(it.bYUnits) <= 1_000_000
            }) {
                "Linhas de cobertura fora da área de trabalho."
            }
            fun nodeKey(node: DesktopSkeletonPoint): Long =
                (node.x.toLong() shl 32) or (node.y.toLong() and 0xffffffffL)
            val segment = StrokeFlowSegment(
                id = center.segmentId,
                points = center.geometry.map { SkeletonPoint(it.x, it.y) },
                startNode = nodeKey(center.from),
                endNode = nodeKey(center.to)
            )
            normalized[center.segmentId] = segment to rows.map {
                StrokeFlowSatinRow(
                    it.centerXUnits, it.centerYUnits,
                    it.aXUnits, it.aYUnits, it.bXUnits, it.bYUnits
                )
            }
        }
        require(rowsBySegment.keys == normalized.keys) {
            "Linhas de segmento não correspondem ao plano de costura."
        }
        val output = mutableListOf<EmbroideryPoint>()
        val densityUnits = (densityMm * 10f).coerceIn(2.5f, 12f)
        for (step in plan) {
            val (segment, baseRows) = checkNotNull(normalized[step.segmentId])
            val fromKey = (step.from.x.toLong() shl 32) or
                (step.from.y.toLong() and 0xffffffffL)
            val rows = orientStrokeFlowRows(segment, baseRows, fromKey)
            when (step.phase) {
                "CENTER_RUN" -> emitStrokeFlowCenterRun(
                    output, rows, includeUnderlay, densityUnits
                )
                "SATIN_RETURN" -> emitStrokeFlowCoverage(output, rows)
                else -> error("Fase desconhecida do algoritmo Android.")
            }
            require(output.size <= 100_000) {
                "Número de pontadas excede o limite da QA."
            }
        }
        require(output.any { it.command == StitchCommand.STITCH }) {
            "O emissor não produziu pontadas de costura."
        }
        return output
    }

""" + types + emit + split + "\n}\n"
        val target = generatedStrokeEmitter.get().file(
            "com/timachado/brothermatrizes/desktop/platform/DesktopStrokeFlowEmitter.kt"
        ).asFile
        target.parentFile.mkdirs()
        target.writeText(wrapper, Charsets.UTF_8)
    }
}
kotlin.sourceSets.getByName("main").kotlin.srcDir(generatedStrokeEmitter)

/**
 * Motor Satin REAL do APK Android 1.11.8: importar o arquivo Kotlin inteiro,
 * byte por byte, sem forks de rotinas de pontada, do código fonte Android.
 *
 * É o CleanRoomSatinEngine chamado por ImportedFontMatrixGenerator quando
 * options.style == SATIN e specialStitchMode == null. O antigo stroke-flow
 * não é a rota comercial vigente e fica apenas como experimento isolado.
 *
 * AndroidSkiaOutlineExtractor e ImportedFontCoverageGuard são adaptadores
 * de plataforma e FALHAM FECHADOS até equivalência numérica demonstrada.
 */
val generatedActiveSatin = layout.buildDirectory.dir("generated/active-satin-android/kotlin")
val copyAndroidActiveSatin by tasks.registering(Sync::class) {
    from("../app/src/main/java") {
        include("com/timachado/brothermatrizes/font/CleanRoomSatinEngine.kt")
    }
    into(generatedActiveSatin)
}
kotlin.sourceSets.getByName("main").kotlin.srcDir(generatedActiveSatin)

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
        copyAndroidLoader, copyAndroidReferenceSatin, copyAndroidStrokeSkeleton,
        copyAndroidStrokePlanner, copyAndroidStrokeRows,
        copyAndroidStrokeEmitter, copyAndroidActiveSatin
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

/**
 * Comparação isolada de contornos da MESMA TTF.
 * A fonte vem do artifact do job Android; sem rede no CLI, sem exportar PES.
 */
tasks.register<JavaExec>("exportFontOutlineQa") {
    group = "verification"
    description = "Exporta contornos Skia da mesma fonte TTF do emulador Android"
    dependsOn(tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(
        "com.timachado.brothermatrizes.desktop.platform.DesktopFontParityCliKt"
    )
    args(
        layout.buildDirectory.file("qa/font-parity/android/bm-parity-font.ttf").get()
            .asFile.absolutePath,
        layout.buildDirectory.file("qa/font-parity/windows/bm-parity-windows-outline.tsv").get()
            .asFile.absolutePath
    )
}
