package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveCommercialImportGate
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveImportFactory
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveInventory
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveQuotaJournal
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveQuotaStatus
import com.timachado.brothermatrizes.desktop.platform.DesktopBrotherProV2QaGateway
import com.timachado.brothermatrizes.desktop.platform.DesktopVerifiedFontSession
import com.timachado.brothermatrizes.desktop.platform.DesktopWindowsStandaloneMatrixAuthorizedImport
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.util.Comparator
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * All replies and credentials are fake; NO production Supabase, Google,
 * entitlement, customers, Pro grant or payment. In particular, even the
 * production project reference cannot be passed into this QA adapter.
 */
class DesktopBrotherProV2QaGatewayTest {
    private val qaRef = "abcdefghijklmnopqrst"
    private val key = "sb_publishable_synthetic_offline_only_1234567890"
    private val token = "synthetic_verified_google_qa_token_1234567890"
    private val uuid = "12345678-1234-4234-9234-123456789abc"
    private val receipt = "pro_" + uuid

    private class FakeHttp(
        url: URL,
        private val status: Int = 200,
        private val mime: String = "application/json",
        private val body: String = ""
    ) : HttpURLConnection(url) {
        val sent = ByteArrayOutputStream()
        var closed = false
        override fun connect() = Unit
        override fun disconnect() { closed = true }
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = status
        override fun getContentType(): String = mime
        override fun getOutputStream(): OutputStream = sent
        override fun getInputStream(): InputStream =
            ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
        fun payload(): JsonObject = Json.parseToJsonElement(
            sent.toString(Charsets.UTF_8)
        ).jsonObject
    }

    private fun client(fake: FakeHttp) = DesktopBrotherProV2QaGateway(
        publishableKey = key,
        versionCode = 196,
        stagingProjectRef = qaRef,
        connectionFactory = { url ->
            assertEquals(
                "https://" + qaRef + ".supabase.co/functions/v1/brother-usage",
                url.toString()
            )
            fake
        }
    )

    @Test
    fun proV2PreflightRequiresExactServerApprovalAndNeverCreatesReceipt() =
        runBlocking {
            val endpoint = URL(
                "https://" + qaRef + ".supabase.co/functions/v1/brother-usage"
            )
            for (operation in listOf("import_font", "import_matrix")) {
                val accepted = FakeHttp(endpoint,
                    body = """{"operation":"$operation","protocol":"pro_v2","ready":true}""")
                client(accepted).ensureV2Ready(token, operation)
                assertEquals("preflight_v2",
                    accepted.payload()["action"]?.jsonPrimitive?.content)
                assertEquals(operation,
                    accepted.payload()["operation"]?.jsonPrimitive?.content)
                assertEquals(2, accepted.payload().keys.size)
                assertFalse(accepted.payload().containsKey("client_request_id"))
                assertTrue(accepted.closed)
            }
            val denied = listOf(
                """{"operation":"import_matrix","protocol":"pro_v2","ready":false}""",
                """{"operation":"import_matrix","protocol":"pro_v2","ready":"true"}""",
                """{"operation":"import_matrix","protocol":"free_v2","ready":true}""",
                """{"operation":"import_font","protocol":"pro_v2","ready":true}"""
            )
            for (reply in denied) {
                val fake = FakeHttp(endpoint, body = reply)
                assertFailsWith<IllegalStateException> {
                    client(fake).ensureV2Ready(token, "import_matrix")
                }
                assertTrue(fake.closed)
            }
            val oldBackend = FakeHttp(endpoint, status = 400,
                body = """{"message":"unsupported action"}""")
            assertFailsWith<IllegalStateException> {
                client(oldBackend).ensureV2Ready(token, "import_matrix")
            }
            assertTrue(oldBackend.closed)
            var attempts = 0
            val gateway = DesktopBrotherProV2QaGateway(key, 196, qaRef) {
                attempts++
                error("Nenhum acesso de rede permitido para operação inválida.")
            }
            assertFailsWith<IllegalArgumentException> {
                gateway.ensureV2Ready(token, "grant_pro")
            }
            assertEquals(0, attempts)
        }

