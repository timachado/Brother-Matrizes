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
            "ViewerScreen.kt", "ConverterScreen.kt", "MonogramScreen.kt",
            "NameEditorPlacement.kt",
            "NameEditorStitchLayer.kt", "ImportedNameSelectionPanel.kt"
        )) include("com/timachado/brothermatrizes/$name")
    }
    into(generatedViewer)
}
kotlin.sourceSets.getByName("main").kotlin.srcDir(generatedViewer)


// Enviar para a máquina: reutilizar a TELA OFICIAL Android sem copiar
// nenhuma implementação anterior de Windows. Somente callbacks de
// filesystem e textos de instrução específicos de Android mudam aqui.
// O fluxo não declara Wi-Fi, pendrive detectado ou bordadeira conectada.
val generatedMachineTransfer = layout.buildDirectory.dir("generated/android-machine-transfer/kotlin")
val copyAndroidMachineTransfer by tasks.registering {
    val input = originalAndroidCode.file("MachineTransferScreen.kt")
    inputs.file(input)
    outputs.dir(generatedMachineTransfer)
    doLast {
        val source = input.asFile.readText(Charsets.UTF_8).replace("\r\n", "\n")
        check(
            source.contains("fun MachineTransferScreen(") &&
                source.contains("MachineTransferValidator") &&
                source.contains("validation.ready") &&
                source.contains("onUsbOtg: (String) -> Unit") &&
                source.contains("onWifi: (String) -> Unit") &&
                source.contains("onUsbOtg(\n                            format\n                        )") &&
                source.contains("onWifi(\n                            format\n                        )") &&
                source.contains("Conecte o pendrive ao celular.") &&
                source.contains("Gera o arquivo validado e abre os apps compatíveis do Android.")
        ) { "Fluxo de envio Android alterado: revisar contrato antes do Windows." }

        val desktop = source
            .replace("onUsbOtg: (String) -> Unit", "onUsbOtg: (String, HoopProfile) -> Unit")
            .replace("onWifi: (String) -> Unit", "onWifi: (String, HoopProfile) -> Unit")
            .replace(
                "onUsbOtg(\n                            format\n                        )",
                "onUsbOtg(format, hoop)"
            )
            .replace(
                "onWifi(\n                            format\n                        )",
                "onWifi(format, hoop)"
            )
            .replace(
                "Conecte o pendrive ao celular. O Android abrirá o seletor de arquivos; escolha o armazenamento USB como destino.",
                "Conecte o pendrive ao PC. Selecione o arquivo de destino no Windows. O app exporta a matriz; não detecta nem envia diretamente à bordadeira."
            )
            .replace(
                "Gera o arquivo validado e abre os apps compatíveis do Android. Quando a bordadeira oferecer protocolo Wi-Fi direto suportado, ele poderá ser conectado aqui sem trocar o fluxo.",
                "Salve um arquivo validado e abra-o manualmente no software da bordadeira. Não existe conexão Wi-Fi direta nesta versão Windows."
            )
            .replace("Enviar via Wi-Fi / app", "Salvar arquivo para app da máquina")
            .replace("Wi-Fi / app da máquina", "Exportar para app da máquina")
            .replace("✓ Pronta para enviar", "✓ Pronta para exportar")
            .replace("⚠ Corrija antes de enviar", "⚠ Corrija antes de exportar")

        check(
            !desktop.contains("Conecte o pendrive ao celular.") &&
                !desktop.contains("apps compatíveis do Android") &&
                desktop.contains("fun MachineTransferScreen(") &&
                desktop.contains("onUsbOtg(format, hoop)") &&
                desktop.contains("onWifi(format, hoop)") &&
                desktop.contains("MachineTransferValidator") &&
                desktop.contains("não detecta nem envia diretamente")
        ) { "Transferência Windows não pode prometer envio direto nem perder o validador." }
        val out = generatedMachineTransfer.get().file(
            "com/timachado/brothermatrizes/MachineTransferScreen.kt"
        ).asFile
        out.parentFile.mkdirs()
        out.writeText(desktop, Charsets.UTF_8)
    }
}
kotlin.sourceSets.getByName("main").kotlin.srcDir(generatedMachineTransfer)


