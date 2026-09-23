package com.darjnest.kinecare.feature.client_panel.presentation.view

import com.darjnest.kinecare.core.common.domain.model.ModalidadServicio
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darjnest.kinecare.core.designsystem.components.bar.KineCareBottomNavBar
import com.darjnest.kinecare.core.designsystem.components.bar.PestanaClienteInferior
import com.darjnest.kinecare.core.designsystem.theme.InicioDorado
import com.darjnest.kinecare.core.designsystem.theme.KineCareTheme
import com.darjnest.kinecare.core.designsystem.theme.LoginAzulSuave
import com.darjnest.kinecare.core.designsystem.theme.LoginFondo
import com.darjnest.kinecare.core.designsystem.theme.LoginGrisClaro
import com.darjnest.kinecare.core.designsystem.theme.LoginGrisTexto
import com.darjnest.kinecare.core.designsystem.theme.LoginMenta
import com.darjnest.kinecare.core.designsystem.theme.LoginMentaSuave
import com.darjnest.kinecare.core.designsystem.theme.LoginPrimario
import com.darjnest.kinecare.core.designsystem.theme.LoginPrimarioOscuro
import com.darjnest.kinecare.core.designsystem.theme.TextoPrincipal
import com.darjnest.kinecare.feature.client_panel.presentation.viewmodel.CitaCancelada
import com.darjnest.kinecare.feature.client_panel.presentation.viewmodel.CitaEnCurso
import com.darjnest.kinecare.feature.client_panel.presentation.viewmodel.CitaHistorial
import com.darjnest.kinecare.feature.client_panel.presentation.viewmodel.CitaProxima
import com.darjnest.kinecare.feature.client_panel.presentation.viewmodel.MisCitasAction
import com.darjnest.kinecare.feature.client_panel.presentation.viewmodel.MisCitasState
import com.darjnest.kinecare.feature.client_panel.presentation.viewmodel.MisCitasViewModel
import com.darjnest.kinecare.feature.client_panel.presentation.viewmodel.TabMisCitas

@Composable
fun MisCitasRoot(
    modifier: Modifier = Modifier,
    viewModel: MisCitasViewModel = hiltViewModel(),
    onIrAExplorar: () -> Unit = {},
    onIrAFavoritos: () -> Unit = {},
    onIrAMiPerfil: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MisCitasScreen(
        state = state,
        onAction = viewModel::onAction,
        onIrAExplorar = onIrAExplorar,
        onIrAFavoritos = onIrAFavoritos,
        onIrAMiPerfil = onIrAMiPerfil,
        modifier = modifier,
    )
}

