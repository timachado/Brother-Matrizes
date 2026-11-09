package com.timachado.brothermatrizes.desktop.platform

/**
 * Opt-in developer QA for the SAME official Supabase Brother Matrizes uses
 * on Android. Never create a separate project, a user directory, a Pro grant
 * or an independent quota ledger for Windows.
 *
 * The feature is disabled in release QA packages by default. Even when opted
 * in, no network request, browser login or paid import starts automatically.
 * No service-role/secret keys are accepted; all paid rights remain server-side.
 */
internal object DesktopWindowsAccountQaOptIn {
    internal fun publicKeyFromEnvironment(environment: Map<String, String>): String? {
        if (environment["BROTHER_WINDOWS_QA_GOOGLE_AUTH"] != "enabled") return null
        val key = environment["BROTHER_WINDOWS_QA_SUPABASE_PUBLISHABLE_KEY"]
            ?: return null
        return key.takeIf {
            it.startsWith("sb_publishable_") &&
                it.length in 20..4096 &&
                it.all { c -> c.code in 0x21..0x7e && c != '"' && c != '\\' }
        }
    }

    /**
     * Operator-only confirmation that the OAuth redirect configuration has
     * been manually reviewed with a dedicated QA account. This flag is NOT
     * evidence that the Supabase allowlist is active or that commercial
     * authentication has been homologated; it only allows an explicit test.
     * Real Windows releases must not set it by default.
     */
    internal fun redirectReviewedForQa(
        environment: Map<String, String> = System.getenv()
    ): Boolean =
        environment["BROTHER_WINDOWS_QA_REDIRECT_REVIEWED"] == "enabled" &&
            publicKeyFromEnvironment(environment) != null

    // Read-only configuration identity. No second Supabase project required.
    internal fun targetForQa(environment: Map<String, String>): DesktopBrotherBackendTarget? {
        if (environment["BROTHER_WINDOWS_QA_GOOGLE_AUTH"] != "enabled") return null
        return DesktopBrotherBackendTarget.official()
    }

    /**
     * One shared QA session is owned by the desktop host, not each screen.
     * Missing explicit opt-in or missing Google backend always fails closed.
     * Real reserve/finalize is only reachable by deliberate catalog clicks
     * after the user signs in and Supabase verifies the Google identity.
     */
    fun fontCatalogAccessForQa(
        qaEnvironment: DesktopWindowsGoogleQaEnvironment?,
        environment: Map<String, String> = System.getenv()
    ): DesktopFontCatalogAccess {
        val key = publicKeyFromEnvironment(environment)
            ?: return DesktopFontCatalogAccessUnavailable
        if (qaEnvironment == null) return DesktopFontCatalogAccessUnavailable
        return DesktopBrotherDaFontUsageGateway(
            publishableKey = key,
            // Verified against the source app/build.gradle.kts by a QA test.
            // Version metadata is NOT authorization or proof of parity.
            versionCode = DesktopAndroidBaseline.VERSION_CODE,
            verifiedGoogle = DesktopVerifiedGoogleTokenSource {
                qaEnvironment.verifiedAccessTokenForQa()
            }
        )
    }

    /**
     * QA-only Free V2 font import requires FOUR independent operator gates:
     * Google QA, reviewed OAuth redirect, reviewed/deployed V2 backend and
     * deliberate font import. None proves commercial entitlement or enables
     * Pro V2. Without every gate, the UI gets no real quota adapter.
     * Real import is server-authorized, never inferred from these flags.
     */
    internal fun fontImportEnabled(environment: Map<String, String>): Boolean =
        environment["BROTHER_WINDOWS_QA_FONT_IMPORT"] == "enabled" &&
            environment["BROTHER_WINDOWS_QA_FONT_V2_BACKEND_REVIEWED"] == "enabled" &&
            redirectReviewedForQa(environment)

    fun fontImportGateForQa(
        qaEnvironment: DesktopWindowsGoogleQaEnvironment?,
        environment: Map<String, String> = System.getenv()
    ): DesktopCommercialFontImportGate? {
        if (qaEnvironment == null || !fontImportEnabled(environment)) return null
        val key = checkNotNull(publicKeyFromEnvironment(environment))
        val fonts = DesktopWindowsDpapiSessionStore.defaultUserDataFolder()
            .resolve("fonts")
        return DesktopCommercialFontImportGate(
            library = DesktopImportedFontLibrary(fonts),
            verifiedSession = DesktopVerifiedFontSession {
                qaEnvironment.verifiedAccessTokenForQa()
            },
            usage = DesktopBrotherFontUsageGateway(
                publishableKey = key,
                appVersionCode = DesktopAndroidBaseline.VERSION_CODE
            ),
            journal = DesktopFontImportSafety.journalFor(fonts)
        )
    }

    /**
     * Explicitly isolated developer QA for the ORIGINAL Android archive
     * selection with server-authorized import_matrix/import_font.
     *
     * Current official production V1 does NOT support reserve_v2.
     * Enabling these flags is a human operator assertion, not proof of
     * deployment, license or safety. Normal Windows packages leave OFF.
     */
    internal fun archiveImportEnabled(
        environment: Map<String, String> = System.getenv()
    ): Boolean =
        environment["BROTHER_WINDOWS_QA_ARCHIVE_IMPORT"] == "enabled" &&
            environment["BROTHER_WINDOWS_QA_ARCHIVE_V2_BACKEND_REVIEWED"] == "enabled" &&
            redirectReviewedForQa(environment)