// Criar Desenho: UI Android original + gestos Compose de desenho livre.
// Substituir APENAS o ActivityResult/ContentResolver Android pelo seletor
// SVG do Windows. Os algoritmos SimpleSvgParser e VectorMatrixGenerator
// são copiados sem alterações de core/embroidery/VectorMatrixGenerator.kt.
val generatedDrawing = layout.buildDirectory.dir("generated/android-drawing/kotlin")
val copyAndroidCreateDrawing by tasks.registering {
    val input = originalAndroidCode.file("CreateDrawingScreen.kt")
    inputs.file(input)
    outputs.dir(generatedDrawing)
    doLast {
        val source = input.asFile.readText(Charsets.UTF_8).replace("\r\n", "\n")
        val pickerStart = "    val svgPicker ="
        val pickerEnd = "    val freehandArtwork ="
        val start = source.indexOf(pickerStart)
        val end = source.indexOf(pickerEnd, start)
        val launch = """svgPicker.launch(
                                arrayOf(
                                    "image/svg+xml",
                                    "application/xml",
                                    "text/xml"
                                )
                            )"""
        val context = "    val context =\n        LocalContext.current\n\n"
        check(start > 0 && end > start &&
            source.split(pickerStart).size == 2 &&
            source.split(pickerEnd).size == 2 &&
            source.split(context).size == 2 &&
            source.split(launch).size == 2 &&
            source.substring(start, end).contains("SimpleSvgParser") &&
            source.substring(start, end).contains("OpenableColumns") &&
            source.contains("detectDragGestures(") &&
            source.contains("VectorMatrixGenerator") &&
            source.contains("fun CreateDrawingScreen(")
        ) { "Criar Desenho Android mudou; revisar a ponte SVG antes do port Windows." }

        val localPicker = """
    fun requestSvgFromWindows() {
        val selected = DesktopSvgFilePicker.chooseFromDialog() ?: return
        selected.fold(
            onSuccess = { picked ->
                val parsed = SimpleSvgParser.parse(picked.name, picked.bytes)
                importedArtwork = parsed.getOrNull()
                importMessage = parsed.fold(
                    onSuccess = { artwork ->
                        artwork.label + " • " + artwork.paths.size + " caminho(s)"
                    },
                    onFailure = { error -> DesktopSvgFilePicker.userMessage(error) }
                )
            },
            onFailure = { error ->
                importedArtwork = null
                importMessage = DesktopSvgFilePicker.userMessage(error)
            }
        )
    }

"""
        val desktop = (source.substring(0, start) + localPicker +
            source.substring(end))
            .replace(context, "")
            .replace(
                "import android.provider.OpenableColumns\n", ""
            )
            .replace(
                "import androidx.activity.compose.rememberLauncherForActivityResult\n", ""
            )
            .replace(
                "import androidx.activity.result.contract.ActivityResultContracts\n", ""
            )
            .replace("import androidx.compose.ui.platform.LocalContext\n",
                "import com.timachado.brothermatrizes.desktop.platform.DesktopSvgFilePicker\n")
            .replace(launch, "requestSvgFromWindows()")
        check(!desktop.contains("android.provider.") &&
            !desktop.contains("rememberLauncherForActivityResult") &&
            !desktop.contains("ActivityResultContracts") &&
            !desktop.contains("contentResolver") &&
            !desktop.contains("svgPicker.launch") &&
            desktop.contains("requestSvgFromWindows()") &&
            desktop.contains("fun FreehandPad(") &&
            desktop.contains("VectorMatrixGenerator") &&
            desktop.contains("fun CreateDrawingScreen(")
        ) { "Criar Desenho Windows contém ponte Android incorreta." }
        val out = generatedDrawing.get().file(
            "com/timachado/brothermatrizes/CreateDrawingScreen.kt"
        ).asFile
        out.parentFile.mkdirs()
        out.writeText(desktop, Charsets.UTF_8)
    }
}
kotlin.sourceSets.getByName("main").kotlin.srcDir(generatedDrawing)


// Editor de Matrizes: fonte original Android mantida integralmente,
// exceto o BackHandler de Activity (o botão Voltar e seu AlertDialog
// permanecem idênticos). recommendedHoopFor é extraído literalmente do
// MainActivity.kt Android para impedir divergência nas dimensões do bastidor.
val generatedEditor = layout.buildDirectory.dir("generated/android-editor/kotlin")
val copyAndroidEditor by tasks.registering {
    val editorFile = originalAndroidCode.file("EditorScreen.kt")
    val activityFile = originalAndroidCode.file("MainActivity.kt")
    inputs.file(editorFile)
    inputs.file(activityFile)
    outputs.dir(generatedEditor)
    doLast {
        val original = editorFile.asFile.readText(Charsets.UTF_8).replace("\r\n", "\n")
        val handlerImport = "import androidx.activity.compose.BackHandler\n"
        val handlerBlock = "    BackHandler {\n        requestBack()\n    }\n\n"
        check(original.contains("fun EditorScreen(") &&
            original.contains("MatrixEditor.apply(") &&
            original.contains("fun requestBack()") &&
            original.contains("showExitDialog") &&
            original.contains("Aplicar edição") &&
            original.split(handlerImport).size == 2 &&
            original.split(handlerBlock).size == 2
        ) { "Editor Android mudou: portagem Desktop precisa de revisão." }
        val desktop = original.replace(handlerImport, "").replace(handlerBlock, "")
        check(!desktop.contains("BackHandler") &&
            desktop.contains("onApply(preview)") &&
            desktop.contains("Alterações não aplicadas")
        ) { "Adaptação do retorno Desktop descartou funcionalidades." }
        val editorOut = generatedEditor.get().file(
            "com/timachado/brothermatrizes/EditorScreen.kt"
        ).asFile
        editorOut.parentFile.mkdirs()
        editorOut.writeText(desktop, Charsets.UTF_8)

        val activity = activityFile.asFile.readText(Charsets.UTF_8).replace("\r\n", "\n")
        val begin = activity.indexOf("internal fun recommendedHoopFor(")
        val end = activity.indexOf("\nprivate sealed interface Screen", begin)
        check(begin > 0 && end > begin) {
            "A referência Android de recommendedHoopFor mudou."
        }
        val function = activity.substring(begin, end).trim()
        check(function.contains("HoopProfile.entries") &&
            function.contains("design.bounds.widthMm") &&
            function.contains("design.bounds.heightMm") &&
            function.endsWith("}")
        ) { "Heurística oficial de bastidor alterada." }
        val helper = """
package com.timachado.brothermatrizes

import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.HoopProfile

$function
""".trimStart()
        val helperOut = generatedEditor.get().file(
            "com/timachado/brothermatrizes/RecommendedHoopFor.kt"
        ).asFile
        helperOut.parentFile.mkdirs()
        helperOut.writeText(helper, Charsets.UTF_8)
    }
}
kotlin.sourceSets.getByName("main").kotlin.srcDir(generatedEditor)

