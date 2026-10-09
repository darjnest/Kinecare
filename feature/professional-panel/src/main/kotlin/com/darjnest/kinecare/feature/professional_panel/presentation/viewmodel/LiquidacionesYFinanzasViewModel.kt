@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darjnest.kinecare.core.common.data.repository.ReservaRepository
import com.darjnest.kinecare.core.common.data.repository.ServicioRepository
import com.darjnest.kinecare.core.common.domain.model.EstadoReserva
import com.darjnest.kinecare.core.common.domain.model.Reserva
import com.darjnest.kinecare.core.common.domain.model.TipoMetodoPago
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.core.common.util.ZonaHorariaChile
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.time.Clock
import com.darjnest.kinecare.core.common.domain.model.EstadoPago as EstadoPagoDominio

/**
 * Monto acumulado por liquidar y proximo deposito programado. Con Mercado
 * Pago Marketplace el pago llega directo a la cuenta del profesional (no hay
 * liquidacion de KineCare), asi que hoy el ViewModel no lo llena y la tarjeta
 * se oculta (docs/TASKS.md, Fase 7).
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

/**
 * Resumen de ingresos y atenciones del mes en curso. Los campos que no se
 * pueden calcular con los datos que hay (variacion sin mes anterior, boletas
 * sin integracion con el SII) son nulos y la vista oculta esa parte.
 */
data class ResumenMensual(
    val mes: String,
    val ingresosBrutos: Long,
    val variacionPorcentaje: Int?,
    val atenciones: Int,
    val promedioDiario: Double,
    val comisionPlataformaPorcentaje: Int,
    val boletasEmitidas: Int? = null,
)

/** Estado de un pago ya procesado, mostrado en el historial. */
enum class EstadoPago { EXITOSA, PENDIENTE, RECHAZADA, REEMBOLSADA }

data class PagoHistorico(
    val id: String,
    val titulo: String,
    val estado: EstadoPago,
    val fechaTexto: String,
    val medioTexto: String,
    /** Honorario neto del profesional: monto cobrado menos la comision de KineCare. */
    val monto: Long,
)

data class LiquidacionesYFinanzasState(
    val cargando: Boolean = false,
    /** La carga de reservas fallo: la vista ofrece reintentar en vez de mostrar ceros. */
    val errorCarga: Boolean = false,
    val siiConectado: Boolean = false,
    val resumenFinanciero: ResumenFinanciero? = null,
    val cuentaBancaria: CuentaBancaria? = null,
    val resumenMensual: ResumenMensual? = null,
    val historialPagos: List<PagoHistorico> = emptyList(),
    /** Aviso puntual para un Snackbar; la vista lo consume con [LiquidacionesYFinanzasAction.MensajeMostrado]. */
    val mensaje: String? = null,
)

sealed interface LiquidacionesYFinanzasAction {
    data object ModificarCuentaBancaria : LiquidacionesYFinanzasAction
    data class DescargarComprobante(val pagoId: String) : LiquidacionesYFinanzasAction
    data object DescargarCertificadoAnual : LiquidacionesYFinanzasAction
    data object Reintentar : LiquidacionesYFinanzasAction
    data object MensajeMostrado : LiquidacionesYFinanzasAction
}

/** Cuantos pagos lista el historial (los mas recientes). */
internal const val MAX_PAGOS_HISTORIAL = 20

private const val MENSAJE_PROXIMAMENTE = "Disponible próximamente."

private val MESES = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)

private val MESES_ABREVIADOS = listOf(
    "ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic",
)

/** Reservas cuya atencion cuenta como ingreso: cobradas, con la cita ya iniciada y no canceladas. */
internal fun Reserva.esIngreso(ahora: Instant): Boolean =
    pago.estado == EstadoPagoDominio.AUTORIZADO &&
        estado in ESTADOS_ATENDIDOS &&
        fechaHora <= ahora

private val ESTADOS_ATENDIDOS = setOf(EstadoReserva.CONFIRMADA, EstadoReserva.EN_CURSO, EstadoReserva.COMPLETADA)

private fun Instant.aFechaChile(): LocalDate = toLocalDateTime(ZonaHorariaChile).date

private fun LocalDate.mismoMes(otro: LocalDate) = year == otro.year && month == otro.month

private fun LocalDate.mesAnterior(): LocalDate =
    if (month.ordinal == 0) LocalDate(year - 1, 12, 1) else LocalDate(year, month.ordinal, 1)

/** "01 oct" en el año en curso, "01 oct 2025" en otro. */
private fun LocalDate.aTextoCorto(hoy: LocalDate): String {
    val base = "%02d %s".format(day, MESES_ABREVIADOS[month.ordinal])
    return if (year == hoy.year) base else "$base $year"
}

private fun Reserva.medioTexto(): String = when {
    pago.estado == EstadoPagoDominio.PENDIENTE -> "Por cobrar"
    pago.metodo.tipo == TipoMetodoPago.TRANSFERENCIA -> "Transferencia · Mercado Pago"
    pago.metodo.ultimosDigitos != null -> "Tarjeta •••• ${pago.metodo.ultimosDigitos} · Mercado Pago"
    else -> "Mercado Pago"
}

