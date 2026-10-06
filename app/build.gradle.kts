plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.timachado.brothermatrizes"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.timachado.brothermatrizes"
        minSdk = 26
        targetSdk = 36
        versionCode = 111
        versionName = "0.46.51"

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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
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

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.04.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")

    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    /*
     * Skia nativo para o caminho TTF/OTF compatível com o MãoDesign.
     * A referência usa Skia para glyph path/metrics; Android Paint produz
     * contornos diferentes em fontes cursivas e muda o SatinColumnSampler.
     */
    implementation("io.github.humbleui:skija-android-arm64:0.143.17")
    implementation("io.github.humbleui:skija-android-x64:0.143.17")

    implementation("com.github.EmbroidePy.EmbroideryIO:embroideryio-android:0.1.23")

    implementation(platform("io.github.jan-tennert.supabase:bom:3.2.3"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.ktor:ktor-client-android:3.2.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("com.posthog:posthog-android:3.71.4")

    testImplementation("junit:junit:4.13.2")
}
