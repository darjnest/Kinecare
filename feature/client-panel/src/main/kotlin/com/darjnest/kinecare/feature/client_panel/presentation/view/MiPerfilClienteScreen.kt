package com.darjnest.kinecare.feature.client_panel.presentation.view

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
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darjnest.kinecare.core.designsystem.components.bar.KineCareBottomNavBar
import com.darjnest.kinecare.core.designsystem.components.bar.PestanaClienteInferior
import com.darjnest.kinecare.core.designsystem.theme.KineCareTheme
import com.darjnest.kinecare.core.designsystem.theme.LoginAzulSuave
import com.darjnest.kinecare.core.designsystem.theme.LoginFondo
import com.darjnest.kinecare.core.designsystem.theme.LoginGrisClaro
import com.darjnest.kinecare.core.designsystem.theme.LoginGrisTexto
import com.darjnest.kinecare.core.designsystem.theme.LoginMenta
import com.darjnest.kinecare.core.designsystem.theme.LoginMentaSuave
import com.darjnest.kinecare.core.designsystem.theme.LoginPrimario
import com.darjnest.kinecare.core.designsystem.theme.LoginPrimarioOscuro
import com.darjnest.kinecare.core.designsystem.theme.RojoError40
import com.darjnest.kinecare.core.designsystem.theme.RojoError90
import com.darjnest.kinecare.core.designsystem.theme.TextoPrincipal
import com.darjnest.kinecare.feature.client_panel.presentation.viewmodel.AjustesSeguridad
import com.darjnest.kinecare.feature.client_panel.presentation.viewmodel.DireccionCliente
import com.darjnest.kinecare.feature.client_panel.presentation.viewmodel.FacturacionCliente
import com.darjnest.kinecare.feature.client_panel.presentation.viewmodel.MetodoPagoGuardado
import com.darjnest.kinecare.feature.client_panel.presentation.viewmodel.MetricaPaciente
import com.darjnest.kinecare.feature.client_panel.presentation.viewmodel.MiPerfilClienteAction
import com.darjnest.kinecare.feature.client_panel.presentation.viewmodel.MiPerfilClienteState
import com.darjnest.kinecare.feature.client_panel.presentation.viewmodel.MiPerfilClienteViewModel
import com.darjnest.kinecare.feature.client_panel.presentation.viewmodel.PacienteResumen
import com.darjnest.kinecare.feature.client_panel.presentation.viewmodel.PrevisionSalud
import com.darjnest.kinecare.feature.client_panel.presentation.viewmodel.TratamientoClinico

private const val VERSION_APP = "KineCare Chile v1.2.0 • Build 842"

@Composable
fun MiPerfilClienteRoot(
    modifier: Modifier = Modifier,
    viewModel: MiPerfilClienteViewModel = hiltViewModel(),
    onIrAExplorar: () -> Unit = {},
    onIrAMisCitas: () -> Unit = {},
    onIrAFavoritos: () -> Unit = {},
    onCerrarSesion: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MiPerfilClienteScreen(
        state = state,
        onAction = viewModel::onAction,
        onIrAExplorar = onIrAExplorar,
        onIrAMisCitas = onIrAMisCitas,
        onIrAFavoritos = onIrAFavoritos,
        onCerrarSesion = onCerrarSesion,
        modifier = modifier,
    )
}

