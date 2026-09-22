package com.darjnest.kinecare.feature.professional_panel.presentation.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darjnest.kinecare.core.designsystem.theme.AzulPetroleo30
import com.darjnest.kinecare.core.designsystem.theme.InicioDorado
import com.darjnest.kinecare.core.designsystem.theme.KineCareTheme
import com.darjnest.kinecare.core.designsystem.theme.LoginAzulSuave
import com.darjnest.kinecare.core.designsystem.theme.LoginFondo
import com.darjnest.kinecare.core.designsystem.theme.LoginGrisClaro
import com.darjnest.kinecare.core.designsystem.theme.LoginGrisTexto
import com.darjnest.kinecare.core.designsystem.theme.LoginMenta
import com.darjnest.kinecare.core.designsystem.theme.LoginPrimario
import com.darjnest.kinecare.core.designsystem.theme.LoginPrimarioOscuro
import com.darjnest.kinecare.core.designsystem.theme.LoginTerciario
import com.darjnest.kinecare.core.designsystem.theme.RojoError40
import com.darjnest.kinecare.core.designsystem.theme.RojoError90
import com.darjnest.kinecare.core.designsystem.theme.TextoPrincipal
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.ModalidadSolicitud
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.NivelUrgenciaSolicitud
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.PestanaSolicitudes
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.SolicitudAtencion
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.SolicitudesDeAtencionAction
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.SolicitudesDeAtencionState
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.SolicitudesDeAtencionViewModel

@Composable
fun SolicitudesDeAtencionRoot(
    modifier: Modifier = Modifier,
    viewModel: SolicitudesDeAtencionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SolicitudesDeAtencionScreen(state = state, onAction = viewModel::onAction, modifier = modifier)
}

