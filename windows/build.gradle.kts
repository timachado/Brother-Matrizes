import org.gradle.api.tasks.Sync
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.2.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20"
    id("org.jetbrains.compose") version "1.9.3"
}

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
            include("com/timachado/brothermatrizes/core/embroidery/EmbroideryIntegrity.kt")
            include("com/timachado/brothermatrizes/core/embroidery/HoopLayerPositioning.kt")
            include("com/timachado/brothermatrizes/core/embroidery/NamePlacementSafety.kt")
            include("com/timachado/brothermatrizes/core/embroidery/EmbroideryModels.kt")
            include("com/timachado/brothermatrizes/core/embroidery/EmbroideryProfiles.kt")
            include("com/timachado/brothermatrizes/core/embroidery/EmbroideryStats.kt")
            include("com/timachado/brothermatrizes/core/embroidery/EmbroideryStressPolicy.kt")
            include("com/timachado/brothermatrizes/core/embroidery/MachineFinishing.kt")
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
tasks.named("compileKotlin") {
    dependsOn(syncOfficialCore)
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
            packageName = "BrotherMatrizesDesktop"
            packageVersion = "0.1.0"
            description = "Brother Matrizes - prévia de homologação Windows"
            vendor = "T.I. Machado"
            copyright = "Copyright 2026 T.I. Machado"
        }
    }
}
