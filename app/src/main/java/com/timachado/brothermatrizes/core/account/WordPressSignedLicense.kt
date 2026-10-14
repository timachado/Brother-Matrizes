package com.timachado.brothermatrizes.core.account

import java.security.PublicKey
import java.security.Signature
import java.time.Instant
import java.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Offline verifier for a prospective WordPress-issued license lease.
 *
 * IMPORTANT:
 * - Not wired to AccountSnapshot/hasProAccess until WooCommerce issuance is
 *   implemented and a genuine WordPress public key is pinned in the APK.
 * - Never accept a public key, plan, user ID or device ID supplied by a link.
 * - A receipt, WooCommerce order number and browser redirect are NOT a license.
 * - Leases have a seven-day ceiling so revocation is eventually effective,
 *   even when this hosting cannot serve real-time mobile API requests.
 */
internal object WordPressSignedLicense {
    private const val ISSUER = "ti-machado-app-commerce"
    private const val AUDIENCE = "brother-matrizes-android"
    private const val MAX_LEASE_SECONDS = 7L * 24L * 60L * 60L
    private const val MAX_FUTURE_SKEW_SECONDS = 60L
    private val acceptedPlans = setOf(
        "pro_monthly", "pro_yearly", "pro_lifetime", "pro_lifetime_launch"
    )
    private val strictJson = Json { ignoreUnknownKeys = false }

    @Serializable
    data class Claims(
        @SerialName("v") val version: Int,
        @SerialName("iss") val issuer: String,
        @SerialName("aud") val audience: String,
        @SerialName("sub") val googleSubject: String,
        @SerialName("dev") val deviceId: String,
        @SerialName("lic") val licenseId: String,
        @SerialName("plan") val planCode: String,
        @SerialName("status") val status: String,
        @SerialName("iat") val issuedAt: Long,
        @SerialName("nbf") val notBefore: Long,
        @SerialName("exp") val expiresAt: Long
    )

    data class VerifiedLease(
        val licenseId: String,
        val planCode: String,
        val expiresAt: Instant
    )

    /**
     * Expected wire format: bm1.base64url(UTF8(JSON)).base64url(DER-ECDSA-P256)
     * Signature bytes cover EXACTLY the ASCII "bm1.<payload>" string.
     *
     * [trustedPublicKey] must come from an app-pinned key, never the token.
     * [expectedGoogleSubject] must come from the verified Supabase session.
     * [expectedDeviceId] must come from DeviceIdentity.current(context).
     */
    fun verify(
        token: String,
        trustedPublicKey: PublicKey,
        expectedGoogleSubject: String,
        expectedDeviceId: String,
        now: Instant = Instant.now()
    ): VerifiedLease? {
        if (token.length !in 80..4096 || expectedGoogleSubject.isBlank() ||
            !expectedDeviceId.matches(Regex("[a-f0-9]{32}"))) return null

        val pieces = token.split('.')
        if (pieces.size != 3 || pieces[0] != "bm1") return null
        val dataPart = pieces[1]
        val signaturePart = pieces[2]
        if (!dataPart.matches(Regex("[A-Za-z0-9_-]{20,3500}")) ||
            !signaturePart.matches(Regex("[A-Za-z0-9_-]{32,256}"))) return null

        return runCatching {
            val payload = Base64.getUrlDecoder().decode(dataPart)
            val signatureBytes = Base64.getUrlDecoder().decode(signaturePart)
            if (payload.size !in 64..2800 || signatureBytes.size !in 64..128) {
                return@runCatching null
            }
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(trustedPublicKey)
            verifier.update("bm1.$dataPart".toByteArray(Charsets.US_ASCII))
            if (!verifier.verify(signatureBytes)) return@runCatching null

            val claims = strictJson.decodeFromString<Claims>(
                payload.toString(Charsets.UTF_8)
            )
            val nowSeconds = now.epochSecond
            if (claims.version != 1 ||
                claims.issuer != ISSUER ||
                claims.audience != AUDIENCE ||
                claims.googleSubject != expectedGoogleSubject ||
                claims.deviceId != expectedDeviceId ||
                claims.licenseId.isBlank() ||
                !claims.licenseId.matches(Regex("[A-Za-z0-9_-]{8,128}")) ||
                claims.planCode !in acceptedPlans ||
                claims.status != "active" ||
                claims.issuedAt <= 0 ||
                claims.notBefore < claims.issuedAt - MAX_FUTURE_SKEW_SECONDS ||
                claims.notBefore > nowSeconds ||
                claims.issuedAt > nowSeconds + MAX_FUTURE_SKEW_SECONDS ||
                claims.expiresAt <= nowSeconds ||
                claims.expiresAt <= claims.issuedAt ||
                claims.expiresAt - claims.issuedAt > MAX_LEASE_SECONDS
            ) return@runCatching null

            VerifiedLease(claims.licenseId, claims.planCode, Instant.ofEpochSecond(claims.expiresAt))
        }.getOrNull()
    }
}