@Composable
fun MiPerfilClienteScreen(
    state: MiPerfilClienteState,
    onAction: (MiPerfilClienteAction) -> Unit = {},
    onIrAExplorar: () -> Unit = {},
    onIrAMisCitas: () -> Unit = {},
    onIrAFavoritos: () -> Unit = {},
    onCerrarSesion: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = LoginFondo,
        bottomBar = {
            KineCareBottomNavBar(
                pestanaActiva = PestanaClienteInferior.MI_PERFIL,
                onExplorar = onIrAExplorar,
                onMisCitas = onIrAMisCitas,
                onFavoritos = onIrAFavoritos,
                onMiPerfil = {},
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
            EncabezadoMiPerfilCliente()

            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TarjetaCabeceraPaciente(resumen = state.resumen, onAction = onAction)

                if (state.metricas.isNotEmpty()) {
                    FilaMetricasPaciente(metricas = state.metricas)
                }

                state.prevision?.let { prevision ->
                    TarjetaPrevisionYReembolsos(prevision = prevision, onAction = onAction)
                }

                state.tratamiento?.let { tratamiento ->
                    TarjetaFichaClinica(tratamiento = tratamiento, onAction = onAction)
                }

                if (state.direcciones.isNotEmpty()) {
                    TarjetaDirecciones(direcciones = state.direcciones, onAction = onAction)
                }

                if (state.metodoPago != null || state.facturacion != null) {
                    TarjetaPagosYFacturacion(
                        metodoPago = state.metodoPago,
                        facturacion = state.facturacion,
                        onAction = onAction,
                    )
                }

                TarjetaAjustesYSeguridad(
                    ajustes = state.ajustes,
                    onAction = onAction,
                    onCerrarSesion = onCerrarSesion,
                )

                Text(
                    text = VERSION_APP,
                    style = MaterialTheme.typography.labelSmall,
                    color = LoginGrisTexto,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun EncabezadoMiPerfilCliente() {
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
                    text = "Perfil",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextoPrincipal,
                )
            }
        }
        Icon(Icons.Filled.NotificationsNone, contentDescription = "Notificaciones", tint = TextoPrincipal)
    }
}

@Composable
private fun TarjetaCabeceraPaciente(
    resumen: PacienteResumen?,
    onAction: (MiPerfilClienteAction) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(LoginMenta),
                    contentAlignment = Alignment.Center,
                ) {
                    if (resumen != null && resumen.iniciales.isNotBlank()) {
                        Text(
                            text = resumen.iniciales,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = LoginPrimarioOscuro,
                        )
                    } else {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(40.dp))
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(LoginPrimarioOscuro)
                        .clickable { onAction(MiPerfilClienteAction.EditarInformacionPersonal) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = "Cambiar foto", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = resumen?.nombreCompleto ?: "Paciente KineCare",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextoPrincipal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (resumen != null) {
                Text(
                    text = "RUT ${resumen.rut} • ${resumen.ciudad}",
                    style = MaterialTheme.typography.labelMedium,
                    color = LoginGrisTexto,
                )
                if (resumen.verificadoClaveUnica) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(LoginMentaSuave)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Shield, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Verificada ClaveÚnica",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = LoginPrimarioOscuro,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(LoginGrisClaro)
                    .clickable { onAction(MiPerfilClienteAction.EditarInformacionPersonal) }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null, tint = TextoPrincipal, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Editar información personal", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = TextoPrincipal)
            }
        }
    }
}