    @Test
    fun deniedProV2PreflightInStandaloneImportKeepsJournalAndLibraryEmpty() =
        runBlocking {
            // Full Windows gate with an explicitly staging-only fake HTTP server.
            // The source is deliberately NOT a valid PES: the quota preflight
            // must reject before opening, processing or publishing its bytes.
            val root = Files.createTempDirectory("bm-pro-preflight-denied-")
            val projects = Files.createDirectory(root.resolve("projects"))
            val fonts = Files.createDirectory(root.resolve("fonts"))
            val journal = DesktopArchiveQuotaJournal(
                Files.createDirectory(root.resolve("journal"))
            )
            val source = root.resolve("matrix.pes")
            Files.write(source, byteArrayOf(1, 2, 3))
            val fake = FakeHttp(
                URL("https://" + qaRef + ".supabase.co/functions/v1/brother-usage"),
                body = """{"operation":"import_matrix","protocol":"pro_v2","ready":false}"""
            )
            try {
                val importer = DesktopWindowsStandaloneMatrixAuthorizedImport(
                    usage = client(fake),
                    verifiedSession = DesktopVerifiedFontSession { token },
                    journal = journal,
                    projectFolder = projects,
                    fontFolder = fonts
                )
                assertFailsWith<IllegalStateException> {
                    importer.importOne(source)
                }
                assertEquals("preflight_v2",
                    fake.payload()["action"]?.jsonPrimitive?.content)
                assertEquals("import_matrix",
                    fake.payload()["operation"]?.jsonPrimitive?.content)
                assertEquals(2, fake.payload().keys.size)
                assertFalse(fake.payload().containsKey("client_request_id"))
                assertTrue(fake.closed)
                assertTrue(journal.listPending().isEmpty())
                assertEquals(0L, Files.list(projects).use { it.count() })
                assertEquals(0L, Files.list(fonts).use { it.count() })
                assertEquals(3L, Files.size(source))
            } finally {
                Files.walk(root).use { paths ->
                    paths.sorted(Comparator.reverseOrder())
                        .forEach { Files.deleteIfExists(it) }
                }
            }
        }

    @Test
    fun proV2CannotTargetOfficialProductionOrUsePrivateKey() {
        var opened = 0
        assertFailsWith<IllegalArgumentException> {
            DesktopBrotherProV2QaGateway(
                key, 196, "dwpcddiramxlhavdmmyn"
            ) { opened++; error("Never open production") }
        }
        for (bad in listOf("sb_secret_private", "service_role", "sb_publishable_x")) {
            assertFailsWith<IllegalArgumentException> {
                DesktopBrotherProV2QaGateway(bad, 196, qaRef) {
                    opened++
                    error("Never open network with secret")
                }
            }
        }
        assertEquals(0, opened)
    }

    @Test
    fun stageOnlyTransportUsesRecordedProReceiptForFontAndMatrix() = runBlocking {
        for ((cat, operation) in listOf(
            DesktopArchiveInventory.Category.FONT to "import_font",
            DesktopArchiveInventory.Category.MATRIX to "import_matrix"
        )) {
            for (replayed in listOf(true, false)) {
                val fake = FakeHttp(
                    URL("https://" + qaRef + ".supabase.co/functions/v1/brother-usage"),
                    body = """{"allowed":true,"pro":true,"operation":"$operation","request_key":"$receipt","state":"reserved","replayed":$replayed}"""
                )
                val gateway = client(fake)
                assertEquals(DesktopArchiveQuotaJournal.ReservationProtocol.PRO_V2,
                    gateway.reservationProtocol)
                assertEquals(receipt, gateway.reserveOnce(token, cat, uuid))
                assertEquals("reserve_pro_v2", fake.payload()["action"]?.jsonPrimitive?.content)
                assertEquals(operation, fake.payload()["operation"]?.jsonPrimitive?.content)
                assertEquals(uuid, fake.payload()["client_request_id"]?.jsonPrimitive?.content)
                assertEquals("Bearer " + token, fake.getRequestProperty("Authorization"))
                assertEquals(key, fake.getRequestProperty("apikey"))
                assertFalse(fake.instanceFollowRedirects)
                assertTrue(fake.closed)
                assertFalse(gateway.toString().contains(token))
                assertFalse(gateway.toString().contains(key))
            }
        }
    }

    @Test
    fun neverFallsBackToEphemeralProV1AndInvalidIdsOpenNoConnection() = runBlocking {
        var calls = 0
        val gateway = DesktopBrotherProV2QaGateway(key, 196, qaRef) {
            calls++
            error("Unexpected network")
        }
        assertFailsWith<IllegalStateException> {
            gateway.reserve(token, DesktopArchiveInventory.Category.FONT)
        }
        for (bad in listOf(
            "not-a-uuid",
            "12345678-1234-1234-9234-123456789abc",
            "12345678-1234-4234-1234-123456789abc"
        )) {
            assertFailsWith<IllegalArgumentException> {
                gateway.reserveOnce(token, DesktopArchiveInventory.Category.FONT, bad)
            }
        }
        assertFailsWith<IllegalArgumentException> {
            gateway.finalize(token, DesktopArchiveInventory.Category.FONT,
                "pro_12345678-1234-1234-1234-123456789abc", true)
        }
        assertEquals(0, calls)
    }

