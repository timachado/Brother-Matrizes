package com.timachado.brothermatrizes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.brothermatrizes.core.account.AccountPresentation
import com.timachado.brothermatrizes.core.account.AccountSnapshot

// Paleta exclusiva da experiência Conta, fiel à referência aprovada.
internal val BrotherAccountNavy = Color(0xFF081B29)
internal val BrotherAccountSurface = Color(0xFF152E40)
internal val BrotherAccountGold = Color(0xFFE6BE72)
internal val BrotherAccountWhite = Color(0xFFF8FAFD)
internal val BrotherAccountMuted = Color(0xFFB7C7D3)
internal val BrotherAccountBorder = Color(0xFF345064)

@Composable
internal fun BrotherAccountHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "BROTHER MATRIZES",
                color = BrotherAccountGold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
            Text("Minha Conta", color = BrotherAccountWhite,
                fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        Column(horizontalAlignment = Alignment.End) {
            BrotherAccountGlyph("crown", Modifier.size(28.dp))
            TextButton(onClick = onBack, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("‹ Voltar", color = BrotherAccountGold, fontSize = 10.sp)
            }
        }
    }
}

@Composable
internal fun BrotherAccountHero(
    account: AccountSnapshot,
    onBenefits: () -> Unit,
    onLicense: () -> Unit
) {
    val verified = account.commercialConfigured
    val active = verified && account.hasProAccess
    val lifetime = active && account.isLifetime
    val name = when {
        !verified -> "Licença a verificar"
        lifetime -> "PRO Vitalício"
        active && account.planCode == "trial" -> "Teste PRO"
        active -> AccountPresentation.planLabel(account.planCode)
        account.isPaid -> "Assinatura sem acesso ativo"
        else -> "Plano Gratuito"
    }
    val status = when {
        !verified -> "Verificar"
        active -> "Ativo"
        account.isPaid -> AccountPresentation.statusLabel(account.subscriptionStatus)
        else -> "Gratuito"
    }
    val subtitle = when {
        !verified -> "Os dados da conta permanecem preservados."
        lifetime -> "Pagamento único  •  Acesso permanente"
        active && account.planCode == "trial" -> "Acesso PRO durante o período de teste"
        active -> "Benefícios PRO ativos nesta conta"
        account.isPaid -> "Consulte os detalhes da assinatura"
        else -> "Comece gratuitamente e descubra o PRO"
    }
    Surface(
        color = BrotherAccountSurface,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(name, modifier = Modifier.weight(1f),
                    color = BrotherAccountWhite, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Surface(
                    color = if (active) Color(0xFF0E433C) else Color(0xFF294355),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(status,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                        color = if (active) Color(0xFF42D58D) else BrotherAccountMuted,
                        fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                }
            }
            Text(subtitle, color = BrotherAccountMuted, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Button(
                    onClick = onBenefits,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(13.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrotherAccountGold, contentColor = BrotherAccountNavy
                    )
                ) {
                    Text(if (active) "Meus benefícios" else "Ver planos",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onLicense,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(13.dp),
                    border = BorderStroke(1.dp, BrotherAccountBorder),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)
                ) {
                    Text("Minha licença",
                        color = BrotherAccountWhite, fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
internal fun BrotherAccountMenuItem(
    title: String,
    glyph: String,
    onClick: () -> Unit,
    expanded: Boolean = false,
    last: Boolean = false
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
                .padding(horizontal = 2.dp, vertical = 17.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BrotherAccountGlyph(glyph, Modifier.size(23.dp))
            Text(title, modifier = Modifier.weight(1f), color = BrotherAccountWhite,
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(if (expanded) "⌄" else "›",
                color = BrotherAccountGold, fontSize = 24.sp)
        }
        if (!last) {
            Spacer(
                Modifier.fillMaxWidth().height(1.dp)
                    .background(BrotherAccountBorder)
            )
        }
    }
}

@Composable
internal fun BrotherAccountGlyph(glyph: String, modifier: Modifier = Modifier.size(22.dp)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val gold = BrotherAccountGold
        val line = w / 12f
        fun segment(a: Float, b: Float, c: Float, d: Float) =
            drawLine(gold, Offset(w*a,h*b), Offset(w*c,h*d), strokeWidth=line)
        when (glyph) {
            "crown" -> {
                val p = Path().apply {
                    moveTo(w*.08f, h*.31f)
                    lineTo(w*.29f,h*.56f); lineTo(w*.5f,h*.19f)
                    lineTo(w*.71f,h*.56f); lineTo(w*.92f,h*.31f)
                    lineTo(w*.84f,h*.77f); lineTo(w*.16f,h*.77f)
                    close()
                }
                drawPath(p,gold,style=Stroke(width=line))
                segment(.20f,.87f,.80f,.87f)
            }
            "plans" -> {
                drawRoundRect(gold,topLeft=Offset(w*.10f,h*.25f),
                    size=Size(w*.8f,h*.55f),cornerRadius=CornerRadius(w*.10f),
                    style=Stroke(width=line))
                segment(.17f,.42f,.83f,.42f)
            }
            "history" -> {
                drawArc(gold,startAngle=-70f,sweepAngle=290f,useCenter=false,
                    topLeft=Offset(w*.16f,h*.18f),size=Size(w*.68f,h*.68f),
                    style=Stroke(width=line))
                segment(.18f,.1f,.18f,.31f); segment(.18f,.31f,.39f,.31f)
                segment(.5f,.33f,.5f,.53f); segment(.5f,.53f,.65f,.59f)
            }
            "help" -> {
                drawArc(gold,startAngle=190f,sweepAngle=160f,useCenter=false,
                    topLeft=Offset(w*.13f,h*.12f),size=Size(w*.74f,h*.74f),
                    style=Stroke(width=line))
                segment(.14f,.51f,.14f,.72f); segment(.86f,.51f,.86f,.72f)
                segment(.86f,.72f,.72f,.78f)
            }
            "settings" -> {
                segment(.15f,.25f,.85f,.25f); segment(.15f,.5f,.85f,.5f)
                segment(.15f,.75f,.85f,.75f)
                drawCircle(gold,radius=w*.10f,center=Offset(w*.35f,h*.25f))
                drawCircle(gold,radius=w*.10f,center=Offset(w*.67f,h*.5f))
                drawCircle(gold,radius=w*.10f,center=Offset(w*.45f,h*.75f))
            }
            "devices" -> {
                drawRoundRect(gold,topLeft=Offset(w*.12f,h*.15f),
                    size=Size(w*.76f,h*.58f),cornerRadius=CornerRadius(w*.05f),
                    style=Stroke(width=line))
                segment(.5f,.74f,.5f,.86f); segment(.30f,.86f,.70f,.86f)
            }
            "profile" -> {
                drawCircle(gold,radius=w*.16f,center=Offset(w*.5f,h*.3f),
                    style=Stroke(width=line))
                drawArc(gold,200f,140f,false,topLeft=Offset(w*.16f,h*.47f),
                    size=Size(w*.68f,h*.46f),style=Stroke(width=line))
            }
            "license" -> {
                drawRoundRect(gold,topLeft=Offset(w*.15f,h*.12f),
                    size=Size(w*.7f,h*.76f),cornerRadius=CornerRadius(w*.08f),
                    style=Stroke(width=line))
                segment(.31f,.37f,.69f,.37f); segment(.31f,.55f,.60f,.55f)
            }
            else -> {
                drawCircle(gold,radius=w*.33f,center=Offset(w*.5f,h*.5f),
                    style=Stroke(width=line))
            }
        }
    }
}