// Editor de Nomes Android: importar 100% da interface oficial para Desktop,
// exceto ponte ActivityResult/Google/quotas de importação de fonte (bloqueada).
// Fontes embutidas, gestos, abas, preview, autoajuste e motor são os originais.
val generatedNameEditor = layout.buildDirectory.dir("generated/android-name-editor/kotlin")
val copyAndroidNameEditor by tasks.registering {
    val input = originalAndroidCode.file("NameEditorScreen.kt")
    inputs.file(input)
    outputs.dir(generatedNameEditor)
    doLast {
        val source = input.asFile.readText(Charsets.UTF_8).replace("\r\n", "\n")
        val startAnchor = "    val fontPicker = rememberLauncherForActivityResult("
        val endAnchor = "    fun optionsFor(value: NameDraft)"
        val start = source.indexOf(startAnchor)
        val end = source.indexOf(endAnchor, start)
        check(start > 0 && end > start &&
            source.split(startAnchor).size == 2 &&
            source.split(endAnchor).size == 2 &&
            source.substring(start, end).contains("BrotherMatrizesAccountService.authorizeImport") &&
            source.substring(start, end).contains("BrotherMatrizesAccountService.finalizeImport") &&
            source.contains("NamePersonalization.add(") &&
            source.contains("NameAutoPlacement.find(") &&
            source.contains("NameEditorPlacement.") &&
            source.contains("NameEditorStitchLayer(") &&
            source.contains("onApply(baseDesign)") &&
            source.contains("onClick = { preview?.let(onApply) }")
        ) { "NameEditor Android mudou: revisar permissões antes do Windows." }
        val safePicker = """
    // Fontes externas TTF/OTF são protegidas por licença e cotas Android.
    // Não simular login, importação, cobrança ou geração de pontadas.
    fun requestFontImport() {
        fontImportStatus = "Importação TTF/OTF indisponível nesta QA Windows: autenticação e cotas ainda não homologadas."
    }

""".trimStart('\n')
        val buttonStart = "                fontPicker.launch(arrayOf("
        val buttonAt = source.indexOf(buttonStart, end)
        val buttonEnd = source.indexOf("\n            },", buttonAt)
        check(buttonAt > end && buttonEnd > buttonAt &&
            source.substring(buttonAt, buttonEnd).contains("font/ttf") &&
            source.split(buttonStart).size == 2
        ) { "O botão de importação mudou; não expor TTF sem autorização." }
        val clean = (source.substring(0, start) + safePicker +
            source.substring(end, buttonAt) +
            "                requestFontImport()" + source.substring(buttonEnd))
            .replace("import androidx.activity.compose.rememberLauncherForActivityResult\n", "")
            .replace("import androidx.activity.result.contract.ActivityResultContracts\n", "")
            .replace("import androidx.compose.ui.platform.LocalContext\n",
                "import com.timachado.brothermatrizes.desktop.platform.LocalContext\n")
            .replace("import com.timachado.brothermatrizes.font.ImportedFontMatrixGenerator\n",
                "import com.timachado.brothermatrizes.desktop.platform.ImportedFontMatrixGenerator\n")
            .replace("import com.timachado.brothermatrizes.font.ImportedFontStore\n",
                "import com.timachado.brothermatrizes.desktop.platform.ImportedFontStore\n")
            .replace("import com.timachado.brothermatrizes.core.account.BrotherMatrizesAccountService\n", "")
            .replace("+ Importar TTF/OTF do celular (DaFont, NetFontes)",
                "+ Importar TTF/OTF (indisponível no Windows)")
            .replace("A fonte deste projeto não está instalada neste aparelho. " +
                "\" +\n                \"Reimporte o mesmo TTF/OTF para editar o nome.",
                "Fonte TTF/OTF do projeto indisponível no Windows. " +
                "\" +\n                \"Não é possível reprocessar o nome sem autorização comercial.")
        check(!clean.contains("rememberLauncherForActivityResult") &&
            !clean.contains("ActivityResultContracts") &&
            !clean.contains("fontPicker.launch") &&
            !clean.contains("BrotherMatrizesAccountService") &&
            !clean.contains("NameQaImportPolicy") &&
            !clean.contains("BuildConfig.DEBUG") &&
            clean.contains("fun NameEditorScreen(") &&
            clean.contains("NamePersonalization.add(") &&
            clean.contains("requestFontImport()") &&
            clean.contains("fun optionsFor(value: NameDraft)")
        ) { "Permissão comercial não isolada no NameEditor Desktop." }
        val output = generatedNameEditor.get().file(
            "com/timachado/brothermatrizes/NameEditorScreen.kt"
        ).asFile
        output.parentFile.mkdirs()
        output.writeText(clean, Charsets.UTF_8)
    }
}
kotlin.sourceSets.getByName("main").kotlin.srcDir(generatedNameEditor)

