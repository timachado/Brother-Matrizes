package com.timachado.brothermatrizes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.timachado.brothermatrizes.core.account.AccountErrorMessage
import com.timachado.brothermatrizes.core.account.AccountSnapshot
import com.timachado.brothermatrizes.core.account.DeviceIdentity
import com.timachado.brothermatrizes.core.account.BrotherMatrizesAccountService
import com.timachado.brothermatrizes.core.account.SignUpOutcome
import com.timachado.brothermatrizes.core.settings.AppColorMode
import com.timachado.brothermatrizes.core.network.NetworkStatus
import com.timachado.brothermatrizes.ui.theme.FioGold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AccountHostScreen(
    onBack: () -> Unit,
    refreshRequest: Int = 0,
    textScale: Float,
    onTextScaleChange: (Float) -> Unit,
    colorMode: AppColorMode,
    onColorModeChange: (AppColorMode) -> Unit
) {
    val context =
        LocalContext.current

    val scope =
        rememberCoroutineScope()

    val snackbar =
        remember {
            SnackbarHostState()
        }

    var account by remember {
        mutableStateOf<
            AccountSnapshot?
        >(null)
    }

    var loading by remember {
        mutableStateOf(true)
    }

    var offline by remember {
        mutableStateOf(
            !NetworkStatus
                .isOnline(
                    context
                )
        )
    }

    fun checkOnline(): Boolean {
        val online =
            NetworkStatus
                .isOnline(
                    context
                )

        offline =
            !online

        if (
            !online
        ) {
            scope.launch {
                snackbar.showSnackbar(
                    "Sem conexão com a internet. Sua sessão não foi encerrada."
                )
            }
        }

        return online
    }

    suspend fun withDevices(
        snapshot: AccountSnapshot?
    ): AccountSnapshot? {
        if (
            snapshot ==
                null
        ) {
            return null
        }

        val localDevice =
            DeviceIdentity.current(
                context
            )

        val devices =
            withContext(
                Dispatchers.IO
            ) {
                BrotherMatrizesAccountService
                    .syncDevices(
                        localDevice
                    )
            }.getOrElse {
                emptyList()
            }

        return snapshot.copy(
            devices =
                devices,
            currentDeviceId =
                localDevice.deviceId
        )
    }

    LaunchedEffect(
        refreshRequest
    ) {
        val online =
            NetworkStatus
                .isOnline(
                    context
                )

        offline =
            !online

        if (
            !online
        ) {
            loading =
                false

            snackbar.showSnackbar(
                "Sem conexão com a internet. Sua sessão permanece no aparelho e será carregada quando a rede voltar."
            )

            return@LaunchedEffect
        }

        val result =
            withContext(
                Dispatchers.IO
            ) {
                BrotherMatrizesAccountService
                    .currentAccount()
            }

        loading =
            false

        result.fold(
            onSuccess = {
                    current ->
                offline =
                    false

                account =
                    withDevices(
                        current
                    )
            },
            onFailure = {
                    error ->
                offline =
                    !NetworkStatus
                        .isOnline(
                            context
                        )

                snackbar
                    .showSnackbar(
                        AccountErrorMessage
                            .forUser(
                                error,
                                "Não foi possível atualizar sua conta."
                            )
                    )
            }
        )
    }

    fun refreshAccount() {
        if (
            !checkOnline()
        ) {
            return
        }

        scope.launch {
            loading =
                true

            val result =
                withContext(
                    Dispatchers.IO
                ) {
                    BrotherMatrizesAccountService
                        .currentAccount()
                }

            loading =
                false

            result.fold(
                onSuccess = {
                        current ->
                    offline =
                        false

                    account =
                        withDevices(
                            current
                        )

                    snackbar
                        .showSnackbar(
                            "Assinatura atualizada."
                        )
                },
                onFailure = {
                        error ->
                    offline =
                        !NetworkStatus
                            .isOnline(
                                context
                            )

                    snackbar
                        .showSnackbar(
                            AccountErrorMessage
                                .forUser(
                                    error,
                                    "Não foi possível atualizar sua assinatura."
                                )
                        )
                }
            )
        }
    }

    fun signInWithGoogle() {
        if (
            !checkOnline()
        ) {
            return
        }

        scope.launch {
            loading =
                true

            val result =
                BrotherMatrizesAccountService
                    .signInWithGoogle()

            loading =
                false

            result.fold(
                onSuccess = {
                    snackbar
                        .showSnackbar(
                            "Conclua o login na sua conta Google."
                        )
                },
                onFailure = {
                        error ->
                    offline =
                        !NetworkStatus
                            .isOnline(
                                context
                            )

                    snackbar
                        .showSnackbar(
                            AccountErrorMessage
                                .forUser(
                                    error,
                                    "Não foi possível abrir o login do Google."
                                )
                        )
                }
            )
        }
    }

    fun signIn(
        email: String,
        password: String
    ) {
        if (
            !checkOnline()
        ) {
            return
        }

        scope.launch {
            loading =
                true

            val result =
                withContext(
                    Dispatchers.IO
                ) {
                    BrotherMatrizesAccountService
                        .signIn(
                            email,
                            password
                        )
                }

            loading =
                false

            result.fold(
                onSuccess = {
                        signedIn ->
                    offline =
                        false

                    account =
                        withDevices(
                            signedIn
                        )

                    snackbar
                        .showSnackbar(
                            "Conta conectada."
                        )
                },
                onFailure = {
                        error ->
                    offline =
                        !NetworkStatus
                            .isOnline(
                                context
                            )

                    snackbar
                        .showSnackbar(
                            AccountErrorMessage
                                .forUser(
                                    error,
                                    "Não foi possível entrar."
                                )
                        )
                }
            )
        }
    }

    fun signUp(
        displayName: String,
        email: String,
        password: String
    ) {
        if (
            !checkOnline()
        ) {
            return
        }

        scope.launch {
            loading =
                true

            val result =
                withContext(
                    Dispatchers.IO
                ) {
                    BrotherMatrizesAccountService
                        .signUp(
                            displayName,
                            email,
                            password
                        )
                }

            loading =
                false

            result.fold(
                onSuccess = {
                        outcome ->
                    when (
                        outcome
                    ) {
                        is SignUpOutcome
                            .SignedIn -> {
                            offline =
                                false

                            account =
                                withDevices(
                                    outcome.account
                                )

                            snackbar
                                .showSnackbar(
                                    "Conta criada e conectada."
                                )
                        }

                        is SignUpOutcome
                            .ConfirmationRequired -> {
                            offline =
                                false

                            account =
                                null

                            snackbar
                                .showSnackbar(
                                    "Conta criada. Confira o e-mail " +
                                        outcome.email +
                                        " antes de entrar."
                                )
                        }
                    }
                },
                onFailure = {
                        error ->
                    offline =
                        !NetworkStatus
                            .isOnline(
                                context
                            )

                    snackbar
                        .showSnackbar(
                            AccountErrorMessage
                                .forUser(
                                    error,
                                    "Não foi possível criar a conta."
                                )
                        )
                }
            )
        }
    }

    fun saveName(
        displayName: String
    ) {
        if (
            !checkOnline()
        ) {
            return
        }

        scope.launch {
            loading =
                true

            val result =
                withContext(
                    Dispatchers.IO
                ) {
                    BrotherMatrizesAccountService
                        .updateDisplayName(
                            displayName
                        )
                }

            loading =
                false

            result.fold(
                onSuccess = {
                        updated ->
                    offline =
                        false

                    account =
                        withDevices(
                            updated
                        )

                    snackbar
                        .showSnackbar(
                            "Nome atualizado."
                        )
                },
                onFailure = {
                        error ->
                    offline =
                        !NetworkStatus
                            .isOnline(
                                context
                            )

                    snackbar
                        .showSnackbar(
                            AccountErrorMessage
                                .forUser(
                                    error,
                                    "Não foi possível atualizar o nome."
                                )
                        )
                }
            )
        }
    }

    fun signOut() {
        scope.launch {
            loading =
                true

            val result =
                withContext(
                    Dispatchers.IO
                ) {
                    BrotherMatrizesAccountService
                        .signOut()
                }

            loading =
                false

            result.fold(
                onSuccess = {
                    offline =
                        false

                    account =
                        null

                    snackbar
                        .showSnackbar(
                            "Você saiu da conta."
                        )
                },
                onFailure = {
                        error ->
                    snackbar
                        .showSnackbar(
                            AccountErrorMessage
                                .forUser(
                                    error,
                                    "Não foi possível sair da conta."
                                )
                        )
                }
            )
        }
    }

    Box(
        Modifier.fillMaxSize()
    ) {
        AccountScreen(
            account =
                account,
            offline =
                offline,
            onBack =
                onBack,
            textScale =
                textScale,
            onTextScaleChange =
                onTextScaleChange,
            colorMode = colorMode,
            onColorModeChange = onColorModeChange,
            onGoogleSignIn = {
                signInWithGoogle()
            },
            onSignIn = {
                    email,
                    password ->
                signIn(
                    email,
                    password
                )
            },
            onSignUp = {
                    name,
                    email,
                    password ->
                signUp(
                    name,
                    email,
                    password
                )
            },
            onSaveName = {
                    name ->
                saveName(
                    name
                )
            },
            onRefresh = {
                refreshAccount()
            },
            onSignOut = {
                signOut()
            }
        )

        SnackbarHost(
            hostState =
                snackbar,
            modifier =
                Modifier
                    .align(
                        Alignment
                            .BottomCenter
                    )
        )

        if (
            loading
        ) {
            CircularProgressIndicator(
                modifier =
                    Modifier
                        .align(
                            Alignment.Center
                        ),
                color =
                    FioGold
            )
        }
    }
}
