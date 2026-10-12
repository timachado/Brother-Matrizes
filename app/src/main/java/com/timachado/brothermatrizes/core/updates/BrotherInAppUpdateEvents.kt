package com.timachado.brothermatrizes.core.updates

import android.content.Context
import android.content.Intent

/**
 * Atualiza o cartão da Home quando o serviço FCM ou Worker publica um aviso.
 * Broadcast apenas dentro do próprio aplicativo. Não carrega dados externos:
 * a UI sempre consulta novamente o endpoint de release oficial.
 */
internal object BrotherInAppUpdateEvents {
    const val ACTION = "com.timachado.brothermatrizes.INTERNAL_UPDATE_ANNOUNCED"

    fun announce(context: Context) {
        context.sendBroadcast(Intent(ACTION).setPackage(context.packageName))
    }
}