// Biblioteca: a UI das seções e cartões continua literalmente a do Android.
// Somente o bloco de ponte RAR/Google/ActivityResult (não suportado no Desktop)
// é substituído por um aviso. Não fingir login ou descontar cotas do cliente.
val generatedProjectLibrary = layout.buildDirectory.dir("generated/android-project-library/kotlin")
val copyAndroidProjectLibrary by tasks.registering {
    val input = originalAndroidCode.file("ProjectLibraryScreen.kt")
    inputs.file(input)
    outputs.dir(generatedProjectLibrary)
    doLast {
        val source = input.asFile.readText(Charsets.UTF_8).replace("\r\n", "\n")
        val start = source.indexOf("    val context = LocalContext.current\n")
        val end = source.indexOf("    val recentProjects =", start)
        check(start > 0 && end > start &&
            source.contains("fun ProjectLibraryScreen(") &&
            source.contains("fun ProjectLibraryCard(") &&
            source.contains("fun SentMatricesList(") &&
            source.substring(start, end).contains("BrotherMatrizesAccountService") &&
            source.substring(start, end).contains("RarExternalOpener")
        ) { "Ponte RAR da Biblioteca mudou: revisar antes do port Windows." }
        val secureRarBlock = """
    var showRarOptions by remember { mutableStateOf(false) }
    var rarMessage by remember { mutableStateOf<String?>(null) }
    fun chooseRar() {
        rarMessage = "RAR exige autenticação e cota comercial ainda não portadas para Windows."
    }
    if (showRarOptions) {
        AlertDialog(
            onDismissRequest = { showRarOptions = false },
            title = { Text("Abrir com RAR (WinRAR)") },
            text = { Text("Integração RAR/conta indisponível nesta versão de QA Windows. Nenhuma operação nem cota será cobrada.") },
            confirmButton = {
                TextButton(onClick = { showRarOptions = false }) {
                    Text("Entendido", color = FioGold)
                }
            }
        )
    }

"""
        // A Biblioteca Windows agora exporta/restaura tanto o backup
        // original Android completo como os projetos FLP locais.
        // Preservar a nomenclatura aprovada na interface Android.
        check(source.split("\"Backup completo\"").size == 2 &&
            source.split("\"Restaurar\"").size == 2) {
            "Rótulos oficiais da Biblioteca mudaram; revisar portagem."
        }
        val baseDesktop = (source.substring(0, start) + secureRarBlock +
            source.substring(end))
            .replace("import androidx.activity.compose.rememberLauncherForActivityResult\n", "")
            .replace("import androidx.activity.result.contract.ActivityResultContracts\n", "")
            .replace("import androidx.compose.ui.platform.LocalContext\n", "")
            .replace("import com.timachado.brothermatrizes.core.account.BrotherMatrizesAccountService\n", "")
        // Ação de análise local não invoca a ferramenta comercial "rar_receive".
        val callbackAnchor = "    onSearchMatrices: () -> Unit,\n"
        val rarActionAnchor = "        rarMessage?.let {"
        check(baseDesktop.contains(callbackAnchor) &&
            baseDesktop.contains(rarActionAnchor)) {
            "Biblioteca Android mudou as ações; revisar ponte do Extrator."
        }
        val inspectButton = """
        OutlinedButton(
            onClick = onInspectArchive,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Analisar ZIP / RAR / 7Z (sem importar)", color = FioGold)
        }

"""
        val desktop = baseDesktop
            .replace(callbackAnchor,
                callbackAnchor + "    onInspectArchive: () -> Unit,\n")
            .replace(rarActionAnchor, inspectButton + rarActionAnchor)
        check(!desktop.contains("import androidx.activity") &&
            !desktop.contains("RarExternalOpener") &&
            !desktop.contains("BrotherMatrizesAccountService") &&
            desktop.contains("fun ProjectLibraryScreen(") &&
            desktop.contains("fun SentMatricesList(") &&
            desktop.contains("onInspectArchive: () -> Unit") &&
            desktop.contains("Analisar ZIP / RAR / 7Z (sem importar)") &&
            desktop.contains("\"Backup completo\"") &&
            desktop.contains("\"Restaurar\"")
        ) { "Biblioteca Android contém dependências não portadas: bloquear build." }
        val out = generatedProjectLibrary.get().file(
            "com/timachado/brothermatrizes/ProjectLibraryScreen.kt"
        ).asFile
        out.parentFile.mkdirs()
        out.writeText(desktop, Charsets.UTF_8)
    }
}
kotlin.sourceSets.getByName("main").kotlin.srcDir(generatedProjectLibrary)

// Catálogos oficiais DaFont/NetFontes: mesmas URLs, regras HTTPS e
// buscas usadas no Android; não duplicar URLs nem lógica de navegação.
// A autenticação Google/cota continua sob o mesmo Supabase oficial.
// Esta etapa somente prepara o contrato para QA offline, sem liberar
// pesquisa comercial sem token verificado.
val generatedFontCatalogRules = layout.buildDirectory.dir("generated/android-font-catalog/kotlin")
val copyAndroidFontCatalogRules by tasks.registering(Sync::class) {
    from("../app/src/main/java") {
        include("com/timachado/brothermatrizes/font/DaFontNavigationRules.kt")
        include("com/timachado/brothermatrizes/font/NetFontesNavigationRules.kt")
    }
    into(generatedFontCatalogRules)
}
kotlin.sourceSets.getByName("main").kotlin.srcDir(generatedFontCatalogRules)

// Cartões de fontes NATIVAS: importar o bloco Compose oficial da
// FontLibraryScreen Android (prévias de pontadas BM/SATIN e metadados reais).
// Nenhuma TTF/OTF externa é instalada; não há alterações em quotas ou login.
val originalNativeFontGallery = layout.buildDirectory.dir("generated/android-native-font-gallery/kotlin")
val copyAndroidNativeFontGallery by tasks.registering {
    val input = originalAndroidCode.file("FontLibraryScreen.kt")
    inputs.file(input)
    outputs.dir(originalNativeFontGallery)
    doLast {
        val source = input.asFile.readText(Charsets.UTF_8).replace("\r\n", "\n")
        val opening = "            items(\n                EmbroideryFontPreset"
        val start = source.indexOf("                val preview =\n", source.indexOf(opening))
        val end = source.indexOf(
            "\n            }\n\n            item {\n                Text(\n                    \"As fontes nativas usam", start
        )
        check(source.contains(opening) && start > 0 && end > start) {
            "FontLibraryScreen Android mudou; revisar extração dos cartões."
        }
        val officialCard = source.substring(start, end)
        check(officialCard.contains("TextMatrixGenerator") &&
            officialCard.contains("TextMatrixOptions(") &&
            officialCard.contains("EmbroideryCanvas(") &&
            officialCard.contains("font.displayName") &&
            officialCard.contains("stitchCount") &&
            officialCard.contains("SatinUnderlayMode") &&
            officialCard.contains("FioSurface")) {
            "A prévia de pontadas Android perdeu alguma função."
        }
        val header = """
package com.timachado.brothermatrizes.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.brothermatrizes.EmbroideryCanvas
import com.timachado.brothermatrizes.core.embroidery.EmbroideryFontPreset
import com.timachado.brothermatrizes.core.embroidery.SatinUnderlayMode
import com.timachado.brothermatrizes.core.embroidery.TextMatrixGenerator
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.ui.theme.FioSurface
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted

/** Original Android font card. Only extracted into a reusable Windows Composable. */
@Composable
internal fun OriginalAndroidNativeFontCard(font: EmbroideryFontPreset) {
""".trimStart()
        val output = originalNativeFontGallery.get().file(
            "com/timachado/brothermatrizes/desktop/OriginalAndroidNativeFontCard.kt"
        ).asFile
        output.parentFile.mkdirs()
        output.writeText(header + officialCard + "\n}\n", Charsets.UTF_8)
    }
}
kotlin.sourceSets.getByName("main").kotlin.srcDir(originalNativeFontGallery)


