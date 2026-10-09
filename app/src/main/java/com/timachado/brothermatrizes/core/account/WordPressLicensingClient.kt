package com.timachado.brothermatrizes.core.account

import com.timachado.brothermatrizes.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Commercial source of truth: the WordPress/WooCommerce licensing plugin.
 * Supabase's access token is forwarded only to the configured HTTPS backend.
 * Never embed Efí credentials, WooCommerce secrets or commercial decisions.
 */
internal object WordPressLicensingClient {
    // Small in-memory grace window only (not SharedPreferences). It cannot
    // resurrect entitlements across app reinstall, device clock edits or reboot.
    // Full persistent offline Pro requires a server-signed license lease.
    private var lastAccount: AccountSnapshot? = null
    private var lastVerifiedElapsed: Long = 0L

    fun recentlyVerified(sub: String): AccountSnapshot? {
        val result = lastAccount ?: return null
        val age = android.os.SystemClock.elapsedRealtime() - lastVerifiedElapsed
        if (age < 0 || age > 10 * 60 * 1000L || result.userId != sub) return null
        return if (result.planCode == "free" || result.hasProAccess) result else null
    }

    private fun baseUrl(): String {
        val base = BuildConfig.WORDPRESS_URL.trim().trimEnd('/')
        require(base.startsWith("https://") && base.length > 10 &&
            !base.contains('@') && !base.contains('#')) {
            "Servidor comercial WordPress ainda não configurado."
        }
        return base
    }

    private fun json(
        route: String,
        token: String?,
        body: JSONObject? = null
    ): JSONObject {
        require(route.startsWith("/wp-json/brother-matrizes/v1/"))
        val root = URL(baseUrl())
        val url = URL(root, route)
        require(url.protocol == "https" && url.host == root.host) {
            "Host comercial inesperado."
        }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 12000
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("Accept", "application/json")
            token?.let {
                setRequestProperty("Authorization", "Bearer $it")
            }
            if (body != null) {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) connection.outputStream.use {
                it.write(body.toString().toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            val raw = (if (status in 200..299) connection.inputStream
                       else connection.errorStream)?.bufferedReader()
                ?.use { it.readText().take(1024 * 1024) } ?: ""
            require(status in 200..299) {
                JSONObject(raw.ifBlank { "{}" }).optString("message")
                    .ifBlank { "Servidor comercial indisponível (HTTP $status)." }
            }
            return JSONObject(raw)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun account(
        sub: String,
        authEmail: String,
        token: String,
        authName: String?,
        authAvatar: String?
    ): AccountSnapshot = withContext(Dispatchers.IO) {
        val data = json("/wp-json/brother-matrizes/v1/me", token)
        check(data.optString("subject") == sub) {
            "Identidade comercial não corresponde ao login Google."
        }
        val license = data.optJSONObject("license") ?: JSONObject()
        val trial = license.optJSONObject("trial") ?: JSONObject()
        val level = license.optString("level", "free")
        val expires = license.optString("expires_at").takeIf { it.isNotBlank() }
        val serverPlans = json("/wp-json/brother-matrizes/v1/plans", null)
            .optJSONArray("plans")
        val plans = buildList {
            if (serverPlans != null) for (index in 0 until serverPlans.length()) {
                val p = serverPlans.optJSONObject(index) ?: continue
                val code = p.optString("code")
                val lifetime = code.contains("lifetime")
                val promo = code == "pro_lifetime_launch"
                add(AccountPlanOption(
                    code = code,
                    name = p.optString("name", code),
                    billingType = when {
                        lifetime -> "one_time"
                        code == "pro_monthly" -> "monthly"
                        code == "pro_yearly" -> "yearly"
                        else -> "free"
                    },
                    isPaid = code != "free",
                    isLifetime = lifetime,
                    isPromotional = promo,
                    description = if (code == "free") "Recursos básicos."
                    else if (lifetime) "Pagamento único."
                    else "Renovação manual até validação do gateway Efí.",
                    priceCents = p.optInt("price_cents").takeIf {
                        !p.isNull("price_cents")
                    },
                    currency = p.optString("currency", "BRL"),
                    active = p.optBoolean("active", false),
                    availableFrom = null,
                    availableUntil = null,
                    displayOrder = index
                ))
            }
        }
        val quota = buildMap<String, Int> {
            // Usage comes from WordPress only. It is never authoritative
            // when the backend is unavailable.
            val usage = json("/wp-json/brother-matrizes/v1/usage", token)
                .optJSONObject("actions")
            if (usage != null) for (key in listOf(
                "create_name", "export_matrix", "import_font", "import_matrix"
            )) {
                val action = usage.optJSONObject(key) ?: continue
                if (!action.isNull("remaining")) put(key, action.optInt("remaining"))
            }
        }
        val snapshot = AccountSnapshot(
            userId = sub,
            email = data.optString("email", authEmail),
            displayName = data.optString("display_name").ifBlank {
                authName ?: authEmail.substringBefore('@').ifBlank { "Brother Matrizes" }
            },
            avatarUrl = data.optString("avatar_url").takeIf {
                it.isNotBlank() && it != "null"
            } ?: authAvatar,
            planCode = level,
            subscriptionStatus = license.optString("status", "free"),
            currentPeriodEnd = expires,
            currency = "BRL",
            manageUrl = baseUrl() + "/minha-conta/",
            availablePlans = plans,
            trialStatus = trial.optString("status", "unknown"),
            trialRemainingSeconds = trial.optLong("remaining_seconds"),
            quotaRemaining = quota,
            commercialConfigured = true
        )
        lastAccount = snapshot
        lastVerifiedElapsed = android.os.SystemClock.elapsedRealtime()
        snapshot
    }

    suspend fun updateProfile(token: String, name: String) = withContext(Dispatchers.IO) {
        json("/wp-json/brother-matrizes/v1/me/profile", token,
            JSONObject().put("display_name", name))
    }

    suspend fun trial(token: String) = withContext(Dispatchers.IO) {
        json("/wp-json/brother-matrizes/v1/trial/activate", token, JSONObject())
    }

    suspend fun checkout(token: String, planCode: String): String =
        withContext(Dispatchers.IO) {
            val answer = json("/wp-json/brother-matrizes/v1/checkout", token,
                JSONObject().put("plan_code", planCode)
                    .put("request_key", UUID.randomUUID().toString()))
            val address = answer.getString("checkout_url")
            val url = URL(address)
            val expected = URL(baseUrl())
            require(url.protocol == "https" && url.host == expected.host) {
                "Checkout não pertence ao WordPress autorizado."
            }
            address
        }

    suspend fun syncDevices(token: String, current: LocalDeviceIdentity):
        List<AccountDevice> = withContext(Dispatchers.IO) {
        val r = json("/wp-json/brother-matrizes/v1/devices", token,
            JSONObject().put("device_id", current.deviceId)
                .put("device_name", current.deviceName)
                .put("app_version", BuildConfig.VERSION_NAME))
        val devices = r.optJSONArray("devices") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until devices.length()) {
                val device = devices.optJSONObject(i) ?: continue
                val id = device.optString("device_id")
                add(AccountDevice(
                    deviceId = id,
                    deviceName = device.optString("device_name"),
                    platform = device.optString("platform", "android"),
                    appVersion = device.optString("app_version"),
                    lastSeenAt = device.optString("last_seen_at"),
                    isCurrent = id == current.deviceId
                ))
            }
        }
    }
}
