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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darjnest.kinecare.core.designsystem.theme.AzulPetroleo30
import com.darjnest.kinecare.core.designsystem.theme.KineCareTheme
import com.darjnest.kinecare.core.designsystem.theme.LiquidacionesCelesteRenta
import com.darjnest.kinecare.core.designsystem.theme.LiquidacionesCelesteRentaTexto
import com.darjnest.kinecare.core.designsystem.theme.LiquidacionesVerdeDegradadoFin
import com.darjnest.kinecare.core.designsystem.theme.LiquidacionesVerdeDegradadoInicio
import com.darjnest.kinecare.core.designsystem.theme.LoginAzulSuave
import com.darjnest.kinecare.core.designsystem.theme.LoginFondo
import com.darjnest.kinecare.core.designsystem.theme.LoginGrisClaro
import com.darjnest.kinecare.core.designsystem.theme.LoginGrisTexto
import com.darjnest.kinecare.core.designsystem.theme.LoginMenta
import com.darjnest.kinecare.core.designsystem.theme.LoginPrimario
import com.darjnest.kinecare.core.designsystem.theme.LoginPrimarioOscuro
import com.darjnest.kinecare.core.designsystem.theme.RojoError40
import com.darjnest.kinecare.core.designsystem.theme.TextoPrincipal
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.CuentaBancaria
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.EstadoPago
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.LiquidacionesYFinanzasAction
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.LiquidacionesYFinanzasState
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.LiquidacionesYFinanzasViewModel
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.PagoHistorico
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.ResumenFinanciero
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.ResumenMensual
import java.util.Locale

@Composable
fun LiquidacionesYFinanzasRoot(
    modifier: Modifier = Modifier,
    viewModel: LiquidacionesYFinanzasViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LiquidacionesYFinanzasScreen(state = state, onAction = viewModel::onAction, modifier = modifier)
}

@Composable
fun LiquidacionesYFinanzasScreen(
    state: LiquidacionesYFinanzasState,
    onAction: (LiquidacionesYFinanzasAction) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = LoginFondo,
        topBar = { EncabezadoFinanzas() },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LoginFondo)
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            FilaTituloYEstadoSii(siiConectado = state.siiConectado)

            state.resumenFinanciero?.let { resumenFinanciero ->
                Spacer(modifier = Modifier.height(16.dp))
                TarjetaMontoPorLiquidar(resumenFinanciero = resumenFinanciero)
            }

            state.cuentaBancaria?.let { cuentaBancaria ->
                Spacer(modifier = Modifier.height(16.dp))
                TarjetaCuentaBancaria(cuentaBancaria = cuentaBancaria, onClick = { onAction(LiquidacionesYFinanzasAction.ModificarCuentaBancaria) })
            }

            state.resumenMensual?.let { resumenMensual ->
                Spacer(modifier = Modifier.height(24.dp))
                SeccionResumenMensual(resumenMensual = resumenMensual)
            }

            if (state.historialPagos.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                SeccionHistorialPagos(pagos = state.historialPagos, onAction = onAction)
            }

            Spacer(modifier = Modifier.height(24.dp))
            TarjetaOperacionRenta(onClick = { onAction(LiquidacionesYFinanzasAction.DescargarCertificadoAnual) })

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EncabezadoFinanzas() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(LoginFondo)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = TextoPrincipal)
        }
        Text(
            text = "Liquidaciones y Finanzas",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextoPrincipal,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
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
private fun FilaTituloYEstadoSii(siiConectado: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "PANEL FINANCIERO PROFESIONAL",
                style = MaterialTheme.typography.labelSmall,
                color = AzulPetroleo30,
            )
            Text(
                text = "Finanzas & Liquidaciones",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextoPrincipal,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (siiConectado) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(LoginAzulSuave)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(LoginPrimario),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "SII Conectado", style = MaterialTheme.typography.labelSmall, color = AzulPetroleo30)
            }
        }
    }
}

@Composable
private fun TarjetaMontoPorLiquidar(resumenFinanciero: ResumenFinanciero) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(listOf(LiquidacionesVerdeDegradadoInicio, LiquidacionesVerdeDegradadoFin)),
                )
                .padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "MONTO ACUMULADO POR LIQUIDAR",
                    style = MaterialTheme.typography.labelMedium,
                    color = LoginMenta,
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = LoginMenta, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = resumenFinanciero.periodicidad, style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = "CLP", style = MaterialTheme.typography.labelLarge, color = LoginMenta, modifier = Modifier.padding(bottom = 6.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = formatearClp(resumenFinanciero.montoPorLiquidar, incluirPrefijo = false),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.EventAvailable, contentDescription = null, tint = LoginMenta, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Próximo depósito: ${resumenFinanciero.proximoDepositoFecha}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
            }
            Text(
                text = "Liquidación ${resumenFinanciero.periodicidad.lowercase(Locale.getDefault())} automática programada a las ${resumenFinanciero.proximoDepositoHora} hrs.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 2.dp),
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = LoginMenta, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "En custodia KineCare Protect • 100% garantizado",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun TarjetaCuentaBancaria(
    cuentaBancaria: CuentaBancaria,
    onClick: () -> Unit,
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
                    Icon(Icons.Filled.AccountBalance, contentDescription = null, tint = AzulPetroleo30, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Cuenta Bancaria para Abonos",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextoPrincipal,
                    )
                }
                if (cuentaBancaria.verificada) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(LoginMenta)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(11.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Verificada", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = LoginPrimarioOscuro)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(LoginGrisClaro)
                    .padding(14.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = cuentaBancaria.banco, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = TextoPrincipal)
                    Text(
                        text = "${cuentaBancaria.tipoCuenta} terminada en ${cuentaBancaria.numeroCuentaTerminadoEn}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LoginGrisTexto,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = "Titular: ${cuentaBancaria.titular}", style = MaterialTheme.typography.labelSmall, color = LoginGrisTexto)
                    Text(text = "RUT: ${cuentaBancaria.rut}", style = MaterialTheme.typography.labelSmall, color = LoginGrisTexto, modifier = Modifier.padding(top = 2.dp))
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.CreditCard, contentDescription = null, tint = AzulPetroleo30, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(LoginGrisClaro)
                    .clickable(onClick = onClick)
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null, tint = TextoPrincipal, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Modificar cuenta bancaria", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = TextoPrincipal)
            }
        }
    }
}

