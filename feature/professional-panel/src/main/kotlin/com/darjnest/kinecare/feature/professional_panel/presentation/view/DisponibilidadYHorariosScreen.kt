package com.darjnest.kinecare.feature.professional_panel.presentation.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Toll
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darjnest.kinecare.core.designsystem.theme.AzulPetroleo30
import com.darjnest.kinecare.core.designsystem.theme.KineCareTheme
import com.darjnest.kinecare.core.designsystem.theme.LoginAzulSuave
import com.darjnest.kinecare.core.designsystem.theme.LoginFondo
import com.darjnest.kinecare.core.designsystem.theme.LoginGrisClaro
import com.darjnest.kinecare.core.designsystem.theme.LoginGrisTexto
import com.darjnest.kinecare.core.designsystem.theme.LoginMenta
import com.darjnest.kinecare.core.designsystem.theme.LoginPrimarioOscuro
import com.darjnest.kinecare.core.designsystem.theme.LoginTerciario
import com.darjnest.kinecare.core.designsystem.theme.RojoError40
import com.darjnest.kinecare.core.designsystem.theme.RojoError90
import com.darjnest.kinecare.core.designsystem.theme.TextoPrincipal
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.ComunaCobertura
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.DiaLaboral
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.DisponibilidadYHorariosAction
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.DisponibilidadYHorariosState
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.DisponibilidadYHorariosViewModel
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.TonoTurno
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.TurnoHorario
import kotlin.math.roundToInt

private const val RADIO_KM_MINIMO = 3f
private const val RADIO_KM_MAXIMO = 25f
private val OPCIONES_TRASLADO_MINUTOS = listOf(15, 30, 45, 60)

@Composable
fun DisponibilidadYHorariosRoot(
    modifier: Modifier = Modifier,
    viewModel: DisponibilidadYHorariosViewModel = hiltViewModel(),
    onVolver: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DisponibilidadYHorariosScreen(state = state, onAction = viewModel::onAction, onVolver = onVolver, modifier = modifier)
}

@Composable
fun DisponibilidadYHorariosScreen(
    state: DisponibilidadYHorariosState,
    onAction: (DisponibilidadYHorariosAction) -> Unit = {},
    onVolver: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = LoginFondo,
        bottomBar = {
            BarraGuardarDisponibilidad(
                onClick = { onAction(DisponibilidadYHorariosAction.GuardarDisponibilidad) },
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
            EncabezadoDisponibilidad(onVolver = onVolver)

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Spacer(modifier = Modifier.height(12.dp))
                TarjetaReservasInmediatas(activas = state.reservasInmediatasActivas, onAction = onAction)

                if (state.diasLaborales.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    SeccionDiasLaborales(dias = state.diasLaborales, onAction = onAction)
                }

                if (state.turnos.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    val diaSeleccionado = state.diasLaborales.firstOrNull { it.seleccionado }
                    SeccionHorarios(diaSeleccionado = diaSeleccionado, turnos = state.turnos, onAction = onAction)

                    Spacer(modifier = Modifier.height(16.dp))
                    TarjetaTrasladoEntreSesiones(
                        minutosSeleccionado = state.minutosTrasladoSeleccionado,
                        onAction = onAction,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                SeccionCoberturaDomicilio(state = state, onAction = onAction)

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun EncabezadoDisponibilidad(onVolver: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Volver",
            tint = TextoPrincipal,
            modifier = Modifier.clickable(onClick = onVolver),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Disponibilidad y Horarios",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextoPrincipal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones", tint = LoginGrisTexto)
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(LoginGrisClaro),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Person, contentDescription = "Mi perfil", tint = LoginGrisTexto, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun TarjetaReservasInmediatas(
    activas: Boolean,
    onAction: (DisponibilidadYHorariosAction) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(LoginMenta),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Bolt, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Reservas inmediatas",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextoPrincipal,
                        )
                        Text(
                            text = "Pacientes pueden agendar hoy",
                            style = MaterialTheme.typography.labelSmall,
                            color = LoginGrisTexto,
                        )
                    }
                }
                Switch(
                    checked = activas,
                    onCheckedChange = { onAction(DisponibilidadYHorariosAction.CambiarReservasInmediatas(it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = LoginPrimarioOscuro,
                        uncheckedThumbColor = LoginGrisTexto,
                        uncheckedTrackColor = LoginGrisClaro,
                    ),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(LoginMenta)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(LoginPrimarioOscuro),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (activas) "Disponible para hoy" else "No disponible hoy",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = LoginPrimarioOscuro,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = LoginTerciario, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Modo dinámico activado",
                        style = MaterialTheme.typography.labelSmall,
                        color = LoginGrisTexto,
                    )
                }
            }
        }
    }
}

