package com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Monto acumulado por liquidar y proximo deposito programado. El calculo y
 * la ejecucion real de la liquidacion siempre se resuelven en Cloud
 * Functions (nunca en el cliente); esta pantalla solo lee el resultado.
 */
data class ResumenFinanciero(
    val montoPorLiquidar: Long,
    val periodicidad: String,
    val proximoDepositoFecha: String,
    val proximoDepositoHora: String,
)

/** Cuenta bancaria registrada para recibir abonos de liquidaciones. */
data class CuentaBancaria(
    val banco: String,
    val tipoCuenta: String,
    val numeroCuentaTerminadoEn: String,
    val titular: String,
    val rut: String,
    val verificada: Boolean,
)

/** Resumen de ingresos y atenciones del mes en curso. */
data class ResumenMensual(
    val mes: String,
    val ingresosBrutos: Long,
    val variacionPorcentaje: Double,
    val atenciones: Int,
    val promedioDiario: Double,
    val comisionPlataformaPorcentaje: Int,
    val boletasEmitidas: Int,
)

/** Estado de un pago/liquidacion ya procesado, mostrado en el historial. */
enum class EstadoPago { EXITOSA, PENDIENTE, RECHAZADA }

data class PagoHistorico(
    val id: String,
    val titulo: String,
    val estado: EstadoPago,
    val fechaTexto: String,
    val medioTexto: String,
    val monto: Long,
)

data class LiquidacionesYFinanzasState(
    val cargando: Boolean = false,
    val siiConectado: Boolean = false,
    val resumenFinanciero: ResumenFinanciero? = null,
    val cuentaBancaria: CuentaBancaria? = null,
    val resumenMensual: ResumenMensual? = null,
    val historialPagos: List<PagoHistorico> = emptyList(),
)

sealed interface LiquidacionesYFinanzasAction {
    data object ModificarCuentaBancaria : LiquidacionesYFinanzasAction
    data class DescargarComprobante(val pagoId: String) : LiquidacionesYFinanzasAction
    data object DescargarCertificadoAnual : LiquidacionesYFinanzasAction
}

@HiltViewModel
class LiquidacionesYFinanzasViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(LiquidacionesYFinanzasState())
    val state: StateFlow<LiquidacionesYFinanzasState> = _state.asStateFlow()

    fun onAction(action: LiquidacionesYFinanzasAction) {
        when (action) {
            // Modificar la cuenta bancaria exige verificacion biometrica adicional,
            // y descargar comprobantes/certificados genera documentos tributarios
            // desde Cloud Functions (SII): ninguna de estas operaciones se resuelve
            // en el cliente todavia (docs/TASKS.md, Fase 7).
            LiquidacionesYFinanzasAction.ModificarCuentaBancaria,
            is LiquidacionesYFinanzasAction.DescargarComprobante,
            LiquidacionesYFinanzasAction.DescargarCertificadoAnual,
            -> Unit
        }
    }
}
