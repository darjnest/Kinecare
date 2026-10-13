package com.darjnest.kinecare.core.designsystem.components.bar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.darjnest.kinecare.core.designsystem.theme.KineCareTheme

/** Pestana activa en la barra de navegacion inferior del rol Profesional. */
enum class PestanaProfesionalInferior {
    INICIO,
    SOLICITUDES,
    AGENDA,
    PERFIL,
}

/**
 * Barra de navegacion inferior compartida por las pantallas de nivel raiz del rol
 * Profesional (Inicio, Solicitudes, Agenda, Perfil). Antes cada pantalla llevaba su propia
 * copia, con etiquetas e iconos distintos ("Reservas" vs "Solicitudes", carpeta vs credencial).
 * La navegacion real entre pestanas se resuelve con callbacks hacia el NavHost de `:app`;
 * la pestana activa no navega a si misma. Respeta el inset de la barra de navegacion del
 * sistema (gestos), igual que [KineCareBottomNavBar].
 */
@Composable
fun KineCareProfessionalBottomNavBar(
    pestanaActiva: PestanaProfesionalInferior,
    onInicio: () -> Unit,
    onSolicitudes: () -> Unit,
    onAgenda: () -> Unit,
    onPerfil: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .navigationBarsPadding()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ItemNavegacionInferior(
            texto = "Inicio",
            icono = Icons.Filled.Dashboard,
            activo = pestanaActiva == PestanaProfesionalInferior.INICIO,
            onClick = onInicio,
        )
        ItemNavegacionInferior(
            texto = "Solicitudes",
            icono = Icons.Filled.NotificationsActive,
            activo = pestanaActiva == PestanaProfesionalInferior.SOLICITUDES,
            onClick = onSolicitudes,
        )
        ItemNavegacionInferior(
            texto = "Agenda",
            icono = Icons.Filled.CalendarMonth,
            activo = pestanaActiva == PestanaProfesionalInferior.AGENDA,
            onClick = onAgenda,
        )
        ItemNavegacionInferior(
            texto = "Perfil",
            icono = Icons.Filled.Badge,
            activo = pestanaActiva == PestanaProfesionalInferior.PERFIL,
            onClick = onPerfil,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun KineCareProfessionalBottomNavBarPreview() {
    KineCareTheme {
        KineCareProfessionalBottomNavBar(
            pestanaActiva = PestanaProfesionalInferior.INICIO,
            onInicio = {},
            onSolicitudes = {},
            onAgenda = {},
            onPerfil = {},
        )
    }
}
