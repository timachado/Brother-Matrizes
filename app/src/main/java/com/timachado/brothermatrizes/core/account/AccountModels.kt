package com.timachado.brothermatrizes.core.account

import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BrotherMatrizesProfileRow(
    @SerialName("user_id")
    val userId: String,
    @SerialName("display_name")
    val displayName: String,
    @SerialName("avatar_url")
    val avatarUrl: String? = null
)

@Serializable
data class BrotherMatrizesProfileUpsert(
    @SerialName("user_id")
    val userId: String,
    @SerialName("display_name")
    val displayName: String,
    @SerialName("avatar_url")
    val avatarUrl: String? = null
)

@Serializable
data class BrotherMatrizesPlanRow(
    val code: String,
    val name: String,
    @SerialName("billing_type")
    val billingType: String,
    @SerialName("is_paid")
    val isPaid: Boolean,
    @SerialName("is_lifetime")
    val isLifetime: Boolean,
    @SerialName("is_promotional")
    val isPromotional: Boolean,
    val description: String,
    @SerialName("price_cents")
    val priceCents: Int? = null,
    val currency: String = "BRL",
    val active: Boolean = true,
    @SerialName("available_from")
    val availableFrom: String? = null,
    @SerialName("available_until")
    val availableUntil: String? = null,
    @SerialName("display_order")
    val displayOrder: Int = 0
)

@Serializable
data class BrotherMatrizesSubscriptionRow(
    @SerialName("user_id")
    val userId: String,
    @SerialName("plan_code")
    val planCode: String,
    val status: String,
    val source: String,
    @SerialName("current_period_end")
    val currentPeriodEnd: String? = null,
    @SerialName("purchased_at")
    val purchasedAt: String? = null,
    @SerialName("purchase_price_cents")
    val purchasePriceCents: Int? = null,
    val currency: String = "BRL",
    val provider: String? = null,
    @SerialName("external_reference")
    val externalReference: String? = null,
    @SerialName("manage_url")
    val manageUrl: String? = null
)

@Serializable
data class BrotherMatrizesDeviceRow(
    @SerialName("user_id")
    val userId: String,
    @SerialName("device_id")
    val deviceId: String,
    @SerialName("device_name")
    val deviceName: String,
    val platform: String,
    @SerialName("app_version")
    val appVersion: String,
    @SerialName("last_seen_at")
    val lastSeenAt: String
)

@Serializable
data class BrotherMatrizesDeviceUpsert(
    @SerialName("user_id")
    val userId: String,
    @SerialName("device_id")
    val deviceId: String,
    @SerialName("device_name")
    val deviceName: String,
    val platform: String,
    @SerialName("app_version")
    val appVersion: String,
    @SerialName("last_seen_at")
    val lastSeenAt: String
)

@Serializable
data class BrotherMatrizesSubscriptionHistoryRow(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("plan_code")
    val planCode: String,
    @SerialName("event_type")
    val eventType: String,
    val status: String,
    @SerialName("amount_cents")
    val amountCents: Int? = null,
    val currency: String = "BRL",
    val provider: String? = null,
    @SerialName("external_reference")
    val externalReference: String? = null,
    @SerialName("occurred_at")
    val occurredAt: String
)

data class AccountPlanOption(
    val code: String,
    val name: String,
    val billingType: String,
    val isPaid: Boolean,
    val isLifetime: Boolean,
    val isPromotional: Boolean,
    val description: String,
    val priceCents: Int?,
    val currency: String,
    val active: Boolean,
    val availableFrom: String?,
    val availableUntil: String?,
    val displayOrder: Int
)

/**
 * Display-only placeholders preserve the approved plan section when WordPress
 * has not been configured or a request fails. They are never entitlements,
 * orders or prices: all offers are disabled until fetched from WooCommerce.
 */
object AccountPlanCatalog {
    fun forDisplay(serverPlans: List<AccountPlanOption>): List<AccountPlanOption> {
        if (serverPlans.isNotEmpty()) {
            return serverPlans.distinctBy { it.code }.sortedBy { it.displayOrder }
        }
        return listOf(
            placeholder("free", "Gratuito", "free", false, 0),
            placeholder("pro_monthly", "Pro Mensal", "monthly", false, 1),
            placeholder("pro_yearly", "Pro Anual", "yearly", false, 2),
            placeholder("pro_lifetime", "Pro Vitalício", "one_time", true, 3),
            placeholder("pro_lifetime_launch", "Vitalício Lançamento", "one_time", true, 4)
        )
    }

    private fun placeholder(
        code: String, name: String, billing: String,
        lifetime: Boolean, index: Int
    ): AccountPlanOption = AccountPlanOption(
        code = code,
        name = name,
        billingType = billing,
        isPaid = code != "free",
        isLifetime = lifetime,
        isPromotional = code == "pro_lifetime_launch",
        description = if (code == "free") "Acesso gratuito permanente."
        else "Disponível após configuração dos produtos no WooCommerce.",
        priceCents = if (code == "free") 0 else null,
        currency = "BRL",
        active = false,
        availableFrom = null,
        availableUntil = null,
        displayOrder = index
    )
}

data class AccountDevice(
    val deviceId: String,
    val deviceName: String,
    val platform: String,
    val appVersion: String,
    val lastSeenAt: String,
    val isCurrent: Boolean
)

enum class BrotherMatrizesFeature {
    CORE,
    PRO_ONLY
}

