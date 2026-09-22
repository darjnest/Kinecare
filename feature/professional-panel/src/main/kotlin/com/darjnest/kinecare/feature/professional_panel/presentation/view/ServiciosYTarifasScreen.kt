package com.darjnest.kinecare.feature.professional_panel.presentation.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darjnest.kinecare.core.common.domain.model.ModalidadServicio
import com.darjnest.kinecare.core.designsystem.theme.AzulPetroleo30
import com.darjnest.kinecare.core.designsystem.theme.KineCareTheme
import com.darjnest.kinecare.core.designsystem.theme.LoginAzulSuave
import com.darjnest.kinecare.core.designsystem.theme.LoginFondo
import com.darjnest.kinecare.core.designsystem.theme.LoginGrisClaro
import com.darjnest.kinecare.core.designsystem.theme.LoginGrisTexto
import com.darjnest.kinecare.core.designsystem.theme.LoginMenta
import com.darjnest.kinecare.core.designsystem.theme.LoginPrimario
import com.darjnest.kinecare.core.designsystem.theme.LoginPrimarioOscuro
import com.darjnest.kinecare.core.designsystem.theme.TextoPrincipal
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.FiltroModalidadServicio
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.ResumenCatalogoServicios
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.ServicioProfesional
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.ServiciosYTarifasAction
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.ServiciosYTarifasState
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.ServiciosYTarifasViewModel

@Composable
fun ServiciosYTarifasRoot(
    modifier: Modifier = Modifier,
    viewModel: ServiciosYTarifasViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ServiciosYTarifasScreen(state = state, onAction = viewModel::onAction, modifier = modifier)
}

@Composable
fun ServiciosYTarifasScreen(
    state: ServiciosYTarifasState,
    onAction: (ServiciosYTarifasAction) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val serviciosFiltrados = state.servicios.filter { servicio ->
        when (state.filtroSeleccionado) {
            FiltroModalidadServicio.TODOS -> true
            FiltroModalidadServicio.DOMICILIO -> ModalidadServicio.DOMICILIO in servicio.modalidades
            FiltroModalidadServicio.CONSULTA -> ModalidadServicio.CONSULTA in servicio.modalidades
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = LoginFondo,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LoginFondo)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            EncabezadoServiciosYTarifas(onAction = onAction)

            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TarjetaResumenPortal(region = state.region, resumen = state.resumenCatalogo)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(LoginPrimarioOscuro)
                        .clickable { onAction(ServiciosYTarifasAction.AgregarNuevoServicio) }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.AddCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "+ Agregar Nuevo Servicio", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White)
                }

                FilaFiltrosModalidad(
                    filtroSeleccionado = state.filtroSeleccionado,
                    totalServicios = state.servicios.size,
                    onAction = onAction,
                )

                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    serviciosFiltrados.forEach { servicio ->
                        TarjetaServicio(servicio = servicio, onAction = onAction)
                    }
                }

                TarjetaMaterialInsumos()

                TarjetaKineCareProtect()

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun EncabezadoServiciosYTarifas(onAction: (ServiciosYTarifasAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onAction(ServiciosYTarifasAction.VolverAtras) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = TextoPrincipal)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Servicios y Tarifas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextoPrincipal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
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
}

@Composable
private fun TarjetaResumenPortal(region: String, resumen: ResumenCatalogoServicios?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = LoginGrisClaro),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
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
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(LoginPrimarioOscuro),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "PORTAL PROFESIONAL",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = LoginPrimario,
                    )
                }
                if (region.isNotBlank()) {
                    Text(
                        text = region,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = LoginGrisTexto,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }

            resumen?.let { catalogo ->
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.LocalHospital, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Catálogo Activo", style = MaterialTheme.typography.labelSmall, color = LoginGrisTexto)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${catalogo.serviciosActivos} servicios",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextoPrincipal,
                            )
                        }
                    }
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Payments, contentDescription = null, tint = AzulPetroleo30, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Tarifa Promedio", style = MaterialTheme.typography.labelSmall, color = LoginGrisTexto)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = formatearClp(catalogo.tarifaPromedio),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextoPrincipal,
                            )
                            Text(text = "/ sesión", style = MaterialTheme.typography.labelSmall, color = LoginGrisTexto)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(LoginMenta)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.ReceiptLong, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Boletas exentas electrónicas automatizadas con el SII al confirmar cada atención.",
                    style = MaterialTheme.typography.labelSmall,
                    color = LoginPrimarioOscuro,
                )
            }
        }
    }
}

