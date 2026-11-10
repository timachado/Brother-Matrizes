package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopBrotherBackendTarget
import com.timachado.brothermatrizes.desktop.platform.DesktopBrotherEntitlementReader
import com.timachado.brothermatrizes.desktop.platform.DesktopBrotherFontUsageGateway
import com.timachado.brothermatrizes.desktop.platform.DesktopCommercialFontImportGate
import com.timachado.brothermatrizes.desktop.platform.DesktopFontImportSafety
import com.timachado.brothermatrizes.desktop.platform.DesktopImportedFontLibrary
import com.timachado.brothermatrizes.desktop.platform.DesktopQaSignIn
import com.timachado.brothermatrizes.desktop.platform.DesktopSessionRefresher
import com.timachado.brothermatrizes.desktop.platform.DesktopSupabaseGoogleIdentityVerifier
import com.timachado.brothermatrizes.desktop.platform.DesktopTransientAuthSession
import com.timachado.brothermatrizes.desktop.platform.DesktopVerifiedFontSession
import com.timachado.brothermatrizes.desktop.platform.DesktopWindowsAuthQaController
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Offline integration regression: one fake Google identity, read-only entitlement
 * and real Windows import gate use exactly the official Brother Supabase contract.
 * No external HTTP, real credentials, quota consumption, production data or UI grant.
 */
class DesktopWindowsSharedAccountQuotaQaTest {
    private val ref = DesktopBrotherBackendTarget.OFFICIAL_REF
    private val publishableKey = "sb_publishable_offline_only_123456789012345"
    private val access = "offline_google_access_token_1234567890123456"
    private val refresh = "offline_google_refresh_token_123456789012345"
    private val userId = "11111111-2222-4333-8444-555555555555"
    private val email = "shared-qa@example.invalid"

    private class FakeEndpoint(
        url: URL,
        private val reply: (String) -> String
    ) : HttpURLConnection(url) {
        private val request = ByteArrayOutputStream()
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode(): Int = HTTP_OK
        override fun getContentType(): String = "application/json"
        override fun getOutputStream(): OutputStream = request
        override fun getInputStream(): InputStream =
            ByteArrayInputStream(
                reply(request.toString(StandardCharsets.UTF_8))
                    .toByteArray(StandardCharsets.UTF_8)
            )
    }

    private fun fontFixture(): Path {
        val fonts = Path.of(System.getenv("WINDIR") ?: "C:\\Windows", "Fonts")
        return listOf(fonts.resolve("arial.ttf"), fonts.resolve("segoeui.ttf"))
            .firstOrNull { Files.isRegularFile(it) }
            ?: error("Fonte de referência TTF não encontrada no runner Windows.")
    }

