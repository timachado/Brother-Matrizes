package com.timachado.brothermatrizes.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.URI

/**
 * Paridade parcial com BrotherCommunityScreen Android.
 * Somente interface pública. Não usa conta, notificações FCM ou token.
 */
@Composable
internal fun DesktopCommunityPanel(
    enabled: Boolean,
    onBack: () -> Unit,
    onOpenExternal: (URI) -> Unit
) {
    var feedbackType by remember { mutableStateOf("Sugestão") }
    var feedbackText by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(enabled = enabled, onClick = onBack) {
            Text("Voltar ao início")
        }
        Text(
            "Comunidade Brother Matrizes",
            style = MaterialTheme.typography.headlineSmall,
            color = DesktopBrandColors.gold
        )
        Text(
            "Novidades, tutoriais e sugestões para a evolução do aplicativo.",
            color = DesktopBrandColors.muted
        )

        Text(
            "Atualizações oficiais",
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "Consulte as novidades no site. A versão Windows ainda é QA, " +
                "não uma atualização comercial.",
            fontSize = 12.sp,
            color = DesktopBrandColors.muted
        )
        OutlinedButton(
            enabled = enabled,
            onClick = { onOpenExternal(DesktopCommunityLinks.productPage()) }
        ) { Text("Ver notas e novidades") }

        Text(
            "Grupo oficial de feedbacks",
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "Ao participar do WhatsApp, outros membros podem visualizar seu " +
                "número. Não compartilhe senhas, matrizes de clientes ou dados sigilosos.",
            fontSize = 12.sp, color = DesktopBrandColors.muted
        )
        Button(
            enabled = enabled,
            onClick = { onOpenExternal(DesktopCommunityLinks.officialGroup()) }
        ) { Text("Abrir grupo oficial do WhatsApp") }

        Text(
            "Enviar feedback",
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DesktopCommunityLinks.categories.forEach { category ->
                TextButton(
                    enabled = enabled,
                    onClick = { feedbackType = category }
                ) {
                    Text(
                        (if (feedbackType == category) "● " else "") + category
                    )
                }
            }
        }
        OutlinedTextField(
            value = feedbackText,
            onValueChange = {
                feedbackText = it.take(DesktopCommunityLinks.MAX_FEEDBACK_CHARS)
            },
            label = { Text("Sua mensagem") },
            placeholder = { Text("Descreva o problema ou a melhoria desejada…") },
            maxLines = 6,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled
        )
        Text(
            "${feedbackText.length}/2500 caracteres. " +
                "A mensagem só é preparada depois de escolher o aplicativo externo. " +
                "Você confirma o envio no WhatsApp ou e-mail.",
            fontSize = 11.sp, color = DesktopBrandColors.muted
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = enabled && feedbackText.isNotBlank(),
                onClick = {
                    onOpenExternal(
                        DesktopCommunityLinks.whatsappFeedback(
                            feedbackType, feedbackText
                        )
                    )
                }
            ) { Text("WhatsApp") }
            OutlinedButton(
                enabled = enabled && feedbackText.isNotBlank(),
                onClick = {
                    onOpenExternal(
                        DesktopCommunityLinks.emailFeedback(
                            feedbackType, feedbackText
                        )
                    )
                }
            ) { Text("E-mail") }
        }
        Text(
            "Notificações push e controles de assinatura do Android " +
                "não estão ativos neste módulo Windows.",
            fontSize = 11.sp, color = DesktopBrandColors.muted
        )
    }
}
