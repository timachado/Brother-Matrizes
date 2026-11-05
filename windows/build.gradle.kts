import org.gradle.api.tasks.Sync
import java.security.MessageDigest
import java.util.Base64
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.2.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20"
    id("org.jetbrains.compose") version "1.9.3"
}

// O ICO oficial é versionado como Base64 para manter o binário íntegro
// no fluxo de edição do repositório. A compilação restaura o arquivo
// somente no diretório build/, sem alterar o código Android.
val officialIconBase64 = file("icons/Brother-Matrizes.ico.b64")
val officialIconBytes = Base64.getMimeDecoder().decode(officialIconBase64.readText())
val officialIconSha256 = MessageDigest.getInstance("SHA-256")
    .digest(officialIconBytes)
    .joinToString("") { "%02x".format(it) }
check(officialIconSha256 == "95d6112188cfa2a04662240986cebde69246c02f43bcabf196d494a91a7b61e8") {
    "Ícone oficial Brother Matrizes inválido ou modificado."
}
val officialWindowsIcon = layout.buildDirectory.file("generated/icons/Brother-Matrizes.ico").get().asFile
officialWindowsIcon.parentFile.mkdirs()
officialWindowsIcon.writeBytes(officialIconBytes)

group = "com.timachado.brothermatrizes"
version = "0.1.0-qa"

kotlin {
    jvmToolchain(17)
}

/**
 * Compila NO WINDOWS os mesmos fontes Kotlin do motor Android oficial.
 * NÃO move, substitui, edita ou sincroniza no sentido inverso arquivos do Android.
 * O Sync escreve unicamente em windows/build/ (não versionado).
 */
val officialCoreDir = layout.buildDirectory.dir("generated/official-core/kotlin")
val syncOfficialCore by tasks.registering(Sync::class) {
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
            include("com/timachado/brothermatrizes/core/embroidery/MonogramGenerator.kt")
            include("com/timachado/brothermatrizes/core/embroidery/MatrixConverter.kt")
            include("com/timachado/brothermatrizes/core/embroidery/MatrixEditor.kt")
            include("com/timachado/brothermatrizes/core/embroidery/PesHoopPositionMetadata.kt")
            include("com/timachado/brothermatrizes/core/embroidery/SatinGenerator.kt")
            include("com/timachado/brothermatrizes/core/embroidery/SpecialStitchProcessor.kt")
            include("com/timachado/brothermatrizes/core/embroidery/TextMatrixGenerator.kt")
    }
    into(officialCoreDir)
}
kotlin.sourceSets.getByName("main").kotlin.srcDir(officialCoreDir)

/**
 * ProjectCodec oficial está no mesmo arquivo Android que ProjectStore, o qual
 * depende de Context e File privados da plataforma. Extrair somente a região
 * Kotlin/JVM pura, sem modificar nem duplicar manualmente a codificação v11.
 * Falha fechada caso a estrutura do arquivo oficial seja alterada.
 */
val officialProjectFile = layout.projectDirectory.file(
    "../app/src/main/java/com/timachado/brothermatrizes/core/project/ProjectStore.kt"
)
val officialProjectDir = layout.buildDirectory.dir("generated/official-project/kotlin")
val extractOfficialProjectCodec by tasks.registering {
    inputs.file(officialProjectFile)
    outputs.dir(officialProjectDir)
    doLast {
        val original = officialProjectFile.asFile.readText(Charsets.UTF_8)
        val beginMarker = "data class SavedProjectSummary("
        val endMarker = "\nobject ProjectStore {"
        val begin = original.indexOf(beginMarker)
        val end = original.indexOf(endMarker)
        check(begin > 0 && end > begin) {
            "Estrutura do ProjectCodec Android mudou; extração Windows bloqueada."
        }
        check(original.indexOf(beginMarker, begin + beginMarker.length) == -1 &&
            original.indexOf(endMarker, end + endMarker.length) == -1) {
            "Marcadores duplicados no ProjectStore Android."
        }
        val imports = original.lineSequence().filter {
            it.startsWith("import ") &&
                !it.startsWith("import android.") &&
                !it.contains("core.storage.AtomicFileWriter")
        }.joinToString("\n")
        val codec = original.substring(begin, end)
        check(!codec.contains("Context") && !codec.contains("AtomicFileWriter")) {
            "ProjectCodec passou a depender de APIs Android."
        }
        val target = officialProjectDir.get().file(
            "com/timachado/brothermatrizes/core/project/ProjectCodec.kt"
        ).asFile
        target.parentFile.mkdirs()
        target.writeText(
            "package com.timachado.brothermatrizes.core.project\n\n" +
                imports + "\n\n" + codec + "\n",
            Charsets.UTF_8
        )
    }
}
kotlin.sourceSets.getByName("main").kotlin.srcDir(officialProjectDir)

