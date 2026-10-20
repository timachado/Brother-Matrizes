import java.util.Base64

plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Only a dedicated QA keystore can sign a distributable QA APK.
// CI unit/smoke tests may compile with Gradle's ephemeral debug key,
// but those APKs must NOT be published as if they were update-compatible.
val qaSigningFile = providers.environmentVariable(
    "BROTHER_QA_STORE_FILE"
).orNull?.takeIf { it.isNotBlank() }

android {
    namespace = "com.timachado.brothermatrizes"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.timachado.brothermatrizes"
        minSdk = 26
        targetSdk = 36
        versionCode = 184
        versionName = "1.10.8"

        // Acesso ao token FCM apenas no APK de homologação explicitamente construído
        // com -PbrotherFcmDiagnostic=true. Na versão pública o recurso é desativado.
        buildConfigField(
            "boolean", "ENABLE_FCM_DIAGNOSTIC",
            (providers.gradleProperty("brotherFcmDiagnostic").orNull == "true").toString()
        )

        buildConfigField(
            "String",
            "SUPABASE_URL",
            "\"https://dwpcddiramxlhavdmmyn.supabase.co\""
        )
        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            "\"sb_publishable_K7JQ8O5fNgZjhEGSQsoZdQ_W4TLRHY4\""
        )
        // Local beta preview quota for imports only. No purchase or Pro unlock.
        // Disable and migrate quotas before launching commercial sales.
        buildConfigField("boolean", "BETA_LOCAL_IMPORTS", "true")
        // Public WordPress store URL only. No WooCommerce or Efí secrets.
        buildConfigField(
            "String", "WORDPRESS_URL", "\"https://timachado.ifree.page\""
        )
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (qaSigningFile != null) {
            create("qaStable") {
                storeFile = file(qaSigningFile)
                storePassword = providers.environmentVariable(
                    "BROTHER_QA_STORE_PASSWORD"
                ).orNull ?: error("QA keystore password not configured")
                keyAlias = providers.environmentVariable(
                    "BROTHER_QA_KEY_ALIAS"
                ).orNull ?: error("QA key alias not configured")
                keyPassword = providers.environmentVariable(
                    "BROTHER_QA_KEY_PASSWORD"
                ).orNull ?: error("QA key password not configured")
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        debug {
            // QA installs alongside the commercial release, without replacing it.
            // No production signing key is used. This suffix applies to debug ONLY.
            // Permanent identity across all future QA versions.
            // Keep Firebase debug client's package name identical.
            applicationIdSuffix = ".qaphysicalgate"
            versionNameSuffix = "-qa"
            if (qaSigningFile != null) {
                signingConfig = signingConfigs.getByName("qaStable")
            }
            /*
             * Project token do PostHog é um identificador público de cliente,
             * usado somente na build de diagnóstico. Nenhuma chave pessoal/API
             * privada é incluída no app.
             */
            buildConfigField(
                "String",
                "POSTHOG_PROJECT_TOKEN",
                "\"phc_CSmVhfjhoqBHjLtFB2nwNSdGgfss7RtTzx8xXgdrAoSu\""
            )
        }

        release {
            // Commercial release must NEVER use the resettable per-device beta counters.
            buildConfigField("boolean", "BETA_LOCAL_IMPORTS", "false")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Debug-only monotonic CI versionCode. Release stays at its original
// commercial versionCode=184; this prevents Android downgrade errors on
// subsequent QA updates and never alters release signing or product code.
androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        val ciRun = providers.environmentVariable("GITHUB_RUN_NUMBER")
            .orNull?.toIntOrNull()
        if (ciRun != null && ciRun in 1..1_000_000) {
            variant.outputs.forEach { output ->
                output.versionCode.set(100_000 + ciRun)
            }
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.04.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")

    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("com.github.EmbroidePy.EmbroideryIO:embroideryio-android:0.1.23")

    // Archive readers only. RAR license restricts writing a compatible archiver.
    implementation("com.github.junrar:junrar:8.1.1")
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("org.tukaani:xz:1.10")
    implementation("commons-io:commons-io:2.20.0")
    implementation("org.apache.commons:commons-lang3:3.18.0")

    implementation(platform("io.github.jan-tennert.supabase:bom:3.2.3"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.ktor:ktor-client-android:3.2.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("com.posthog:posthog-android:3.71.4")

    testImplementation("junit:junit:4.13.2")
}


/**
 * A arte oficial fica em branding/brother_matrizes_brand_logo.base64.
 * Gera sempre os recursos de ícone antes do processamento Android.
 * Evita empacotar recursos de marca obsoletos por acidente.
 */
val prepareBrotherBrandAssets = tasks.register("prepareBrotherBrandAssets") {
    val source = rootProject.file("branding/brother_matrizes_brand_logo.base64")
    val drawable = project.file("src/main/res/drawable-nodpi/brother_matrizes_brand_logo.jpg")
    val mipmap = project.file("src/main/res/mipmap-xxxhdpi/ic_launcher_brother_matrizes.jpg")
    inputs.file(source)
    outputs.files(drawable, mipmap)
    doLast {
        val data = Base64.getMimeDecoder().decode(source.readText())
        check(data.size >= 3000 && data[0] == 0xff.toByte() && data[1] == 0xd8.toByte()) {
            "Imagem oficial Brother Matrizes ausente ou invalida"
        }
        drawable.parentFile.mkdirs()
        mipmap.parentFile.mkdirs()
        drawable.writeBytes(data)
        mipmap.writeBytes(data)
    }
}
tasks.configureEach {
    if (name == "preBuild" || 
        (name.startsWith("merge") && name.endsWith("Resources")) ||
        (name.startsWith("process") && name.endsWith("Resources"))) {
        dependsOn(prepareBrotherBrandAssets)
    }
}