// Android 1.11.9: o nome real de TTF/OTF vem DIRETAMENTE do leitor
// OpenType da versão oficial; evitar forks entre Android/Windows.
val generatedAndroidOpenType = layout.buildDirectory.dir("generated/android-opentype/kotlin")
val copyAndroidOpenTypeNames by tasks.registering(Sync::class) {
    from("../app/src/main/java") {
        include("com/timachado/brothermatrizes/font/OpenTypeFontNames.kt")
    }
    into(generatedAndroidOpenType)
}
kotlin.sourceSets.getByName("main").kotlin.srcDir(generatedAndroidOpenType)

// Shared support contact/feedback text is compiled directly from Android.
// Do not fork the public phone, email, labels or message format for Windows.
val generatedCommunityContacts = layout.buildDirectory.dir("generated/android-community/kotlin")
val copyAndroidCommunityContacts by tasks.registering(Sync::class) {
    from("../app/src/main/java") {
        include("com/timachado/brothermatrizes/BrotherFeedbackDelivery.kt")
    }
    into(generatedCommunityContacts)
}
kotlin.sourceSets.getByName("main").kotlin.srcDir(generatedCommunityContacts)

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

// Planos/Benefícios: Compose e textos de comércio copiados da fonte Android.
// Apenas BackHandler (exclusivo de Activity Android) é substituído pelo botão
// Voltar já presente no Composable original. Nenhum entitlement novo é criado.
val generatedOfficialPlans = layout.buildDirectory.dir("generated/android-plans/kotlin")
val copyOfficialAndroidPlans by tasks.registering {
    val base = originalAndroidCode.asFile
    val out = generatedOfficialPlans.get().asFile
    inputs.files(
        base.resolve("BrotherPlansScreen.kt"),
        base.resolve("AccountPremiumComponents.kt"),
        base.resolve("core/account/AccountModels.kt"),
        base.resolve("core/account/BrotherLaunchOfferCatalog.kt"),
        base.resolve("core/account/BrotherPlanBenefits.kt"),
        base.resolve("core/account/WordPressWebStore.kt")
    )
    outputs.dir(generatedOfficialPlans)
    doLast {
        val shared = listOf(
            "AccountPremiumComponents.kt",
            "core/account/AccountModels.kt",
            "core/account/BrotherLaunchOfferCatalog.kt",
            "core/account/BrotherPlanBenefits.kt",
            "core/account/WordPressWebStore.kt"
        )
        shared.forEach { path ->
            val target = out.resolve("com/timachado/brothermatrizes/$path")
            target.parentFile.mkdirs()
            target.writeBytes(base.resolve(path).readBytes())
        }
        val source = base.resolve("BrotherPlansScreen.kt").readText(Charsets.UTF_8)
            .replace("\r\n", "\n")
        check(source.contains("internal fun BrotherPlansScreen(") &&
            source.contains("import androidx.activity.compose.BackHandler") &&
            source.contains("BackHandler(enabled = showFullComparison) { showFullComparison = false }")) {
            "Tela de Planos Android alterada; revisar adaptação de teclado Windows."
        }
        val desktop = source
            .replace("import androidx.activity.compose.BackHandler\n", "")
            .replace(
                "    BackHandler(enabled = showFullComparison) { showFullComparison = false }",
                "    // Windows: o botão Voltar original fecha o comparativo."
            )
        val target = out.resolve("com/timachado/brothermatrizes/BrotherPlansScreen.kt")
        target.parentFile.mkdirs()
        target.writeText(desktop, Charsets.UTF_8)
    }
}
kotlin.sourceSets.getByName("main").kotlin.srcDir(generatedOfficialPlans)