    /** Called only by an explicit import click, never by screen composition. */
    fun archiveImportForQa(
        qaEnvironment: DesktopWindowsGoogleQaEnvironment?,
        projectFolder: java.nio.file.Path?,
        environment: Map<String, String> = System.getenv()
    ): DesktopWindowsArchiveAuthorizedImport? {
        if (qaEnvironment == null || projectFolder == null ||
            !archiveImportEnabled(environment)) return null
        val key = publicKeyFromEnvironment(environment) ?: return null
        val root = DesktopWindowsDpapiSessionStore.defaultUserDataFolder()
        check(!java.nio.file.Files.isSymbolicLink(root) &&
            java.nio.file.Files.isDirectory(
                root, java.nio.file.LinkOption.NOFOLLOW_LINKS
            )) { "Diretório privado QA não está preparado." }
        val project = projectFolder.toAbsolutePath().normalize()
        check(!java.nio.file.Files.isSymbolicLink(project) &&
            java.nio.file.Files.isDirectory(
                project, java.nio.file.LinkOption.NOFOLLOW_LINKS
            )) { "Selecione uma Biblioteca FLP real antes de importar." }
        val fonts = root.resolve("fonts")
        val audit = root.resolve("archive-import-usage-journal")
        for (directory in listOf(fonts, audit)) {
            check(directory.parent == root &&
                !java.nio.file.Files.isSymbolicLink(directory)) {
                "Caminho de importação QA inseguro."
            }
            if (!java.nio.file.Files.exists(
                    directory, java.nio.file.LinkOption.NOFOLLOW_LINKS
                )) java.nio.file.Files.createDirectory(directory)
            check(!java.nio.file.Files.isSymbolicLink(directory) &&
                java.nio.file.Files.isDirectory(
                    directory, java.nio.file.LinkOption.NOFOLLOW_LINKS
                )) { "Diretório de importação QA inválido." }
        }
        return DesktopWindowsArchiveAuthorizedImport(
            usage = DesktopBrotherArchiveUsageGateway(
                publishableKey = key,
                versionCode = DesktopAndroidBaseline.VERSION_CODE
            ),
            session = DesktopVerifiedFontSession {
                qaEnvironment.verifiedAccessTokenForQa()
            },
            journal = DesktopArchiveQuotaJournal(audit),
            projectFolder = project,
            fontFolder = fonts
        )
    }

    /** Same official Google/PKCE controller as Minha Conta and DaFont. */
    fun telegramSearchAccessForQa(
        qaEnvironment: DesktopWindowsGoogleQaEnvironment?,
        environment: Map<String, String> = System.getenv()
    ): DesktopTelegramSearchAccess {
        val key = publicKeyFromEnvironment(environment)
            ?: return DesktopTelegramSearchUnavailable
        if (qaEnvironment == null) return DesktopTelegramSearchUnavailable
        return DesktopBrotherTelegramUsageGateway(
            publishableKey = key,
            versionCode = DesktopAndroidBaseline.VERSION_CODE,
            verifiedGoogle = DesktopVerifiedGoogleTokenSource {
                qaEnvironment.verifiedAccessTokenForQa()
            }
        )
    }

    /**
     * Diagnose an existing font import quota marker with the already
     * verified QA Google session. Construction is offline; the server status
     * query happens ONLY after the user clicks the QA review button.
     *
     * This does not depend on the separate commercial import opt-in.
     * It never creates a reservation, activates a font or clears a marker.
     */
    /**
     * Extra deployment gate: the CURRENT official production brother-usage
     * Edge Function has only legacy aggregate "status", not the per-request
     * status/status_pro_v2 contract in the staged branch. An explicit QA
     * operator must verify the new backend is deployed before opting in.
     * This flag NEVER grants commercial import or a subscription.
     */
    internal fun knownQuotaStatusV2Enabled(
        environment: Map<String, String>
    ): Boolean =
        environment["BROTHER_WINDOWS_QA_KNOWN_QUOTA_STATUS_V2"] == "enabled" &&
            publicKeyFromEnvironment(environment) != null

    fun remoteFontQuotaReviewForQa(
        qaEnvironment: DesktopWindowsGoogleQaEnvironment?,
        environment: Map<String, String> = System.getenv()
    ): DesktopWindowsRemoteFontQuotaQaReview? {
        if (!knownQuotaStatusV2Enabled(environment)) return null
        val key = publicKeyFromEnvironment(environment) ?: return null
        if (qaEnvironment == null) return null
        return DesktopWindowsRemoteFontQuotaQaReview(
            statusReader = DesktopBrotherKnownQuotaStatusReader(
                publishableKey = key,
                versionCode = DesktopAndroidBaseline.VERSION_CODE
            ),
            verifiedQaToken = { qaEnvironment.verifiedAccessTokenForQa() }
        )
    }

    fun openWhenOptedIn(): DesktopWindowsGoogleQaEnvironment? {
        val environment = System.getenv()
        val key = publicKeyFromEnvironment(environment) ?: return null
        val target = targetForQa(environment) ?: return null
        return DesktopWindowsGoogleQaEnvironment.openForQa(
            publishableKey = key,
            target = target,
            redirectReviewedForQa = redirectReviewedForQa(environment)
        )
    }
}
