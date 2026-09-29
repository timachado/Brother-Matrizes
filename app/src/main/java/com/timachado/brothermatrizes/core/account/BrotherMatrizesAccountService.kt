package com.timachado.brothermatrizes.core.account

import android.content.Intent
import com.timachado.brothermatrizes.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
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
                scheme =
                    AUTH_SCHEME
                host =
                    AUTH_HOST
                defaultRedirectUrl =
                    AUTH_REDIRECT_URL
            }

            install(
                Postgrest
            )
        }
    }

    suspend fun currentAccount():
        Result<AccountSnapshot?> =
        runCatching {
            val session =
                client.auth
                    .currentSessionOrNull()
                    ?: return@runCatching null

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

    suspend fun updateDisplayName(
        displayName: String
    ): Result<AccountSnapshot> =
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

            val session =
                client.auth
                    .currentSessionOrNull()
                    ?: error(
                        "Entre na sua conta primeiro."
                    )

            val user =
                session.user
                    ?: error(
                        "Usuário não encontrado."
                    )

            client.from(
                "brother_matrizes_profiles"
            ).update(
                {
                    set(
                        "display_name",
                        name
                    )
                }
            ) {
                filter {
                    eq(
                        "user_id",
                        user.id
                    )
                }
            }

            accountFor(
                userId =
                    user.id,
                email =
                    user.email
                        ?: ""
            )
        }

    suspend fun signOut():
        Result<Unit> =
        runCatching {
            client.auth
                .signOut()
        }

    suspend fun syncDevices(
        localDevice:
            LocalDeviceIdentity
    ): Result<List<AccountDevice>> =
        runCatching {
            val session =
                client.auth
                    .currentSessionOrNull()
                    ?: error(
                        "Entre na sua conta primeiro."
                    )

            val user =
                session.user
                    ?: error(
                        "Usuário não encontrado."
                    )

            val now =
                Instant
                    .now()
                    .toString()

            val existing =
                client.from(
                    "brother_matrizes_devices"
                ).select {
                    filter {
                        eq(
                            "user_id",
                            user.id
                        )

                        eq(
                            "device_id",
                            localDevice
                                .deviceId
                        )
                    }
                }
                    .decodeList<
                        BrotherMatrizesDeviceRow
                    >()
                    .firstOrNull()

            if (
                existing ==
                    null
            ) {
                client.from(
                    "brother_matrizes_devices"
                ).insert(
                    BrotherMatrizesDeviceUpsert(
                        userId =
                            user.id,
                        deviceId =
                            localDevice
                                .deviceId,
                        deviceName =
                            localDevice
                                .deviceName,
                        platform =
                            "android",
                        appVersion =
                            BuildConfig
                                .VERSION_NAME,
                        lastSeenAt =
                            now
                    )
                )
            } else {
                client.from(
                    "brother_matrizes_devices"
                ).update(
                    {
                        set(
                            "device_name",
                            localDevice
                                .deviceName
                        )

                        set(
                            "platform",
                            "android"
                        )

                        set(
                            "app_version",
                            BuildConfig
                                .VERSION_NAME
                        )

                        set(
                            "last_seen_at",
                            now
                        )
                    }
                ) {
                    filter {
                        eq(
                            "user_id",
                            user.id
                        )

                        eq(
                            "device_id",
                            localDevice
                                .deviceId
                        )
                    }
                }
            }

            client.from(
                "brother_matrizes_devices"
            ).select {
                filter {
                    eq(
                        "user_id",
                        user.id
                    )
                }
            }
                .decodeList<
                    BrotherMatrizesDeviceRow
                >()
                .sortedByDescending {
                    it.lastSeenAt
                }
                .map {
                    AccountDevice(
                        deviceId =
                            it.deviceId,
                        deviceName =
                            it.deviceName,
                        platform =
                            it.platform,
                        appVersion =
                            it.appVersion,
                        lastSeenAt =
                            it.lastSeenAt,
                        isCurrent =
                            it.deviceId ==
                                localDevice
                                    .deviceId
                    )
                }
        }

    private suspend fun accountFor(
        userId: String,
        email: String,
        authDisplayName: String? = null,
        authAvatarUrl: String? = null
    ): AccountSnapshot {
        val profile =
            client.from(
                "brother_matrizes_profiles"
            ).select {
                filter {
                    eq(
                        "user_id",
                        userId
                    )
                }
            }
                .decodeList<
                    BrotherMatrizesProfileRow
                >()
                .firstOrNull()
                ?: run {
                    val fallbackName =
                        authDisplayName
                            ?.trim()
                            ?.takeIf {
                                it.length in
                                    2..80
                            }
                            ?: email
                                .substringBefore(
                                    '@'
                                )
                                .take(
                                    80
                                )
                                .ifBlank {
                                    "Usuário Brother Matrizes"
                                }

                    client.from(
                        "brother_matrizes_profiles"
                    ).insert(
                        BrotherMatrizesProfileUpsert(
                            userId =
                                userId,
                            displayName =
                                fallbackName,
                            avatarUrl =
                                authAvatarUrl
                        )
                    )

                    client.from(
                        "brother_matrizes_profiles"
                    ).select {
                        filter {
                            eq(
                                "user_id",
                                userId
                            )
                        }
                    }
                        .decodeList<
                            BrotherMatrizesProfileRow
                        >()
                        .firstOrNull()
                        ?: error(
                            "Não foi possível criar o perfil."
                        )
                }

        val effectiveProfile =
            if (
                profile.avatarUrl
                    .isNullOrBlank() &&
                !authAvatarUrl
                    .isNullOrBlank()
            ) {
                runCatching {
                    client.from(
                        "brother_matrizes_profiles"
                    ).update(
                        {
                            set(
                                "avatar_url",
                                authAvatarUrl
                            )
                        }
                    ) {
                        filter {
                            eq(
                                "user_id",
                                userId
                            )
                        }
                    }

                    profile.copy(
                        avatarUrl =
                            authAvatarUrl
                    )
                }.getOrDefault(
                    profile
                )
            } else {
                profile
            }

        val subscription =
            client.from(
                "brother_matrizes_subscriptions"
            ).select {
                filter {
                    eq(
                        "user_id",
                        userId
                    )
                }
            }
                .decodeList<
                    BrotherMatrizesSubscriptionRow
                >()
                .firstOrNull()

        val plans =
            runCatching {
                client.from(
                    "brother_matrizes_plans"
                ).select()
                    .decodeList<
                        BrotherMatrizesPlanRow
                    >()
                    .sortedBy {
                        it.displayOrder
                    }
                    .map {
                        AccountPlanOption(
                            code =
                                it.code,
                            name =
                                it.name,
                            billingType =
                                it.billingType,
                            isPaid =
                                it.isPaid,
                            isLifetime =
                                it.isLifetime,
                            isPromotional =
                                it.isPromotional,
                            description =
                                it.description,
                            priceCents =
                                it.priceCents,
                            currency =
                                it.currency,
                            active =
                                it.active,
                            availableFrom =
                                it.availableFrom,
                            availableUntil =
                                it.availableUntil,
                            displayOrder =
                                it.displayOrder
                        )
                    }
            }.getOrElse {
                emptyList()
            }

        val history =
            runCatching {
                client.from(
                    "brother_matrizes_subscription_history"
                ).select {
                    filter {
                        eq(
                            "user_id",
                            userId
                        )
                    }
                }
                    .decodeList<
                        BrotherMatrizesSubscriptionHistoryRow
                    >()
                    .sortedByDescending {
                        it.occurredAt
                    }
                    .take(
                        20
                    )
                    .map {
                        AccountSubscriptionEvent(
                            planCode =
                                it.planCode,
                            eventType =
                                it.eventType,
                            status =
                                it.status,
                            amountCents =
                                it.amountCents,
                            currency =
                                it.currency,
                            provider =
                                it.provider,
                            occurredAt =
                                it.occurredAt
                        )
                    }
            }.getOrElse {
                emptyList()
            }

        return AccountSnapshot(
            userId =
                userId,
            email =
                email,
            displayName =
                effectiveProfile
                    .displayName,
            avatarUrl =
                effectiveProfile
                    .avatarUrl,
            planCode =
                subscription
                    ?.planCode
                    ?: "free",
            subscriptionStatus =
                subscription
                    ?.status
                    ?: "active",
            currentPeriodEnd =
                subscription
                    ?.currentPeriodEnd,
            purchasedAt =
                subscription
                    ?.purchasedAt,
            purchasePriceCents =
                subscription
                    ?.purchasePriceCents,
            currency =
                subscription
                    ?.currency
                    ?: "BRL",
            provider =
                subscription
                    ?.provider,
            manageUrl =
                subscription
                    ?.manageUrl,
            availablePlans =
                plans,
            subscriptionHistory =
                history
        )
    }

    const val AUTH_SCHEME =
        "com.timachado.brothermatrizes"

    const val AUTH_HOST =
        "login-callback"

    const val AUTH_REDIRECT_URL =
        "$AUTH_SCHEME://$AUTH_HOST"
}