@Composable
private fun SeccionResumenMensual(resumenMensual: ResumenMensual) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Resumen de ${resumenMensual.mes}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextoPrincipal,
            )
            Text(text = "En curso", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = AzulPetroleo30)
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Card(
                modifier = Modifier.weight(1f),
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
                        Text(text = "INGRESOS BRUTOS", style = MaterialTheme.typography.labelSmall, color = AzulPetroleo30)
                        Icon(Icons.Filled.Payments, contentDescription = null, tint = AzulPetroleo30, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = formatearClp(resumenMensual.ingresosBrutos),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextoPrincipal,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val subeIngreso = resumenMensual.variacionPorcentaje >= 0
                        Icon(
                            imageVector = if (subeIngreso) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                            contentDescription = null,
                            tint = if (subeIngreso) LoginPrimario else RojoError40,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${if (subeIngreso) "+" else ""}${resumenMensual.variacionPorcentaje}% vs mes anterior",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (subeIngreso) LoginPrimario else RojoError40,
                        )
                    }
                }
            }
            Card(
                modifier = Modifier.weight(1f),
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
                        Text(text = "ATENCIONES", style = MaterialTheme.typography.labelSmall, color = AzulPetroleo30)
                        Icon(Icons.Filled.FitnessCenter, contentDescription = null, tint = AzulPetroleo30, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${resumenMensual.atenciones} sesiones",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextoPrincipal,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Promedio: ${resumenMensual.promedioDiario} al día",
                        style = MaterialTheme.typography.labelSmall,
                        color = LoginGrisTexto,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
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
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(LoginAzulSuave),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Hub, contentDescription = null, tint = AzulPetroleo30, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(text = "Comisión de plataforma KineCare", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = TextoPrincipal)
                            Text(text = "Infraestructura, pasarelas y soporte 24/7", style = MaterialTheme.typography.labelSmall, color = LoginGrisTexto)
                        }
                    }
                    Text(text = "${resumenMensual.comisionPlataformaPorcentaje}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AzulPetroleo30)
                }

                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(LoginGrisClaro),
                )

                Spacer(modifier = Modifier.height(10.dp))
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
                                .background(LoginMenta),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.ReceiptLong, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(text = "Boletas de honorarios automáticas", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = TextoPrincipal)
                            Text(text = "Sincronizado con Servicio de Impuestos Internos", style = MaterialTheme.typography.labelSmall, color = LoginGrisTexto)
                        }
                    }
                    Text(
                        text = "${resumenMensual.boletasEmitidas} emitidas",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextoPrincipal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(LoginGrisClaro)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SeccionHistorialPagos(
    pagos: List<PagoHistorico>,
    onAction: (LiquidacionesYFinanzasAction) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Historial de Pagos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextoPrincipal)
            Text(text = "${pagos.size} últimos ciclos", style = MaterialTheme.typography.labelSmall, color = AzulPetroleo30)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            pagos.forEach { pago ->
                TarjetaPagoHistorico(pago = pago, onClick = { onAction(LiquidacionesYFinanzasAction.DescargarComprobante(pago.id)) })
            }
        }
    }
}

@Composable
private fun TarjetaPagoHistorico(
    pago: PagoHistorico,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(LoginMenta),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = pago.titulo, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = TextoPrincipal)
                        Spacer(modifier = Modifier.width(8.dp))
                        if (pago.estado == EstadoPago.EXITOSA) {
                            Text(
                                text = "EXITOSA",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = LoginPrimarioOscuro,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(LoginMenta)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Text(
                        text = "${pago.fechaTexto} • ${pago.medioTexto}",
                        style = MaterialTheme.typography.labelSmall,
                        color = LoginGrisTexto,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = formatearClp(pago.monto), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = TextoPrincipal)
                Row(
                    modifier = Modifier.clickable(onClick = onClick),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, tint = AzulPetroleo30, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(text = "Comprobante", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = AzulPetroleo30)
                }
            }
        }
    }
}

@Composable
private fun TarjetaOperacionRenta(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = LiquidacionesCelesteRenta),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AzulPetroleo30),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Assignment, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = "Operación Renta 2025", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = LiquidacionesCelesteRentaTexto)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Obtén el consolidado fiscal timbrado de todas tus atenciones kine para tu declaración anual de impuestos (F22).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LiquidacionesCelesteRentaTexto,
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
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
                Icon(Icons.Filled.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Descargar Certificado Anual de Ingresos", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

/** Reutilizado en cada Screen que muestra montos CLP (no existe aun en :core:common). */
private fun formatearClp(monto: Long, incluirPrefijo: Boolean = true): String {
    val agrupado = monto.toString()
        .reversed()
        .chunked(3)
        .joinToString(".")
        .reversed()
    return if (incluirPrefijo) "CLP $$agrupado" else "$$agrupado"
}

@Preview(showBackground = true, heightDp = 1700)
@Composable
private fun LiquidacionesYFinanzasScreenPreview() {
    KineCareTheme {
        LiquidacionesYFinanzasScreen(state = LiquidacionesYFinanzasState())
    }
}
