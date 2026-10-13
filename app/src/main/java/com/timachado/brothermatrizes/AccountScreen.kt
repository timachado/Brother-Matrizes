package com.timachado.brothermatrizes

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.brothermatrizes.core.account.AccountPlanOption
import com.timachado.brothermatrizes.core.account.AccountPlanCatalog
import com.timachado.brothermatrizes.core.account.BrotherLaunchPriceDisplay
import com.timachado.brothermatrizes.core.account.BrotherPlanBenefits
import com.timachado.brothermatrizes.core.account.AccountPresentation
import com.timachado.brothermatrizes.core.account.AccountSnapshot
import com.timachado.brothermatrizes.core.account.AccountSubscriptionEvent
import com.timachado.brothermatrizes.core.network.SafeRemoteImage
import com.timachado.brothermatrizes.core.settings.UiPreferencesStore
import com.timachado.brothermatrizes.core.settings.AppColorMode
import com.timachado.brothermatrizes.ui.theme.FioBackground
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioSurface
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

private enum class AccountMode(
    val label: String
) {
    SIGN_IN("Entrar"),
    SIGN_UP("Criar conta")
}

@Composable
fun AccountScreen(
    account: AccountSnapshot?,
    offline: Boolean = false,
    onBack: () -> Unit,
    textScale: Float,
    onTextScaleChange: (Float) -> Unit,
    colorMode: AppColorMode,
    onColorModeChange: (AppColorMode) -> Unit,
    onGoogleSignIn: () -> Unit,
    onSignIn: (
        email: String,
        password: String
    ) -> Unit,
    onSignUp: (
        displayName: String,
        email: String,
        password: String
    ) -> Unit,
    onSaveName: (
        displayName: String
    ) -> Unit,
    onRefresh: () -> Unit,
    onActivateTrial: () -> Unit,
    onOpenWebAccount: () -> Unit,
    onOpenPlan: (String) -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(
                horizontal =
                    16.dp
            )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical =
                        6.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onBack
            ) {
                Text(
                    "‹ Voltar",
                    color =
                        FioGold
                )
            }

            Column(
                Modifier.weight(
                    1f
                )
            ) {
                Text(
                    "Minha Conta",
                    color =
                        FioText,
                    fontWeight =
                        FontWeight.Bold,
                    fontSize =
                        20.sp
                )

                Text(
                    if (
                        account ==
                            null
                    ) {
                        "Entre para acompanhar sua conta."
                    } else {
                        "Perfil e assinatura Brother Matrizes."
                    },
                    color =
                        FioTextMuted,
                    fontSize =
                        10.sp
                )
            }
        }

        if (BuildConfig.BETA_LOCAL_IMPORTS) {
            Text(
                when {
                    account?.planCode == "trial" && account.hasProAccess ->
                        "TESTE PRO ATIVO • Acesso completo durante os 7 dias gratuitos, sem cartão."
                    account?.hasProAccess == true ->
                        "BROTHER MATRIZES PRO • Licença validada. Consulte seu plano e renovação abaixo."
                    else ->
                        "PLANO GRATUITO • 3 fontes e 5 matrizes por mês neste aparelho. " +
                            "Conheça as opções Pro abaixo."
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                color = FioGold,
                fontSize = 10.sp
            )
        }

        // Scroll all account content together so the preference card cannot
        // consume a fixed-height strip while subscriptions scroll beneath it.
        // The title/back row stays visible, but settings and account cards are
        // now part of one uninterrupted viewport and scrolling container.
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
        if (
            offline
        ) {
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            top =
                                6.dp,
                            bottom =
                                8.dp
                        ),
                colors =
                    CardDefaults
                        .cardColors(
                            containerColor =
                                FioGold.copy(
                                    alpha =
                                        .10f
                                )
                        ),
                shape =
                    RoundedCornerShape(
                        18.dp
                    )
            ) {
                Column(
                    Modifier.padding(
                        14.dp
                    )
                ) {
                    Text(
                        "Sem internet",
                        color =
                            FioGold,
                        fontWeight =
                            FontWeight.Bold,
                        fontSize =
                            12.sp
                    )

                    Text(
                        if (
                            account ==
                                null
                        ) {
                            "Sua sessão não foi encerrada. Reconecte para carregar os dados da conta."
                        } else {
                            "Os dados exibidos permanecem na tela, mas assinatura e dispositivos só atualizam quando a conexão voltar."
                        },
                        modifier =
                            Modifier.padding(
                                top =
                                    3.dp
                            ),
                        color =
                            FioTextMuted,
                        fontSize =
                            10.sp
                    )

                    OutlinedButton(
                        onClick =
                            onRefresh,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    top =
                                        8.dp
                                )
                    ) {
                        Text(
                            "Tentar novamente"
                        )
                    }
                }
            }
        }

        TextScalePreferenceCard(
            textScale = textScale,
            onTextScaleChange = onTextScaleChange,
            colorMode = colorMode,
            onColorModeChange = onColorModeChange
        )

        Spacer(
            Modifier.height(
                10.dp
            )
        )

        if (
            account ==
                null
        ) {
            SignedOutAccount(
                onGoogleSignIn =
                    onGoogleSignIn,
                onSignIn =
                    onSignIn,
                onSignUp =
                    onSignUp,
                onOpenWebAccount = onOpenWebAccount
            )
        } else {
            SignedInAccount(
                account =
                    account,
                onSaveName =
                    onSaveName,
                onRefresh = onRefresh,
                onActivateTrial = onActivateTrial,
                onOpenWebAccount = onOpenWebAccount,
                onOpenPlan = onOpenPlan,
                onSignOut = onSignOut
            )
        }
        }
    }
}

