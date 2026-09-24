package com.darjnest.kinecare.core.designsystem.components.bar

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.darjnest.kinecare.core.designsystem.theme.KineCareTheme
import com.darjnest.kinecare.core.designsystem.theme.LoginGrisTexto
import com.darjnest.kinecare.core.designsystem.theme.LoginPrimarioOscuro

/** Pestana activa en la barra de navegacion inferior del rol Cliente. */
enum class PestanaClienteInferior {
    EXPLORAR,
    MIS_CITAS,
    FAVORITOS,
    MI_PERFIL,
}

/**
 * Barra de navegacion inferior compartida por las pantallas de nivel raiz
 * del rol Cliente (Explorar, Mis Citas, Favoritos, Mi Perfil). Cada
 * pantalla vive en un modulo `:feature:*` distinto, asi que la navegacion
 * real entre pestanas se resuelve con callbacks hacia el NavHost de `:app`.
 */
@Composable
fun KineCareBottomNavBar(
    pestanaActiva: PestanaClienteInferior,
    onExplorar: () -> Unit,
    onMisCitas: () -> Unit,
    onFavoritos: () -> Unit,
    onMiPerfil: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ItemNavegacionInferior(
            texto = "Explorar",
            icono = Icons.Filled.Search,
            activo = pestanaActiva == PestanaClienteInferior.EXPLORAR,
            onClick = onExplorar,
        )
        ItemNavegacionInferior(
            texto = "Mis Citas",
            icono = Icons.Filled.CalendarMonth,
            activo = pestanaActiva == PestanaClienteInferior.MIS_CITAS,
            onClick = onMisCitas,
        )
        ItemNavegacionInferior(
            texto = "Favoritos",
            icono = Icons.Filled.Favorite,
            activo = pestanaActiva == PestanaClienteInferior.FAVORITOS,
            onClick = onFavoritos,
        )
        ItemNavegacionInferior(
            texto = "Mi Perfil",
            icono = Icons.Filled.Person,
            activo = pestanaActiva == PestanaClienteInferior.MI_PERFIL,
            onClick = onMiPerfil,
        )
    }
}

@Composable
private fun ItemNavegacionInferior(
    texto: String,
    icono: ImageVector,
    activo: Boolean,
    onClick: () -> Unit,
) {
    val color = if (activo) LoginPrimarioOscuro else LoginGrisTexto
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Icon(icono, contentDescription = texto, tint = color, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = texto,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun KineCareBottomNavBarPreview() {
    KineCareTheme {
        KineCareBottomNavBar(
            pestanaActiva = PestanaClienteInferior.EXPLORAR,
            onExplorar = {},
            onMisCitas = {},
            onFavoritos = {},
            onMiPerfil = {},
        )
    }
}