@Composable
fun SolicitudesDeAtencionScreen(
    state: SolicitudesDeAtencionState,
    onAction: (SolicitudesDeAtencionAction) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = LoginFondo,
        bottomBar = { BarraNavegacionInferiorSolicitudes() },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LoginFondo)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            EncabezadoSolicitudes()

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Spacer(modifier = Modifier.height(12.dp))
                FilaPestanas(
                    pestanaSeleccionada = state.pestanaSeleccionada,
                    totalPendientes = state.solicitudesPendientes.size,
                    onAction = onAction,
                )

                if (state.pestanaSeleccionada == PestanaSolicitudes.PENDIENTES) {
                    if (state.solicitudesPendientes.isNotEmpty() && state.plazoRespuestaTexto.isNotBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        BannerPlazoRespuesta(
                            totalPendientes = state.solicitudesPendientes.size,
                            plazoTexto = state.plazoRespuestaTexto,
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    if (state.solicitudesPendientes.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            state.solicitudesPendientes.forEach { solicitud ->
                                TarjetaSolicitud(solicitud = solicitud, onAction = onAction)
                            }
                        }
                    } else {
                        TarjetaSinSolicitudesPendientes()
                    }
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                    TarjetaHistorialResumen(
                        completadasEstaSemana = state.completadasEstaSemana,
                        porcentajeRespuestaATiempo = state.porcentajeRespuestaATiempo,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun EncabezadoSolicitudes() {
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
                    text = "KINECARE PRO",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = LoginPrimario,
                )
                Text(
                    text = "Solicitudes de Atención",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextoPrincipal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.NotificationsNone, contentDescription = "Notificaciones", tint = LoginGrisTexto)
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
private fun FilaPestanas(
    pestanaSeleccionada: PestanaSolicitudes,
    totalPendientes: Int,
    onAction: (SolicitudesDeAtencionAction) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(LoginGrisClaro)
            .padding(4.dp),
    ) {
        PestanaItem(
            texto = "Pendientes",
            badgeNumero = totalPendientes,
            seleccionada = pestanaSeleccionada == PestanaSolicitudes.PENDIENTES,
            onClick = { onAction(SolicitudesDeAtencionAction.CambiarPestana(PestanaSolicitudes.PENDIENTES)) },
            modifier = Modifier.weight(1f),
        )
        PestanaItem(
            texto = "Historial / Resueltas",
            badgeNumero = null,
            seleccionada = pestanaSeleccionada == PestanaSolicitudes.HISTORIAL,
            onClick = { onAction(SolicitudesDeAtencionAction.CambiarPestana(PestanaSolicitudes.HISTORIAL)) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PestanaItem(
    texto: String,
    badgeNumero: Int?,
    seleccionada: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (seleccionada) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = texto,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (seleccionada) AzulPetroleo30 else LoginGrisTexto,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (badgeNumero != null && badgeNumero > 0) {
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(RojoError40)
                    .padding(horizontal = 7.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "$badgeNumero", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BannerPlazoRespuesta(totalPendientes: Int, plazoTexto: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(LoginAzulSuave)
            .padding(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(AzulPetroleo30),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Timer, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = "$totalPendientes solicitudes por confirmar",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = AzulPetroleo30,
            )
            Text(
                text = plazoTexto,
                style = MaterialTheme.typography.bodyMedium,
                color = TextoPrincipal,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun TarjetaSinSolicitudesPendientes() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 40.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(LoginGrisClaro),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.EventAvailable, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(30.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Sin solicitudes pendientes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextoPrincipal,
            )
            Text(
                text = "Cuando un paciente reserve contigo, aparecerá aquí para tu confirmación.",
                style = MaterialTheme.typography.bodyMedium,
                color = LoginGrisTexto,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun TarjetaHistorialResumen(completadasEstaSemana: Int, porcentajeRespuestaATiempo: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 40.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(LoginGrisClaro),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.FactCheck, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(30.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Historial al día",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextoPrincipal,
            )
            Text(
                text = if (completadasEstaSemana > 0) {
                    "Has completado y gestionado $completadasEstaSemana solicitudes esta semana con $porcentajeRespuestaATiempo% de respuesta a tiempo."
                } else {
                    "Aún no tienes solicitudes resueltas esta semana."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = LoginGrisTexto,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun TarjetaSolicitud(
    solicitud: SolicitudAtencion,
    onAction: (SolicitudesDeAtencionAction) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BadgeUrgencia(urgencia = solicitud.urgencia, texto = solicitud.tiempoRestanteTexto)
                BadgeModalidad(modalidad = solicitud.modalidad, texto = solicitud.modalidadTexto)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(LoginMenta),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(28.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = solicitud.pacienteNombre,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextoPrincipal,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Filled.Star, contentDescription = null, tint = InicioDorado, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(text = "${solicitud.calificacion}", style = MaterialTheme.typography.labelMedium, color = LoginGrisTexto)
                    }
                    Text(
                        text = solicitud.pacienteVerificadoTexto,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = LoginPrimarioOscuro,
                    )
                    Text(text = solicitud.especialidad, style = MaterialTheme.typography.labelSmall, color = LoginGrisTexto)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(LoginGrisClaro)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.EventAvailable, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = solicitud.fechaHoraTexto, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = TextoPrincipal)
                    solicitud.tiempoRelativoTexto?.let { relativo ->
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = relativo, style = MaterialTheme.typography.labelSmall, color = LoginGrisTexto)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PinDrop, contentDescription = null, tint = AzulPetroleo30, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = solicitud.direccion,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextoPrincipal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(LoginGrisClaro)
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.MedicalServices, contentDescription = null, tint = LoginGrisTexto, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "MOTIVO DE CONSULTA",
                        style = MaterialTheme.typography.labelSmall,
                        color = LoginGrisTexto,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "“${solicitud.motivoConsulta}”",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = TextoPrincipal,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(text = "HONORARIO NETO", style = MaterialTheme.typography.labelSmall, color = LoginGrisTexto)
                    Text(
                        text = formatearClp(solicitud.honorarioClp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextoPrincipal,
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(LoginMenta)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Shield, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = solicitud.custodiaTexto,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = LoginPrimarioOscuro,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(LoginGrisClaro)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = LoginTerciario, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = solicitud.avisoPagoTexto,
                    style = MaterialTheme.typography.labelSmall,
                    color = LoginGrisTexto,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(LoginGrisClaro)
                        .clickable { onAction(SolicitudesDeAtencionAction.RechazarSolicitud(solicitud.id)) }
                        .padding(vertical = 13.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = TextoPrincipal, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Rechazar", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = TextoPrincipal)
                }
                Row(
                    modifier = Modifier
                        .weight(1.6f)
                        .clip(RoundedCornerShape(50))
                        .background(LoginPrimarioOscuro)
                        .clickable { onAction(SolicitudesDeAtencionAction.AceptarSolicitud(solicitud.id)) }
                        .padding(vertical = 13.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Aceptar Cita", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun BadgeUrgencia(urgencia: NivelUrgenciaSolicitud, texto: String) {
    val fondo = if (urgencia == NivelUrgenciaSolicitud.URGENTE) RojoError90 else LoginGrisClaro
    val color = if (urgencia == NivelUrgenciaSolicitud.URGENTE) RojoError40 else LoginGrisTexto
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(fondo)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = texto, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun BadgeModalidad(modalidad: ModalidadSolicitud, texto: String) {
    val icono: ImageVector = if (modalidad == ModalidadSolicitud.A_DOMICILIO) Icons.Filled.PinDrop else Icons.Filled.Apartment
    val fondo = if (modalidad == ModalidadSolicitud.A_DOMICILIO) LoginGrisClaro else LoginAzulSuave
    val color = if (modalidad == ModalidadSolicitud.A_DOMICILIO) LoginGrisTexto else AzulPetroleo30
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(fondo)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icono, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = texto, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun BarraNavegacionInferiorSolicitudes() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        // Inicio, Agenda, Pacientes y Perfil se conectan cuando existan sus
        // subpantallas (Fase 7).
        ItemNavegacionInferiorSolicitudes(texto = "Inicio", icono = Icons.Filled.Dashboard, activo = false)
        ItemNavegacionInferiorSolicitudes(texto = "Solicitudes", icono = Icons.Filled.NotificationsActive, activo = true)
        ItemNavegacionInferiorSolicitudes(texto = "Agenda", icono = Icons.Filled.CalendarMonth, activo = false)
        ItemNavegacionInferiorSolicitudes(texto = "Pacientes", icono = Icons.Filled.HowToReg, activo = false)
        ItemNavegacionInferiorSolicitudes(texto = "Perfil", icono = Icons.Filled.Badge, activo = false)
    }
}

@Composable
private fun ItemNavegacionInferiorSolicitudes(
    texto: String,
    icono: ImageVector,
    activo: Boolean,
) {
    val color = if (activo) LoginPrimarioOscuro else LoginGrisTexto
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
private fun SolicitudesDeAtencionScreenPreview() {
    KineCareTheme {
        SolicitudesDeAtencionScreen(state = SolicitudesDeAtencionState())
    }
}

@Preview(showBackground = true, heightDp = 2400, name = "Con solicitudes")
@Composable
private fun SolicitudesDeAtencionScreenConDatosPreview() {
    KineCareTheme {
        SolicitudesDeAtencionScreen(
            state = SolicitudesDeAtencionState(
                plazoRespuestaTexto = "Responde antes de que expire el plazo (2 hrs) para resguardar tu índice de puntualidad y reputación clínica.",
                solicitudesPendientes = listOf(
                    SolicitudAtencion(
                        id = "1",
                        urgencia = NivelUrgenciaSolicitud.URGENTE,
                        tiempoRestanteTexto = "Urgente • Expira en 38 min",
                        modalidad = ModalidadSolicitud.A_DOMICILIO,
                        modalidadTexto = "A Domicilio",
                        pacienteNombre = "Matías Morales",
                        calificacion = 4.9,
                        pacienteVerificadoTexto = "Paciente Verificado Fonasa/Isapre",
                        especialidad = "Kinesiología Deportiva",
                        fechaHoraTexto = "Hoy, 17:30 hrs",
                        tiempoRelativoTexto = "(En 2 horas)",
                        direccion = "Av. Pocuro 2150, Depto 402, Providencia",
                        motivoConsulta = "Dolor lumbar agudo tras entrenamiento de running, dificultad severa para flexionar el tronco desde hace 4 horas.",
                        honorarioClp = 25000,
                        custodiaTexto = "Custodia KineCare Activa",
                        avisoPagoTexto = "El paciente ya realizó el pago garantizado vía Webpay Plus.",
                    ),
                ),
            ),
        )
    }
}