@Composable
private fun TextScalePreferenceCard(
    textScale: Float,
    onTextScaleChange: (Float) -> Unit,
    colorMode: AppColorMode,
    onColorModeChange: (AppColorMode) -> Unit
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth(),
        colors =
            CardDefaults
                .cardColors(
                    containerColor =
                        FioSurface
                ),
        shape =
            RoundedCornerShape(
                20.dp
            )
    ) {
        Column(
            Modifier.padding(
                14.dp
            )
        ) {
            Text(
                "Tamanho do texto",
                color =
                    FioText,
                fontWeight =
                    FontWeight.Bold,
                fontSize =
                    14.sp
            )

            Text(
                "Ajuste a leitura do Brother Matrizes neste aparelho. A escala do Android continua respeitada.",
                modifier =
                    Modifier.padding(
                        top =
                            3.dp,
                        bottom =
                            9.dp
                    ),
                color =
                    FioTextMuted,
                fontSize =
                    9.sp
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        6.dp
                    )
            ) {
                UiPreferencesStore
                    .textScaleOptions
                    .forEach {
                            option ->
                        val selected =
                            UiPreferencesStore
                                .normalizeTextScale(
                                    textScale
                                ) ==
                                option

                        OutlinedButton(
                            onClick = {
                                onTextScaleChange(
                                    option
                                )
                            },
                            modifier =
                                Modifier.weight(
                                    1f
                                )
                        ) {
                            Text(
                                (
                                    if (
                                        selected
                                    ) {
                                        "● "
                                    } else {
                                        ""
                                    }
                                    ) +
                                    (
                                        option *
                                            100
                                        ).toInt() +
                                    "%",
                                color =
                                    if (
                                        selected
                                    ) {
                                        FioGold
                                    } else {
                                        FioText
                                    },
                                fontSize =
                                    9.sp
                            )
                        }
                    }
            }

            Text(
                "Atual: " +
                    UiPreferencesStore
                        .textScaleLabel(
                            textScale
                        ),
                modifier =
                    Modifier.padding(
                        top =
                            6.dp
                    ),
                color =
                    FioGold,
                fontWeight =
                    FontWeight.SemiBold,
                fontSize =
                    9.sp
            )

            Spacer(Modifier.height(12.dp))
            Text(
                "Aparência",
                color = FioText,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Text(
                "Escolha o tema deste aparelho. O modo Sistema acompanha o Android.",
                color = FioTextMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AppColorMode.entries.forEach { option ->
                    OutlinedButton(
                        onClick = { onColorModeChange(option) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            (if (colorMode == option) "● " else "") + option.label,
                            color = if (colorMode == option) FioGold else FioText,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SignedOutAccount(
    onGoogleSignIn: () -> Unit,
    onOpenWebAccount: () -> Unit,
    onSignIn: (
        email: String,
        password: String
    ) -> Unit,
    onSignUp: (
        displayName: String,
        email: String,
        password: String
    ) -> Unit
) {
    var mode by remember {
        mutableStateOf(
            AccountMode.SIGN_IN
        )
    }

    var displayName by remember {
        mutableStateOf("")
    }

    var email by remember {
        mutableStateOf("")
    }

    var password by remember {
        mutableStateOf("")
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(
                top =
                    18.dp,
                bottom =
                    30.dp
            )
    ) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth(),
            colors =
                CardDefaults
                    .cardColors(
                        containerColor =
                            FioSurface
                    ),
            shape =
                RoundedCornerShape(
                    24.dp
                )
        ) {
            Column(
                Modifier.padding(
                    18.dp
                )
            ) {
                OutlinedButton(
                    onClick =
                        onGoogleSignIn,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                ) {
                    Text(
                        "G  Continuar com Google",
                        color =
                            FioText,
                        fontWeight =
                            FontWeight.SemiBold
                    )
                }

                Text(
                    "Mais rápido e sem precisar criar outra senha no Brother Matrizes.",
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                top =
                                    6.dp,
                                bottom =
                                    14.dp
                            ),
                    color =
                        FioTextMuted,
                    fontSize =
                        10.sp
                )

                Row(
                    Modifier
                        .fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {
                    AccountMode
                        .entries
                        .forEach {
                                option ->
                            val selected =
                                mode ==
                                    option

                            OutlinedButton(
                                onClick = {
                                    mode =
                                        option
                                },
                                modifier =
                                    Modifier.weight(
                                        1f
                                    )
                            ) {
                                Text(
                                    if (
                                        selected
                                    ) {
                                        "● " +
                                            option.label
                                    } else {
                                        option.label
                                    },
                                    color =
                                        if (
                                            selected
                                        ) {
                                            FioGold
                                        } else {
                                            FioText
                                        }
                                )
                            }
                        }
                }

                Spacer(
                    Modifier.height(
                        14.dp
                    )
                )

                if (
                    mode ==
                        AccountMode
                            .SIGN_UP
                ) {
                    OutlinedTextField(
                        value =
                            displayName,
                        onValueChange = {
                            displayName =
                                it.take(
                                    80
                                )
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth(),
                        label = {
                            Text(
                                "Seu nome"
                            )
                        },
                        singleLine =
                            true
                    )

                    Spacer(
                        Modifier.height(
                            10.dp
                        )
                    )
                }

                OutlinedTextField(
                    value =
                        email,
                    onValueChange = {
                        email = it
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                    label = {
                        Text(
                            "E-mail"
                        )
                    },
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType =
                                KeyboardType
                                    .Email
                        ),
                    singleLine =
                        true
                )

                Spacer(
                    Modifier.height(
                        10.dp
                    )
                )

                OutlinedTextField(
                    value =
                        password,
                    onValueChange = {
                        password = it
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                    label = {
                        Text(
                            "Senha"
                        )
                    },
                    visualTransformation =
                        PasswordVisualTransformation(),
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType =
                                KeyboardType
                                    .Password
                        ),
                    singleLine =
                        true
                )

                if (
                    mode ==
                        AccountMode
                            .SIGN_UP
                ) {
                    Text(
                        "Use pelo menos 8 caracteres. Dependendo da configuração de segurança, você receberá um e-mail para confirmar a conta.",
                        modifier =
                            Modifier.padding(
                                top =
                                    8.dp
                            ),
                        color =
                            FioTextMuted,
                        fontSize =
                            10.sp
                    )
                }

                Spacer(
                    Modifier.height(
                        16.dp
                    )
                )

                Button(
                    onClick = {
                        if (
                            mode ==
                                AccountMode
                                    .SIGN_IN
                        ) {
                            onSignIn(
                                email,
                                password
                            )
                        } else {
                            onSignUp(
                                displayName,
                                email,
                                password
                            )
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                    colors =
                        ButtonDefaults
                            .buttonColors(
                                containerColor =
                                    FioGold,
                                contentColor =
                                    FioBackground
                            )
                ) {
                    Text(
                        if (
                            mode ==
                                AccountMode
                                    .SIGN_IN
                        ) {
                            "Entrar"
                        } else {
                            "Criar minha conta"
                        },
                        fontWeight =
                            FontWeight.Bold
                    )
                }
            }
        }

        Spacer(
            Modifier.height(
                14.dp
            )
        )

        OutlinedButton(
            onClick = onOpenWebAccount,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Minhas compras e licenças no site", color = FioGold)
        }
        Text(
            "A loja utiliza uma sessão separada no navegador. Fazer login no site não ativa uma licença Pro neste aplicativo.",
            color = FioTextMuted,
            fontSize = 10.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Text(
            "Suas matrizes continuam salvas localmente no celular. Entrar na conta não envia automaticamente seus projetos para a nuvem.",
            color =
                FioTextMuted,
            fontSize =
                10.sp
        )

        Spacer(
            Modifier.height(
                14.dp
            )
        )

        AboutBrotherMatrizesCard()

        Spacer(
            Modifier.height(
                14.dp
            )
        )

        UpdateStatusCard()
    }
}

@Composable
private fun SignedInAccount(
    account: AccountSnapshot,
    onSaveName: (
        displayName: String
    ) -> Unit,
    onRefresh: () -> Unit,
    onActivateTrial: () -> Unit,
    onOpenWebAccount: () -> Unit,
    onOpenPlan: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val context =
        LocalContext.current

    var displayName by remember(
        account.userId,
        account.displayName
    ) {
        mutableStateOf(
            account.displayName
        )
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(
                top =
                    18.dp,
                bottom =
                    30.dp
            )
    ) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth(),
            colors =
                CardDefaults
                    .cardColors(
                        containerColor =
                            FioSurface
                    ),
            shape =
                RoundedCornerShape(
                    24.dp
                )
        ) {
            Column(
                Modifier.padding(
                    18.dp
                ),
                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {
                AccountAvatar(
                    account =
                        account
                )

                Spacer(
                    Modifier.height(
                        12.dp
                    )
                )

                Text(
                    account.displayName,
                    color =
                        FioText,
                    fontWeight =
                        FontWeight.Bold,
                    fontSize =
                        20.sp
                )

                Text(
                    account.email,
                    color =
                        FioTextMuted,
                    fontSize =
                        11.sp
                )

                Spacer(
                    Modifier.height(
                        18.dp
                    )
                )

                OutlinedTextField(
                    value =
                        displayName,
                    onValueChange = {
                        displayName =
                            it.take(
                                80
                            )
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                    label = {
                        Text(
                            "Nome no Brother Matrizes"
                        )
                    },
                    singleLine =
                        true
                )

                Spacer(
                    Modifier.height(
                        10.dp
                    )
                )

                Button(
                    onClick = {
                        onSaveName(
                            displayName
                        )
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                    colors =
                        ButtonDefaults
                            .buttonColors(
                                containerColor =
                                    FioGold,
                                contentColor =
                                    FioBackground
                            )
                ) {
                    Text(
                        "Salvar nome",
                        fontWeight =
                            FontWeight.Bold
                    )
                }
            }
        }

        Spacer(
            Modifier.height(
                14.dp
            )
        )

        Card(
            modifier =
                Modifier
                    .fillMaxWidth(),
            colors =
                CardDefaults
                    .cardColors(
                        containerColor =
                            FioSurface
                    ),
            shape =
                RoundedCornerShape(
                    24.dp
                )
        ) {
            Column(
                Modifier.padding(
                    18.dp
                )
            ) {
                Text(
                    "Minha assinatura",
                    color =
                        FioText,
                    fontWeight =
                        FontWeight.Bold,
                    fontSize =
                        18.sp
                )

                Spacer(
                    Modifier.height(
                        10.dp
                    )
                )

                if (!account.commercialConfigured) {
                    Text(
                        "Seu login continua conectado. Não foi possível confirmar seu plano agora. " +
                            "Seus dados e suas compras foram preservados.",
                        color = FioTextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                } else {
                    when (account.trialStatus) {
                        "unknown" -> {
                            Text(
                                "Teste Pro por 7 dias, sem cartão. A disponibilidade " +
                                    "ainda não foi confirmada pelo servidor. Confira sua conexão.",
                                color = FioTextMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                        }
                        "eligible" -> {
                            if (!account.hasProAccess) {
                            Button(
                                onClick = onActivateTrial,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Ativar 7 dias grátis Pro — sem cartão") }
                            Text(
                                "Começa ao ativar. Não haverá cobrança automática.",
                                color = FioTextMuted,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 5.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            }
                        }
                        "active" -> {
                            Text(
                                "Teste Pro ativo • " +
                                    ((account.trialRemainingSeconds + 86399L) / 86400L) +
                                    " dia(s) restante(s)",
                                color = FioGold,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                        "expired" -> {
                            Text(
                                "SEU TESTE GRATUITO TERMINOU. Seus projetos e fontes " +
                                    "permanecem salvos. Assine o Pro ou continue gratuitamente.",
                                color = FioTextMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                        }
                    }
                    if (account.quotaRemaining.isNotEmpty()) {
                        val titles = mapOf(
                            "create_name" to "Criar nomes",
                            "export_matrix" to "Exportar matrizes",
                            "import_font" to "Importar fontes",
                            "import_matrix" to "Importar matrizes"
                        )
                        Text("Cotas gratuitas restantes", color = FioText,
                            fontWeight = FontWeight.SemiBold)
                        account.quotaRemaining.forEach { (key, remaining) ->
                            Text("${titles[key] ?: key}: $remaining",
                                color = FioTextMuted, fontSize = 11.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }

                val currentPlan =
                    account.currentPlan

                AccountLine(
                    label =
                        "Plano",
                    value =
                        if (!account.commercialConfigured) "Não verificado"
                        else currentPlan?.name ?: AccountPresentation
                            .planLabel(account.planCode)
                )

                AccountLine(
                    label =
                        "Tipo",
                    value =
                        if (!account.commercialConfigured) "Aguardando confirmação"
                        else currentPlan
                            ?.let {
                                AccountPresentation
                                    .billingLabel(
                                        it.billingType
                                    )
                            }
                            ?: if (account.planCode == "trial") {
                                "Teste gratuito sem cartão"
                            } else if (
                                account.isLifetime
                            ) {
                                "Acesso permanente"
                            } else if (
                                account.isPaid
                            ) {
                                "Assinatura"
                            } else {
                                "Gratuito"
                            }
                )

                AccountLine(
                    label =
                        "Status",
                    value =
                        AccountPresentation
                            .statusLabel(
                                account
                                    .subscriptionStatus
                            )
                )

                if (
                    account.isPaid &&
                    !account.purchasedAt
                        .isNullOrBlank()
                ) {
                    AccountLine(
                        label =
                            "Início",
                        value =
                            formattedDate(
                                account
                                    .purchasedAt
                            )
                    )
                }

                when {
                    account.planCode == "trial" -> {
                        AccountLine(
                            label = "Fim do teste",
                            value = formattedDate(account.currentPeriodEnd)
                        )
                    }
                    account.isLifetime -> {
                        AccountLine(
                            label =
                                "Acesso",
                            value =
                                "Permanente"
                        )

                        Unit
                    }

                    account.isPaid -> {
                        AccountLine(
                            label =
                                "Próxima renovação",
                            value =
                                formattedDate(
                                    account
                                        .currentPeriodEnd
                                )
                        )
                    }

                    else -> {
                        AccountLine(
                            label =
                                "Renovação",
                            value =
                                "Não se aplica"
                        )
                    }
                }

                if (
                    account.purchasePriceCents !=
                        null
                ) {
                    AccountLine(
                        label =
                            "Valor pago",
                        value =
                            formattedMoney(
                                account
                                    .purchasePriceCents,
                                account.currency
                            )
                    )
                }

                account.provider
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?.let {
                        AccountLine(
                            label =
                                "Pagamento",
                            value =
                                it
                                    .replaceFirstChar {
                                            first ->
                                        first
                                            .uppercase()
                                    }
                        )
                    }

                if (
                    account.isLaunchLifetime
                ) {
                    Spacer(
                        Modifier.height(
                            10.dp
                        )
                    )

                    Text(
                        "★ Membro de Lançamento • benefício vitalício preservado",
                        color =
                            FioGold,
                        fontWeight =
                            FontWeight.SemiBold,
                        fontSize =
                            11.sp
                    )
                }

                Spacer(
                    Modifier.height(
                        10.dp
                    )
                )

                Text(
                    when {
                        account.isLifetime ->
                            "Este plano não possui renovação nem próxima cobrança."

                        account.planCode == "trial" ->
                            "Teste gratuito sem cartão e sem cobrança automática. " +
                                "Ao terminar, o aplicativo volta para o plano Gratuito."

                        account.isPaid ->
                            "O Brother Matrizes acompanha aqui o plano, status e renovação vinculados à sua conta."

                        else ->
                            "Após o pagamento, toque em Verificar licença. O Pro só será liberado depois da confirmação segura no servidor."
                    },
                    color =
                        FioTextMuted,
                    fontSize =
                        10.sp
                )

                Spacer(
                    Modifier.height(
                        12.dp
                    )
                )

                OutlinedButton(
                    onClick =
                        onRefresh,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                ) {
                    Text(
                        "↻ Verificar licença",
                        color =
                            FioGold,
                        fontWeight =
                            FontWeight.SemiBold
                    )
                }

                if (
                    account.isPaid &&
                    !account.isLifetime
                ) {
                    Spacer(
                        Modifier.height(
                            8.dp
                        )
                    )

                    val manageUrl =
                        account.manageUrl
                            ?.takeIf {
                                it.startsWith(
                                    "https://"
                                )
                            }

                    Row(
                        Modifier
                            .fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                8.dp
                            )
                    ) {
                        OutlinedButton(
                            onClick = {
                                manageUrl
                                    ?.let {
                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse(
                                                    it
                                                )
                                            )
                                        )
                                    }
                            },
                            enabled =
                                manageUrl !=
                                    null,
                            modifier =
                                Modifier.weight(
                                    1f
                                )
                        ) {
                            Text(
                                "Renovar / gerenciar"
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                manageUrl
                                    ?.let {
                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse(
                                                    it
                                                )
                                            )
                                        )
                                    }
                            },
                            enabled =
                                manageUrl !=
                                    null,
                            modifier =
                                Modifier.weight(
                                    1f
                                )
                        ) {
                            Text(
                                "Cancelar"
                            )
                        }
                    }

                    if (
                        manageUrl ==
                            null
                    ) {
                        Text(
                            "As opções de renovação e cancelamento ficarão disponíveis após a confirmação da sua compra.",
                            modifier =
                                Modifier.padding(
                                    top =
                                        6.dp
                                ),
                            color =
                                FioTextMuted,
                            fontSize =
                                9.sp
                        )
                    }
                }
            }
        }

        Spacer(
            Modifier.height(
                14.dp
            )
        )

        // Keep the plan section visible even during commercial API
        // outages or before the WooCommerce product IDs are mapped.
        // No cached/placeholder price is ever presented as an active offer.
        val visiblePlans = AccountPlanCatalog.forDisplay(account.availablePlans)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = FioSurface),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    "Escolha seu plano",
                    color = FioText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    if (account.availablePlans.isEmpty())
                        "Valores de referência. Confira preço e disponibilidade no site."
                    else "Compare as opções e escolha como prefere trabalhar.",
                    modifier = Modifier.padding(top = 3.dp, bottom = 10.dp),
                    color = FioTextMuted,
                    fontSize = 11.sp
                )
                visiblePlans.forEachIndexed { index, plan ->
                    PlanSummary(
                        plan = plan,
                        current = account.commercialConfigured &&
                            account.hasProAccess && plan.code == account.planCode ||
                            (!plan.isPaid && account.planCode == "free"),
                        hasActiveLifetime = account.commercialConfigured &&
                            account.hasProAccess && account.isLifetime,
                        onOpenPlan = onOpenPlan
                    )
                    if (index < visiblePlans.lastIndex) {
                        Spacer(Modifier.height(10.dp))
                    }
                }
                if (account.availablePlans.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onRefresh,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Atualizar planos", color = FioGold) }
                }
                Text(
                    "*Valores informativos. Checkout e WooCommerce confirmam preço, estoque e renovação.",
                    color = FioTextMuted, fontSize = 10.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
                TextButton(onClick = onOpenWebAccount, modifier = Modifier.fillMaxWidth()) {
                    Text("Minhas compras e licenças", color = FioGold)
                }
            }
        }

        BrotherPlansComparisonAndFaq()

        Spacer(Modifier.height(14.dp))

        Card(
            modifier =
                Modifier
                    .fillMaxWidth(),
            colors =
                CardDefaults
                    .cardColors(
                        containerColor =
                            FioSurface
                    ),
            shape =
                RoundedCornerShape(
                    24.dp
                )
        ) {
            Column(
                Modifier.padding(
                    18.dp
                )
            ) {
                Text(
                    "Dispositivos conectados",
                    color =
                        FioText,
                    fontWeight =
                        FontWeight.Bold,
                    fontSize =
                        18.sp
                )

                Spacer(
                    Modifier.height(
                        8.dp
                    )
                )

                if (
                    account.devices
                        .isEmpty()
                ) {
                    Text(
                        "Este aparelho será listado assim que a sincronização da conta concluir.",
                        color =
                            FioTextMuted,
                        fontSize =
                            10.sp
                    )
                } else {
                    account.devices
                        .take(
                            8
                        )
                        .forEachIndexed {
                                index,
                                device ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        vertical =
                                            5.dp
                                    ),
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {
                                Column(
                                    Modifier.weight(
                                        1f
                                    )
                                ) {
                                    Text(
                                        device.deviceName +
                                            if (
                                                device.isCurrent
                                            ) {
                                                " • Este aparelho"
                                            } else {
                                                ""
                                            },
                                        color =
                                            if (
                                                device.isCurrent
                                            ) {
                                                FioGold
                                            } else {
                                                FioText
                                            },
                                        fontWeight =
                                            FontWeight.SemiBold,
                                        fontSize =
                                            11.sp
                                    )

                                    Text(
                                        "Brother Matrizes " +
                                            device.appVersion +
                                            " • último acesso " +
                                            formattedDate(
                                                device.lastSeenAt
                                            ),
                                        color =
                                            FioTextMuted,
                                        fontSize =
                                            9.sp
                                    )
                                }
                            }

                            if (
                                index <
                                    account.devices
                                        .take(
                                            8
                                        )
                                        .lastIndex
                            ) {
                                Spacer(
                                    Modifier.height(
                                        4.dp
                                    )
                                )
                            }
                        }
                }
            }
        }

        Spacer(
            Modifier.height(
                14.dp
            )
        )

        Card(
            modifier =
                Modifier
                    .fillMaxWidth(),
            colors =
                CardDefaults
                    .cardColors(
                        containerColor =
                            FioSurface
                    ),
            shape =
                RoundedCornerShape(
                    24.dp
                )
        ) {
            Column(
                Modifier.padding(
                    18.dp
                )
            ) {
                Text(
                    "Histórico da assinatura",
                    color =
                        FioText,
                    fontWeight =
                        FontWeight.Bold,
                    fontSize =
                        18.sp
                )

                Spacer(
                    Modifier.height(
                        8.dp
                    )
                )

                if (
                    account.subscriptionHistory
                        .isEmpty()
                ) {
                    Text(
                        "Seu histórico aparecerá aqui após a primeira contratação, renovação ou compra vitalícia.",
                        color =
                            FioTextMuted,
                        fontSize =
                            10.sp
                    )
                } else {
                    account.subscriptionHistory
                        .take(
                            6
                        )
                        .forEachIndexed {
                                index,
                                event ->
                            SubscriptionHistoryLine(
                                event =
                                    event
                            )

                            if (
                                index <
                                    account
                                        .subscriptionHistory
                                        .take(
                                            6
                                        )
                                        .lastIndex
                            ) {
                                Spacer(
                                    Modifier.height(
                                        8.dp
                                    )
                                )
                            }
                        }
                }
            }
        }

        Spacer(
            Modifier.height(
                14.dp
            )
        )

        AboutBrotherMatrizesCard()

        Spacer(
            Modifier.height(
                14.dp
            )
        )

        UpdateStatusCard()

        Spacer(
            Modifier.height(
                14.dp
            )
        )

        OutlinedButton(
            onClick =
                onSignOut,
            modifier =
                Modifier
                    .fillMaxWidth()
        ) {
            Text(
                "Sair da conta"
            )
        }
    }
}

@Composable
private fun AccountAvatar(
    account: AccountSnapshot
) {
    var avatar by remember(
        account.avatarUrl
    ) {
        mutableStateOf<
            androidx.compose.ui.graphics.ImageBitmap?
        >(null)
    }

    LaunchedEffect(
        account.avatarUrl
    ) {
        avatar =
            account.avatarUrl
                ?.takeIf {
                    it.startsWith(
                        "https://"
                    )
                }
                ?.let {
                        url ->
                    withContext(
                        Dispatchers.IO
                    ) {
                        SafeRemoteImage
                            .load(
                                url
                            )
                            .getOrNull()
                            ?.let {
                                bytes ->
                                BitmapFactory
                                    .decodeByteArray(
                                        bytes,
                                        0,
                                        bytes.size
                                    )
                                    ?.asImageBitmap()
                            }
                    }
                }
    }

    Box(
        Modifier
            .size(
                72.dp
            )
            .clip(
                CircleShape
            )
            .background(
                FioGold
            ),
        contentAlignment =
            Alignment.Center
    ) {
        val image =
            avatar

        if (
            image !=
                null
        ) {
            Image(
                bitmap =
                    image,
                contentDescription =
                    "Foto do perfil",
                modifier =
                    Modifier
                        .fillMaxSize(),
                contentScale =
                    ContentScale.Crop
            )
        } else {
            Text(
                account.displayName
                    .trim()
                    .firstOrNull()
                    ?.uppercase()
                    ?: "F",
                color =
                    FioBackground,
                fontWeight =
                    FontWeight.Black,
                fontSize =
                    30.sp
            )
        }
    }
}

@Composable
private fun AboutBrotherMatrizesCard() {
    val context =
        LocalContext.current

    Card(
        modifier =
            Modifier
                .fillMaxWidth(),
        colors =
            CardDefaults
                .cardColors(
                    containerColor =
                        FioSurface
                ),
        shape =
            RoundedCornerShape(
                24.dp
            )
    ) {
        Column(
            Modifier.padding(
                18.dp
            )
        ) {
            Text(
                "Sobre o Brother Matrizes",
                color =
                    FioText,
                fontWeight =
                    FontWeight.Bold,
                fontSize =
                    18.sp
            )

            Text(
                "Brother Matrizes • " +
                    BrotherMatrizesAbout
                        .productTagline,
                modifier =
                    Modifier.padding(
                        top =
                            4.dp
                    ),
                color =
                    FioTextMuted,
                fontSize =
                    10.sp
            )

            Spacer(
                Modifier.height(
                    12.dp
                )
            )

            AccountLine(
                label =
                    "Versão",
                value =
                    BuildConfig
                        .VERSION_NAME
            )

            Text(
                BrotherMatrizesAbout
                    .developerCredit,
                modifier =
                    Modifier.padding(
                        top =
                            10.dp
                    ),
                color =
                    FioText,
                fontWeight =
                    FontWeight.SemiBold,
                fontSize =
                    12.sp
            )

            Spacer(
                Modifier.height(
                    12.dp
                )
            )

            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(
                                    BrotherMatrizesAbout
                                        .developerWebsite
                                )
                            )
                        )
                    }
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
            ) {
                Text(
                    "Conhecer T.I. Machado",
                    color =
                        FioGold,
                    fontWeight =
                        FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun PlanSummary(
    plan: AccountPlanOption,
    current: Boolean,
    hasActiveLifetime: Boolean,
    onOpenPlan: (String) -> Unit
) {
    val equivalentLifetime = plan.isLifetime && hasActiveLifetime && !current
    val owned = current || equivalentLifetime
    val priceCents = plan.priceCents ?: BrotherLaunchPriceDisplay.priceCents(plan.code)
    val priceLabel = when {
        !plan.isPaid -> "Grátis"
        priceCents == null -> "Preço no site"
        else -> formattedMoney(priceCents, plan.currency) + when (plan.code) {
            "pro_monthly" -> " / mês"
            "pro_yearly" -> " / ano"
            else -> " • única vez"
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (owned) FioGold.copy(alpha = .11f)
                else FioBackground.copy(alpha = .42f),
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                plan.name,
                modifier = Modifier.weight(1f),
                color = if (owned) FioGold else FioText,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            if (current) {
                Text("Seu plano", color = FioGold, fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold)
            } else if (equivalentLifetime) {
                Text("Já incluído", color = FioGold, fontSize = 10.sp)
            } else if (plan.isPromotional) {
                Text("Oferta especial", color = FioGold, fontSize = 10.sp)
            }
        }
        Text(priceLabel, color = FioText, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(
            BrotherPlanBenefits.cardSubtitle(plan.code),
            color = FioTextMuted, fontSize = 11.sp
        )
        BrotherPlanBenefits.cardHighlights(plan.code).forEach { highlight ->
            Text("✓ " + highlight, color = FioTextMuted, fontSize = 11.sp)
        }
        if (plan.code == "pro_yearly") {
            BrotherPlanBenefits.yearlySavings(
                BrotherLaunchPriceDisplay.priceCents("pro_monthly"), priceCents
            )?.let { savings ->
                Text(
                    "Economize " + formattedMoney(savings, plan.currency) +
                        " frente a 12 mensalidades*",
                    color = FioGold, fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        if (plan.isPaid && !owned) {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onOpenPlan(plan.code) }
            ) {
                Text(
                    if (plan.isPromotional) "Conferir oferta"
                    else "Conhecer plano",
                    color = FioGold
                )
            }
        } else if (equivalentLifetime) {
            Text("Seu Vitalício já oferece esse acesso PRO.",
                color = FioGold, fontSize = 10.sp)
        }
    }
}

@Composable
private fun BrotherPlansComparisonAndFaq() {
    var compareOpen by remember { mutableStateOf(false) }
    var vitalicioOpen by remember { mutableStateOf(false) }
    var faqOpen by remember { mutableStateOf(false) }
    var selectedQuestion by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = FioSurface)
        ) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Comparar recursos", modifier = Modifier.weight(1f),
                        color = FioText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    TextButton(onClick = { compareOpen = !compareOpen }) {
                        Text(if (compareOpen) "Fechar" else "Ver comparativo", color = FioGold)
                    }
                }
                Text("Gratuito ou PRO? Compare apenas quando precisar.",
                    color = FioTextMuted, fontSize = 11.sp)
                if (compareOpen) {
                    BrotherPlanBenefits.comparison.forEach { item ->
                        Column(
                            Modifier.fillMaxWidth()
                                .padding(vertical = 7.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(item.name, color = FioText, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                            Row(Modifier.fillMaxWidth()) {
                                Text("Grátis: " + item.free, modifier = Modifier.weight(1f),
                                    color = FioTextMuted, fontSize = 10.sp)
                                Text("PRO: " + item.pro, modifier = Modifier.weight(1f),
                                    color = FioGold, fontSize = 10.sp)
                            }
                        }
                    }
                    Text(
                        "Conversor EMB/PES em desenvolvimento. Não incluído como recurso disponível.",
                        color = FioTextMuted, fontSize = 10.sp
                    )
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = FioSurface)
        ) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Por que escolher o Vitalício?", color = FioGold,
                    fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Uma compra. Recursos PRO cobertos pela licença, sem mensalidades.",
                    color = FioTextMuted, fontSize = 11.sp)
                TextButton(onClick = { vitalicioOpen = !vitalicioOpen }) {
                    Text(if (vitalicioOpen) "Mostrar menos" else "Saiba mais",
                        color = FioGold)
                }
                if (vitalicioOpen) {
                    Text(
                        "O Vitalício de Lançamento oferece a mesma categoria PRO do convencional, por preço promocional sujeito ao estoque. A licença não inclui outros aplicativos ou serviços separados.",
                        color = FioTextMuted, fontSize = 11.sp
                    )
                    Text(
                        "Acesso antecipado e programas Beta são possibilidades futuras, sujeitos a regras, vagas e disponibilidade técnica; ainda não são direitos contratados.",
                        color = FioTextMuted, fontSize = 11.sp
                    )
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = FioSurface)
        ) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Dúvidas frequentes", modifier = Modifier.weight(1f),
                        color = FioText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    TextButton(onClick = { faqOpen = !faqOpen }) {
                        Text(if (faqOpen) "Fechar" else "Ver dúvidas", color = FioGold)
                    }
                }
                Text("Teste grátis, renovação, pagamentos e novidades.",
                    color = FioTextMuted, fontSize = 11.sp)
                if (faqOpen) {
                    BrotherPlanBenefits.questions.forEach { (question, answer) ->
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .clickable {
                                    selectedQuestion = if (selectedQuestion == question) null
                                        else question
                                }
                                .padding(vertical = 9.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                question + if (selectedQuestion == question) "  −" else "  +",
                                color = FioText, fontWeight = FontWeight.SemiBold, fontSize = 12.sp
                            )
                            if (selectedQuestion == question) {
                                Text(answer, color = FioTextMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscriptionHistoryLine(
    event:
        AccountSubscriptionEvent
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Column(
            Modifier.weight(
                1f
            )
        ) {
            Text(
                AccountPresentation
                    .eventLabel(
                        event.eventType
                    ),
                color =
                    FioText,
                fontWeight =
                    FontWeight.SemiBold,
                fontSize =
                    12.sp
            )

            Text(
                AccountPresentation
                    .planLabel(
                        event.planCode
                    ) +
                    " • " +
                    formattedDate(
                        event.occurredAt
                    ),
                color =
                    FioTextMuted,
                fontSize =
                    9.sp
            )
        }

        event.amountCents
            ?.let {
                Text(
                    formattedMoney(
                        it,
                        event.currency
                    ),
                    color =
                        FioText,
                    fontWeight =
                        FontWeight.SemiBold,
                    fontSize =
                        10.sp
                )
            }
    }
}

@Composable
private fun AccountLine(
    label: String,
    value: String
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                vertical =
                    4.dp
            )
    ) {
        Text(
            label,
            modifier =
                Modifier.weight(
                    1f
                ),
            color =
                FioTextMuted,
            fontSize =
                12.sp
        )

        Text(
            value,
            color =
                FioText,
            fontWeight =
                FontWeight.SemiBold,
            fontSize =
                12.sp
        )
    }
}

private fun formattedDate(
    value: String?
): String {
    if (
        value.isNullOrBlank()
    ) {
        return "—"
    }

    return runCatching {
        DateTimeFormatter
            .ofPattern(
                "dd/MM/yyyy"
            )
            .withZone(
                ZoneId
                    .systemDefault()
            )
            .format(
                Instant.parse(
                    value
                )
            )
    }.getOrDefault(
        value
            .take(
                10
            )
    )
}

private fun formattedMoney(
    cents: Int,
    currencyCode: String
): String =
    runCatching {
        NumberFormat
            .getCurrencyInstance(
                Locale.forLanguageTag(
                    "pt-BR"
                )
            )
            .apply {
                currency =
                    Currency.getInstance(
                        currencyCode
                    )
            }
            .format(
                cents /
                    100.0
            )
    }.getOrDefault(
        (
            cents /
                100.0
            ).toString() +
            " " +
            currencyCode
    )

