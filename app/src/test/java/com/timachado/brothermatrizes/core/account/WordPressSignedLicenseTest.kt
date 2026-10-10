package com.timachado.brothermatrizes.core.account

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.util.Base64
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class WordPressSignedLicenseTest {
    private val now = Instant.parse("2026-10-10T10:00:00Z")
    private val device = "0123456789abcdef0123456789abcdef"
    private val sub = "supabase-google-user-uuid"

    private fun pair(): KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private fun claims(
        plan: String = "pro_lifetime_launch",
        status: String = "active",
        expiresAt: Long = now.epochSecond + 3600,
        issuedAt: Long = now.epochSecond - 30,
        notBefore: Long = now.epochSecond - 20,
        user: String = sub,
        targetDevice: String = device,
        audience: String = "brother-matrizes-android",
        issuer: String = "ti-machado-app-commerce"
    ) = WordPressSignedLicense.Claims(
        version = 1,
        issuer = issuer,
        audience = audience,
        googleSubject = user,
        deviceId = targetDevice,
        licenseId = "license_12345678",
        planCode = plan,
        status = status,
        issuedAt = issuedAt,
        notBefore = notBefore,
        expiresAt = expiresAt
    )

    private fun token(pair: KeyPair, claims: WordPressSignedLicense.Claims): String {
        val json = Json.encodeToString(claims)
        val data = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(json.toByteArray(Charsets.UTF_8))
        val input = "bm1.$data"
        val signed = Signature.getInstance("SHA256withECDSA").apply {
            initSign(pair.private)
            update(input.toByteArray(Charsets.US_ASCII))
        }.sign()
        return "$input." + Base64.getUrlEncoder().withoutPadding().encodeToString(signed)
    }

    @Test fun validWordPressSignatureBindsUserDeviceAndPlan() {
        val k = pair()
        val license = WordPressSignedLicense.verify(
            token(k, claims()), k.public, sub, device, now
        )
        assertNotNull(license)
        assertEquals("pro_lifetime_launch", license?.planCode)
        assertEquals("license_12345678", license?.licenseId)
    }

    @Test fun forgedSignatureOrDifferentTrustedKeyIsRejected() {
        val trusted = pair()
        val attacker = pair()
        val legitimate = token(trusted, claims())
        assertNull(WordPressSignedLicense.verify(
            token(attacker, claims()), trusted.public, sub, device, now
        ))
        val chunks = legitimate.split('.')
        val originalPayload = Base64.getUrlDecoder().decode(chunks[1]).toString(Charsets.UTF_8)
        val tamperedData = Base64.getUrlEncoder().withoutPadding().encodeToString(
            originalPayload.replace("pro_lifetime_launch", "pro_lifetime").toByteArray(Charsets.UTF_8)
        )
        assertNull(WordPressSignedLicense.verify(
            "bm1.$tamperedData.${chunks[2]}", trusted.public, sub, device, now
        ))
    }

    @Test fun rejectsWrongGoogleUserOrOtherDevice() {
        val k = pair()
        val voucher = token(k, claims())
        assertNull(WordPressSignedLicense.verify(voucher, k.public, "different", device, now))
        assertNull(WordPressSignedLicense.verify(voucher, k.public, sub,
            "ffffffffffffffffffffffffffffffff", now))
        assertNull(WordPressSignedLicense.verify(voucher, k.public, "", device, now))
    }

    @Test fun rejectsExpiredFutureOrOverlongLeases() {
        val k = pair()
        val scenarios = listOf(
            claims(expiresAt = now.epochSecond),
            claims(notBefore = now.epochSecond + 30),
            claims(issuedAt = now.epochSecond + 90),
            claims(expiresAt = now.epochSecond + 31L * 24 * 3600),
            claims(expiresAt = now.epochSecond - 1)
        )
        scenarios.forEach { claim ->
            assertNull(WordPressSignedLicense.verify(
                token(k, claim), k.public, sub, device, now
            ))
        }
    }

    @Test fun rejectsRevokedUnrelatedAndMalformedClaims() {
        val k = pair()
        val scenarios = listOf(
            claims(status = "refunded"),
            claims(plan = "free"),
            claims(plan = "admin"),
            claims(issuer = "fake-issuer"),
            claims(audience = "another-app"),
            claims(targetDevice = "another"),
            claims(user = "someone-else")
        )
        scenarios.forEach { claim ->
            assertNull(WordPressSignedLicense.verify(
                token(k, claim), k.public, sub, device, now
            ))
        }
        for (bad in listOf("", "bm1.abc.def", "bm2.bad.bad", "some-order-id",
                "bm1.${"A".repeat(5000)}.abc")) {
            assertNull(WordPressSignedLicense.verify(bad, k.public, sub, device, now))
        }
    }
}