// Extrator Inteligente: reutilizar motor ZIP/RAR/7Z Android original.
// A única alteração é a ponte ContentResolver/Uri -> Path/Files do Windows.
// As regras de tamanho, entrada e extração ficam na MESMA fonte Android.
val generatedOriginalArchive = layout.buildDirectory.dir("generated/android-archive/kotlin")
val copyOriginalAndroidArchive by tasks.registering {
    val input = originalAndroidCode.file("core/archive/SafeArchiveExtractor.kt")
    inputs.file(input)
    outputs.dir(generatedOriginalArchive)
    doLast {
        val source = input.asFile.readText(Charsets.UTF_8).replace("\r\n", "\n")
        val start = source.indexOf("    fun isArchive(context: Context, uri: Uri): Boolean {")
        val end = source.indexOf(
            "    /** Metadata-only inspector for files staged in private storage. */", start
        )
        check(start > 0 && end > start &&
            source.contains("fun extract(context: Context, inventory: ArchiveInventory,") &&
            source.contains("val destination = File(context.cacheDir,") &&
            source.contains("internal fun describe(") &&
            source.contains("MAX_ARCHIVE = 128L * 1024 * 1024")) {
            "SafeArchiveExtractor Android mudou; revisar somente a ponte Windows."
        }
        val pathAdapter = """
    /** Windows file-picker equivalent of Android isArchive(Context, Uri). */
    fun isArchive(path: java.nio.file.Path): Boolean =
        listOf(".zip", ".rar", ".7z").any {
            path.fileName.toString().lowercase().endsWith(it)
        }

    /** Copy an explicitly selected Windows file into private temporary storage. */
    fun stage(sourcePath: java.nio.file.Path, password: CharArray? = null): ArchiveInventory {
        val sourceFile = sourcePath.toAbsolutePath().normalize()
        require(!java.nio.file.Files.isSymbolicLink(sourceFile) &&
            java.nio.file.Files.isRegularFile(
                sourceFile, java.nio.file.LinkOption.NOFOLLOW_LINKS
            ) && java.nio.file.Files.size(sourceFile) in 1..MAX_ARCHIVE) {
            "Selecione um pacote regular local de até 128 MB."
        }
        val stage = java.nio.file.Files.createTempFile("brother-android-archive-", ".bin")
        try {
            java.nio.file.Files.newInputStream(
                sourceFile, java.nio.file.LinkOption.NOFOLLOW_LINKS
            ).use { input ->
                stage.toFile().outputStream().use { out ->
                    val buffer = ByteArray(16384)
                    var total = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        require(total <= MAX_ARCHIVE) {
                            "Pacote maior que 128 MB; utilize outro aplicativo."
                        }
                        out.write(buffer, 0, n)
                    }
                }
            }
            return inspect(stage.toFile(), password)
        } catch (failure: Exception) {
            java.nio.file.Files.deleteIfExists(stage)
            throw failure
        }
    }

"""
        val desktop = (
            source.substring(0, start) + pathAdapter + source.substring(end)
        )
            .replace("import android.content.Context\n", "")
            .replace("import android.net.Uri\n", "")
            .replace(
                "fun extract(context: Context, inventory: ArchiveInventory,",
                "fun extract(cacheDir: File, inventory: ArchiveInventory,"
            )
            .replace("val destination = File(context.cacheDir,", "val destination = File(cacheDir,")
        check(!desktop.contains("android.") && !desktop.contains("context.cacheDir") &&
            desktop.contains("fun inspect(file: File, password: CharArray?") &&
            desktop.contains("fun extract(cacheDir: File, inventory: ArchiveInventory,") &&
            desktop.contains("val destination = File(cacheDir,")) {
            "Motor original Android não foi portado de forma segura."
        }
        val target = generatedOriginalArchive.get().file(
            "com/timachado/brothermatrizes/core/archive/SafeArchiveExtractor.kt"
        ).asFile
        target.parentFile.mkdirs()
        target.writeText(desktop, Charsets.UTF_8)
    }
}
kotlin.sourceSets.getByName("main").kotlin.srcDir(generatedOriginalArchive)

/**
 * Arquivo visual oficial Android ArchiveExtractorScreen. Remove APENAS
 * as APIs Android de ContentResolver/Intent e a transação comercial:
 * o modo Windows permite selecionar e VISUALIZAR a matriz, sem gravar
 * matrizes/fontes na biblioteca ou consumir cotas.
 *
 * O Compose visual, checkbox, textos, senha e estados saem da fonte oficial.
 * Uma futura importação autenticada usará o gate de cotas já existente,
 * jamais uma rota local que fure o limite comercial do Android.
 */
