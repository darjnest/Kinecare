package com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darjnest.kinecare.core.common.data.error.MercadoPagoError
import com.darjnest.kinecare.core.common.data.repository.MercadoPagoRepository
import com.darjnest.kinecare.core.common.domain.model.EstadoMercadoPago
import com.darjnest.kinecare.core.common.result.Result
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

/** Estado de la tarjeta "Cobros con Mercado Pago". */
sealed interface CobrosMercadoPago {
    data object Cargando : CobrosMercadoPago
    data object NoConectada : CobrosMercadoPago
    data object Conectada : CobrosMercadoPago

    /** No se pudo leer si la cuenta esta conectada; la tarjeta ofrece reintentar. */
    data class Error(val error: MercadoPagoError) : CobrosMercadoPago
}

data class LiquidacionesYFinanzasState(
    val cargando: Boolean = false,
    val siiConectado: Boolean = false,
    val resumenFinanciero: ResumenFinanciero? = null,
    val cuentaBancaria: CuentaBancaria? = null,
    val resumenMensual: ResumenMensual? = null,
    val historialPagos: List<PagoHistorico> = emptyList(),
    val cobrosMercadoPago: CobrosMercadoPago = CobrosMercadoPago.Cargando,
    /** Conectando o desconectando: la tarjeta deshabilita sus botones. */
    val operacionMercadoPagoEnCurso: Boolean = false,
    /** Pidio desconectar y falta que confirme en el dialogo. */
    val confirmandoDesconexionMercadoPago: Boolean = false,
    /**
     * URL de autorizacion de Mercado Pago lista para abrir en el navegador. El
     * `Root` la abre y despacha [LiquidacionesYFinanzasAction.UrlConexionMercadoPagoAbierta]
     * (o [LiquidacionesYFinanzasAction.UrlConexionMercadoPagoNoSePudoAbrir]) para limpiarla.
     */
    val urlConexionPendiente: String? = null,
    /** Fallo la ultima operacion de conectar/desconectar; el estado de la cuenta no cambio. */
    val errorOperacionMercadoPago: MercadoPagoError? = null,
    /** El dispositivo no pudo abrir la URL de autorizacion en el navegador. */
    val errorAbrirNavegadorMercadoPago: Boolean = false,
) {
    /** Hay una operacion de conexion en marcha (llamada a la funcion o navegador por abrir). */
    val mercadoPagoOcupado: Boolean
        get() = operacionMercadoPagoEnCurso || urlConexionPendiente != null
}

sealed interface LiquidacionesYFinanzasAction {
    data object ModificarCuentaBancaria : LiquidacionesYFinanzasAction
    data class DescargarComprobante(val pagoId: String) : LiquidacionesYFinanzasAction
    data object DescargarCertificadoAnual : LiquidacionesYFinanzasAction

    data object ConectarMercadoPago : LiquidacionesYFinanzasAction
    data object PedirDesconectarMercadoPago : LiquidacionesYFinanzasAction
    data object ConfirmarDesconectarMercadoPago : LiquidacionesYFinanzasAction
    data object CancelarDesconectarMercadoPago : LiquidacionesYFinanzasAction

    /** "Reintentar" tras un error de lectura: vuelve a leer mostrando el cargando. */
    data object ReintentarMercadoPago : LiquidacionesYFinanzasAction

    /** La pantalla volvio a primer plano (p. ej. tras autorizar en el navegador): releer el estado. */
    data object PantallaReanudada : LiquidacionesYFinanzasAction

    /** El `Root` ya abrio [LiquidacionesYFinanzasState.urlConexionPendiente] en el navegador. */
    data object UrlConexionMercadoPagoAbierta : LiquidacionesYFinanzasAction

    /** `openUri` lanzo (no hay navegador, etc.): limpia la URL y avisa el error. */
    data object UrlConexionMercadoPagoNoSePudoAbrir : LiquidacionesYFinanzasAction
}