@Composable
private fun FilaFiltrosModalidad(
    filtroSeleccionado: FiltroModalidadServicio,
    totalServicios: Int,
    onAction: (ServiciosYTarifasAction) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChipFiltroModalidad(
            texto = "Todos ($totalServicios)",
            icono = Icons.Filled.Tune,
            seleccionado = filtroSeleccionado == FiltroModalidadServicio.TODOS,
            onClick = { onAction(ServiciosYTarifasAction.SeleccionarFiltro(FiltroModalidadServicio.TODOS)) },
        )
        ChipFiltroModalidad(
            texto = "A Domicilio",
            icono = Icons.Filled.Home,
            seleccionado = filtroSeleccionado == FiltroModalidadServicio.DOMICILIO,
            onClick = { onAction(ServiciosYTarifasAction.SeleccionarFiltro(FiltroModalidadServicio.DOMICILIO)) },
        )
        ChipFiltroModalidad(
            texto = "En Consulta",
            icono = Icons.Filled.Domain,
            seleccionado = filtroSeleccionado == FiltroModalidadServicio.CONSULTA,
            onClick = { onAction(ServiciosYTarifasAction.SeleccionarFiltro(FiltroModalidadServicio.CONSULTA)) },
        )
    }
}

@Composable
private fun ChipFiltroModalidad(
    texto: String,
    icono: ImageVector,
    seleccionado: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (seleccionado) AzulPetroleo30 else LoginGrisClaro)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icono,
            contentDescription = null,
            tint = if (seleccionado) Color.White else LoginGrisTexto,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = texto,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (seleccionado) Color.White else LoginGrisTexto,
        )
    }
}

private fun etiquetaModalidad(modalidades: List<ModalidadServicio>): Pair<String, ImageVector> = when {
    modalidades.size > 1 -> "Domicilio / Consulta" to Icons.Filled.SwapHoriz
    modalidades.contains(ModalidadServicio.DOMICILIO) -> "A Domicilio" to Icons.Filled.Home
    modalidades.contains(ModalidadServicio.CONSULTA) -> "En Consulta" to Icons.Filled.Domain
    else -> "Online" to Icons.Filled.SwapHoriz
}

@Composable
private fun TarjetaServicio(
    servicio: ServicioProfesional,
    onAction: (ServiciosYTarifasAction) -> Unit,
) {
    val (etiqueta, iconoModalidad) = etiquetaModalidad(servicio.modalidades)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (servicio.activo) Color.White else LoginGrisClaro),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(LoginAzulSuave)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(iconoModalidad, contentDescription = null, tint = AzulPetroleo30, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = etiqueta, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = AzulPetroleo30)
                        }
                        if (servicio.reembolsableIsapreFonasa) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(LoginMenta)
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Verified, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Reembolsable Isapre/Fonasa", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = LoginPrimarioOscuro)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = servicio.nombre,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextoPrincipal,
                    )
                }
                Switch(
                    checked = servicio.activo,
                    onCheckedChange = { onAction(ServiciosYTarifasAction.CambiarActivoServicio(servicio.id, it)) },
                    colors = SwitchDefaults.colors(checkedTrackColor = LoginPrimarioOscuro),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = servicio.descripcion,
                style = MaterialTheme.typography.bodyMedium,
                color = LoginGrisTexto,
            )

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
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = LoginGrisTexto, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "${servicio.duracionMinutos} min", style = MaterialTheme.typography.labelMedium, color = LoginGrisTexto)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "Por sesión", style = MaterialTheme.typography.labelSmall, color = LoginGrisTexto)
                    Text(
                        text = formatearClp(servicio.precio),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = LoginPrimario,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                servicio.notaInferior?.let { nota ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = nota, style = MaterialTheme.typography.labelSmall, color = LoginGrisTexto)
                    }
                } ?: Spacer(modifier = Modifier.width(1.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(LoginGrisClaro)
                        .clickable { onAction(ServiciosYTarifasAction.EditarServicio(servicio.id)) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, tint = TextoPrincipal, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Editar", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = TextoPrincipal)
                }
            }
        }
    }
}

@Composable
private fun TarjetaMaterialInsumos() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = LoginGrisClaro),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.LocalHospital, contentDescription = null, tint = LoginGrisTexto, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Material e Insumos Pro",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextoPrincipal,
                )
                Text(
                    text = "Recuerda llevar tu Camilla KineCare homologada para las sesiones a domicilio activas.",
                    style = MaterialTheme.typography.labelSmall,
                    color = LoginGrisTexto,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun TarjetaKineCareProtect() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = LoginGrisClaro),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(LoginMenta),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Security, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Custodia KineCare Protect",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextoPrincipal,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "100% Blindado",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = LoginPrimarioOscuro,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(LoginMenta)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Tu tiempo está completamente resguardado. Cada paciente ingresa un medio de pago con pre-autorización previa. La retención de garantía y cobro puntual de honorarios aplica estrictamente a todas las tarifas fijadas, incluso ante inasistencias o cancelaciones tardías (menos de 4 horas).",
                    style = MaterialTheme.typography.labelSmall,
                    color = LoginGrisTexto,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = LoginPrimario, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Garantía de liquidación bancaria semanal directa a tu cuenta",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = LoginPrimario,
                    )
                }
            }
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

@Preview(showBackground = true, heightDp = 1800)
@Composable
private fun ServiciosYTarifasScreenPreview() {
    KineCareTheme {
        ServiciosYTarifasScreen(state = ServiciosYTarifasState())
    }
}