/** `null` si la reserva no tiene un pago que mostrar (p. ej. una solicitud aun sin responder). */
private fun Reserva.estadoPagoHistorico(): EstadoPago? = when (pago.estado) {
    EstadoPagoDominio.AUTORIZADO -> EstadoPago.EXITOSA
    EstadoPagoDominio.RECHAZADO -> EstadoPago.RECHAZADA
    EstadoPagoDominio.REEMBOLSADO -> EstadoPago.REEMBOLSADA
    EstadoPagoDominio.PENDIENTE -> if (estado in ESTADOS_ATENDIDOS) EstadoPago.PENDIENTE else null
}

internal fun Reserva.aPagoHistorico(hoy: LocalDate, servicios: Map<String, String>, estadoPago: EstadoPago) =
    PagoHistorico(
        id = id,
        titulo = servicios[servicioId] ?: "Sesión",
        estado = estadoPago,
        fechaTexto = fechaHora.aFechaChile().aTextoCorto(hoy),
        medioTexto = medioTexto(),
        monto = honorarioNeto(),
    )

internal fun resumenMensual(reservas: List<Reserva>, ahora: Instant): ResumenMensual {
    val hoy = ahora.aFechaChile()
    val anterior = hoy.mesAnterior()
    val delMes = reservas.filter { it.esIngreso(ahora) && it.fechaHora.aFechaChile().mismoMes(hoy) }
    val ingresos = delMes.sumOf { it.pago.monto }
    val ingresosMesAnterior = reservas
        .filter { it.esIngreso(ahora) && it.fechaHora.aFechaChile().mismoMes(anterior) }
        .sumOf { it.pago.monto }
    val comision = delMes.firstOrNull()?.comisionPorcentaje
        ?: reservas.firstOrNull()?.comisionPorcentaje
        ?: COMISION_POR_DEFECTO
    return ResumenMensual(
        mes = "${MESES[hoy.month.ordinal]} ${hoy.year}",
        ingresosBrutos = ingresos,
        variacionPorcentaje = ingresosMesAnterior.takeIf { it > 0 }
            ?.let { ((ingresos - it) * 100.0 / it).roundToInt() },
        atenciones = delMes.size,
        promedioDiario = ((delMes.size * 10.0 / hoy.day).roundToInt()) / 10.0,
        comisionPlataformaPorcentaje = (comision * 100).roundToInt(),
    )
}

/** Misma constante del servidor (`COMISION_PORCENTAJE`); solo se usa si el profesional aun no tiene reservas. */
private const val COMISION_POR_DEFECTO = 0.10

@HiltViewModel
class LiquidacionesYFinanzasViewModel @Inject constructor(
    private val reservaRepository: ReservaRepository,
    private val servicioRepository: ServicioRepository,
    private val firebaseAuth: FirebaseAuth,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(LiquidacionesYFinanzasState())
    val state: StateFlow<LiquidacionesYFinanzasState> = _state.asStateFlow()

    private var servicios: Map<String, String> = emptyMap()
    private var cargaJob: Job? = null

    init {
        cargar()
    }

    fun onAction(action: LiquidacionesYFinanzasAction) {
        when (action) {
            LiquidacionesYFinanzasAction.Reintentar -> cargar()
            LiquidacionesYFinanzasAction.MensajeMostrado -> _state.update { it.copy(mensaje = null) }

            // Modificar la cuenta bancaria exige verificacion biometrica adicional,
            // y los comprobantes/certificados son documentos tributarios generados
            // desde Cloud Functions (SII): ninguna se resuelve en el cliente todavia.
            LiquidacionesYFinanzasAction.ModificarCuentaBancaria,
            is LiquidacionesYFinanzasAction.DescargarComprobante,
            LiquidacionesYFinanzasAction.DescargarCertificadoAnual,
            -> _state.update { it.copy(mensaje = MENSAJE_PROXIMAMENTE) }
        }
    }

    private fun cargar() {
        if (cargaJob?.isActive == true) return
        val uid = firebaseAuth.currentUser?.uid ?: return
        cargaJob = viewModelScope.launch {
            _state.update { it.copy(cargando = true, errorCarga = false) }
            when (val resultado = reservaRepository.obtenerPorProfesional(uid)) {
                is Result.Success -> {
                    resolverServicios(uid)
                    _state.update { derivar(it, resultado.data).copy(cargando = false) }
                }
                is Result.Error -> _state.update { it.copy(cargando = false, errorCarga = true) }
            }
        }
    }

    /** Catalogo propio, incluidos los pausados. Si falla, los pagos quedan con un titulo generico. */
    private suspend fun resolverServicios(uid: String) {
        if (servicios.isNotEmpty()) return
        val resultado = servicioRepository.obtenerPorProfesional(uid)
        if (resultado is Result.Success) servicios = resultado.data.associate { it.id to it.nombre }
    }

    private fun derivar(estado: LiquidacionesYFinanzasState, reservas: List<Reserva>): LiquidacionesYFinanzasState {
        val ahora = clock.now()
        val hoy = ahora.aFechaChile()
        val historial = reservas
            .sortedByDescending { it.fechaHora }
            .mapNotNull { reserva -> reserva.estadoPagoHistorico()?.let { reserva.aPagoHistorico(hoy, servicios, it) } }
            .take(MAX_PAGOS_HISTORIAL)
        return estado.copy(
            resumenMensual = resumenMensual(reservas, ahora),
            historialPagos = historial,
        )
    }
}
