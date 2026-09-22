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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import com.darjnest.kinecare.core.designsystem.theme.TextoPrincipal
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.Especialidad
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.IdentidadProfesional
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.MetricasReputacion
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.MiPerfilProfesionalAction
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.MiPerfilProfesionalState
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.MiPerfilProfesionalViewModel
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.ResenaDestacada
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.ZonaAtencion

@Composable
fun MiPerfilProfesionalRoot(
    modifier: Modifier = Modifier,
    viewModel: MiPerfilProfesionalViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MiPerfilProfesionalScreen(state = state, onAction = viewModel::onAction, modifier = modifier)
}

@Composable
fun MiPerfilProfesionalScreen(
    state: MiPerfilProfesionalState,
    onAction: (MiPerfilProfesionalAction) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = LoginFondo,
        bottomBar = { BarraNavegacionInferiorPerfil() },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LoginFondo)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            EncabezadoMiPerfil()

            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                BannerPerfilPublico(activo = state.perfilPublicoActivo, onAction = onAction)

                state.identidad?.let { identidad ->
                    TarjetaIdentidadProfesional(identidad = identidad)
                }

                state.metricas?.let { metricas ->
                    FilaMetricasReputacion(metricas = metricas)
                }

                if (state.biografia.isNotBlank()) {
                    TarjetaBiografia(
                        biografia = state.biografia,
                        compromisoKineCare = state.compromisoKineCare,
                        onAction = onAction,
                    )
                }

                if (state.especialidades.isNotEmpty()) {
                    TarjetaEspecialidades(
                        especialidades = state.especialidades,
                        certificacionAdicional = state.certificacionAdicional,
                    )
                }

                if (state.zonasAtencion.isNotEmpty()) {
                    TarjetaZonasAtencion(
                        zonas = state.zonasAtencion,
                        direccionMapaTexto = state.direccionMapaTexto,
                    )
                }

                state.resenaDestacada?.let { resena ->
                    TarjetaUltimasEvaluaciones(
                        resena = resena,
                        totalResenas = state.metricas?.totalResenas ?: 0,
                        onAction = onAction,
                    )
                }

                BotonesAccionPerfil(onAction = onAction)

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun EncabezadoMiPerfil() {
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
                    text = "KineCare Pro",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = LoginPrimario,
                )
                Text(
                    text = "Mi Perfil Profesional",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextoPrincipal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
private fun BannerPerfilPublico(
    activo: Boolean,
    onAction: (MiPerfilProfesionalAction) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = LoginAzulSuave),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Visibility, contentDescription = null, tint = AzulPetroleo30, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Perfil público activo",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = AzulPetroleo30,
                    )
                    Text(
                        text = "Visible en búsquedas de pacientes",
                        style = MaterialTheme.typography.labelSmall,
                        color = AzulPetroleo30,
                    )
                }
            }
            Switch(
                checked = activo,
                onCheckedChange = { onAction(MiPerfilProfesionalAction.CambiarPerfilPublico(it)) },
                colors = SwitchDefaults.colors(checkedTrackColor = LoginPrimarioOscuro),
            )
        }
    }
}

@Composable
private fun TarjetaIdentidadProfesional(identidad: IdentidadProfesional) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(LoginGrisClaro),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = LoginGrisTexto, modifier = Modifier.size(36.dp))
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(LoginPrimarioOscuro),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Verified, contentDescription = "Registro SIS verificado", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = identidad.nombre,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextoPrincipal,
                    )
                    Text(
                        text = identidad.especialidadPrincipal,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = LoginPrimario,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.School, contentDescription = null, tint = AzulPetroleo30, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${identidad.universidad} (${identidad.anioTitulacion})",
                            style = MaterialTheme.typography.labelSmall,
                            color = LoginGrisTexto,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(
                        modifier = Modifier.padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Reg. SIS Nº ${identidad.numeroRegistroSis} • ${identidad.entidadRegistro}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = LoginGrisTexto,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
                    Icon(Icons.Filled.HealthAndSafety, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (identidad.habilitadoIsapreFonasa) "Habilitada para atención Isapre & Fonasa" else "Atención particular",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextoPrincipal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (identidad.credencialesAlDia) {
                    Text(
                        text = "AL DÍA",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = LoginPrimarioOscuro,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(LoginMenta)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FilaMetricasReputacion(metricas: MetricasReputacion) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MetricaTarjeta(
            modifier = Modifier.weight(1f),
            contenidoSuperior = {
                Icon(Icons.Filled.Star, contentDescription = null, tint = InicioDorado, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text(text = "${metricas.calificacion}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextoPrincipal)
            },
            etiqueta = "${metricas.totalResenas} opiniones",
            nota = "Excelente",
            colorNota = LoginPrimario,
        )
        MetricaTarjeta(
            modifier = Modifier.weight(1f),
            contenidoSuperior = {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(LoginMenta),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.HowToReg, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(14.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "${metricas.atencionesCompletadas}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextoPrincipal)
            },
            etiqueta = "Atenciones",
            nota = "Completadas",
            colorNota = LoginGrisTexto,
        )
        MetricaTarjeta(
            modifier = Modifier.weight(1f),
            contenidoSuperior = {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(LoginAzulSuave),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = AzulPetroleo30, modifier = Modifier.size(14.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "${metricas.porcentajePuntualidad}%", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextoPrincipal)
            },
            etiqueta = "Puntualidad",
            nota = "Destacada",
            colorNota = LoginPrimarioOscuro,
        )
    }
}

@Composable
private fun MetricaTarjeta(
    contenidoSuperior: @Composable () -> Unit,
    etiqueta: String,
    nota: String,
    colorNota: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) { contenidoSuperior() }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = etiqueta,
                style = MaterialTheme.typography.labelSmall,
                color = LoginGrisTexto,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = nota,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = colorNota,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TarjetaBiografia(
    biografia: String,
    compromisoKineCare: String?,
    onAction: (MiPerfilProfesionalAction) -> Unit,
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
                    Icon(Icons.Filled.Psychology, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Biografía y Enfoque Clínico",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextoPrincipal,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(LoginGrisClaro)
                        .clickable { onAction(MiPerfilProfesionalAction.EditarBiografia) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Editar biografía", tint = LoginGrisTexto, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = biografia,
                style = MaterialTheme.typography.bodyMedium,
                color = LoginGrisTexto,
            )
            compromisoKineCare?.let { compromiso ->
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(LoginGrisClaro)
                        .padding(12.dp),
                ) {
                    Icon(Icons.Filled.Spa, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = buildString {
                            append("Compromiso KineCare: ")
                            append(compromiso)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = LoginGrisTexto,
                    )
                }
            }
        }
    }
}

@Composable
private fun TarjetaEspecialidades(
    especialidades: List<Especialidad>,
    certificacionAdicional: String?,
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
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Especialidades & Técnicas",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextoPrincipal,
                    )
                }
                Text(
                    text = "${especialidades.size} Activas",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = LoginPrimarioOscuro,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(LoginMenta)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                especialidades.forEach { especialidad ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(LoginGrisClaro)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(especialidad.icono, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = especialidad.nombre, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = TextoPrincipal)
                    }
                }
            }
            certificacionAdicional?.let { certificacion ->
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = certificacion, style = MaterialTheme.typography.labelSmall, color = LoginGrisTexto)
                }
            }
        }
    }
}