@HiltViewModel
class LiquidacionesYFinanzasViewModel @Inject constructor(
    private val mercadoPagoRepository: MercadoPagoRepository,
    private val firebaseAuth: FirebaseAuth,
) : ViewModel() {

    private val _state = MutableStateFlow(LiquidacionesYFinanzasState())
    val state: StateFlow<LiquidacionesYFinanzasState> = _state.asStateFlow()

    private var cargaMercadoPagoJob: Job? = null

    /**
     * `init` ya lee el estado; el primer ON_RESUME de la pantalla (que llega
     * apenas se compone) no debe repetir esa lectura.
     */
    private var primerResumeOmitido = false

    init {
        cargarMercadoPago(mostrarCargando = true)
    }

    fun onAction(action: LiquidacionesYFinanzasAction) {
        when (action) {
            LiquidacionesYFinanzasAction.ConectarMercadoPago -> conectarMercadoPago()
            LiquidacionesYFinanzasAction.PedirDesconectarMercadoPago ->
                _state.update {
                    if (it.cobrosMercadoPago == CobrosMercadoPago.Conectada && !it.mercadoPagoOcupado) {
                        it.copy(confirmandoDesconexionMercadoPago = true, errorOperacionMercadoPago = null)
                    } else {
                        it
                    }
                }
            LiquidacionesYFinanzasAction.CancelarDesconectarMercadoPago ->
                _state.update { it.copy(confirmandoDesconexionMercadoPago = false) }
            LiquidacionesYFinanzasAction.ConfirmarDesconectarMercadoPago -> desconectarMercadoPago()
            LiquidacionesYFinanzasAction.ReintentarMercadoPago -> {
                if (!_state.value.mercadoPagoOcupado) cargarMercadoPago(mostrarCargando = true)
            }
            LiquidacionesYFinanzasAction.PantallaReanudada -> {
                if (!primerResumeOmitido) {
                    primerResumeOmitido = true
                } else if (!_state.value.mercadoPagoOcupado) {
                    cargarMercadoPago(mostrarCargando = false)
                }
            }
            LiquidacionesYFinanzasAction.UrlConexionMercadoPagoAbierta ->
                _state.update { it.copy(urlConexionPendiente = null) }
            LiquidacionesYFinanzasAction.UrlConexionMercadoPagoNoSePudoAbrir ->
                _state.update { it.copy(urlConexionPendiente = null, errorAbrirNavegadorMercadoPago = true) }
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

    /**
     * Lee `mercadoPagoEstados/{uid}`. Con [mostrarCargando] en `false` (releer al
     * volver a primer plano) la tarjeta conserva lo que mostraba hasta tener la
     * respuesta, para no parpadear. Una lectura nueva cancela la anterior.
     */
    private fun cargarMercadoPago(mostrarCargando: Boolean) {
        val uid = firebaseAuth.currentUser?.uid
        if (uid == null) {
            _state.update { it.copy(cobrosMercadoPago = CobrosMercadoPago.Error(MercadoPagoError.SIN_SESION)) }
            return
        }
        cargaMercadoPagoJob?.cancel()
        cargaMercadoPagoJob = viewModelScope.launch {
            if (mostrarCargando) {
                _state.update { it.copy(cobrosMercadoPago = CobrosMercadoPago.Cargando) }
            }
            val cobros = when (val resultado = mercadoPagoRepository.obtenerEstado(uid)) {
                is Result.Success -> resultado.data.aCobros()
                is Result.Error -> CobrosMercadoPago.Error(resultado.error)
            }
            _state.update { it.copy(cobrosMercadoPago = cobros) }
        }
    }

    private fun conectarMercadoPago() {
        if (!iniciarOperacionMercadoPago { it.cobrosMercadoPago == CobrosMercadoPago.NoConectada }) return
        viewModelScope.launch {
            when (val resultado = mercadoPagoRepository.iniciarConexion()) {
                is Result.Success -> _state.update {
                    it.copy(operacionMercadoPagoEnCurso = false, urlConexionPendiente = resultado.data)
                }
                is Result.Error -> _state.update {
                    it.copy(operacionMercadoPagoEnCurso = false, errorOperacionMercadoPago = resultado.error)
                }
            }
        }
    }

    private fun desconectarMercadoPago() {
        // El dialogo de confirmacion se cierra siempre; la operacion solo corre si corresponde.
        _state.update { it.copy(confirmandoDesconexionMercadoPago = false) }
        if (!iniciarOperacionMercadoPago { it.cobrosMercadoPago == CobrosMercadoPago.Conectada }) return
        viewModelScope.launch {
            when (val resultado = mercadoPagoRepository.desconectar()) {
                is Result.Success -> _state.update {
                    it.copy(operacionMercadoPagoEnCurso = false, cobrosMercadoPago = CobrosMercadoPago.NoConectada)
                }
                is Result.Error -> _state.update {
                    it.copy(operacionMercadoPagoEnCurso = false, errorOperacionMercadoPago = resultado.error)
                }
            }
        }
    }

    /**
     * Marca la operacion en curso si [permitida] y no hay otra en marcha
     * (evita dobles toques); cancela una lectura pendiente para que no pise
     * el resultado de la operacion. Retorna si pudo iniciarla.
     */
    private fun iniciarOperacionMercadoPago(permitida: (LiquidacionesYFinanzasState) -> Boolean): Boolean {
        val anterior = _state.getAndUpdate {
            if (it.mercadoPagoOcupado || !permitida(it)) {
                it
            } else {
                it.copy(
                    operacionMercadoPagoEnCurso = true,
                    errorOperacionMercadoPago = null,
                    errorAbrirNavegadorMercadoPago = false,
                )
            }
        }
        val iniciada = !anterior.mercadoPagoOcupado && permitida(anterior)
        if (iniciada) cargaMercadoPagoJob?.cancel()
        return iniciada
    }
}

private fun EstadoMercadoPago.aCobros(): CobrosMercadoPago =
    if (conectado) CobrosMercadoPago.Conectada else CobrosMercadoPago.NoConectada