data class AccountSubscriptionEvent(
    val planCode: String,
    val eventType: String,
    val status: String,
    val amountCents: Int?,
    val currency: String,
    val provider: String?,
    val occurredAt: String
)

data class AccountSnapshot(
    val userId: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String?,
    val planCode: String,
    val subscriptionStatus: String,
    val currentPeriodEnd: String?,
    val purchasedAt: String? = null,
    val purchasePriceCents: Int? = null,
    val currency: String = "BRL",
    val provider: String? = null,
    val manageUrl: String? = null,
    val devices: List<AccountDevice> =
        emptyList(),
    val currentDeviceId: String? = null,
    val availablePlans: List<AccountPlanOption> =
        emptyList(),
    val subscriptionHistory:
        List<AccountSubscriptionEvent> =
        emptyList(),
    val trialStatus: String = "unknown",
    val trialRemainingSeconds: Long = 0L,
    val quotaRemaining: Map<String, Int> = emptyMap(),
    val commercialConfigured: Boolean = false
) {
    val currentPlan: AccountPlanOption?
        get() =
            availablePlans
                .firstOrNull {
                    it.code ==
                        planCode
                }

    val isPaid: Boolean
        get() =
            planCode.trim().lowercase() != "trial" &&
                (currentPlan?.isPaid == true ||
                    planCode.trim().lowercase() != "free")

    val isLifetime: Boolean
        get() =
            currentPlan
                ?.isLifetime ==
                true ||
                planCode
                    .trim()
                    .lowercase() in
                    setOf(
                        "lifetime",
                        "lifetime_launch",
                        "pro_lifetime",
                        "pro_lifetime_launch"
                    )

    val isLaunchLifetime: Boolean
        get() =
            currentPlan
                ?.isPromotional ==
                true ||
                planCode
                    .equals(
                        "pro_lifetime_launch",
                        ignoreCase =
                            true
                    )

    val isSubscriptionUsable: Boolean
        get() =
            subscriptionStatus in
                setOf(
                    "active",
                    "trialing"
                )

    // Recurring entitlements expire server-side even when an out-of-date
    // cached status still says active. Never grant paid access from a local
    // button, price display or a user-editable profile field.
    val hasProAccess: Boolean
        get() {
            if (!isSubscriptionUsable) return false
            val expiry = currentPeriodEnd
                ?.let { runCatching { Instant.parse(it) }.getOrNull() }
            if (planCode.trim().lowercase() == "trial") {
                return expiry?.isAfter(Instant.now()) == true
            }
            if (!isPaid) return false
            if (isLifetime) return true
            return expiry?.isAfter(Instant.now()) == true
        }

    fun canUse(
        feature: BrotherMatrizesFeature
    ): Boolean =
        when (
            feature
        ) {
            BrotherMatrizesFeature.CORE ->
                true

            BrotherMatrizesFeature.PRO_ONLY ->
                hasProAccess
        }
}

sealed interface SignUpOutcome {
    data class SignedIn(
        val account: AccountSnapshot
    ) : SignUpOutcome

    data class ConfirmationRequired(
        val email: String
    ) : SignUpOutcome
}

object AccountPresentation {
    fun planLabel(
        planCode: String
    ): String =
        when (
            planCode
                .trim()
                .lowercase()
        ) {
            "free" ->
                "Gratuito"

            "pro" ->
                "Brother Matrizes Pro"

            "premium" ->
                "Brother Matrizes Premium"

            "pro_monthly" ->
                "Brother Matrizes Pro Mensal"

            "pro_annual", "pro_yearly" ->
                "Brother Matrizes Pro Anual"

            "lifetime", "pro_lifetime" ->
                "Brother Matrizes Vitalício"

            "lifetime_launch", "pro_lifetime_launch" ->
                "Brother Matrizes Vitalício • Lançamento"

            else ->
                planCode
                    .trim()
                    .replaceFirstChar {
                        if (
                            it.isLowerCase()
                        ) {
                            it.titlecase()
                        } else {
                            it.toString()
                        }
                    }
        }

    fun billingLabel(
        billingType: String
    ): String =
        when (
            billingType
                .trim()
                .lowercase()
        ) {
            "free" ->
                "Gratuito"

            "monthly" ->
                "Mensal"

            "annual" ->
                "Anual"

            "lifetime" ->
                "Acesso permanente"

            else ->
                billingType
        }

    fun statusLabel(
        status: String
    ): String =
        when (
            status
                .trim()
                .lowercase()
        ) {
            "active" ->
                "Ativa"

            "trialing" ->
                "Período de teste"

            "past_due" ->
                "Pagamento pendente"

            "canceled" ->
                "Cancelada"

            "expired" ->
                "Expirada"

            else ->
                status
                    .trim()
                    .replaceFirstChar {
                        if (
                            it.isLowerCase()
                        ) {
                            it.titlecase()
                        } else {
                            it.toString()
                        }
                    }
        }

    fun eventLabel(
        eventType: String
    ): String =
        when (
            eventType
                .trim()
                .lowercase()
        ) {
            "created" ->
                "Cadastro"

            "activated" ->
                "Ativação"

            "renewed" ->
                "Renovação"

            "upgraded" ->
                "Upgrade"

            "downgraded" ->
                "Alteração de plano"

            "canceled" ->
                "Cancelamento"

            "expired" ->
                "Expiração"

            "payment_failed" ->
                "Falha no pagamento"

            "refunded" ->
                "Reembolso"

            "lifetime_purchase" ->
                "Compra vitalícia"

            else ->
                eventType
        }
}