/**
 * Extrai somente o modelo ImportedFont JVM puro do Android. O armazenamento
 * ImportedFontStore usa APIs Android e continua totalmente fora do Windows.
 */
val officialImportedFontFile = layout.projectDirectory.file(
    "../app/src/main/java/com/timachado/brothermatrizes/font/ImportedFontStore.kt"
)
val importedFontModelDir = layout.buildDirectory.dir("generated/official-font/kotlin")
val extractImportedFontModel by tasks.registering {
    inputs.file(officialImportedFontFile)
    outputs.dir(importedFontModelDir)
    doLast {
        val source = officialImportedFontFile.asFile.readText(Charsets.UTF_8)
        val startMarker = "data class ImportedFont("
        val endMarker = "\nobject ImportedFontStore {"
        val start = source.indexOf(startMarker)
        val end = source.indexOf(endMarker)
        check(start > 0 && end > start) {
            "Modelo de fonte Android mudou; build Desktop bloqueado."
        }
        val code = source.substring(start, end)
        check(!code.contains("Context") && !code.contains("Typeface")) {
            "Modelo de fonte passou a depender do Android."
        }
        val generated = importedFontModelDir.get().file(
            "com/timachado/brothermatrizes/font/ImportedFont.kt"
        ).asFile
        generated.parentFile.mkdirs()
        generated.writeText(
            "package com.timachado.brothermatrizes.font\n\n" + code + "\n",
            Charsets.UTF_8
        )
    }
}
kotlin.sourceSets.getByName("main").kotlin.srcDir(importedFontModelDir)

val officialSatinDir = layout.buildDirectory.dir("generated/official-satin/kotlin")
val syncOfficialSatin by tasks.registering(Sync::class) {
    from("../app/src/main/java") {
        include("com/timachado/brothermatrizes/font/CleanRoomSatinEngine.kt")
    }
    into(officialSatinDir)
}
kotlin.sourceSets.getByName("main").kotlin.srcDir(officialSatinDir)

tasks.named("compileKotlin") {
    dependsOn(syncOfficialCore, extractOfficialProjectCodec,
        extractImportedFontModel, syncOfficialSatin)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    // Mesma versão de EmbroideryIO, variante JVM apropriada ao Desktop.
    implementation("com.github.EmbroidePy.EmbroideryIO:embroideryio-jvm:0.1.23")
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks.test {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "com.timachado.brothermatrizes.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "Brother Matrizes"
            packageVersion = "0.1.0"
            description = "Brother Matrizes - prévia de homologação Windows"
            vendor = "T.I. Machado"
            copyright = "Copyright 2026 T.I. Machado"

            // jpackage NÃO cria atalhos por padrão. Sem estes controles o
            // instalador EXE/MSI aparece, mas o usuário não encontra o app.
            windows {
                menu = true
                menuGroup = "T.I. Machado"
                shortcut = true
                // Ícone oficial enviado pelo proprietário da marca.
                iconFile.set(officialWindowsIcon)
            }
        }
    }
}