    @Test
    fun proV2RejectsFakeEntitlementMissingReceiptAndStringBooleans() = runBlocking {
        val cases = listOf(
            """{"allowed":true,"pro":true,"operation":"import_font","request_key":"$receipt"}""",
            """{"allowed":true,"pro":"true","operation":"import_font","request_key":"$receipt","state":"reserved","replayed":false}""",
            """{"allowed":true,"pro":false,"operation":"import_font","request_key":"$receipt","state":"reserved","replayed":false}""",
            """{"allowed":true,"pro":true,"operation":"import_matrix","request_key":"$receipt","state":"reserved","replayed":false}""",
            """{"allowed":true,"pro":true,"operation":"import_font","request_key":"usage_$uuid","state":"reserved","replayed":false}""",
            """{"allowed":true,"pro":true,"operation":"import_font","request_key":"$receipt","state":"completed","replayed":false}""",
            """{"allowed":true,"pro":true,"operation":"import_font","request_key":"$receipt","state":"reserved","replayed":"true"}"""
        )
        for (body in cases) {
            val fake = FakeHttp(URL("https://" + qaRef + ".supabase.co"), body = body)
            assertFailsWith<IllegalStateException> {
                client(fake).reserveOnce(token, DesktopArchiveInventory.Category.FONT, uuid)
            }
            assertTrue(fake.closed)
        }
    }

    @Test
    fun finalizeProV2RequiresExactReceiptOutcomeAndNoLegacyAck() = runBlocking {
        for (success in listOf(true, false)) {
            val state = if (success) "completed" else "failed"
            val fake = FakeHttp(
                URL("https://" + qaRef + ".supabase.co"),
                body = """{"ok":true,"operation":"import_font","request_key":"$receipt","state":"$state","replayed":false}"""
            )
            client(fake).finalize(token, DesktopArchiveInventory.Category.FONT, receipt, success)
            assertEquals("finalize_pro_v2", fake.payload()["action"]?.jsonPrimitive?.content)
            assertEquals(receipt, fake.payload()["request_key"]?.jsonPrimitive?.content)
            assertEquals(success.toString(), fake.payload()["success"]?.jsonPrimitive?.content)
            assertTrue(fake.closed)
        }
        for (bad in listOf(
            """{"ok":true}""",
            """{"ok":true,"operation":"import_font","request_key":"$receipt","state":"reserved","replayed":false}""",
            """{"ok":true,"operation":"import_font","request_key":"$receipt","state":"completed","replayed":"false"}""",
            """{"ok":true,"operation":"import_matrix","request_key":"$receipt","state":"completed","replayed":false}"""
        )) {
            val fake = FakeHttp(URL("https://" + qaRef + ".supabase.co"), body = bad)
            assertFailsWith<IllegalStateException> {
                client(fake).finalize(token, DesktopArchiveInventory.Category.FONT, receipt, true)
            }
        }
    }

    @Test
    fun proStatusOnlyChecksKnownOwnerScopedReceiptAndCannotClearJournal() = runBlocking {
        for ((reply, state) in listOf(
            """{"found":true,"operation":"import_font","request_key":"$receipt","state":"reserved"}""" to
                DesktopArchiveQuotaStatus.State.RESERVED,
            """{"found":true,"operation":"import_font","request_key":"$receipt","state":"completed"}""" to
                DesktopArchiveQuotaStatus.State.COMPLETED,
            """{"found":false,"operation":"import_font","request_key":"$receipt"}""" to
                DesktopArchiveQuotaStatus.State.NOT_FOUND
        )) {
            val fake = FakeHttp(URL("https://" + qaRef + ".supabase.co"), body = reply)
            val result = client(fake).lookupKnown(token, DesktopArchiveInventory.Category.FONT, receipt)
            assertEquals(state, result.state)
            assertEquals(receipt, result.requestKey)
            assertEquals("status_pro_v2", fake.payload()["action"]?.jsonPrimitive?.content)
            assertEquals(receipt, fake.payload()["request_key"]?.jsonPrimitive?.content)
            assertTrue(fake.closed)
        }
        val wrongOperation = FakeHttp(
            URL("https://" + qaRef + ".supabase.co"),
            body = """{"found":true,"operation":"import_matrix","request_key":"$receipt","state":"completed"}"""
        )
        assertFailsWith<IllegalStateException> {
            client(wrongOperation).lookupKnown(
                token, DesktopArchiveInventory.Category.FONT, receipt
            )
        }
    }

    @Test
    fun proV2CannotReserveBeforeDurableJournalAndOwnership() = runBlocking {
        var opened = 0
        val gateway = DesktopBrotherProV2QaGateway(key, 196, qaRef) {
            opened++
            error("No reservation before safety gate")
        }
        val item = DesktopArchiveInventory.Item(
            0, "QA/safe.pes", "safe.pes", "PES", 32,
            DesktopArchiveInventory.Category.MATRIX
        )
        val gate = DesktopArchiveCommercialImportGate(
            usage = gateway,
            verifiedSession = DesktopVerifiedFontSession { token },
            importer = DesktopArchiveImportFactory {
                error("No file may be created")
            },
            journal = null
        )
        assertFailsWith<IllegalStateException> { gate.importOne(item) }
        assertEquals(0, opened)
    }
}
