package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Comparator
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Browser fake -> actual loopback socket -> PKCE -> mocked token -> mocked
 * Supabase getUser -> native Windows DPAPI -> mocked entitlement -> logout.
 * No real Google, Supabase, payments, quotas or license activation.
 */
class DesktopWindowsFullGoogleQaFlowTest {
    private val key = "sb_publishable_offline_test_key_1234567890"
    private val access = "offline_full_flow_access_123456789012345"
    private val refresh = "offline_full_flow_refresh_123456789012345"
    private val uid = "11111111-2222-4333-8444-555555555555"
    private val email = "qa-flow@example.invalid"
    private val officialRef = DesktopBrotherBackendTarget.OFFICIAL_REF

    private class HttpFake(url: URL, private val reply: String) : HttpURLConnection(url) {
        val postBody = ByteArrayOutputStream()
        var disconnected = false
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getResponseCode() = 200
        override fun getContentType() = "application/json"
        override fun getInputStream(): InputStream =
            ByteArrayInputStream(reply.toByteArray(StandardCharsets.UTF_8))
        override fun getOutputStream(): OutputStream = postBody
    }

    private fun windowsTemp(block: suspend (Path) -> Unit) = runBlocking {
        assumeTrue(System.getProperty("os.name").startsWith("Windows", true))
        val folder = Files.createTempDirectory("bm-full-oauth-qa-")
        try { block(folder) }
        finally {
            Files.walk(folder).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    private fun browser(
        params: String,
        workers: MutableList<Thread>,
        errors: MutableList<Throwable>,
        expectedHost: String = "dwpcddiramxlhavdmmyn.supabase.co"
    ): DesktopExternalBrowser = DesktopExternalBrowser { url ->
        assertEquals("https", url.scheme)
        assertEquals(expectedHost, url.host)
        assertEquals("/auth/v1/authorize", url.path)
        val query = url.rawQuery.split("&").associate {
            val parts = it.split("=", limit = 2)
            parts[0] to URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
        }
        assertEquals("google", query["provider"])
        assertEquals("s256", query["code_challenge_method"])
        val redirect = URI(requireNotNull(query["redirect_to"]))
        assertEquals("127.0.0.1", redirect.host)
        workers += thread(isDaemon = true, name = "qa-only-oauth-callback") {
            try {
                Socket("127.0.0.1", redirect.port).use { socket ->
                    socket.soTimeout = 8_000
                    val target = redirect.rawPath + "?" + redirect.rawQuery + "&" + params
                    val http = "GET " + target + " HTTP/1.1\r\n" +
                        "Host: 127.0.0.1:" + redirect.port +
                        "\r\nConnection: close\r\n\r\n"
                    socket.getOutputStream().write(
                        http.toByteArray(StandardCharsets.US_ASCII)
                    )
                    val response = socket.getInputStream()
                        .readBytes().toString(StandardCharsets.UTF_8)
                    assertTrue(response.startsWith("HTTP/1.1 200 OK"))
                    assertFalse(response.contains(access))
                    assertFalse(response.contains(refresh))
                    assertFalse(response.contains("bm_state="))
                }
            } catch (t: Throwable) {
                synchronized(errors) { errors.add(t) }
            }
        }
    }

    private fun joinWorkers(workers: List<Thread>, errors: List<Throwable>) {
        workers.forEach { it.join(8000) }
        assertTrue(workers.all { !it.isAlive })
        synchronized(errors) {
            assertTrue(errors.isEmpty(), errors.firstOrNull()?.message.orEmpty())
        }
    }

    @Test
    fun completesEntireLoginAndReadOnlyEntitlementWithoutExternalNetwork() =
        windowsTemp { folder ->
            val workers = mutableListOf<Thread>()
            val errors = mutableListOf<Throwable>()
            val tokenHttp = HttpFake(
                URL("https://dwpcddiramxlhavdmmyn.supabase.co/auth/v1/token?grant_type=pkce"),
                """{"access_token":"$access","refresh_token":"$refresh","expires_in":3600,"token_type":"bearer"}"""
            )
            var exchanges = 0
            val auth = DesktopSupabaseGooglePkceClient(key) { url ->
                assertEquals("/auth/v1/token", url.path)
                assertEquals("grant_type=pkce", url.query)
                exchanges++
                tokenHttp
            }
            val coordinator = DesktopGooglePkceLoginCoordinator(
                auth, browser("code=synthetic_pkce_code", workers, errors),
                Duration.ofSeconds(15)
            )
            val verifier = DesktopSupabaseGoogleIdentityVerifier(key) { url ->
                assertEquals("/auth/v1/user", url.path)
                HttpFake(url, """{"id":"$uid","email":"$email","app_metadata":{"provider":"google"}}""")
            }
            val reader = DesktopBrotherEntitlementReader(key) { url ->
                assertEquals("/functions/v1/brother-entitlement", url.path)
                HttpFake(url,
                    """{"authenticated":true,"user":{"id":"$uid","email":"$email"},"planCode":"pro_lifetime","pro":true,"status":"active"}""")
            }
            DesktopWindowsAuthQaController.open(
                DesktopWindowsAuthQaController.googleSignInSource(coordinator),
                DesktopSessionRefresher { error("Unexpected token refresh") },
                folder = folder
            ).use { controller ->
                assertFalse(controller.recover())
                val identity = controller.loginAndVerifyGoogleForQa(verifier)
                assertEquals(uid, identity.userId)
                assertEquals(email, identity.email)
                assertEquals(1, exchanges)
                assertTrue(tokenHttp.disconnected)
                assertEquals(access, controller.accessTokenForQa())
                val preview = controller.accountPreviewForQa(verifier, reader)
                assertTrue(preview.hasServerPro)
                assertEquals("pro_lifetime", preview.planCode)
                assertFalse(preview.toString().contains(access))
                val encrypted = Files.readAllBytes(folder.resolve("session.v1.dpapi"))
                    .toString(StandardCharsets.ISO_8859_1)
                assertFalse(encrypted.contains(access))
                assertFalse(encrypted.contains(refresh))
                controller.logout()
                assertFalse(Files.exists(folder.resolve("session.v1.dpapi")))
                assertFailsWith<IllegalStateException> { controller.accessTokenForQa() }
            }
            joinWorkers(workers, errors)
        }

    @Test
    fun qaFactoryOpensOfflineRestoresGoogleAndNeverAutoLaunchesBrowser() =
        windowsTemp { folder ->
            val workers = mutableListOf<Thread>()
            val errors = mutableListOf<Throwable>()
            val requests = mutableListOf<String>()
            val connector: (URL) -> HttpURLConnection = { url ->
                assertEquals("$officialRef.supabase.co", url.host)
                requests += url.path
                when (url.path) {
                    "/auth/v1/token" -> HttpFake(
                        url,
                        """{"access_token":"$access","refresh_token":"$refresh","expires_in":3600,"token_type":"bearer"}"""
                    )
                    "/auth/v1/user" -> HttpFake(
                        url, """{"id":"$uid","email":"$email","app_metadata":{"provider":"google"}}"""
                    )
                    "/functions/v1/brother-entitlement" -> HttpFake(
                        url,
                        """{"authenticated":true,"user":{"id":"$uid","email":"$email"},"planCode":"free","pro":false,"status":"active"}"""
                    )
                    else -> error("Unexpected network request in offline QA")
                }
            }
            DesktopWindowsGoogleQaEnvironment.openForQa(
                publishableKey = key,
                target = DesktopBrotherBackendTarget.official(),
                folder = folder,
                browser = browser(
                    "code=offline_factory_auth_code", workers, errors,
                    expectedHost = "$officialRef.supabase.co"
                ),
                connectionFactory = connector
            ).use { first ->
                assertTrue(requests.isEmpty()) // opt-in, never autologin
                assertFalse(first.restoreAndVerify())
                assertEquals(uid, first.signIn().userId)
                assertEquals("free", first.accountPreview().planCode)
                assertFalse(first.accountPreview().hasServerPro)
                assertTrue(Files.exists(folder.resolve("qa-brother-account-$officialRef")
                    .resolve("session.v1.dpapi")))
                assertFalse(Files.exists(folder.resolve("session.v1.dpapi")))
            }
            DesktopWindowsGoogleQaEnvironment.openForQa(
                publishableKey = key,
                target = DesktopBrotherBackendTarget.official(),
                folder = folder,
                browser = DesktopExternalBrowser {
                    error("Restoring DPAPI must not launch a new browser")
                },
                connectionFactory = connector
            ).use { restarted ->
                val before = requests.count { it == "/auth/v1/token" }
                assertTrue(restarted.restoreAndVerify())
                assertEquals(before, requests.count { it == "/auth/v1/token" })
                assertEquals("free", restarted.accountPreview().planCode)
                restarted.logout()
                assertFalse(Files.exists(folder.resolve("qa-brother-account-$officialRef")
                    .resolve("session.v1.dpapi")))
                assertFalse(restarted.restoreAndVerify())
            }
            joinWorkers(workers, errors)
        }

    @Test
    fun denialCannotExchangeCodeOrPersistTokens() = windowsTemp { folder ->
        val workers = mutableListOf<Thread>()
        val errors = mutableListOf<Throwable>()
        var requestedTokens = 0
        val auth = DesktopSupabaseGooglePkceClient(key) {
            requestedTokens++
            error("No token request on denial")
        }
        val coordinator = DesktopGooglePkceLoginCoordinator(
            auth, browser(
                "error=access_denied&error_description=private_information",
                workers, errors
            ),
            Duration.ofSeconds(12)
        )
        val verifier = DesktopSupabaseGoogleIdentityVerifier(key) {
            error("No verified token on denial")
        }
        DesktopWindowsAuthQaController.open(
            DesktopWindowsAuthQaController.googleSignInSource(coordinator),
            DesktopSessionRefresher { error("No existing session") },
            folder = folder
        ).use { controller ->
            assertFailsWith<DesktopOAuthLoginRejected> {
                controller.loginAndVerifyGoogleForQa(verifier)
            }
            assertEquals(0, requestedTokens)
            assertFalse(Files.exists(folder.resolve("session.v1.dpapi")))
            assertFailsWith<IllegalStateException> { controller.accessTokenForQa() }
        }
        joinWorkers(workers, errors)
    }
}
