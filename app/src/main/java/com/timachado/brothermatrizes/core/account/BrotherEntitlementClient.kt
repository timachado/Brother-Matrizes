package com.timachado.brothermatrizes.core.account

import com.timachado.brothermatrizes.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Commerce remains WordPress/WooCommerce/Efí. This isolated Supabase function
 * verifies the Google session and mirrors payment evidence from WordPress.
 * Never use editable Supabase profile metadata as a paid entitlement.
 */
internal object BrotherEntitlementClient {
    suspend fun account(
        sub: String,
        email: String,
        accessToken: String,
        displayName: String?,
        avatar: String?,
        activateTrial: Boolean = false
    ): AccountSnapshot = withContext(Dispatchers.IO) {
        val root = URL(BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/brother-entitlement")
        require(root.protocol == "https" &&
            root.host == "dwpcddiramxlhavdmmyn.supabase.co" &&
            root.port == -1) { "Servidor de autenticação inesperado." }
        val conn = (root.openConnection() as HttpURLConnection).apply {
            requestMethod = if (activateTrial) "POST" else "GET"
            if (activateTrial) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            connectTimeout = 12000
            readTimeout = 12000
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Authorization", "Bearer $accessToken")
        }
        val body = try {
            if (activateTrial) {
                conn.outputStream.use { stream ->
                    stream.write(JSONObject().put("action", "activate_trial")
                        .toString().toByteArray(Charsets.UTF_8))
                }
            }
            val code = conn.responseCode
            val mime = conn.contentType.orEmpty().substringBefore(';').lowercase().trim()
            check(code == 200 && (mime == "application/json" || mime.endsWith("+json"))) {
                "Não foi possível confirmar sua assinatura (HTTP $code)."
            }
            val raw = conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val buffer = CharArray(8192)
                val content = StringBuilder()
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    check(content.length + count <= 65536) {
                        "Resposta da assinatura muito grande."
                    }
                    content.append(buffer, 0, count)
                }
                content.toString()
            }
            JSONObject(raw)
        } finally {
            conn.disconnect()
        }
        val verifiedUser = body.optJSONObject("user") ?: error("Sessão não verificada.")
        check(body.optBoolean("authenticated") &&
            verifiedUser.optString("id") == sub &&
            verifiedUser.optString("email").equals(email, ignoreCase = true)
        ) { "A assinatura consultada não corresponde à sua sessão Google." }

        val expectedPlan = body.optString("planCode", "free")
        val paidCodes = setOf(
            "pro_monthly", "pro_yearly", "pro_lifetime", "pro_lifetime_launch"
        )
        val serverPro = body.optBoolean("pro", false) &&
            body.optString("status") == "active"
        val plan = when {
            expectedPlan in paidCodes && serverPro -> expectedPlan
            expectedPlan == "trial" && serverPro -> "trial"
            else -> "free"
        }
        val expiry = body.optString("currentPeriodEnd").takeUnless {
            it.isBlank() || it == "null"
        }
        val plans = body.optJSONArray("availablePlans")
        val catalog = buildList {
            if (plans != null) for (i in 0 until plans.length()) {
                val p = plans.optJSONObject(i) ?: continue
                val code = p.optString("code")
                if (code != "free" && code !in paidCodes) continue
                add(AccountPlanOption(
                    code = code,
                    name = p.optString("name"),
                    billingType = p.optString("billing_type"),
                    isPaid = p.optBoolean("is_paid"),
                    isLifetime = p.optBoolean("is_lifetime"),
                    isPromotional = p.optBoolean("is_promotional"),
                    description = if (code == "free") "Acesso gratuito."
                        else "Consulte a oferta no site T.I. Machado.",
                    priceCents = if (p.isNull("price_cents")) null else p.optInt("price_cents"),
                    currency = "BRL",
                    active = p.optBoolean("active", false),
                    availableFrom = null,
                    availableUntil = null,
                    displayOrder = p.optInt("display_order", i)
                ))
            }
        }
        AccountSnapshot(
            userId = sub,
            email = email,
            displayName = displayName ?: email.substringBefore('@').ifBlank { "Brother Matrizes" },
            avatarUrl = avatar,
            planCode = plan,
            subscriptionStatus = if (plan == "free") "active" else "active",
            currentPeriodEnd = if (plan == "free") null else expiry,
            purchasedAt = body.optString("purchasedAt").takeUnless { it.isBlank() || it == "null" },
            purchasePriceCents = if (body.isNull("purchasePriceCents")) null
                else body.optInt("purchasePriceCents"),
            provider = body.optString("provider").takeUnless { it.isBlank() || it == "null" },
            manageUrl = WordPressWebStore.accountUrl(BuildConfig.WORDPRESS_URL),
            availablePlans = catalog,
            commercialConfigured = body.optBoolean("commercialConfigured", false),
            trialStatus = body.optString("trialStatus", "unknown"),
            trialRemainingSeconds = body.optLong("trialRemainingSeconds", 0L)
                .coerceAtLeast(0L)
        )
    }
}
