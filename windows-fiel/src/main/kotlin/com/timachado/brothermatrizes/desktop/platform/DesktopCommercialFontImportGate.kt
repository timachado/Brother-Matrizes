package com.timachado.brothermatrizes.desktop.platform

import java.nio.file.Path

/**
 * Contrato de autorização comercial, sem credenciais persistidas.
 *
 * Paridade com BrotherMatrizesAccountService.authorizeImport/finalizeImport
 * do Android: conta verificada -> reserva "import_font" no servidor ->
 * importação local -> finalize(success = nova importação).
 *
 * Nada nesta classe ativa a UI ou autoriza acesso por estado local.
 * O port Windows ainda não tem OAuth/PKCE homologado; até lá,
 * WindowsFontSessionUnavailable impede a emissão de qualquer autorização.
 */
internal fun interface DesktopVerifiedFontSession {
    suspend fun accessToken(): String
}

internal interface DesktopFontUsageGateway {
    suspend fun reserveFont(accessToken: String): String
    suspend fun finalizeFont(accessToken: String, requestKey: String, success: Boolean)
}

/** Falha fechada para toda a aplicação Windows enquanto não houver OAuth homologado. */
internal object WindowsFontSessionUnavailable : DesktopVerifiedFontSession {
    override suspend fun accessToken(): String =
        error("Conta Google Windows ainda não homologada. Importação comercial indisponível.")
}

/**
 * Testado com adaptadores em memória e fontes de referência.
 * NUNCA interpretar token local, perfil, 'pro=true' ou arquivo existente
 * como prova de entitlement. A validação pertence ao servidor.
 */
internal class DesktopCommercialFontImportGate(
    private val library: DesktopImportedFontLibrary,
    private val verifiedSession: DesktopVerifiedFontSession = WindowsFontSessionUnavailable,
    private val usage: DesktopFontUsageGateway
) {
    private val serverReservation = Regex(
        "^(usage|pro)_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
    )

    suspend fun importFont(source: Path, displayName: String? = null): DesktopFontRecord {
        // Importação e leitura do disco são proibidas antes de autenticar e reservar.
        val token = verifiedSession.accessToken()
        check(token.isNotBlank()) { "É necessário entrar na conta Google verificada." }
        val reservation = usage.reserveFont(token)
        check(serverReservation.matches(reservation)) {
            "Reserva de importação inválida recebida do servidor."
        }

        var created: DesktopFontRecord? = null
        var result: DesktopFontRecord? = null
        var importFailure: Exception? = null
        try {
            val idsBefore = library.list().mapTo(mutableSetOf()) { it.id }
            result = library.importFile(source, displayName)
            if (result.id !in idsBefore) created = result
        } catch (error: Exception) {
            importFailure = error
        }

        // Confirmação é obrigatória também em falhas e duplicatas.
        // Se a rede falhar após criar um arquivo novo, não permitir um
        // font novo local sem que a cota de produção tenha sido sincronizada.
        try {
            usage.finalizeFont(token, reservation, success = created != null)
        } catch (syncError: Exception) {
            if (created != null) {
                try {
                    library.delete(created.id)
                } catch (cleanupError: Exception) {
                    syncError.addSuppressed(cleanupError)
                }
            }
            throw IllegalStateException(
                "A cota de fontes não foi sincronizada. A importação não foi liberada.",
                syncError
            )
        }

        if (importFailure != null) throw importFailure
        return checkNotNull(result) { "Importação não produziu registro de fonte." }
    }
}