    @Test
    fun sameVerifiedGoogleSessionUsesOfficialEntitlementAndSingleImportLedger() = runBlocking {
        assumeTrue(System.getProperty("os.name").startsWith("Windows", true))
        val folder = Files.createTempDirectory("brother-shared-quota-qa-")
        val calls = mutableListOf<String>()
        val finalized = mutableListOf<Boolean>()
        val reservedIds = mutableListOf<String>()
        val preflightRequests = mutableListOf<String>()
        var latestRequestKey: String? = null
        val fakeConnections: (URL) -> HttpURLConnection = { url ->
            assertEquals("$ref.supabase.co", url.host)
            assertEquals("https", url.protocol)
            FakeEndpoint(url) { request ->
                calls += url.path
                when (url.path) {
                    "/auth/v1/user" -> """
                        {"id":"$userId","email":"$email",
                        "app_metadata":{"provider":"google"}}
                    """.trimIndent()
                    "/functions/v1/brother-entitlement" -> """
                        {"authenticated":true,"user":{"id":"$userId","email":"$email"},
                        "planCode":"free","pro":false,"status":"active"}
                    """.trimIndent()
                    "/functions/v1/brother-usage" -> {
                        when {
                            request.contains("\"action\":\"preflight_v2\"") &&
                                request.contains("\"operation\":\"import_font\"") -> {
                                // Read-only check: no UUID, reservation or quota.
                                preflightRequests += request
                                """{"operation":"import_font","protocol":"free_v2","ready":true}"""
                            }
                            request.contains("\"action\":\"reserve_v2\"") &&
                                request.contains("\"operation\":\"import_font\"") -> {
                                // Mirror the staged Free V2 server contract
                                // with an exact idempotent UUID from the
                                // journal. No real server or quota is touched.
                                val id = Regex(
                                    """"client_request_id":"([0-9a-f-]{36})""""
                                ).find(request)?.groupValues?.get(1)
                                    ?: error("Free V2 missing persisted UUID")
                                check(Regex(
                                    "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
                                ).matches(id)) {
                                    "Request ID must be UUIDv4"
                                }
                                check(id !in reservedIds) {
                                    "Duplicate Free V2 reservation from a new local import"
                                }
                                reservedIds += id
                                latestRequestKey = "usage_$id"
                                """{"allowed":true,"operation":"import_font","request_key":"usage_$id","state":"reserved","replayed":false}"""
                            }
                            request.contains("\"action\":\"finalize\"") &&
                                request.contains("\"request_key\":\"${latestRequestKey}\"") -> {
                                val success = when {
                                    request.contains("\"success\":true") -> true
                                    request.contains("\"success\":false") -> false
                                    else -> error("Finalização inválida.")
                                }
                                finalized += success
                                """{"ok":true}"""
                            }
                            else -> error("Operação de quota desconhecida.")
                        }
                    }
                    else -> error("Endpoint de produção fora da allowlist de QA.")
                }
            }
        }

        try {
            DesktopWindowsAuthQaController.open(
                signIn = DesktopQaSignIn {
                    DesktopTransientAuthSession(access, refresh, 3600)
                },
                refresher = DesktopSessionRefresher {
                    error("QA não usa refresh nem chama servidor real.")
                },
                folder = folder
            ).use { session ->
                val identityVerifier = DesktopSupabaseGoogleIdentityVerifier(
                    publishableKey, fakeConnections
                )
                val entitlement = DesktopBrotherEntitlementReader(
                    publishableKey, fakeConnections
                )
                assertEquals(userId, session.loginAndVerifyGoogleForQa(identityVerifier).userId)
                val account = session.accountPreviewForQa(identityVerifier, entitlement)
                assertFalse(account.hasServerPro)
                assertEquals("free", account.planCode)

                // A mesma sessão Google verificada é exigida para cada reserva.
                // Este gate fica só no teste: NÃO conectar à UI comercial.
                val verified = DesktopVerifiedFontSession {
                    session.verifiedAccessTokenForQa(identityVerifier)
                }
                val gateway = DesktopBrotherFontUsageGateway(
                    publishableKey, 196, fakeConnections
                )
                val library = DesktopImportedFontLibrary(folder.resolve("fonts"))
                val gate = DesktopCommercialFontImportGate(
                    library, verified, gateway,
                    DesktopFontImportSafety.journalFor(folder.resolve("fonts"))
                )
                val font = fontFixture()
                val first = gate.importFont(font)
                val duplicate = gate.importFont(font)
                assertEquals(first.id, duplicate.id)
                assertEquals(1, library.list().size)
                assertEquals(listOf(true, false), finalized)
                assertEquals(2, reservedIds.size)
                assertEquals(2, reservedIds.distinct().size)
                assertEquals(2, preflightRequests.size)
                assertTrue(preflightRequests.all { !it.contains("client_request_id") })
                assertEquals(1, calls.count {
                    it == "/functions/v1/brother-entitlement"
                })
                assertEquals(6, calls.count {
                    it == "/functions/v1/brother-usage"
                })
                session.logout()
                assertFailsWith<IllegalStateException> {
                    gate.importFont(font)
                }
                assertEquals(listOf(true, false), finalized)
                assertFalse(Files.exists(folder.resolve("session.v1.dpapi")))
            }
        } finally {
            Files.walk(folder).use { paths ->
                paths.sorted(Comparator.reverseOrder())
                    .forEach { Files.deleteIfExists(it) }
            }
        }
        assertTrue(calls.isNotEmpty())
    }
}
