package com.timachado.brothermatrizes

/**
 * Exceção de importação local apenas na edição de homologação.
 *
 * A política não altera limites, autorização ou login da versão comercial:
 * exige simultaneamente assinatura da variante DEBUG e applicationId separado.
 * Não deve ser usada para liberações ou serviços de produção.
 */
internal object NameQaImportPolicy {
    private const val TEST_PACKAGE = "com.timachado.brothermatrizes.nameqa"

    fun allowLocalImportWithoutAccount(
        debugBuild: Boolean,
        applicationId: String
    ): Boolean = debugBuild && applicationId == TEST_PACKAGE
}
