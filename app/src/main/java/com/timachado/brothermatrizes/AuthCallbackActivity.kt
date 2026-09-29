package com.timachado.brothermatrizes

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.timachado.brothermatrizes.core.account.AccountErrorMessage
import com.timachado.brothermatrizes.core.account.BrotherMatrizesAccountService
import com.timachado.brothermatrizes.ui.theme.FioBackground
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.BrotherMatrizesTheme
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted

const val EXTRA_OPEN_ACCOUNT_FROM_AUTH =
    "com.timachado.brothermatrizes.extra.OPEN_ACCOUNT_FROM_AUTH"

class AuthCallbackActivity :
    ComponentActivity() {

    private var errorMessage by
        mutableStateOf<String?>(
            null
        )

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )

        setContent {
            BrotherMatrizesTheme {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(
                                28.dp
                            ),
                    verticalArrangement =
                        Arrangement.Center,
                    horizontalAlignment =
                        Alignment.CenterHorizontally
                ) {
                    val error =
                        errorMessage

                    if (
                        error ==
                            null
                    ) {
                        CircularProgressIndicator(
                            color =
                                FioGold
                        )

                        Text(
                            "Conectando sua conta Google…",
                            modifier =
                                Modifier.padding(
                                    top =
                                        16.dp
                                ),
                            color =
                                FioText,
                            fontWeight =
                                FontWeight.SemiBold
                        )

                        Text(
                            "O Brother Matrizes está validando sua sessão com segurança.",
                            modifier =
                                Modifier.padding(
                                    top =
                                        6.dp
                                ),
                            color =
                                FioTextMuted
                        )
                    } else {
                        Text(
                            "Não foi possível entrar com Google.",
                            color =
                                FioText,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            error,
                            modifier =
                                Modifier.padding(
                                    top =
                                        8.dp
                                ),
                            color =
                                FioTextMuted
                        )

                        Button(
                            onClick = {
                                openAccount()
                            },
                            modifier =
                                Modifier.padding(
                                    top =
                                        18.dp
                                )
                        ) {
                            Text(
                                "Voltar para Minha Conta"
                            )
                        }
                    }
                }
            }
        }

        BrotherMatrizesAccountService
            .handleAuthRedirect(
                intent =
                    intent,
                onSuccess = {
                    openAccount()
                },
                onError = {
                        error ->
                    errorMessage =
                        AccountErrorMessage
                            .forUser(
                                error,
                                "A autenticação foi cancelada ou não pôde ser concluída."
                            )
                }
            )
    }

    private fun openAccount() {
        startActivity(
            Intent(
                this,
                MainActivity::class.java
            ).apply {
                flags =
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP

                putExtra(
                    EXTRA_OPEN_ACCOUNT_FROM_AUTH,
                    true
                )
            }
        )

        finish()
    }
}