@Composable
private fun TarjetaZonasAtencion(
    zonas: List<ZonaAtencion>,
    direccionMapaTexto: String?,
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
                    Icon(Icons.Filled.HomeWork, contentDescription = null, tint = AzulPetroleo30, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Zonas y Modalidad de Atención",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextoPrincipal,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                zonas.forEach { zona ->
                    FilaZonaAtencion(zona = zona)
                }
            }
            direccionMapaTexto?.let { direccion ->
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(LoginGrisClaro),
                ) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.PinDrop, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = direccion,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextoPrincipal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilaZonaAtencion(zona: ZonaAtencion) {
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
            Icon(zona.icono, contentDescription = null, tint = AzulPetroleo30, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = zona.titulo,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextoPrincipal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = formatearClp(zona.precio),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = LoginPrimario,
                )
            }
            Text(
                text = zona.subtitulo,
                style = MaterialTheme.typography.labelSmall,
                color = LoginGrisTexto,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            zona.notaAdicional?.let { nota ->
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = nota, style = MaterialTheme.typography.labelSmall, color = LoginGrisTexto)
                }
            }
        }
    }
}

@Composable
private fun TarjetaUltimasEvaluaciones(
    resena: ResenaDestacada,
    totalResenas: Int,
    onAction: (MiPerfilProfesionalAction) -> Unit,
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
                    Icon(Icons.Filled.RateReview, contentDescription = null, tint = InicioDorado, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Últimas Evaluaciones",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextoPrincipal,
                    )
                }
                Text(
                    text = "Ver todas ($totalResenas)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = LoginPrimario,
                    modifier = Modifier.clickable { onAction(MiPerfilProfesionalAction.VerTodasLasResenas) },
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
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
                        text = resena.autor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextoPrincipal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Row {
                        repeat(resena.calificacion) {
                            Icon(Icons.Filled.Star, contentDescription = null, tint = InicioDorado, modifier = Modifier.size(12.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "“${resena.comentario}”",
                    style = MaterialTheme.typography.labelSmall,
                    fontStyle = FontStyle.Italic,
                    color = LoginGrisTexto,
                )
            }
        }
    }
}

@Composable
private fun BotonesAccionPerfil(onAction: (MiPerfilProfesionalAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(LoginPrimarioOscuro)
                .clickable { onAction(MiPerfilProfesionalAction.EditarPerfilYCredenciales) }
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Badge, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Editar Perfil y Credenciales", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(LoginGrisClaro)
                .clickable { onAction(MiPerfilProfesionalAction.PrevisualizarPerfilPublico) }
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.RemoveRedEye, contentDescription = null, tint = AzulPetroleo30, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Previsualizar Perfil Público", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = AzulPetroleo30)
        }
    }
}

@Composable
private fun BarraNavegacionInferiorPerfil() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ItemNavegacionInferiorPerfil(texto = "Inicio", icono = Icons.Filled.Dashboard, activo = false)
        // Solicitudes, Agenda y Pacientes se conectan cuando existan sus
        // subpantallas (Fase 7).
        ItemNavegacionInferiorPerfil(texto = "Solicitudes", icono = Icons.Filled.NotificationsActive, activo = false)
        ItemNavegacionInferiorPerfil(texto = "Agenda", icono = Icons.Filled.CalendarMonth, activo = false)
        ItemNavegacionInferiorPerfil(texto = "Pacientes", icono = Icons.Filled.HowToReg, activo = false)
        ItemNavegacionInferiorPerfil(texto = "Perfil", icono = Icons.Filled.Badge, activo = true)
    }
}

@Composable
private fun ItemNavegacionInferiorPerfil(
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

@Preview(showBackground = true, heightDp = 1900)
@Composable
private fun MiPerfilProfesionalScreenPreview() {
    KineCareTheme {
        MiPerfilProfesionalScreen(state = MiPerfilProfesionalState())
    }
}