val generatedAndroidExtractorScreen = layout.buildDirectory.dir("generated/android-extractor-screen/kotlin")
val copyAndroidExtractorScreen by tasks.registering {
    val input = originalAndroidCode.file("ArchiveExtractorScreen.kt")
    inputs.file(input)
    outputs.dir(generatedAndroidExtractorScreen)
    doLast {
        var source = input.asFile.readText(Charsets.UTF_8).replace("\r\n", "\n")
        fun change(old: String, new: String) {
            check(source.contains(old)) {
                "O Extrator Inteligente oficial Android mudou; revisar adaptação Windows: " +
                    old.take(90)
            }
            source = source.replace(old, new)
        }
        listOf(
            "import android.content.Intent\n",
            "import android.net.Uri\n",
            "import androidx.activity.compose.BackHandler\n",
            "import androidx.compose.ui.platform.LocalContext\n",
            "import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign\n",
            "import com.timachado.brothermatrizes.core.embroidery.EmbroideryLoadResult\n",
            "import com.timachado.brothermatrizes.core.embroidery.EmbroideryLoader\n",
            "import com.timachado.brothermatrizes.core.project.ProjectStore\n",
            "import com.timachado.brothermatrizes.core.project.SavedProjectSummary\n",
            "import com.timachado.brothermatrizes.font.ImportedFontStore\n"
        ).forEach { change(it, "") }
        change("import java.util.concurrent.atomic.AtomicBoolean\n",
            "import java.util.concurrent.atomic.AtomicBoolean\nimport java.nio.file.Path\nimport java.awt.Desktop\n")
        change(
            """    archiveUri: Uri,
    onBack: () -> Unit,
    onImported: (List<SavedProjectSummary>, EmbroideryDesign?) -> Unit,
    canImport: suspend (ArchiveCategory) -> String?,
    onImportFinished: suspend (ArchiveCategory, String, Boolean) -> Unit""",
            """    archivePath: Path,
    onBack: () -> Unit,
    onPreviewMatrix: suspend (ArchiveInventory, ArchiveItem, CharArray) -> Unit,
    onAuthorizedImport: (suspend (ArchiveInventory, ArchiveItem, CharArray) -> Unit)? = null"""
        )
        change("    val context = LocalContext.current\n", "")
        change("archiveUri", "archivePath")
        change("    BackHandler(enabled = !importing) { onBack() }\n", "")
        change(
            "runCatching { SafeArchiveExtractor.stage(context, archivePath, secret) }",
            "runCatching { SafeArchiveExtractor.stage(archivePath, secret) }"
        )
        val externalStart = source.indexOf("    fun external(packageName: String? = null) {")
        val externalEnd = source.indexOf("    Column(\n", externalStart)
        check(externalStart >= 0 && externalEnd > externalStart)
        source = source.substring(0, externalStart) + """
    fun external() {
        runCatching {
            require(Desktop.isDesktopSupported())
            Desktop.getDesktop().open(archivePath.toFile())
        }.onFailure {
            error = "Nenhum aplicativo compatível disponível para abrir este pacote."
        }
    }
""" + source.substring(externalEnd)
        val rarStart = source.indexOf("            // Only show RAR button if installed and resolvable.")
        val rarEnd = source.indexOf("            TextButton(onClick = onBack)", rarStart)
        check(rarStart >= 0 && rarEnd > rarStart)
        source = source.substring(0, rarStart) + """
            OutlinedButton(onClick = { external() }) {
                Text("ABRIR COM APLICATIVO DO WINDOWS", color = FioGold)
            }
""" + source.substring(rarEnd)
        val importStart = source.indexOf("            if (importing) {\n")
        val importEnd = source.indexOf("            if (archive.entries.isEmpty()) {\n", importStart)
        check(importStart >= 0 && importEnd > importStart &&
            source.substring(importStart, importEnd).contains("canImport(item.category)") &&
            source.substring(importStart, importEnd).contains("onImportFinished("))
        val safePreview = """
            val selectedMatrices = archive.entries.filter {
                it.index in selected && it.category == ArchiveCategory.MATRIX
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !importing && selectedMatrices.size == 1,
                onClick = {
                    val item = selectedMatrices.single()
                    importing = true
                    report = ""
                    scope.launch {
                        val secret = password.toCharArray()
                        try {
                            onPreviewMatrix(archive, item, secret)
                            report = "Matriz aberta para visualização, sem importação."
                        } catch (failure: Exception) {
                            report = failure.message ?: "Não foi possível visualizar esta matriz."
                        } finally {
                            secret.fill(0.toChar())
                            importing = false
                        }
                    }
                }
            ) { Text("VISUALIZAR MATRIZ SELECIONADA (SEM IMPORTAR)") }
            if (onAuthorizedImport != null) {
                val chosen = archive.entries.filter { it.index in selected }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !importing && chosen.size == 1,
                    onClick = {
                        val item = chosen.single()
                        val approval = javax.swing.JOptionPane.showConfirmDialog(
                            null,
                            "Importar apenas o item selecionado?\\n" +
                                "Confirme que você possui autorização de uso.\\n" +
                                "A conta Google será verificada e uma cota REAL " +
                                "poderá ser consumida. Cancelar não reserva cota.",
                            "Brother Matrizes — autorizar importação",
                            javax.swing.JOptionPane.YES_NO_OPTION,
                            javax.swing.JOptionPane.WARNING_MESSAGE
                        )
                        if (approval == javax.swing.JOptionPane.YES_OPTION) {
                            importing = true
                            report = ""
                            scope.launch {
                                val secret = password.toCharArray()
                                try {
                                    onAuthorizedImport(archive, item, secret)
                                    report = "Item importado e confirmado na Biblioteca."
                                } catch (failure: Exception) {
                                    report = "Importação bloqueada ou sem cota confirmada. " +
                                        "Verifique Minha Conta e o histórico de cotas."
                                } finally {
                                    secret.fill(0.toChar())
                                    importing = false
                                }
                            }
                        }
                    }
                ) { Text("IMPORTAR ITEM COM CONTA E COTA") }
            }
            Text(
                "A interface e o extrator são os originais do Android. " +
                    "Importar para a biblioteca requer autorização Google " +
                    "e reserva de cota no serviço oficial.",
                color = FioTextMuted, fontSize = 11.sp
            )
"""
        source = source.substring(0, importStart) + safePreview + source.substring(importEnd)
        check(!source.contains("import android.") && !source.contains("context.") &&
            !source.contains("BackHandler(") &&
            !source.contains("canImport(") && !source.contains("onImportFinished(") &&
            source.contains("fun ArchiveExtractorScreen(") &&
            source.contains("SafeArchiveExtractor.stage(archivePath, secret)"))
        val destination = generatedAndroidExtractorScreen.get().file(
            "com/timachado/brothermatrizes/ArchiveExtractorScreen.kt"
        ).asFile
        destination.parentFile.mkdirs()
        destination.writeText(source, Charsets.UTF_8)
    }
}
kotlin.sourceSets.getByName("main").kotlin.srcDir(generatedAndroidExtractorScreen)




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
        include("com/timachado/brothermatrizes/core/embroidery/ImportedNameBlockSelection.kt")
        include("com/timachado/brothermatrizes/core/embroidery/MatrixNameRotation.kt")
        include("com/timachado/brothermatrizes/core/embroidery/NameAutoPlacement.kt")
        include("com/timachado/brothermatrizes/core/embroidery/NamePersonalization.kt")
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