@Composable
fun MisCitasScreen(
    state: MisCitasState,
    onAction: (MisCitasAction) -> Unit = {},
    onIrAExplorar: () -> Unit = {},
    onIrAFavoritos: () -> Unit = {},
    onIrAMiPerfil: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = LoginFondo,
        bottomBar = {
            KineCareBottomNavBar(
                pestanaActiva = PestanaClienteInferior.MIS_CITAS,
                onExplorar = onIrAExplorar,
                onMisCitas = {},
                onFavoritos = onIrAFavoritos,
                onMiPerfil = onIrAMiPerfil,
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
            EncabezadoMisCitas()

            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                FilaTabsMisCitas(
                    tabSeleccionado = state.tabSeleccionado,
                    totalProximas = state.proximasCitas.size + if (state.citaEnCurso != null) 1 else 0,
                    totalCanceladas = state.canceladas.size,
                    onAction = onAction,
                )

                when (state.tabSeleccionado) {
                    TabMisCitas.PROXIMAS -> ContenidoProximas(state = state, onAction = onAction, onIrAExplorar = onIrAExplorar)
                    TabMisCitas.HISTORIAL -> ContenidoHistorial(historial = state.historial, onIrAExplorar = onIrAExplorar)
                    TabMisCitas.CANCELADAS -> ContenidoCanceladas(canceladas = state.canceladas)
                }

                BannerSoporte(onAction = onAction)

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ContenidoProximas(
    state: MisCitasState,
    onAction: (MisCitasAction) -> Unit,
    onIrAExplorar: () -> Unit,
) {
    if (state.citaEnCurso == null && state.proximasCitas.isEmpty() && state.historial.isEmpty()) {
        EstadoVacioCitas(onIrAExplorar = onIrAExplorar)
        return
    }

    state.citaEnCurso?.let { cita ->
        TarjetaCitaEnCurso(cita = cita, onAction = onAction)
    }

    if (state.proximasCitas.isNotEmpty()) {
        SeccionProximosDias(citas = state.proximasCitas, onAction = onAction)
    }

    if (state.historial.isNotEmpty()) {
        SeccionHistorialReciente(historial = state.historial, onAction = onAction)
    }
}

@Composable
private fun ContenidoHistorial(historial: List<CitaHistorial>, onIrAExplorar: () -> Unit) {
    if (historial.isEmpty()) {
        EstadoVacioCitas(onIrAExplorar = onIrAExplorar)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        historial.forEach { item -> TarjetaCitaHistorial(cita = item, onAction = {}) }
    }
}

@Composable
private fun ContenidoCanceladas(canceladas: List<CitaCancelada>) {
    if (canceladas.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.EventBusy, contentDescription = null, tint = LoginGrisTexto, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "No tienes citas canceladas",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = TextoPrincipal,
            )
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        canceladas.forEach { cancelada -> TarjetaCitaCancelada(cita = cancelada) }
    }
}

@Composable
private fun EncabezadoMisCitas() {
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
                    text = "KINECARE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = LoginPrimario,
                )
                Text(
                    text = "Mis Citas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextoPrincipal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.NotificationsNone, contentDescription = "Notificaciones", tint = TextoPrincipal)
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
private fun FilaTabsMisCitas(
    tabSeleccionado: TabMisCitas,
    totalProximas: Int,
    totalCanceladas: Int,
    onAction: (MisCitasAction) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(LoginGrisClaro)
            .padding(4.dp),
    ) {
        TabPill(
            texto = "Próximas ($totalProximas)",
            seleccionado = tabSeleccionado == TabMisCitas.PROXIMAS,
            onClick = { onAction(MisCitasAction.SeleccionarTab(TabMisCitas.PROXIMAS)) },
            modifier = Modifier.weight(1f),
        )
        TabPill(
            texto = "Historial",
            seleccionado = tabSeleccionado == TabMisCitas.HISTORIAL,
            onClick = { onAction(MisCitasAction.SeleccionarTab(TabMisCitas.HISTORIAL)) },
            modifier = Modifier.weight(1f),
        )
        TabPill(
            texto = if (totalCanceladas > 0) "Canceladas ($totalCanceladas)" else "Canceladas",
            seleccionado = tabSeleccionado == TabMisCitas.CANCELADAS,
            onClick = { onAction(MisCitasAction.SeleccionarTab(TabMisCitas.CANCELADAS)) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TabPill(
    texto: String,
    seleccionado: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (seleccionado) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = texto,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (seleccionado) LoginPrimarioOscuro else LoginGrisTexto,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EstadoVacioCitas(onIrAExplorar: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = LoginGrisTexto, modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Aún no tienes citas agendadas",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = TextoPrincipal,
        )
        Text(
            text = "Explora profesionales verificados y reserva tu próxima sesión.",
            style = MaterialTheme.typography.labelSmall,
            color = LoginGrisTexto,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(LoginPrimarioOscuro)
                .clickable(onClick = onIrAExplorar)
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Text(text = "Ir a Explorar", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun TarjetaCitaEnCurso(
    cita: CitaEnCurso,
    onAction: (MisCitasAction) -> Unit,
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(LoginPrimario))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${cita.horaTexto} • ${cita.modalidadTexto}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = LoginPrimarioOscuro,
                    )
                }
                cita.profesionalEnCaminoMinutos?.let { minutos ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(LoginMentaSuave)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "En camino ($minutos min)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = LoginPrimarioOscuro,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(LoginMenta),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(28.dp))
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(LoginPrimarioOscuro),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Verified, contentDescription = "Verificado", tint = Color.White, modifier = Modifier.size(11.dp))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = cita.profesionalNombre,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextoPrincipal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = cita.especialidad,
                        style = MaterialTheme.typography.bodyMedium,
                        color = LoginGrisTexto,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (cita.numeroRegistroSis.isNotBlank()) {
                        Row(
                            modifier = Modifier.padding(top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Shield, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "SIS N° ${cita.numeroRegistroSis} Verificado",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = LoginGrisTexto,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(LoginGrisClaro)
                    .padding(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "TRATAMIENTO CLÍNICO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = LoginGrisTexto,
                    )
                    Text(
                        text = "Sesión ${cita.sesionActual} de ${cita.sesionesTotales}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = LoginPrimarioOscuro,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Text(
                    text = cita.tratamientoTitulo,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextoPrincipal,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = cita.tratamientoDescripcion,
                    style = MaterialTheme.typography.labelSmall,
                    color = LoginGrisTexto,
                    modifier = Modifier.padding(top = 2.dp),
                )
                val progreso = if (cita.sesionesTotales > 0) cita.sesionActual / cita.sesionesTotales.toFloat() else 0f
                LinearProgressIndicator(
                    progress = { progreso },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50)),
                    color = LoginPrimarioOscuro,
                    trackColor = LoginGrisClaro,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PinDrop, contentDescription = null, tint = LoginGrisTexto, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = cita.ubicacionTexto,
                    style = MaterialTheme.typography.labelSmall,
                    color = LoginGrisTexto,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(LoginPrimarioOscuro)
                    .clickable { onAction(MisCitasAction.SeguirEnVivo) }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Seguir en vivo / Chat", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(LoginGrisClaro)
                        .clickable { onAction(MisCitasAction.Reprogramar) }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = TextoPrincipal, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Reprogramar", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = TextoPrincipal)
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(LoginGrisClaro)
                        .clickable { onAction(MisCitasAction.VerPautaDigital) }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Description, contentDescription = null, tint = TextoPrincipal, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Pauta Digital", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = TextoPrincipal)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(LoginMentaSuave)
                    .padding(12.dp),
            ) {
                Icon(Icons.Filled.Security, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "KineCare Protect Activo",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = LoginPrimarioOscuro,
                    )
                    Text(
                        text = "Tu pago está protegido en custodia hasta completar la sesión. Recibirás la boleta electrónica automáticamente.",
                        style = MaterialTheme.typography.labelSmall,
                        color = LoginGrisTexto,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SeccionProximosDias(
    citas: List<CitaProxima>,
    onAction: (MisCitasAction) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Próximos días",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextoPrincipal,
            )
            Text(
                text = "${citas.size} confirmada${if (citas.size == 1) "" else "s"}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = LoginGrisTexto,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            citas.forEach { cita -> TarjetaCitaProxima(cita = cita, onAction = onAction) }
        }
    }
}

@Composable
private fun TarjetaCitaProxima(
    cita: CitaProxima,
    onAction: (MisCitasAction) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(LoginAzulSuave)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (cita.modalidad == ModalidadServicio.DOMICILIO) Icons.Filled.Home else Icons.Filled.Apartment,
                        contentDescription = null,
                        tint = LoginPrimarioOscuro,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (cita.modalidad == ModalidadServicio.DOMICILIO) "A Domicilio" else "En Consulta",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = LoginPrimarioOscuro,
                    )
                }
                Text(
                    text = formatearClp(cita.precioTotal),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = LoginPrimarioOscuro,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = cita.lugar,
                style = MaterialTheme.typography.labelSmall,
                color = LoginGrisTexto,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = cita.fechaHoraTexto,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = TextoPrincipal,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text = "${cita.profesionalNombre} • ${cita.tipoSesion}",
                style = MaterialTheme.typography.labelSmall,
                color = LoginGrisTexto,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(LoginGrisClaro)
                        .clickable { onAction(MisCitasAction.AgregarAGoogleCalendar(cita.id)) }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = TextoPrincipal, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Google Calendar", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextoPrincipal)
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(LoginGrisClaro)
                        .clickable { onAction(MisCitasAction.VerPreparacion(cita.id)) }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = TextoPrincipal, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Preparación", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextoPrincipal)
                }
            }
        }
    }
}

@Composable
private fun SeccionHistorialReciente(
    historial: List<CitaHistorial>,
    onAction: (MisCitasAction) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Historial Reciente & Reembolsos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextoPrincipal,
            )
            Text(
                text = "Ver todo",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = LoginPrimario,
                modifier = Modifier.clickable { onAction(MisCitasAction.SeleccionarTab(TabMisCitas.HISTORIAL)) },
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            historial.take(2).forEach { item -> TarjetaCitaHistorial(cita = item, onAction = onAction) }
        }
    }
}

@Composable
private fun TarjetaCitaHistorial(
    cita: CitaHistorial,
    onAction: (MisCitasAction) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "${cita.fechaTexto} • ${cita.modalidadTexto}",
                style = MaterialTheme.typography.labelSmall,
                color = LoginGrisTexto,
            )
            Text(
                text = cita.tituloSesion,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = TextoPrincipal,
                modifier = Modifier.padding(top = 2.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = cita.profesionalNombre,
                    style = MaterialTheme.typography.labelSmall,
                    color = LoginGrisTexto,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    repeat(cita.calificacion) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = InicioDorado, modifier = Modifier.size(12.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(LoginGrisClaro)
                    .clickable { onAction(MisCitasAction.DescargarBoleta(cita.id)) }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.ReceiptLong, contentDescription = null, tint = TextoPrincipal, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Descargar Boleta Electrónica SII (PDF)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextoPrincipal,
                )
            }
        }
    }
}

@Composable
private fun TarjetaCitaCancelada(cita: CitaCancelada) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = cita.fechaHoraTexto,
                style = MaterialTheme.typography.labelSmall,
                color = LoginGrisTexto,
            )
            Text(
                text = cita.profesionalNombre,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = TextoPrincipal,
                modifier = Modifier.padding(top = 2.dp),
            )
            cita.motivo?.let { motivo ->
                Text(
                    text = motivo,
                    style = MaterialTheme.typography.labelSmall,
                    color = LoginGrisTexto,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun BannerSoporte(onAction: (MisCitasAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(LoginGrisClaro)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.SupportAgent, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "¿Dudas con tu cobertura?",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = TextoPrincipal,
            )
            Text(
                text = "Nuestro equipo te ayuda con reembolsos Isapre y Fonasa.",
                style = MaterialTheme.typography.labelSmall,
                color = LoginGrisTexto,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(LoginPrimarioOscuro)
                .clickable { onAction(MisCitasAction.ChatearConSoporte) }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(text = "Chatear", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
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

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun MisCitasScreenPreview() {
    KineCareTheme {
        MisCitasScreen(state = MisCitasState())
    }
}