@Composable
private fun FilaMetricasPaciente(metricas: List<MetricaPaciente>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        metricas.forEach { metrica ->
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(LoginMenta),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(metrica.icono, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(14.dp))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = metrica.valor, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextoPrincipal)
                    Text(
                        text = metrica.etiqueta,
                        style = MaterialTheme.typography.labelSmall,
                        color = LoginGrisTexto,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun TarjetaPrevisionYReembolsos(
    prevision: PrevisionSalud,
    onAction: (MiPerfilClienteAction) -> Unit,
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
                    Icon(Icons.Filled.HealthAndSafety, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Previsión y Reembolsos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextoPrincipal,
                    )
                }
                prevision.integracionConectadaTexto?.let { integracion ->
                    Text(
                        text = integracion,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = LoginPrimarioOscuro,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(LoginMenta)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(LoginGrisClaro)
                    .padding(12.dp),
            ) {
                Text(text = "PREVISIÓN ACTIVA", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = LoginGrisTexto)
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = prevision.nombrePrevision,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextoPrincipal,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (prevision.sincronizada) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Filled.CheckCircle, contentDescription = "Sincronizado", tint = LoginPrimarioOscuro, modifier = Modifier.size(14.dp))
                    }
                }
                Text(
                    text = "Emisión automática de boleta electrónica al SII",
                    style = MaterialTheme.typography.labelSmall,
                    color = LoginGrisTexto,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            if (prevision.nombreSeguroComplementario != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(LoginAzulSuave)
                        .padding(12.dp),
                ) {
                    Text(text = "SEGURO COMPLEMENTARIO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = LoginGrisTexto)
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = prevision.nombreSeguroComplementario,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextoPrincipal,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Filled.Verified, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(14.dp))
                    }
                    prevision.numeroPoliza?.let { poliza ->
                        Text(
                            text = "Póliza N° $poliza",
                            style = MaterialTheme.typography.labelSmall,
                            color = LoginGrisTexto,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(LoginPrimarioOscuro)
                    .clickable { onAction(MiPerfilClienteAction.GestionarPrevisionYBoletas) }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Autorenew, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Gestionar previsión y boletas SII", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun TarjetaFichaClinica(
    tratamiento: TratamientoClinico,
    onAction: (MiPerfilClienteAction) -> Unit,
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
                Text(
                    text = "Ficha Clínica y Tratamiento",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextoPrincipal,
                )
                Text(
                    text = "Ley 20.584",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = LoginGrisTexto,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(LoginGrisClaro)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
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
                    if (tratamiento.ordenMedicaVigente) {
                        Text(
                            text = "Orden Médica Vigente",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = LoginPrimarioOscuro,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(LoginMenta)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    tratamiento.diasParaVencer?.let { dias ->
                        Text(
                            text = "Vence en $dias días",
                            style = MaterialTheme.typography.labelSmall,
                            color = LoginGrisTexto,
                        )
                    }
                }
                Text(
                    text = tratamiento.titulo,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextoPrincipal,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    text = "${tratamiento.doctorDerivante} • ${tratamiento.institucion}",
                    style = MaterialTheme.typography.labelSmall,
                    color = LoginGrisTexto,
                    modifier = Modifier.padding(top = 2.dp),
                )
                val total = tratamiento.sesionesTotales
                val progreso = if (total > 0) tratamiento.sesionesCompletadas / total.toFloat() else 0f
                LinearProgressIndicator(
                    progress = { progreso },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50)),
                    color = LoginPrimarioOscuro,
                    trackColor = LoginMentaSuave,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = "${tratamiento.sesionesCompletadas} completadas", style = MaterialTheme.typography.labelSmall, color = LoginGrisTexto)
                    Text(
                        text = "${(tratamiento.sesionesTotales - tratamiento.sesionesCompletadas).coerceAtLeast(0)} restantes",
                        style = MaterialTheme.typography.labelSmall,
                        color = LoginGrisTexto,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            FilaNavegable(
                texto = "Pauta Activa en Casa",
                subtitulo = "Ejercicios y cuidados entre sesiones",
                icono = Icons.Filled.FitnessCenter,
                onClick = { onAction(MiPerfilClienteAction.VerPautaActivaEnCasa) },
            )
            Spacer(modifier = Modifier.height(8.dp))
            FilaNavegable(
                texto = "Historial de Evoluciones",
                subtitulo = "Notas clínicas de cada sesión",
                icono = Icons.Filled.Edit,
                onClick = { onAction(MiPerfilClienteAction.VerHistorialDeEvoluciones) },
            )
        }
    }
}

@Composable
private fun FilaNavegable(
    texto: String,
    subtitulo: String,
    icono: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(LoginGrisClaro)
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(icono, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = texto, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = TextoPrincipal)
                Text(text = subtitulo, style = MaterialTheme.typography.labelSmall, color = LoginGrisTexto)
            }
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = LoginGrisTexto, modifier = Modifier.size(12.dp))
    }
}

@Composable
private fun TarjetaDirecciones(
    direcciones: List<DireccionCliente>,
    onAction: (MiPerfilClienteAction) -> Unit,
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
                Text(
                    text = "Direcciones a Domicilio",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextoPrincipal,
                )
                Text(
                    text = "${direcciones.size} registradas",
                    style = MaterialTheme.typography.labelSmall,
                    color = LoginGrisTexto,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                direcciones.forEach { direccion -> FilaDireccion(direccion = direccion) }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(LoginGrisClaro)
                    .clickable { onAction(MiPerfilClienteAction.AgregarNuevaDireccion) }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.AddLocationAlt, contentDescription = null, tint = TextoPrincipal, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Agregar nueva dirección", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = TextoPrincipal)
            }
        }
    }
}