// Portable Android ZIP backup codecs: copy ORIGINAL pure Kotlin algorithms.
// Do NOT port Android Context/ProjectStore/ImportedFontStore or any write paths.
// This enables read-only validation of historical and complete Android backups.
val generatedBackupCodecs = layout.buildDirectory.dir("generated/android-backup-codecs/kotlin")
val copyAndroidBackupCodecs by tasks.registering {
    val projectFile = originalAndroidCode.file("core/project/ProjectBackup.kt")
    val completeFile = originalAndroidCode.file("core/project/CompleteLibraryBackup.kt")
    inputs.files(projectFile, completeFile)
    outputs.dir(generatedBackupCodecs)
    doLast {
        val projectSource = projectFile.asFile.readText(Charsets.UTF_8)
            .replace("\r\n", "\n")
        val completeSource = completeFile.asFile.readText(Charsets.UTF_8)
            .replace("\r\n", "\n")
        val projectBoundary = "\nobject ProjectBackupStore {"
        val completeBoundary = "\nobject CompleteLibraryBackupStore {"
        check(projectSource.contains("object ProjectBackupCodec {") &&
            completeSource.contains("object CompleteLibraryBackupCodec {") &&
            projectSource.split(projectBoundary).size == 2 &&
            completeSource.split(completeBoundary).size == 2 &&
            completeSource.contains("BROTHER_MATRIZES_COMPLETE_BACKUP") &&
            projectSource.contains("BROTHER_MATRIZES_BACKUP") &&
            completeSource.contains("ProjectBackupCodec.decode(")
        ) { "Contrato de backup Android mudou; adaptação Windows bloqueada." }

        val projectCode = projectSource.substringBefore(projectBoundary)
            .replace("import android.content.Context\n", "")
        val completeCode = completeSource.substringBefore(completeBoundary)
            .replace("import android.content.Context\n", "")
            .replace(
                "import com.timachado.brothermatrizes.core.storage.AtomicFileWriter\n", ""
            )
            .replace("import com.timachado.brothermatrizes.font.ImportedFontStore\n", "")
            .replace("import java.io.File\n", "")
        check(!projectCode.contains("import android.") &&
            !completeCode.contains("import android.") &&
            !completeCode.contains("ImportedFontStore") &&
            !projectCode.contains("object ProjectBackupStore") &&
            !completeCode.contains("object CompleteLibraryBackupStore") &&
            projectCode.trimEnd().endsWith("}") &&
            completeCode.trimEnd().endsWith("}")
        ) { "Backup Android portátil requer revisão técnica." }

        val destination = generatedBackupCodecs.get()
            .dir("com/timachado/brothermatrizes/core/project").asFile
        destination.mkdirs()
        destination.resolve("ProjectBackupCodec.kt").writeText(
            projectCode.trimEnd() + "\n", Charsets.UTF_8
        )
        destination.resolve("CompleteLibraryBackupCodec.kt").writeText(
            completeCode.trimEnd() + "\n", Charsets.UTF_8
        )
    }
}
kotlin.sourceSets.getByName("main").kotlin.srcDir(generatedBackupCodecs)

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
        copyAndroidHome, copyAndroidCreateName, copyAndroidVisualization, copyAndroidMachineTransfer, copyAndroidCreateDrawing,
        copyAndroidProjectLibrary, copyAndroidEditor, copyAndroidNameEditor,
        copyAndroidTheme, copyOfficialAndroidPlans, copyOriginalAndroidArchive, copyAndroidExtractorScreen, copyAndroidFontCatalogRules, copyAndroidNativeFontGallery, copyAndroidOpenTypeNames, copyAndroidCommunityContacts,
        copyOfficialCore, copyAndroidProjectCodec,
        copyAndroidBackupCodecs, copyAndroidLoader, copyAndroidReferenceSatin, copyAndroidStrokeSkeleton,
        copyAndroidStrokePlanner, copyAndroidStrokeRows,
        copyAndroidStrokeEmitter, copyAndroidActiveSatin
    )
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("com.github.EmbroidePy.EmbroideryIO:embroideryio-jvm:0.1.23")
    // Mesmas bibliotecas de leitura de metadados RAR/7Z do Android 1.11.8.
    // Somente análise QA: não extrair nem importar arquivos sem licença/cota.
    // Revisar os termos de redistribuição do junrar/UnRAR antes de release.
    implementation("com.github.junrar:junrar:8.1.1")
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("org.tukaani:xz:1.10")
    implementation("commons-io:commons-io:2.20.0")
    implementation("org.apache.commons:commons-lang3:3.18.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.json:json:20240303") // JSON público WooCommerce, usado pelo Android
    implementation("net.java.dev.jna:jna-platform:5.19.1") // Windows DPAPI (current user, never machine scope)
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks.test {
    useJUnitPlatform()
    // A separate JVM requires the FULL test runtime classpath; Gradle's
    // worker java.class.path contains only its worker launcher.
    // This is solely for independent-process quota crash/lock QA.
    systemProperty("brother.qa.childClasspath", sourceSets["test"].runtimeClasspath.asPath)
}

compose.desktop {
    application {
        mainClass = "com.timachado.brothermatrizes.desktop.FielMainKt"
        // SOMENTE imagem portable de QA, sem EXE de instalador ou MSI.
        // createDistributable usa jpackage --type app-image: launcher Windows
        // + runtime JRE numa pasta movível; não modifica o registro.
        // Nenhum targetFormats é ativado e não existe release comercial.
        nativeDistributions {
            // Display brand on Windows while preserving an explicit QA
            // description and version; not a commercial signed release.
            // Launcher name remains ASCII for portable Windows paths.
            packageName = "BrotherMatrizes"
            packageVersion = "0.0.1"
            description = "Brother Matrizes Windows - validacao interna QA"
            vendor = "T.I. Machado Solucoes em Tecnologia"
            // Mantém AWT/Swing (JFileChooser), JNA/DPAPI, Skia e TLS.
            // A redução de módulos fica para análise jdeps posterior.
            includeAllModules = true
            // Mesmo monograma WebP aprovado no launcher adaptativo Android.
            // O script QA converte o original para .ico antes do empacotamento.
            windows {
                iconFile.set(project.file("build/qa/brand/brother_matrizes.ico"))
            }
        }
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
