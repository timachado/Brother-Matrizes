package com.timachado.brothermatrizes.core.account

object AccountErrorMessage {
    fun forUser(
        error: Throwable,
        fallback: String
    ): String {
        val message =
            buildString {
                var current:
                    Throwable? =
                    error

                var depth =
                    0

                while (
                    current !=
                        null &&
                    depth <
                        5
                ) {
                    append(
                        current.message
                            .orEmpty()
                    )

                    append(
                        ' '
                    )

                    current =
                        current.cause

                    depth++
                }
            }
                .lowercase()

        return when {
            listOf(
                "unable to resolve host",
                "network is unreachable",
                "failed to connect",
                "connect timeout",
                "sockettimeout",
                "timeout",
                "no address associated with hostname"
            ).any {
                it in
                    message
            } ->
                "Sem conexão com a internet. Confira sua rede e tente novamente."

            (
                "invalid login credentials" in
                    message ||
                    "invalid credentials" in
                        message
                ) ->
                "E-mail ou senha incorretos."

            (
                "email not confirmed" in
                    message ||
                    "email_not_confirmed" in
                        message
                ) ->
                "Confirme seu e-mail antes de entrar."

            (
                "refresh token" in
                    message ||
                    "jwt expired" in
                        message ||
                    "token has expired" in
                        message ||
                    "session expired" in
                        message
                ) ->
                "Sua sessão expirou. Entre novamente para continuar."

            (
                "user already registered" in
                    message ||
                    "already been registered" in
                        message
                ) ->
                "Já existe uma conta com este e-mail."

            (
                "rate limit" in
                    message ||
                    "too many requests" in
                        message
                ) ->
                "Muitas tentativas em pouco tempo. Aguarde um pouco e tente novamente."

            (
                "activitynotfoundexception" in
                    message ||
                    "no activity found to handle intent" in
                        message ||
                    "unable to find custom tabs" in
                        message
                ) ->
                "Não foi possível abrir o navegador seguro do Google neste aparelho."

            (
                "application context not initialized" in
                    message ||
                    "supabaseinitializer" in
                        message
                ) ->
                "O login Google não foi inicializado corretamente no Android."

            else ->
                fallback
        }
    }
}