@Composable
private fun SeccionDiasLaborales(
    dias: List<DiaLaboral>,
    onAction: (DisponibilidadYHorariosAction) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = AzulPetroleo30, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Días Laborales",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextoPrincipal,
                )
            }
            Text(
                text = "${dias.count { it.habilitado }} de ${dias.size} días",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = AzulPetroleo30,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(LoginGrisClaro)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            dias.forEach { dia ->
                ChipDia(
                    dia = dia,
                    onClick = { onAction(DisponibilidadYHorariosAction.SeleccionarDia(dia.id)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ChipDia(
    dia: DiaLaboral,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fondo = when {
        dia.seleccionado -> LoginPrimarioOscuro
        dia.habilitado -> AzulPetroleo30
        else -> Color.White
    }
    val texto = if (dia.habilitado) Color.White else LoginGrisTexto
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(fondo)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = dia.nombreCorto, style = MaterialTheme.typography.labelSmall, color = texto)
        Text(
            text = "${dia.numeroDia}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = texto,
        )
    }
}

@Composable
private fun SeccionHorarios(
    diaSeleccionado: DiaLaboral?,
    turnos: List<TurnoHorario>,
    onAction: (DisponibilidadYHorariosAction) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text(
                    text = if (diaSeleccionado != null) {
                        "Horarios ${diaSeleccionado.nombreCompleto} ${diaSeleccionado.numeroDia}"
                    } else {
                        "Horarios"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextoPrincipal,
                )
                Text(
                    text = "Kinesiología a Domicilio y Consulta",
                    style = MaterialTheme.typography.labelSmall,
                    color = LoginGrisTexto,
                )
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(LoginAzulSuave)
                    .clickable { onAction(DisponibilidadYHorariosAction.Replicar) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = AzulPetroleo30, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Replicar", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = AzulPetroleo30)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            turnos.forEach { turno -> TarjetaTurno(turno = turno, onAction = onAction) }
        }
    }
}

@Composable
private fun TarjetaTurno(
    turno: TurnoHorario,
    onAction: (DisponibilidadYHorariosAction) -> Unit,
) {
    val colorCirculo = if (turno.tono == TonoTurno.MATUTINO) LoginAzulSuave else LoginMenta
    val colorIcono = if (turno.tono == TonoTurno.MATUTINO) AzulPetroleo30 else LoginPrimarioOscuro
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(colorCirculo),
                        contentAlignment = Alignment.Center,
                    ) {
                        val icono = if (turno.tono == TonoTurno.MATUTINO) Icons.Filled.WbSunny else Icons.Filled.WbTwilight
                        Icon(icono, contentDescription = null, tint = colorIcono, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(text = turno.titulo, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = TextoPrincipal)
                        Text(
                            text = "${turno.cupos} cupos de ${turno.duracionMinutos} min",
                            style = MaterialTheme.typography.labelSmall,
                            color = LoginGrisTexto,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(LoginGrisClaro)
                        .clickable { onAction(DisponibilidadYHorariosAction.EditarTurno(turno.id)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Tune, contentDescription = "Editar turno", tint = AzulPetroleo30, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
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
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = AzulPetroleo30, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${turno.horaInicio} – ${turno.horaFin} hrs",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextoPrincipal,
                    )
                }
                val fondoBadge = if (turno.activo) LoginMenta else RojoError90
                val textoBadge = if (turno.activo) LoginPrimarioOscuro else RojoError40
                Text(
                    text = if (turno.activo) "ACTIVO" else "INACTIVO",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = textoBadge,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(fondoBadge)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun TarjetaTrasladoEntreSesiones(
    minutosSeleccionado: Int,
    onAction: (DisponibilidadYHorariosAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(LoginAzulSuave)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = AzulPetroleo30, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Traslado entre sesiones", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = TextoPrincipal)
            }
            if (minutosSeleccionado > 0) {
                Text(
                    text = "$minutosSeleccionado min",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(AzulPetroleo30)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Margen de amortiguación para viajes entre comunas y preparación de implementos clínicos.",
            style = MaterialTheme.typography.labelSmall,
            color = LoginGrisTexto,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OPCIONES_TRASLADO_MINUTOS.forEach { minutos ->
                ChipMinutos(
                    minutos = minutos,
                    seleccionado = minutos == minutosSeleccionado,
                    onClick = { onAction(DisponibilidadYHorariosAction.SeleccionarMinutosTraslado(minutos)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ChipMinutos(
    minutos: Int,
    seleccionado: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "$minutos min",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = if (seleccionado) Color.White else TextoPrincipal,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (seleccionado) LoginPrimarioOscuro else Color.White)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    )
}

@Composable
private fun SeccionCoberturaDomicilio(
    state: DisponibilidadYHorariosState,
    onAction: (DisponibilidadYHorariosAction) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Cobertura a Domicilio", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = TextoPrincipal)
            }
            if (state.regionTexto.isNotBlank()) {
                Text(
                    text = state.regionTexto.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = LoginGrisTexto,
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(LoginGrisClaro),
                ) {
                    if (state.direccionBase.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.9f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(LoginTerciario),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Base: ${state.direccionBase}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextoPrincipal,
                            )
                        }
                    }
                }
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "Radio máximo de atención", style = MaterialTheme.typography.labelLarge, color = TextoPrincipal)
                        Text(
                            text = "${state.radioKm} km",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = LoginPrimarioOscuro,
                        )
                    }
                    Slider(
                        value = state.radioKm.toFloat(),
                        onValueChange = { onAction(DisponibilidadYHorariosAction.CambiarRadioKm(it.roundToInt())) },
                        valueRange = RADIO_KM_MINIMO..RADIO_KM_MAXIMO,
                        colors = SliderDefaults.colors(
                            thumbColor = LoginPrimarioOscuro,
                            activeTrackColor = LoginPrimarioOscuro,
                            inactiveTrackColor = LoginGrisClaro,
                        ),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(text = "3 km (Cercano)", style = MaterialTheme.typography.labelSmall, color = LoginGrisTexto)
                        Text(text = "15 km", style = MaterialTheme.typography.labelSmall, color = LoginGrisTexto)
                        Text(text = "25 km (Regional)", style = MaterialTheme.typography.labelSmall, color = LoginGrisTexto)
                    }
                }
            }
        }

        if (state.comunasHabilitadas.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "Comunas habilitadas", style = MaterialTheme.typography.labelLarge, color = TextoPrincipal)
                    Text(
                        text = "${state.comunasHabilitadas.count { it.habilitada }} seleccionadas",
                        style = MaterialTheme.typography.labelSmall,
                        color = AzulPetroleo30,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.comunasHabilitadas.forEach { comuna ->
                        ChipComuna(comuna = comuna, onClick = { onAction(DisponibilidadYHorariosAction.ToggleComuna(comuna.nombre)) })
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        TarjetaRecargoZonaLejana(state = state, onAction = onAction)
    }
}

@Composable
private fun ChipComuna(
    comuna: ComunaCobertura,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (comuna.habilitada) AzulPetroleo30 else LoginGrisClaro)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (comuna.habilitada) Icons.Filled.Check else Icons.Filled.Add,
            contentDescription = null,
            tint = if (comuna.habilitada) Color.White else LoginGrisTexto,
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = comuna.nombre,
            style = MaterialTheme.typography.labelMedium,
            color = if (comuna.habilitada) Color.White else LoginGrisTexto,
        )
    }
}

@Composable
private fun TarjetaRecargoZonaLejana(
    state: DisponibilidadYHorariosState,
    onAction: (DisponibilidadYHorariosAction) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(LoginAzulSuave),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Toll, contentDescription = null, tint = AzulPetroleo30, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(text = "Recargo por zona lejana", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = TextoPrincipal)
                        Text(
                            text = "Fuera de radio base (> ${state.radioKm} km)",
                            style = MaterialTheme.typography.labelSmall,
                            color = LoginGrisTexto,
                        )
                    }
                }
                Switch(
                    checked = state.recargoZonaLejanaActivo,
                    onCheckedChange = { onAction(DisponibilidadYHorariosAction.CambiarRecargoZonaLejana(it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AzulPetroleo30,
                        uncheckedThumbColor = LoginGrisTexto,
                        uncheckedTrackColor = LoginGrisClaro,
                    ),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(LoginGrisClaro)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Tarifa adicional por km excedente", style = MaterialTheme.typography.labelSmall, color = LoginGrisTexto)
                Text(
                    text = "${formatearClp(state.tarifaRecargoClp)} / km",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextoPrincipal,
                )
            }
        }
    }
}

@Composable
private fun BarraGuardarDisponibilidad(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(LoginPrimarioOscuro)
                .clickable(onClick = onClick)
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Guardar Disponibilidad",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 16.sp,
            )
        }
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
private fun DisponibilidadYHorariosScreenPreview() {
    KineCareTheme {
        DisponibilidadYHorariosScreen(state = DisponibilidadYHorariosState())
    }
}
