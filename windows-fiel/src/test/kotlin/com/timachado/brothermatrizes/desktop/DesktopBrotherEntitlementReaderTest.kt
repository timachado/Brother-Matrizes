package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopBrotherEntitlementReader
import com.timachado.brothermatrizes.desktop.platform.DesktopVerifiedGoogleIdentity
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** All test data is synthetic; no production user, quotas or network. */
class DesktopBrotherEntitlementReaderTest {
    private val key = "sb_publishable_fake_safe_qa_1234567890"
    private val token = "fake_only_qa_access_12345678901234567"
    private val userId = "11111111-2222-4333-8444-555555555555"
    private val account = DesktopVerifiedGoogleIdentity(userId, "tester@example.invalid")
    private val endpoint = URL(
        "https://dwpcddiramxlhavdmmyn.supabase.co/functions/v1/brother-entitlement"
    )

    private class FakeConnection(
        url: URL,
        private val status: Int,
        private val mime: String,
        private val response: String
    ) : HttpURLConnection(url) {
        var disconnected = false
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = status
        override fun getContentType() = mime
        override fun getInputStream(): InputStream =
            ByteArrayInputStream(response.toByteArray(Charsets.UTF_8))
    }

    private fun json(
        id: String = userId,
        email: String = "tester@example.invalid",
        plan: String = "free",
        pro: String = "false",
        status: String = "active",
        authenticated: String = "true"
    ): String = """{"authenticated":$authenticated,"user":{"id":"$id","email":"$email"},"planCode":"$plan","status":"$status","pro":$pro}"""

    @Test
    fun readsFreeAndServerApprovedPaidPlansWithoutWritingCommercialState() = runBlocking {
        for ((plan, pro, expected) in listOf(
            Triple("free", "false", false),
            Triple("pro_monthly", "true", true),
            Triple("pro_yearly", "true", true),
            Triple("pro_lifetime", "true", true),
            Triple("pro_lifetime_launch", "true", true),
            Triple("trial", "true", true),
            Triple("pro_yearly", "false", false),
            Triple("UNKNOWN_FUTURE_PLAN", "true", false)
        )) {
            val connection = FakeConnection(endpoint, 200, "application/json", json(
                plan = plan, pro = pro
            ))
            val reader = DesktopBrotherEntitlementReader(key) { url ->
                assertEquals(endpoint.toString(), url.toString())
                connection
            }
            val snapshot = reader.readOnlyAccount(account, token)
            assertEquals("GET", connection.requestMethod)
            assertEquals("Bearer $token", connection.getRequestProperty("Authorization"))
            assertEquals(key, connection.getRequestProperty("apikey"))
            assertFalse(connection.instanceFollowRedirects)
            assertEquals(expected, snapshot.hasServerPro)
            assertEquals(if (expected) plan else "free", snapshot.planCode)
            assertEquals("active", snapshot.subscriptionStatus)
            assertTrue(connection.disconnected)
            assertFalse(snapshot.toString().contains(token))
            assertFalse(snapshot.toString().contains(userId))
        }
    }

    @Test
    fun rejectsForgedIdentityMalformedResponsesAndServerErrors() = runBlocking {
        val scenarios = listOf(
            Triple(401, "application/json", json()),
            Triple(500, "application/json", json()),
            Triple(200, "text/html", "<html>invalid</html>"),
            Triple(200, "application/json", "[]"),
            Triple(200, "application/json", "{}"),
            Triple(200, "application/json", json(authenticated = "false")),
            Triple(200, "application/json", json(id = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee")),
            Triple(200, "application/json", json(email = "different@example.invalid")),
            Triple(200, "application/json", "x".repeat(65_537))
        )
        for ((status, type, response) in scenarios) {
            val connection = FakeConnection(endpoint, status, type, response)
            val reader = DesktopBrotherEntitlementReader(key) { connection }
            val failure = assertFailsWith<Exception> {
                reader.readOnlyAccount(account, token)
            }
            assertTrue(connection.disconnected)
            assertFalse(failure.message.orEmpty().contains(token))
        }
    }

    @Test
    fun doesNotConfuseInactiveOrUnconfirmedSubscriptionsWithPaidAccess() = runBlocking {
        val connection = FakeConnection(
            endpoint, 200, "application/json", json(
                plan = "pro_lifetime", pro = "true", status = "cancelled"
            )
        )
        val reader = DesktopBrotherEntitlementReader(key) { connection }
        val snapshot = reader.readOnlyAccount(account, token)
        assertFalse(snapshot.hasServerPro)
        assertEquals("free", snapshot.planCode)
        assertEquals("unavailable", snapshot.subscriptionStatus)
    }

    @Test
    fun rejectsServerKeysAndInvalidSessionWithoutNetwork() = runBlocking {
        assertFailsWith<IllegalArgumentException> {
            DesktopBrotherEntitlementReader("sb_secret_no_client")
        }
        var called = 0
        val reader = DesktopBrotherEntitlementReader(key) {
            called++
            error("must not connect")
        }
        assertFailsWith<IllegalArgumentException> {
            reader.readOnlyAccount(account, "short")
        }
        assertFailsWith<IllegalArgumentException> {
            reader.readOnlyAccount(account, "fake\r\nBearer injected token")
        }
        assertEquals(0, called)
    }
}
