package com.timachado.brothermatrizes.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Entrada desktop inspirada na Home Android comercial 1.11.8:
 * marca, slogan, ação principal e ferramentas reais existentes.
 * Não adiciona telas ou ações de Android ainda não portadas.
 */
@Composable
internal fun DesktopHomeLanding(
    enabled: Boolean,
    onCreateName: () -> Unit,
    onOpenMatrix: () -> Unit,
    onFonts: () -> Unit,
    onMonogram: () -> Unit,
    onEditor: () -> Unit,
    onLibrary: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Brother", color = DesktopBrandColors.gold,
            fontSize = 30.sp, fontWeight = FontWeight.Bold
        )
        Text(
            "Matrizes", color = DesktopBrandColors.goldSoft,
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            "Matrizes que transformam ideias em grandes projetos.",
            color = DesktopBrandColors.muted,
            style = MaterialTheme.typography.bodyMedium
        )
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Crie sua próxima matriz",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Crie a matriz e ajuste fonte, tamanho, cor e ponto.",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    enabled = enabled, onClick = onCreateName,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Criar nome") }
                TextButton(
                    enabled = enabled, onClick = onOpenMatrix,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Abrir matriz DST / PES / JEF") }
            }
        }
        Text(
            "Ferramentas", color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            HomeTool(
                title = "Biblioteca de fontes",
                enabled = enabled, onClick = onFonts,
                modifier = Modifier.weight(1f)
            )
            HomeTool(
                title = "Monogramas",
                enabled = enabled, onClick = onMonogram,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            HomeTool(
                title = "Editor",
                enabled = enabled, onClick = onEditor,
                modifier = Modifier.weight(1f)
            )
            HomeTool(
                title = "Biblioteca de projetos",
                enabled = enabled, onClick = onLibrary,
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            "Versão Windows em homologação: nem todas as funções Android " +
                "estão disponíveis. Nenhum arquivo é enviado automaticamente.",
            color = DesktopBrandColors.muted,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun HomeTool(
    title: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = DesktopBrandColors.surfaceAlt,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
    ) {
        TextButton(enabled = enabled, onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
