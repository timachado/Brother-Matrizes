package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveInventory
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveQuotaStatus
import com.timachado.brothermatrizes.desktop.platform.DesktopQaStagingProject
import com.timachado.brothermatrizes.desktop.platform.DesktopStagingQuotaStatusReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Fully offline. A fake Supabase backend receives status requests only. */
class DesktopStagingQuotaStatusReaderTest {
    private val staging = DesktopQaStagingProject.isolated("stagingonlyqa1234567")
    private val key = "sb_publishable_fake_safe_for_qa_1234567890"
    private val token = "offline_valid_auth_token_for_qa_1234567890"
    private val free = "usage_12345678-1234-4234-8234-123456789abc"
    private val pro = "pro_12345678-1234-4234-8234-123456789abc"

    private class FakeStatus(url: URL, private val response: String) :
        HttpURLConnection(url) {
        val posted = ByteArrayOutputStream()
        var disconnected = false
        override fun connect() = Unit
        override fun usingProxy() = false
        override fun disconnect() { disconnected = true }
        override fun getResponseCode() = 200
        override fun getContentType() = "application/json"
        override fun getOutputStream(): OutputStream = posted
        override fun getInputStream(): InputStream =
            ByteArrayInputStream(response.toByteArray(Charsets.UTF_8))
    }

    @Test
    fun stagingFreeAndProStatusNeverTargetProductionOrWriteCommercialQuota() = runBlocking {
        for ((requestKey, expectedAction) in listOf(
            free to "status",
            pro to "status_pro_v2"
        )) {
            val endpoint = staging.endpoint("/functions/v1/brother-usage")
            val connection = FakeStatus(
                endpoint,
                """{"found":true,"operation":"import_matrix","request_key":"$requestKey","state":"reserved"}"""
            )
            var attempts = 0
            val client = DesktopStagingQuotaStatusReader(
                target = staging,
                publishableKey = key,
                versionCode = 196,
                connectionFactory = { url ->
                    attempts++
                    assertEquals(endpoint.toString(), url.toString())
                    assertFalse(url.host == "dwpcddiramxlhavdmmyn.supabase.co")
                    connection
                }
            )
            val status = client.lookupKnown(
                token, DesktopArchiveInventory.Category.MATRIX, requestKey
            )
            assertEquals(1, attempts)
            assertEquals(DesktopArchiveQuotaStatus.State.RESERVED, status.state)
            val json = Json.parseToJsonElement(
                connection.posted.toString(Charsets.UTF_8)
            ).jsonObject
            assertEquals(expectedAction, json["action"]?.jsonPrimitive?.content)
            assertEquals("import_matrix", json["operation"]?.jsonPrimitive?.content)
            assertEquals(requestKey, json["request_key"]?.jsonPrimitive?.content)
            assertEquals(3, json.keys.size)
            assertFalse(json.containsKey("success"))
            assertFalse(json.containsKey("client_request_id"))
            assertEquals("POST", connection.requestMethod)
            assertFalse(connection.instanceFollowRedirects)
            assertTrue(connection.disconnected)
            assertFalse(client.toString().contains(token))
        }
    }
}
