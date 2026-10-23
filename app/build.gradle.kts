import java.util.Base64
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.image.ConvolveOp
import java.awt.image.Kernel
import java.awt.image.RescaleOp

plugins {
    id("com.android.application")
    id("com.google.gms.google-services") apply false
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val groupVisionQa = providers.gradleProperty("brotherGroupQaParallel").orNull == "true"
if (!groupVisionQa) apply(plugin = "com.google.gms.google-services")

android {
    namespace = "com.timachado.brothermatrizes"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.timachado.brothermatrizes"
        minSdk = 26
        targetSdk = 36
        versionCode = 188
        versionName = "1.11.1"

        // TDLib requires application-owned Telegram API credentials and a
        // separate Telegram sign-in; omitted in commercial builds until QA.
        // Credentials may only enter an isolated debug .groupqa build.
        // The production package continues to ship with Telegram login disabled.
        val suppliedTelegramId = providers.gradleProperty("brotherTelegramApiId")
            .orNull.orEmpty()
        val suppliedTelegramHash = providers.gradleProperty("brotherTelegramApiHash")
            .orNull.orEmpty()
        val validTelegramPair = suppliedTelegramId.toIntOrNull()?.let { it > 0 } == true &&
            suppliedTelegramHash.matches(Regex("[0-9a-fA-F]{32}"))
        if (groupVisionQa && (suppliedTelegramId.isNotBlank() ||
                suppliedTelegramHash.isNotBlank())) {
            require(validTelegramPair) {
                "Telegram QA requires a valid api_id and 32-hex api_hash pair."
            }
        }
        val telegramId = if (groupVisionQa && validTelegramPair) {
            suppliedTelegramId.toInt()
        } else 0
        val telegramHash = if (groupVisionQa && validTelegramPair) {
            suppliedTelegramHash
        } else ""
        buildConfigField("int", "BROTHER_TELEGRAM_API_ID", telegramId.toString())
        buildConfigField("String", "BROTHER_TELEGRAM_API_HASH", "\"$telegramHash\"")
        buildConfigField("boolean", "BROTHER_GROUP_VISION_QA", "false")

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
        // Off in commercial APK until WordPress plugin and Supabase exchange are live.
        // Enable only for a reviewed QA build with -PbrotherCheckoutHandoff=true.
        buildConfigField("boolean", "ENABLE_CHECKOUT_HANDOFF",
            (providers.gradleProperty("brotherCheckoutHandoff").orNull == "true").toString())
        // Local beta preview quota for imports only. No purchase or Pro unlock.
        // Disable and migrate quotas before launching commercial sales.
        buildConfigField("boolean", "BETA_LOCAL_IMPORTS", "true")
        // Public WordPress store URL only. No WooCommerce or Efí secrets.
        buildConfigField(
            "String", "WORDPRESS_URL", "\"https://timachado.ifree.page\""
        )
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            if (groupVisionQa) {
                applicationIdSuffix = ".groupqa"
                versionNameSuffix = "-GROUP-QA"
                buildConfigField("boolean", "BROTHER_GROUP_VISION_QA", "true")
            }
            // QA paralela: não substitui nem desinstala a versão comercial.
            // Só ativar com -PbrotherCheckoutQaParallel=true.
            if (!groupVisionQa && providers.gradleProperty("brotherPremiumQaV2").orNull == "true") {
                // Certificados debug de runners GitHub podem mudar. Um novo ID
                // evita conflito com o APK QA anterior e preserva seus dados.
                applicationIdSuffix = ".qa2"
                versionNameSuffix = "-QA2"
            } else if (!groupVisionQa && providers.gradleProperty("brotherCheckoutQaParallel").orNull == "true") {
                applicationIdSuffix = ".qa"
                versionNameSuffix = "-QA"
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

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.04.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")

    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    // Native Telegram client for scoped, consented access to the user's groups.
    implementation("io.github.tdlib-android:core:0.1.1")
    implementation("io.github.tdlib-android:ktx:0.1.1")

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
 * A Home e o launcher premium utilizam o simbolo BM aprovado em
 * R.drawable.ic_launcher_brother_premium e o adaptive-icon Android.
 * O arquivo antigo de marca em branding permanece arquivado para rollback,
 * mas nao e mais gerado nem empacotado automaticamente no APK premium.
 * Mantemos o nome da tarefa por compatibilidade com os builds existentes.
 */
val prepareBrotherBrandAssets = tasks.register("prepareBrotherBrandAssets") {
    val heroSource = rootProject.file("branding/brother_home_hero.base64")
    val heroDrawable = project.file("src/main/res/drawable-nodpi/brother_home_hero.jpg")
    val approvedIconSource = rootProject.file("branding/brother_bm_premium_approved.webp.base64")
    val approvedIconDrawable = project.file("src/main/res/drawable-nodpi/brother_bm_premium_approved.webp")
    val safeLauncherSource = rootProject.file("branding/brother_bm_launcher_safe.webp.base64")
    val safeLauncherDrawable = project.file("src/main/res/drawable-nodpi/brother_bm_launcher_safe.webp")
    inputs.files(heroSource, approvedIconSource, safeLauncherSource)
    outputs.files(heroDrawable, approvedIconDrawable, safeLauncherDrawable)
    // A remoção dos JPGs legados também ocorre quando o hero já existe no cache.
    outputs.upToDateWhen { false }
    doLast {
        // Remover somente os dois arquivos antigos gerados por builds anteriores.
        // Nunca apagar desenhos, projetos ou qualquer arquivo externo ao app.
        project.file("src/main/res/drawable-nodpi/brother_matrizes_brand_logo.jpg")
            .delete()
        project.file("src/main/res/mipmap-xxxhdpi/ic_launcher_brother_matrizes.jpg")
            .delete()

        // Recurso da arte BM metalizada aprovada. WEBP é suportado no Android
        // minSdk 26; gravamos os bytes sem requantizar ou reimaginar o mockup.
        val premiumData = Base64.getMimeDecoder().decode(approvedIconSource.readText())
        check(premiumData.size >= 4000 &&
            String(premiumData.copyOfRange(0, 4), Charsets.US_ASCII) == "RIFF" &&
            String(premiumData.copyOfRange(8, 12), Charsets.US_ASCII) == "WEBP") {
            "Icone metalizado premium ausente ou invalido"
        }
        approvedIconDrawable.parentFile.mkdirs()
        approvedIconDrawable.writeBytes(premiumData)

        // O foreground adaptativo requer margem incorporada à imagem:
        // somente Insets em XML não impedem que alguns launchers ampliem o símbolo.
        val safeData = Base64.getMimeDecoder().decode(safeLauncherSource.readText())
        check(safeData.size >= 1800 &&
            String(safeData.copyOfRange(0, 4), Charsets.US_ASCII) == "RIFF" &&
            String(safeData.copyOfRange(8, 12), Charsets.US_ASCII) == "WEBP") {
            "Icone BM com margem de seguranca ausente ou invalido"
        }
        safeLauncherDrawable.parentFile.mkdirs()
        safeLauncherDrawable.writeBytes(safeData)

        val heroData = Base64.getMimeDecoder().decode(heroSource.readText())
        check(heroData.size >= 2000 &&
            heroData[0] == 0xff.toByte() && heroData[1] == 0xd8.toByte()) {
            "Fotografia do hero Brother Matrizes ausente ou invalida"
        }
        val originalHero = javax.imageio.ImageIO.read(heroData.inputStream())
        check(originalHero != null && originalHero.width >= 150 && originalHero.height >= 100) {
            "Imagem original do hero invalida"
        }
        val cropX = (originalHero.width * 0.35f).toInt()
        val heroSemTexto = originalHero.getSubimage(
            cropX, 0, originalHero.width - cropX, originalHero.height
        )
        // Melhorar a apresentação do recorte aprovado sem alterar o conteúdo:
        // upscale bicúbico, contraste suave e nitidez localizada. A fonte é
        // a mesma fotografia; não substituímos por foto genérica.
        val width = heroSemTexto.width * 3
        val height = heroSemTexto.height * 3
        val expanded = BufferedImage(
            width, height, BufferedImage.TYPE_INT_RGB
        )
        val graphics = expanded.createGraphics()
        try {
            graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC
            )
            graphics.drawImage(heroSemTexto, 0, 0, width, height, null)
        } finally {
            graphics.dispose()
        }
        val contrast = RescaleOp(1.07f, 3f, null)
        val balanced = contrast.filter(expanded, null)
        val sharpen = ConvolveOp(
            Kernel(3, 3, floatArrayOf(
                0f, -0.22f, 0f,
                -0.22f, 1.88f, -0.22f,
                0f, -0.22f, 0f
            )), ConvolveOp.EDGE_NO_OP, null
        )
        val enhanced = sharpen.filter(balanced, null)
        heroDrawable.parentFile.mkdirs()
        val writer = javax.imageio.ImageIO.getImageWritersByFormatName("jpeg").next()
        javax.imageio.ImageIO.createImageOutputStream(heroDrawable).use { stream ->
            try {
                writer.output = stream
                val params = writer.defaultWriteParam
                params.compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT
                params.compressionQuality = 0.92f
                writer.write(null, javax.imageio.IIOImage(enhanced, null, null), params)
            } finally {
                writer.dispose()
            }
        }
        check(heroDrawable.length() >= 2_000) {
            "Falha ao preparar fotografia premium do bordado"
        }
    }
}
tasks.configureEach {
    if (name == "preBuild" || 
        (name.startsWith("merge") && name.endsWith("Resources")) ||
        (name.startsWith("process") && name.endsWith("Resources"))) {
        dependsOn(prepareBrotherBrandAssets)
    }
}
