package com.darjnest.kinecare.feature.professional_panel.presentation.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darjnest.kinecare.core.designsystem.theme.AzulPetroleo30
import com.darjnest.kinecare.core.designsystem.theme.Gris30
import com.darjnest.kinecare.core.designsystem.theme.InicioDorado
import com.darjnest.kinecare.core.designsystem.theme.KineCareTheme
import com.darjnest.kinecare.core.designsystem.theme.LoginAzulSuave
import com.darjnest.kinecare.core.designsystem.theme.LoginFondo
import com.darjnest.kinecare.core.designsystem.theme.LoginGrisClaro
import com.darjnest.kinecare.core.designsystem.theme.LoginGrisTexto
import com.darjnest.kinecare.core.designsystem.theme.LoginMenta
import com.darjnest.kinecare.core.designsystem.theme.LoginPrimario
import com.darjnest.kinecare.core.designsystem.theme.LoginPrimarioOscuro
import com.darjnest.kinecare.core.designsystem.theme.RojoError40
import com.darjnest.kinecare.core.designsystem.theme.RojoError90
import com.darjnest.kinecare.core.designsystem.theme.TextoPrincipal
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.AccesoGestion
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.ColorAcceso
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.ProfessionalPanelAction
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.ProfessionalPanelState
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.ProfessionalPanelViewModel
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.ProximaCita
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.ResumenHoy
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.VerificacionPanel

@Composable
fun ProfessionalPanelRoot(
    modifier: Modifier = Modifier,
    viewModel: ProfessionalPanelViewModel = hiltViewModel(),
    onAbrirAccesoGestion: (String) -> Unit = {},
    onAbrirSolicitudes: () -> Unit = {},
    onAbrirMiPerfil: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProfessionalPanelScreen(
        state = state,
        onAction = { accion ->
            // La navegacion entre pantallas del panel no es responsabilidad
            // del ViewModel: el Root la resuelve directo contra el NavGraph
            // (ver ProfessionalPanelNavGraph) e intercepta solo esta accion.
            if (accion is ProfessionalPanelAction.SeleccionarAccesoGestion) {
                onAbrirAccesoGestion(accion.accesoId)
            } else {
                viewModel.onAction(accion)
            }
        },
        onAbrirSolicitudes = onAbrirSolicitudes,
        onAbrirMiPerfil = onAbrirMiPerfil,
        modifier = modifier,
    )
}

