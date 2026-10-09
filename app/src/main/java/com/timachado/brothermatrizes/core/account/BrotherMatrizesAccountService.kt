package com.timachado.brothermatrizes.core.account

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
            val token = client.auth.currentSessionOrNull()?.accessToken
                ?: error("Entre na sua conta primeiro.")
            WordPressLicensingClient.updateProfile(token, name)
            currentAccount().getOrThrow()
                ?: error("Conta autenticada não encontrada.")
        }

    suspend fun signOut():
        Result<Unit> =
        runCatching {
            client.auth
                .signOut()
        }

    suspend fun syncDevices(localDevice: LocalDeviceIdentity): Result<List<AccountDevice>> =
        runCatching {
            val token = client.auth.currentSessionOrNull()?.accessToken
                ?: error("Entre na sua conta primeiro.")
            WordPressLicensingClient.syncDevices(token, localDevice)
        }

    suspend fun activateTrial(): Result<AccountSnapshot> = runCatching {
        val token = client.auth.currentSessionOrNull()?.accessToken
            ?: error("Entre na sua conta primeiro.")
        WordPressLicensingClient.trial(token)
        currentAccount().getOrThrow() ?: error("Conta não encontrada.")
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
        // WordPress is the only commercial source; never query the legacy
        // Supabase subscription, profile, device or pricing tables.
        return runCatching {
            WordPressLicensingClient.account(
                userId, email, token, authDisplayName, authAvatarUrl
            )
        }.getOrElse {
            // Keep Google's verified identity visible if WordPress is being
            // installed or temporarily offline; NEVER infer Pro permission.
            AccountSnapshot(
                userId = userId,
                email = email,
                displayName = authDisplayName
                    ?: email.substringBefore('@').ifBlank { "Brother Matrizes" },
                avatarUrl = authAvatarUrl,
                planCode = "free",
                subscriptionStatus = "unavailable",
                currentPeriodEnd = null,
                commercialConfigured = false
            )
        }
    }

    const val AUTH_SCHEME =
        "com.timachado.brothermatrizes"

    const val AUTH_HOST =
        "login-callback"

    const val AUTH_REDIRECT_URL =
        "$AUTH_SCHEME://$AUTH_HOST"
}
