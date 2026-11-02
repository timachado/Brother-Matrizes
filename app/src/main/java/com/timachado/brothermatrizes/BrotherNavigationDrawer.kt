package com.timachado.brothermatrizes

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioNavigationMuted
import com.timachado.brothermatrizes.ui.theme.FioNavigationSurface
import com.timachado.brothermatrizes.ui.theme.FioSurfaceAlt
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted

/**
 * Navegação somente: não altera a matriz, não salva automaticamente e não
 * intercepta gestos nas telas de desenho, posicionamento e editor de nomes.
 */
internal enum class BrotherDrawerDestination {
    HOME, CREATE_NAME, CREATE_MONOGRAM, CREATE_DRAWING, OPEN_MATRIX,
    LIBRARY, EDITOR, SIMULATOR, CONVERTER, TRANSFER, FONTS,
    TELEGRAM, ACCOUNT, COMMUNITY
}

private data class BrotherMenuEntry(
    val destination: BrotherDrawerDestination,
    val title: String,
    @DrawableRes val icon: Int,
    val tag: String,
    val needsMatrix: Boolean = false
)

@Composable
internal fun BrotherNavigationDrawerContent(
    selected: BrotherDrawerDestination?,
    hasActiveMatrix: Boolean,
    onSelect: (BrotherDrawerDestination) -> Unit
) {
    val selectedColors = NavigationDrawerItemDefaults.colors(
        selectedContainerColor = FioSurfaceAlt,
        unselectedContainerColor = FioNavigationSurface,
        selectedTextColor = FioGold,
        unselectedTextColor = FioText,
        selectedIconColor = FioGold,
        unselectedIconColor = FioNavigationMuted
    )

    @Composable
    fun drawerItem(item: BrotherMenuEntry) {
        val available = !item.needsMatrix || hasActiveMatrix
        NavigationDrawerItem(
            label = {
                Column {
                    Text(item.title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    if (!available) {
                        Text(
                            "Abra uma matriz primeiro",
                            color = FioTextMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            },
            selected = selected == item.destination,
            onClick = { onSelect(item.destination) },
            icon = {
                Icon(
                    painter = painterResource(item.icon),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 1.dp)
                .testTag(item.tag),
            colors = selectedColors
        )
    }

    @Composable
    fun sectionTitle(title: String) {
        Text(
            text = title,
            color = FioNavigationMuted,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 8.dp)
        )
    }

    ModalDrawerSheet(
        modifier = Modifier.widthIn(max = 320.dp),
        drawerContainerColor = FioNavigationSurface,
        drawerContentColor = FioText
    ) {
        Column(
            Modifier.fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.brother_bm_premium_approved),
                    contentDescription = null,
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
                Column {
                    Text("Brother Matrizes", color = FioGold, fontWeight = FontWeight.Bold)
                    Text(
                        "Ferramentas organizadas",
                        color = FioNavigationMuted,
                        fontSize = 12.sp
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = FioNavigationMuted.copy(alpha = 0.22f)
            )

            sectionTitle("PRINCIPAL")
            drawerItem(BrotherMenuEntry(
                BrotherDrawerDestination.HOME, "Início",
                R.drawable.ic_ms_home_rounded, "brother_drawer_home"
            ))
            drawerItem(BrotherMenuEntry(
                BrotherDrawerDestination.CREATE_NAME, "Criar nome",
                R.drawable.ic_ms_text_fields_rounded, "brother_drawer_create_name"
            ))
            drawerItem(BrotherMenuEntry(
                BrotherDrawerDestination.CREATE_MONOGRAM, "Criar monograma",
                R.drawable.ic_ms_text_fields_rounded, "brother_drawer_monogram"
            ))
            drawerItem(BrotherMenuEntry(
                BrotherDrawerDestination.CREATE_DRAWING, "Criar desenho",
                R.drawable.ic_ms_edit_rounded, "brother_drawer_drawing"
            ))
            drawerItem(BrotherMenuEntry(
                BrotherDrawerDestination.OPEN_MATRIX, "Abrir matriz",
                R.drawable.ic_ms_folder_open_rounded, "brother_drawer_open_matrix"
            ))

            sectionTitle("MATRIZES E FERRAMENTAS")
            drawerItem(BrotherMenuEntry(
                BrotherDrawerDestination.LIBRARY, "Minhas matrizes e favoritos",
                R.drawable.ic_ms_folder_open_rounded, "brother_drawer_library"
            ))
            drawerItem(BrotherMenuEntry(
                BrotherDrawerDestination.EDITOR, "Editor de matrizes",
                R.drawable.ic_ms_edit_rounded, "brother_drawer_editor", true
            ))
            drawerItem(BrotherMenuEntry(
                BrotherDrawerDestination.SIMULATOR, "Simulador",
                R.drawable.ic_ms_play_arrow_rounded, "brother_drawer_simulator", true
            ))
            drawerItem(BrotherMenuEntry(
                BrotherDrawerDestination.CONVERTER, "Converter PES / DST / JEF",
                R.drawable.ic_ms_swap_horiz_rounded, "brother_drawer_convert", true
            ))
            drawerItem(BrotherMenuEntry(
                BrotherDrawerDestination.TRANSFER, "Enviar para máquina",
                R.drawable.ic_ms_upload_rounded, "brother_drawer_transfer", true
            ))

            sectionTitle("BIBLIOTECAS")
            drawerItem(BrotherMenuEntry(
                BrotherDrawerDestination.FONTS, "Fontes TTF / OTF",
                R.drawable.ic_ms_font_download_rounded, "brother_drawer_fonts"
            ))
            drawerItem(BrotherMenuEntry(
                BrotherDrawerDestination.TELEGRAM, "Pesquisar matrizes",
                R.drawable.ic_ms_search_rounded, "brother_drawer_telegram"
            ))

            sectionTitle("CONTA E SUPORTE")
            drawerItem(BrotherMenuEntry(
                BrotherDrawerDestination.ACCOUNT, "Minha conta e preferências",
                R.drawable.ic_ms_account_circle_rounded, "brother_drawer_account"
            ))
            drawerItem(BrotherMenuEntry(
                BrotherDrawerDestination.COMMUNITY, "Novidades e suporte",
                R.drawable.ic_premium_bell, "brother_drawer_community"
            ))
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Brother Matrizes v${BuildConfig.VERSION_NAME}",
                color = FioNavigationMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 28.dp, bottom = 12.dp)
            )
        }
    }
}