@Composable
fun ProfessionalPanelScreen(
    state: ProfessionalPanelState,
    onAction: (ProfessionalPanelAction) -> Unit = {},
    onAbrirSolicitudes: () -> Unit = {},
    onAbrirMiPerfil: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = LoginFondo,
        bottomBar = {
            BarraNavegacionInferiorProfesional(
                onAbrirSolicitudes = onAbrirSolicitudes,
                onAbrirMiPerfil = onAbrirMiPerfil,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LoginFondo)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            EncabezadoPanel()

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Spacer(modifier = Modifier.height(12.dp))
                FilaSaludoDisponibilidad(
                    nombreProfesional = state.nombreProfesional,
                    disponible = state.disponible,
                    onAction = onAction,
                )

                state.verificacion?.let { verificacion ->
                    Spacer(modifier = Modifier.height(16.dp))
                    TarjetaVerificacion(verificacion = verificacion)
                }

                state.resumenHoy?.let { resumenHoy ->
                    Spacer(modifier = Modifier.height(24.dp))
                    FilaResumenHoyTitulo(actualizadoHaceTexto = resumenHoy.actualizadoHaceTexto)

                    Spacer(modifier = Modifier.height(12.dp))
                    FilaTarjetasResumen(resumenHoy = resumenHoy)
                }

                state.proximaCita?.let { proximaCita ->
                    Spacer(modifier = Modifier.height(16.dp))
                    TarjetaProximaCita(proximaCita = proximaCita, onAction = onAction)
                }

                Spacer(modifier = Modifier.height(24.dp))
                FilaGestionTitulo()

                Spacer(modifier = Modifier.height(12.dp))
                GrillaAccesosGestion(accesos = state.accesosGestion, onAction = onAction)

                Spacer(modifier = Modifier.height(16.dp))
                TarjetaConfiguracionCuenta(onClick = { onAction(ProfessionalPanelAction.AbrirConfiguracionCuenta) })

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun EncabezadoPanel() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(LoginPrimarioOscuro),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "K", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Panel",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = LoginPrimarioOscuro,
                )
                Text(
                    text = "Profesional",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = LoginPrimarioOscuro,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.NotificationsNone,
                contentDescription = "Notificaciones",
                tint = TextoPrincipal,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(LoginGrisClaro),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Person, contentDescription = "Mi perfil", tint = LoginGrisTexto, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun FilaSaludoDisponibilidad(
    nombreProfesional: String,
    disponible: Boolean,
    onAction: (ProfessionalPanelAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column {
            Text(
                text = if (nombreProfesional.isNotBlank()) "Hola, $nombreProfesional👋" else "Hola👋",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextoPrincipal,
            )
            Text(
                text = "Portal Kinesiólogos KineCare",
                style = MaterialTheme.typography.bodyMedium,
                color = LoginGrisTexto,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (disponible) LoginPrimarioOscuro else LoginGrisClaro)
                .clickable { onAction(ProfessionalPanelAction.CambiarDisponibilidad(!disponible)) }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (disponible) "Disponible" else "No disponible",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (disponible) Color.White else LoginGrisTexto,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (disponible) Color.White else LoginGrisTexto,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun TarjetaVerificacion(verificacion: VerificacionPanel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(LoginMenta),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Perfil Verificado SIS N° ${verificacion.numeroSis}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = LoginPrimarioOscuro,
                )
                Text(
                    text = "${verificacion.entidad} • Credenciales al día",
                    style = MaterialTheme.typography.labelSmall,
                    color = LoginGrisTexto,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun FilaResumenHoyTitulo(actualizadoHaceTexto: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Resumen de Hoy",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextoPrincipal,
        )
        Text(
            text = actualizadoHaceTexto,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = LoginPrimario,
        )
    }
}

@Composable
private fun FilaTarjetasResumen(resumenHoy: ResumenHoy) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TarjetaResumen(
            modifier = Modifier.weight(1f),
            icono = Icons.Filled.CalendarMonth,
            colorCirculo = LoginAzulSuave,
            colorIcono = AzulPetroleo30,
            etiqueta = "PRÓXIMAS",
            valor = "${resumenHoy.proximasCitas} citas",
            subtitulo = "Hoy: ${resumenHoy.citasHoyEnAgenda} en agenda",
        )
        TarjetaResumen(
            modifier = Modifier.weight(1f),
            icono = Icons.Filled.Payments,
            colorCirculo = LoginMenta,
            colorIcono = LoginPrimarioOscuro,
            etiqueta = "POR LIQUIDAR",
            valor = formatearClp(resumenHoy.porLiquidar),
            subtitulo = "Liquidación semanal",
        )
        TarjetaResumenCalificacion(
            modifier = Modifier.weight(1f),
            calificacion = resumenHoy.calificacion,
            totalResenas = resumenHoy.totalResenas,
        )
    }
}

@Composable
private fun TarjetaResumen(
    icono: ImageVector,
    colorCirculo: Color,
    colorIcono: Color,
    etiqueta: String,
    valor: String,
    subtitulo: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(colorCirculo),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icono, contentDescription = null, tint = colorIcono, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = etiqueta,
                style = MaterialTheme.typography.labelSmall,
                color = LoginGrisTexto,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = valor,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextoPrincipal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitulo,
                style = MaterialTheme.typography.labelSmall,
                color = LoginGrisTexto,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TarjetaResumenCalificacion(
    calificacion: Double,
    totalResenas: Int,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(LoginGrisClaro),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Star, contentDescription = null, tint = InicioDorado, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "CALIFICACIÓN",
                style = MaterialTheme.typography.labelSmall,
                color = LoginGrisTexto,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$calificacion",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextoPrincipal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Filled.Star, contentDescription = null, tint = InicioDorado, modifier = Modifier.size(14.dp))
            }
            Text(
                text = "$totalResenas opiniones",
                style = MaterialTheme.typography.labelSmall,
                color = LoginGrisTexto,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TarjetaProximaCita(
    proximaCita: ProximaCita,
    onAction: (ProfessionalPanelAction) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(RojoError40),
            )
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Alarm, contentDescription = null, tint = RojoError40, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "En ${proximaCita.minutosRestantes} min • ${proximaCita.modalidad}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = RojoError40,
                        )
                    }
                    Text(
                        text = proximaCita.horaTexto,
                        style = MaterialTheme.typography.labelSmall,
                        color = LoginGrisTexto,
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(LoginMenta),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(26.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = proximaCita.pacienteNombre,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextoPrincipal,
                        )
                        Text(
                            text = proximaCita.servicio,
                            style = MaterialTheme.typography.bodyMedium,
                            color = LoginPrimario,
                        )
                        Row(
                            modifier = Modifier.padding(top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = LoginGrisTexto, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${proximaCita.distanciaKm} km • ${proximaCita.comuna}",
                                style = MaterialTheme.typography.labelSmall,
                                color = LoginGrisTexto,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(LoginGrisClaro)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = LoginGrisTexto, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = proximaCita.direccion,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextoPrincipal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = proximaCita.piso,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = LoginGrisTexto,
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(LoginGrisClaro)
                            .clickable { onAction(ProfessionalPanelAction.VerFichaPaciente) }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Description, contentDescription = null, tint = TextoPrincipal, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Ver Ficha", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = TextoPrincipal)
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(LoginPrimarioOscuro)
                            .clickable { onAction(ProfessionalPanelAction.AbrirRuta) }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Navigation, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Ruta Waze/Maps", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilaGestionTitulo() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Gestión Profesional",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextoPrincipal,
        )
        Text(
            text = "Herramientas",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = LoginPrimario,
        )
    }
}

@Composable
private fun GrillaAccesosGestion(
    accesos: List<AccesoGestion>,
    onAction: (ProfessionalPanelAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        accesos.chunked(2).forEach { fila ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                fila.forEach { acceso ->
                    TarjetaAccesoGestion(
                        acceso = acceso,
                        onClick = { onAction(ProfessionalPanelAction.SeleccionarAccesoGestion(acceso.id)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (fila.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private data class ColoresAcceso(val circulo: Color, val icono: Color)

private fun coloresPara(color: ColorAcceso): ColoresAcceso = when (color) {
    ColorAcceso.AZUL -> ColoresAcceso(circulo = LoginAzulSuave, icono = AzulPetroleo30)
    ColorAcceso.VERDE -> ColoresAcceso(circulo = LoginMenta, icono = LoginPrimarioOscuro)
    ColorAcceso.GRIS -> ColoresAcceso(circulo = LoginGrisClaro, icono = Gris30)
    ColorAcceso.ROJO -> ColoresAcceso(circulo = RojoError90, icono = RojoError40)
}

@Composable
private fun TarjetaAccesoGestion(
    acceso: AccesoGestion,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = coloresPara(acceso.color)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(colores.circulo),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(acceso.icono, contentDescription = null, tint = colores.icono, modifier = Modifier.size(18.dp))
                }
                when {
                    acceso.badgeTexto != null -> Text(
                        text = acceso.badgeTexto,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = LoginPrimarioOscuro,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(LoginMenta)
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                    acceso.badgeNumero != null -> Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(RojoError40),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${acceso.badgeNumero}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                    acceso.badgeCheck -> Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(LoginMenta),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(14.dp))
                    }
                    acceso.mostrarChevron -> Icon(
                        Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        tint = LoginGrisTexto,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = acceso.titulo,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = TextoPrincipal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = acceso.subtitulo,
                style = MaterialTheme.typography.labelSmall,
                color = if (acceso.subtituloEnAlerta) RojoError40 else LoginGrisTexto,
                fontWeight = if (acceso.subtituloEnAlerta) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun TarjetaConfiguracionCuenta(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(LoginGrisClaro),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Settings, contentDescription = null, tint = TextoPrincipal, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Configuración de Cuenta",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextoPrincipal,
                )
                Text(
                    text = "Seguridad, notificaciones y soporte KineCare",
                    style = MaterialTheme.typography.labelSmall,
                    color = LoginGrisTexto,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = LoginGrisTexto,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun BarraNavegacionInferiorProfesional(
    onAbrirSolicitudes: () -> Unit,
    onAbrirMiPerfil: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ItemNavegacionInferior(texto = "Inicio", icono = Icons.Filled.Dashboard, activo = true)
        ItemNavegacionInferior(texto = "Reservas", icono = Icons.Filled.CalendarMonth, activo = false, onClick = onAbrirSolicitudes)
        // Pacientes todavia no tiene subpantalla (Fase 7).
        ItemNavegacionInferior(texto = "Pacientes", icono = Icons.Filled.HowToReg, activo = false)
        ItemNavegacionInferior(texto = "Perfil", icono = Icons.Filled.Folder, activo = false, onClick = onAbrirMiPerfil)
    }
}

@Composable
private fun ItemNavegacionInferior(
    texto: String,
    icono: ImageVector,
    activo: Boolean,
    onClick: (() -> Unit)? = null,
) {
    val color = if (activo) LoginPrimarioOscuro else LoginGrisTexto
    Column(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icono, contentDescription = texto, tint = color, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = texto, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal)
    }
}

private fun formatearClp(monto: Long): String {
    val agrupado = monto.toString()
        .reversed()
        .chunked(3)
        .joinToString(".")
        .reversed()
    return "CLP $$agrupado"
}

@Preview(showBackground = true, heightDp = 1600)
@Composable
private fun ProfessionalPanelScreenPreview() {
    KineCareTheme {
        ProfessionalPanelScreen(state = ProfessionalPanelState())
    }
}