@Composable
private fun FilaDireccion(direccion: DireccionCliente) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(LoginGrisClaro)
            .padding(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (direccion.esOficina) Icons.Filled.Business else Icons.Filled.Home,
                contentDescription = null,
                tint = LoginPrimarioOscuro,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = direccion.etiqueta,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextoPrincipal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (direccion.predeterminada) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Predeterminada",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = LoginPrimarioOscuro,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(LoginMenta)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Text(text = direccion.calle, style = MaterialTheme.typography.labelSmall, color = LoginGrisTexto)
            Text(text = direccion.comunaRegion, style = MaterialTheme.typography.labelSmall, color = LoginGrisTexto)
        }
    }
}

@Composable
private fun TarjetaPagosYFacturacion(
    metodoPago: MetodoPagoGuardado?,
    facturacion: FacturacionCliente?,
    onAction: (MiPerfilClienteAction) -> Unit,
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
                Text(
                    text = "Pagos y Facturación",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextoPrincipal,
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(LoginGrisClaro)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = LoginGrisTexto, modifier = Modifier.size(11.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Transbank Seguro", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = LoginGrisTexto)
                }
            }

            metodoPago?.let { pago ->
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(LoginGrisClaro)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.CreditCard, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "•••• ${pago.ultimosDigitos}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TextoPrincipal)
                        Text(text = pago.descripcion, style = MaterialTheme.typography.labelSmall, color = LoginGrisTexto)
                    }
                    if (pago.verificado) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = "Verificado", tint = LoginPrimarioOscuro, modifier = Modifier.size(18.dp))
                    }
                }
            }

            facturacion?.let { datos ->
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "RUT facturación: ${datos.rut}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextoPrincipal)
                        Text(text = datos.email, style = MaterialTheme.typography.labelSmall, color = LoginGrisTexto)
                    }
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Editar facturación",
                        tint = LoginGrisTexto,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onAction(MiPerfilClienteAction.EditarFacturacion) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TarjetaAjustesYSeguridad(
    ajustes: AjustesSeguridad,
    onAction: (MiPerfilClienteAction) -> Unit,
    onCerrarSesion: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Ajustes y Seguridad",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextoPrincipal,
            )

            Spacer(modifier = Modifier.height(10.dp))
            FilaSwitch(
                texto = "Avisos por WhatsApp",
                subtitulo = "Recordatorios de tus próximas citas",
                checked = ajustes.avisosWhatsApp,
                onCheckedChange = { onAction(MiPerfilClienteAction.CambiarAvisosWhatsApp(it)) },
            )
            Spacer(modifier = Modifier.height(10.dp))
            FilaSwitch(
                texto = "Ingreso Biométrico",
                subtitulo = "Touch ID / Face ID",
                checked = ajustes.ingresoBiometrico,
                onCheckedChange = { onAction(MiPerfilClienteAction.CambiarIngresoBiometrico(it)) },
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onAction(MiPerfilClienteAction.VerDerechosDelPaciente) }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Derechos del Paciente (Ley N° 20.584)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextoPrincipal,
                )
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = LoginGrisTexto, modifier = Modifier.size(16.dp))
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(RojoError90)
                    .clickable(onClick = onCerrarSesion)
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = RojoError40, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Cerrar Sesión", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = RojoError40)
            }
        }
    }
}

@Composable
private fun FilaSwitch(
    texto: String,
    subtitulo: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = texto, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = TextoPrincipal)
            Text(text = subtitulo, style = MaterialTheme.typography.labelSmall, color = LoginGrisTexto)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = LoginPrimarioOscuro),
        )
    }
}

@Preview(showBackground = true, heightDp = 2200)
@Composable
private fun MiPerfilClienteScreenPreview() {
    KineCareTheme {
        MiPerfilClienteScreen(state = MiPerfilClienteState())
    }
}
