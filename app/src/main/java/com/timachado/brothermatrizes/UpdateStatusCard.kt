package com.timachado.brothermatrizes

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.brothermatrizes.core.update.AppUpdateChecker
import com.timachado.brothermatrizes.core.update.AppUpdateInfo
import com.timachado.brothermatrizes.ui.theme.FioBackground
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioSurface
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun UpdateStatusCard() {
    val context =
        LocalContext.current

    var refreshKey by remember {
        mutableStateOf(
            0
        )
    }

    var loading by remember {
        mutableStateOf(
            true
        )
    }

    var failed by remember {
        mutableStateOf(
            false
        )
    }

    var update by remember {
        mutableStateOf<
            AppUpdateInfo?
        >(
            null
        )
    }

    LaunchedEffect(
        refreshKey
    ) {
        loading =
            true
        failed =
            false

        val result =
            withContext(
                Dispatchers.IO
            ) {
                AppUpdateChecker
                    .check()
            }

        result.fold(
            onSuccess = {
                    info ->
                update =
                    info
                loading =
                    false
            },
            onFailure = {
                update =
                    null
                failed =
                    true
                loading =
                    false
            }
        )
    }

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
                "Atualizações",
                color =
                    FioText,
                fontWeight =
                    FontWeight.Bold,
                fontSize =
                    18.sp
            )

            Text(
                "Versão instalada: " +
                    BuildConfig.VERSION_NAME,
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
                    10.dp
                )
            )

            when {
                loading -> {
                    Text(
                        "Verificando a versão mais recente...",
                        color =
                            FioTextMuted,
                        fontSize =
                            11.sp
                    )
                }

                failed -> {
                    Text(
                        "Não foi possível verificar agora. O Brother Matrizes continua funcionando normalmente.",
                        color =
                            FioTextMuted,
                        fontSize =
                            11.sp
                    )

                    Spacer(
                        Modifier.height(
                            10.dp
                        )
                    )

                    OutlinedButton(
                        onClick = {
                            refreshKey++
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                    ) {
                        Text(
                            "Tentar novamente"
                        )
                    }
                }

                update
                    ?.updateAvailable ==
                    true -> {
                    val info =
                        update
                            ?: return@Column

                    Text(
                        "Nova versão disponível: " +
                            info.latestVersion,
                        color =
                            FioGold,
                        fontWeight =
                            FontWeight.SemiBold,
                        fontSize =
                            12.sp
                    )

                    Text(
                        "A instalação só acontece se você abrir a release e escolher atualizar.",
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
                            10.dp
                        )
                    )

                    Button(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(
                                            info.releaseUrl
                                        )
                                    )
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
                            "Ver atualização",
                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                }

                else -> {
                    Text(
                        "Você está usando a versão mais recente publicada.",
                        color =
                            FioTextMuted,
                        fontSize =
                            11.sp
                    )

                    Spacer(
                        Modifier.height(
                            10.dp
                        )
                    )

                    OutlinedButton(
                        onClick = {
                            refreshKey++
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                    ) {
                        Text(
                            "Verificar novamente"
                        )
                    }
                }
            }
        }
    }
}
