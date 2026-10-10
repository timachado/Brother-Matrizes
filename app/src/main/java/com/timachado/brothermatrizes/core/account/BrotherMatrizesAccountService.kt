package com.timachado.brothermatrizes.core.account

import android.content.Context
import android.content.Intent
import com.timachado.brothermatrizes.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Instant

object BrotherMatrizesAccountService {

    private val client:
        SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl =
                BuildConfig
                    .SUPABASE_URL,
            supabaseKey =
                BuildConfig
                    .SUPABASE_PUBLISHABLE_KEY
        ) {
            install(
                Auth
            ) {
                flowType =
                    FlowType.PKCE
                // Keep the user's refresh token across Activity recreation.
                // Defaults are enabled, explicit here to prevent accidental
                // replacement with an in-memory session manager.
                autoLoadFromStorage = true
                autoSaveToStorage = true
                alwaysAutoRefresh = true
                scheme =
                    AUTH_SCHEME
                host =
                    AUTH_HOST
                defaultRedirectUrl =
                    AUTH_REDIRECT_URL
            }

        }
    }

    @Volatile private var lastPaidAccount: AccountSnapshot? = null
    @Volatile private var lastPaidVerifiedMs: Long = 0L

    suspend fun currentAccount():
        Result<AccountSnapshot?> =
        runCatching {
            // Android can temporarily set the Auth status to Initializing
            // when the app leaves or returns from the background. At that
            // moment currentSessionOrNull() is *not* proof of sign-out.
            // Wait for the persisted session to be loaded/refreshed first.
            val auth = client.auth
            auth.awaitInitialization()
            val status = auth.sessionStatus.first {
                it !is SessionStatus.Initializing
            }
            val session = when (status) {
                is SessionStatus.Authenticated -> status.session
                is SessionStatus.NotAuthenticated -> return@runCatching null
                is SessionStatus.RefreshFailure ->
                    error("Não foi possível atualizar sua sessão. Verifique a conexão e tente novamente.")
                else -> error("A sessão ainda não está disponível.")
            }

            val user =
                session.user
                    ?: return@runCatching null

            val metadata =
                user.userMetadata

            val avatarUrl =
                metadata
                    ?.get(
                        "avatar_url"
                    )
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?: metadata
                        ?.get(
                            "picture"
                        )
                        ?.jsonPrimitive
                        ?.contentOrNull

            val authDisplayName =
                metadata
                    ?.get(
                        "full_name"
                    )
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?: metadata
                        ?.get(
                            "name"
                        )
                        ?.jsonPrimitive
                        ?.contentOrNull
                    ?: metadata
                        ?.get(
                            "display_name"
                        )
                        ?.jsonPrimitive
                        ?.contentOrNull

            accountFor(
                userId =
                    user.id,
                email =
                    user.email
                        ?: "",
                authDisplayName =
                    authDisplayName,
                authAvatarUrl =
                    avatarUrl
            )
        }

    fun googleOAuthUrl():
        Result<String> =
        runCatching {
            client.auth
                .getOAuthUrl(
                    provider =
                        Google,
                    redirectUrl =
                        AUTH_REDIRECT_URL
                )
        }

    suspend fun signInWithGoogle():
        Result<Unit> =
        runCatching {
            client.auth
                .signInWith(
                    provider =
                        Google,
                    redirectUrl =
                        AUTH_REDIRECT_URL
                )
        }

    fun handleAuthRedirect(
        intent: Intent,
        onSuccess: () -> Unit,
        onError: (
            Throwable
        ) -> Unit
    ) {
        runCatching {
            client.handleDeeplinks(
                intent =
                    intent,
                onSessionSuccess = {
                    onSuccess()
                }
            )
        }.onFailure(
            onError
        )
    }

    suspend fun signIn(
        email: String,
        password: String
    ): Result<AccountSnapshot> =
        runCatching {
            require(
                email.isNotBlank()
            ) {
                "Informe o e-mail."
            }

            require(
                password.length >=
                    6
            ) {
                "A senha precisa ter pelo menos 6 caracteres."
            }

            client.auth
                .signInWith(
                    Email
                ) {
                    this.email =
                        email.trim()

                    this.password =
                        password
                }

            currentAccount()
                .getOrThrow()
                ?: error(
                    "A sessão não foi iniciada."
                )
        }

    suspend fun signUp(
        displayName: String,
        email: String,
        password: String
    ): Result<SignUpOutcome> =
        runCatching {
            val name =
                displayName
                    .trim()

            require(
                name.length in
                    2..80
            ) {
                "Informe um nome de 2 a 80 caracteres."
            }

            require(
                email.isNotBlank() &&
                    '@' in
                    email
            ) {
                "Informe um e-mail válido."
            }

            require(
                password.length >=
                    8
            ) {
                "Use uma senha com pelo menos 8 caracteres."
            }

            client.auth
                .signUpWith(
                    Email
                ) {
                    this.email =
                        email.trim()

                    this.password =
                        password

                    data =
                        buildJsonObject {
                            put(
                                "display_name",
                                name
                            )

                            put(
                                "app_slug",
                                "brother-matrizes"
                            )
                        }
                }

            val account =
                currentAccount()
                    .getOrThrow()

            if (
                account ==
                    null
            ) {
                SignUpOutcome
                    .ConfirmationRequired(
                        email =
                            email.trim()
                    )
            } else {
                SignUpOutcome
                    .SignedIn(
                        account
                    )
            }
        }

    suspend fun updateDisplayName(displayName: String): Result<AccountSnapshot> =
        runCatching {
            val name = displayName.trim()
            require(name.length in 2..80) { "Informe um nome de 2 a 80 caracteres." }
            check(client.auth.currentSessionOrNull() != null) {
                "Entre na sua conta primeiro."
            }
            // Editing display name belongs to Supabase Auth, not the billing backend.
            client.auth.updateUser {
                data { put("display_name", name) }
            }
            currentAccount().getOrThrow()
                ?: error("Conta autenticada não encontrada.")
        }

    suspend fun signOut():
        Result<Unit> =
        runCatching {
            client.auth
                .signOut()
            lastPaidAccount = null
            lastPaidVerifiedMs = 0L
        }

    suspend fun syncDevices(localDevice: LocalDeviceIdentity): Result<List<AccountDevice>> =
        runCatching {
            // Free InfinityFree does not support direct Android API calls.
            // Expose the current device locally until server-side device
            // registration is added to the isolated entitlement function.
            listOf(AccountDevice(
                deviceId = localDevice.deviceId,
                deviceName = localDevice.deviceName,
                platform = "android",
                appVersion = BuildConfig.VERSION_NAME,
                lastSeenAt = Instant.now().toString(),
                isCurrent = true
            ))
        }

    suspend fun activateTrial(): Result<AccountSnapshot> = runCatching {
        val token = client.auth.currentSessionOrNull()?.accessToken
            ?: error("Entre na sua conta primeiro.")
        // Never claim that the free trial started without authoritative
        // WordPress confirmation; a paid receipt mirror cannot issue trials.
        val receipt = WordPressLicensingClient.trial(token)
        check(receipt.optJSONObject("trial")?.optString("status") == "active") {
            "Seu teste gratuito já foi utilizado ou expirou."
        }
        val session = client.auth.currentSessionOrNull()
            ?: error("Sua sessão Google expirou.")
        val user = session.user ?: error("Conta Google indisponível.")
        val confirmed = WordPressLicensingClient.account(
            user.id, user.email.orEmpty(), token, null, null
        )
        check(confirmed.planCode == "trial" &&
            confirmed.trialStatus == "active" && confirmed.hasProAccess
        ) {
            "Não foi possível confirmar a ativação. Consulte sua licença novamente."
        }
        lastPaidAccount = confirmed
        lastPaidVerifiedMs = android.os.SystemClock.elapsedRealtime()
        confirmed
    }

    // Do not confuse a persisted session being restored with a signed-out
    // user. This is the same recovery logic already used by Minha Conta.
    private suspend fun verifiedAccessToken(): String {
        val auth = client.auth
        auth.awaitInitialization()
        val status = auth.sessionStatus.first {
            it !is SessionStatus.Initializing
        }
        val session = when (status) {
            is SessionStatus.Authenticated -> status.session
            is SessionStatus.NotAuthenticated ->
                error("Entre com Google na aba Minha Conta para validar as cotas.")
            is SessionStatus.RefreshFailure ->
                error("Não foi possível restaurar a sessão Google. Confira a conexão e tente novamente.")
            else -> error("A autenticação está temporariamente indisponível.")
        }
        return session.accessToken
    }

    suspend fun authorizeImport(context: Context, isFont: Boolean): Result<String> = runCatching {
        if (BuildConfig.BETA_LOCAL_IMPORTS) {
            // Resolve paid access from WooCommerce-confirmed Supabase mirror.
            // Refresh a stale lease before importing; free preview works offline.
            val session = client.auth.currentSessionOrNull()
            val user = session?.user
            val age = android.os.SystemClock.elapsedRealtime() - lastPaidVerifiedMs
            var checked = lastPaidAccount?.takeIf {
                age >= 0L && age <= 2 * 60 * 1000L &&
                    user?.id == it.userId && it.hasProAccess
            }
            if (checked == null && session != null && user != null &&
                user.email != null &&
                com.timachado.brothermatrizes.core.network.NetworkStatus.isOnline(context)
            ) {
                val updated = runCatching {
                    BrotherEntitlementClient.account(
                        user.id, user.email!!, session.accessToken, null, null
                    )
                }.getOrNull()
                checked = updated?.takeIf { it.hasProAccess }
                lastPaidAccount = checked
                lastPaidVerifiedMs = android.os.SystemClock.elapsedRealtime()
            }
            if (checked?.hasProAccess == true) {
                "pro_" + java.util.UUID.randomUUID().toString()
            } else {
                LocalBetaImportQuota.authorize(context, isFont)
            }
        } else {
            val token = verifiedAccessToken()
            WordPressLicensingClient.authorizeUsage(
                token,
                if (isFont) "import_font" else "import_matrix"
            )
        }
    }

    suspend fun finalizeImport(
        context: Context, isFont: Boolean, requestKey: String, success: Boolean
    ): Result<Unit> = runCatching {
        if (BuildConfig.BETA_LOCAL_IMPORTS) {
            if (requestKey.startsWith("pro_")) {
                require(requestKey.matches(
                    Regex("pro_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                )) { "Reserva Pro inválida." }
                // Verified Pro imports have no beta free quota debit.
            } else LocalBetaImportQuota.finalize(context, isFont, requestKey, success)
        } else {
            val token = verifiedAccessToken()
            WordPressLicensingClient.finalizeUsage(
                token,
                if (isFont) "import_font" else "import_matrix",
                requestKey, success
            )
        }
        Unit
    }

    suspend fun openCheckout(planCode: String): Result<String> = runCatching {
        val token = client.auth.currentSessionOrNull()?.accessToken
            ?: error("Entre na sua conta primeiro.")
        WordPressLicensingClient.checkout(token, planCode)
    }

    private suspend fun accountFor(
        userId: String,
        email: String,
        authDisplayName: String? = null,
        authAvatarUrl: String? = null
    ): AccountSnapshot {
        val token = client.auth.currentSessionOrNull()?.accessToken
            ?: error("Sessão Google indisponível.")
        // WordPress owns 7-day trial activation and paid entitlements.
        // Its API independently authenticates the Google subject.
        // Fail closed if hosting blocks REST; the Supabase payment-receipt
        // mirror can never manufacture a free trial.
        val verified = runCatching {
            WordPressLicensingClient.account(
                userId, email, token, authDisplayName, authAvatarUrl
            )
        }.getOrNull() ?: runCatching {
            BrotherEntitlementClient.account(
                userId, email, token, authDisplayName, authAvatarUrl
            )
        }.getOrNull()
        if (verified != null) {
            lastPaidAccount = verified.takeIf { it.hasProAccess }
            lastPaidVerifiedMs = android.os.SystemClock.elapsedRealtime()
            return verified
        }
        lastPaidAccount = null
        // Fail closed on network/verification errors; never consult editable
        // profile or old WooCommerce API blocked by InfinityFree for Pro.
        return AccountSnapshot(
            userId = userId, email = email,
            displayName = authDisplayName
                ?: email.substringBefore('@').ifBlank { "Brother Matrizes" },
            avatarUrl = authAvatarUrl,
            planCode = "free", subscriptionStatus = "unavailable",
            currentPeriodEnd = null,
            availablePlans = AccountPlanCatalog.forDisplay(emptyList()),
            commercialConfigured = false
        )
    }

    const val AUTH_SCHEME =
        "com.timachado.brothermatrizes"

    const val AUTH_HOST =
        "login-callback"

    const val AUTH_REDIRECT_URL =
        "$AUTH_SCHEME://$AUTH_HOST"
}
