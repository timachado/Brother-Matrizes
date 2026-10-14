package com.timachado.brothermatrizes.core.account

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountErrorMessageTest {
    @Test
    fun mapsInvalidCredentialsWithoutLeakingBackendText() {
        assertEquals(
            "E-mail ou senha incorretos.",
            AccountErrorMessage
                .forUser(
                    IllegalStateException(
                        "AuthApiException: Invalid login credentials"
                    ),
                    "Falha."
                )
        )
    }

    @Test
    fun mapsExpiredSession() {
        assertEquals(
            "Sua sessão expirou. Entre novamente para continuar.",
            AccountErrorMessage
                .forUser(
                    IllegalStateException(
                        "JWT expired"
                    ),
                    "Falha."
                )
        )
    }

    @Test
    fun mapsNetworkFailure() {
        assertEquals(
            "Sem conexão com a internet. Confira sua rede e tente novamente.",
            AccountErrorMessage
                .forUser(
                    IllegalStateException(
                        "Unable to resolve host"
                    ),
                    "Falha."
                )
        )
    }

    @Test
    fun unknownErrorUsesSafeFallback() {
        assertEquals(
            "Não foi possível atualizar sua conta.",
            AccountErrorMessage
                .forUser(
                    IllegalStateException(
                        "internal backend detail"
                    ),
                    "Não foi possível atualizar sua conta."
                )
        )
    }

    @Test
    fun mapsAndroidOAuthLauncherFailure() {
        assertEquals(
            "Não foi possível abrir o navegador seguro do Google neste aparelho.",
            AccountErrorMessage
                .forUser(
                    IllegalStateException(
                        "ActivityNotFoundException: No Activity found to handle Intent"
                    ),
                    "Falha."
                )
        )
    }

    @Test
    fun mapsMissingSupabaseAndroidInitializer() {
        assertEquals(
            "O login Google não foi inicializado corretamente no Android.",
            AccountErrorMessage
                .forUser(
                    IllegalStateException(
                        "Application context not initialized"
                    ),
                    "Falha."
                )
        )
    }

}
