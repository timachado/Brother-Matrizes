package com.timachado.brothermatrizes.desktop

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * Autenticação Windows independente do Android.
 * Usa somente chave PUBLICÁVEL. Não persistir senha, refresh token ou JWT.
 * Direitos só são informativos até portarmos cotas e enforcement Windows.
 */
internal object DesktopAuthConfig {
    const val URL = "https://dwpcddiramxlhavdmmyn.supabase.co"
    // Chave pública de aplicativo. Não é service_role nem sb_secret.
    const val PUBLISHABLE = "sb_publishable_K7JQ8O5fNgZjhEGSQsoZdQ_W4TLRHY4"
}

internal data class DesktopAccountVerified(
    val id: String,
    val email: String,
    val plan: String,
    val backendReportsPro: Boolean,
    val verifiedAt: Instant
) {
    // Nunca conceder PRO somente porque uma URL ou resposta local diz isso.
    val windowsProFeaturesEnabled: Boolean = false
}
internal data class DesktopAuthReply(val status: Int, val content: String)
internal fun interface DesktopAuthGateway {
    fun fetch(path: String, method: String, payload: String?, token: String?): DesktopAuthReply
}

internal class DesktopAuthHttpGateway: DesktopAuthGateway {
    private val allowed = setOf(
        "/auth/v1/token?grant_type=password", "/auth/v1/user",
        "/auth/v1/logout", "/functions/v1/brother-entitlement"
    )
    private val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(8)).build()
    override fun fetch(
        path: String, method: String, payload: String?, token: String?
    ): DesktopAuthReply {
        require(path in allowed && method in setOf("GET", "POST"))
        val url = URI(DesktopAuthConfig.URL + path)
        require(url.scheme == "https" &&
            url.host == "dwpcddiramxlhavdmmyn.supabase.co" &&
            url.port == -1 && url.userInfo == null)
        val requestBuilder = HttpRequest.newBuilder(url)
            .timeout(Duration.ofSeconds(12))
            .header("apikey", DesktopAuthConfig.PUBLISHABLE)
            .header("Accept", "application/json")
            .header("Cache-Control", "no-store")
        if (token != null) requestBuilder.header(
            "Authorization", "Bearer $token"
        )
        val request = if (method == "POST")
            requestBuilder.header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload ?: "{}")).build()
        else requestBuilder.GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        val bytes = response.body().use { it.readNBytes(65_537) }
        check(bytes.size <= 65_536) { "Resposta da Conta excedeu 64 KiB." }
        return DesktopAuthReply(response.statusCode(), String(bytes, Charsets.UTF_8))
    }
}

internal class DesktopAccountAuth(
    // Não criar HttpClient durante a primeira composição da janela:
    // o Java SSL/Windows provider só é inicializado quando o usuário
    // inicia LOGIN, sempre no Dispatchers.IO (nunca no thread Compose).
    private val suppliedGateway: DesktopAuthGateway? = null
) {
    private val gateway: DesktopAuthGateway by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        suppliedGateway ?: DesktopAuthHttpGateway()
    }
    private val json = Json
    private var token: String? = null
    private var expiry: Instant = Instant.EPOCH
    var current: DesktopAccountVerified? = null
        private set
    val isSignedIn: Boolean get() =
        token != null && current != null && Instant.now().isBefore(expiry)

    private fun obj(reply: DesktopAuthReply): JsonObject {
        check(reply.status == 200 && reply.content.length <= 65_536) {
            "Não foi possível confirmar a conta com o servidor."
        }
        return json.parseToJsonElement(reply.content).jsonObject
    }
    private fun s(o: JsonObject, key: String): String {
        val value = o[key]
        return if (value == null || value == JsonNull) ""
        else (value as? JsonPrimitive)?.content.orEmpty()
    }
    private fun b(o: JsonObject, key: String): Boolean =
        (o[key] as? JsonPrimitive)?.booleanOrNull == true

    private fun verifiedAccount(
        bearer: String, emailExpected: String
    ): DesktopAccountVerified {
        // Identidade verificada pelo Supabase Auth: não confiar em
        // campos editáveis de user_metadata nem em JWT decodificado local.
        val user = obj(gateway.fetch("/auth/v1/user", "GET", null, bearer))
        val id = s(user,"id")
        val email = s(user,"email")
        check(id.matches(Regex("[0-9a-fA-F-]{36}")) &&
            email.equals(emailExpected, true) &&
            s(user,"email_confirmed_at").isNotBlank()
        ) { "Identidade ou e-mail ainda não verificados no Supabase." }
        val ent = obj(gateway.fetch(
            "/functions/v1/brother-entitlement", "GET", null, bearer
        ))
        val source = ent["user"] as? JsonObject
            ?: error("Servidor não confirmou a conta da licença.")
        check(b(ent,"authenticated") && s(source,"id")==id &&
            s(source,"email").equals(email,true)
        ) { "A resposta de assinatura não corresponde ao usuário autenticado." }
        val plan = s(ent,"planCode")
        val paid = plan in setOf(
            "pro_monthly","pro_yearly","pro_lifetime","pro_lifetime_launch"
        )
        val period = s(ent,"currentPeriodEnd")
        val stillValid = if(plan in setOf("pro_monthly","pro_yearly")) {
            runCatching { Instant.parse(period).isAfter(Instant.now()) }
                .getOrDefault(false)
        } else true
        val trial = plan=="trial" &&
            ((ent["trialRemainingSeconds"] as? JsonPrimitive)
                ?.longOrNull ?: 0L) > 0L
        val pro = b(ent,"pro") && s(ent,"status")=="active" &&
            stillValid && (paid || trial)
        return DesktopAccountVerified(
            id, email, if(pro) plan else "free", pro, Instant.now()
        )
    }
    private fun clear() { token=null; expiry=Instant.EPOCH; current=null }

    suspend fun login(email: String, password: String): DesktopAccountVerified =
        withContext(Dispatchers.IO) {
            val normalized = email.trim().lowercase(Locale.ROOT)
            require(normalized.length in 5..254 &&
                normalized.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) &&
                password.length in 8..1024
            ) { "Informe e-mail e senha válidos." }
            clear()
            val body = kotlinx.serialization.json.buildJsonObject {
                put("email", JsonPrimitive(normalized))
                put("password", JsonPrimitive(password))
            }.toString()
            val auth = obj(gateway.fetch(
                "/auth/v1/token?grant_type=password","POST",body,null
            ))
            val access = s(auth,"access_token")
            val ttl = (auth["expires_in"] as? JsonPrimitive)?.longOrNull ?: 0
            check(access.length in 30..12_000 && ttl in 60..86_400 &&
                s(auth,"token_type").equals("bearer",true)
            ) { "Resposta de sessão inválida do servidor." }
            val verified = verifiedAccount(access,normalized)
            token=access
            expiry=Instant.now().plusSeconds(ttl)
            current=verified
            verified
        }

    suspend fun revalidate(): DesktopAccountVerified = withContext(Dispatchers.IO) {
        val access=token
        val previous=current
        check(access!=null && previous!=null && isSignedIn) {
            "Sessão expirada; entre novamente."
        }
        try {
            verifiedAccount(access,previous.email).also { current=it }
        } catch(e: Exception) {
            clear()
            throw IllegalStateException("Sessão não confirmada. Entre novamente.")
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        val access=token
        clear()
        if(access!=null) runCatching {
            gateway.fetch("/auth/v1/logout","POST","{}",access)
        }
    }
}
