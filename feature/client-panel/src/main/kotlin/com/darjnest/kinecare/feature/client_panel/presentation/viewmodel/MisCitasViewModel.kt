@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.client_panel.presentation.viewmodel

import com.darjnest.kinecare.core.common.data.error.CancelarReservaError
import com.darjnest.kinecare.core.common.data.repository.ProfesionalRepository
import com.darjnest.kinecare.core.common.data.repository.ResenaRepository
import com.darjnest.kinecare.core.common.data.repository.ReservaRepository
import com.darjnest.kinecare.core.common.domain.model.EstadoPago
import com.darjnest.kinecare.core.common.domain.model.EstadoReserva
import com.darjnest.kinecare.core.common.domain.model.EstadoVerificacion
import com.darjnest.kinecare.core.common.domain.model.ModalidadServicio
import com.darjnest.kinecare.core.common.domain.model.ReembolsoCancelacion
import com.darjnest.kinecare.core.common.domain.model.Profesional
import com.darjnest.kinecare.core.common.domain.model.Resena
import com.darjnest.kinecare.core.common.domain.model.Reserva
import com.darjnest.kinecare.core.common.domain.model.TipoInsignia
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.feature.client_panel.data.repository.ReporteProblemaRepository
import com.darjnest.kinecare.feature.client_panel.domain.EstadoReporte
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

/** Pestana segmentada seleccionada en "Mis Citas". */
enum class TabMisCitas { PROXIMAS, HISTORIAL, CANCELADAS }

/**
 * Cita en curso u hoy, mostrada en la tarjeta hero. Modelo de presentacion
 * reducido (no reemplaza `Reserva` de dominio; se mapea desde la `Reserva`
 * real en estado `EN_CURSO`).
 */
data class CitaEnCurso(
    val id: String,
    val profesionalId: String,
    val horaTexto: String,
    val modalidadTexto: String,
    val profesionalEnCaminoMinutos: Int? = null,
    val profesionalNombre: String,
    val especialidad: String,
    val numeroRegistroSis: String,
    val tratamientoTitulo: String,
    val tratamientoDescripcion: String,
    val sesionActual: Int,
    val sesionesTotales: Int,
    val ubicacionTexto: String,
    /** Estado del reporte de problema de esta reserva; `null` si no tiene (ver [CitaProxima.estadoReporte]). */
    val estadoReporte: EstadoReporte? = null,
    /** Ver [CitaProxima.estadoPago]: la cita en curso conserva el indicador "Pagada"/"Reembolsada". */
    val estadoPago: EstadoPago? = null,
    /** `true` si el profesional completo la verificacion general (`estadoVerificacionGeneral == APROBADO`): habilita el icono "Verificado" del avatar. */
    val verificado: Boolean = false,
    /** `true` solo si la insignia `CREDENCIALES` esta aprobada: sin ella el RNPI se muestra sin la palabra "Verificado". */
    val rnpiVerificado: Boolean = false,
)

/** Cita agendada en dias proximos, distinta de la cita de hoy. */
data class CitaProxima(
    val id: String,
    val profesionalId: String,
    val modalidad: ModalidadServicio,
    val lugar: String,
    val fechaHoraTexto: String,
    val profesionalNombre: String,
    val tipoSesion: String,
    val precioTotal: Long,
    /** `true` mientras el profesional no responde la solicitud (`SOLICITADA`); `false` si ya la confirmo. */
    val porConfirmar: Boolean = false,
    /**
     * Estado del pago de la reserva; `null` mientras sigue `SOLICITADA`: aun
     * no corresponde pagar. Desde que el profesional confirma se informa en
     * todos los estados siguientes (`EN_CURSO`, `COMPLETADA`, canceladas), para
     * que "Pagada"/"Reembolsada" no desaparezca de la pantalla.
     */
    val estadoPago: EstadoPago? = null,
    /**
     * `true` si la hora de la cita ya paso (`fechaHora` <= ahora al cargar):
     * ya no se puede pagar, el backend responde `RESERVA_NO_PAGABLE`.
     */
    val yaComenzo: Boolean = false,
    /**
     * Estado del reporte de problema de esta reserva; `null` si no tiene o si
     * no se pudo comprobar (sin red): se ofrece "Reportar un problema" igual y
     * la pantalla del reporte vuelve a comprobar.
     */
    val estadoReporte: EstadoReporte? = null,
    /** Inicio de la cita en epoch millis (para "Agregar a Google Calendar"); `0` si no se conoce. */
    val inicioEpochMillis: Long = 0L,
    /** Duracion del servicio reservado; `null` si el servicio ya no se pudo resolver (el evento queda sin hora de fin). */
    val duracionMinutos: Int? = null,
    /** Direccion completa de la cita (calle, numero, comuna); `null` si no es a domicilio / sin direccion. */
    val direccionTexto: String? = null,
) {
    /**
     * Solo una reserva `CONFIRMADA` (no `SOLICITADA`) que aun no comienza, con
     * el pago `PENDIENTE` o `RECHAZADO`, se puede pagar (el backend lo vuelve
     * a validar en `iniciarPago`).
     */
    val puedePagar: Boolean
        get() = !porConfirmar && !yaComenzo &&
            (estadoPago == EstadoPago.PENDIENTE || estadoPago == EstadoPago.RECHAZADO)

    /**
     * Una cita `SOLICITADA` o `CONFIRMADA` (las unicas que llegan aqui) se
     * puede cancelar hasta que comienza; el backend lo vuelve a validar en
     * `cancelarReserva` (`CITA_YA_INICIADA`).
     */
    val puedeCancelar: Boolean
        get() = !yaComenzo
}

/**
 * Que pasa con el dinero si el cliente cancela, segun la politica de
 * `cancelarReserva` (docs/DATA_MODEL.md): con el pago autorizado, 24 horas o
 * mas de anticipacion devuelve el total y con menos no hay reembolso
 * automatico; sin pago cobrado no hay nada que devolver.
 */
enum class PoliticaReembolso { TOTAL, SIN_REEMBOLSO_AUTOMATICO, SIN_COBRO }

/** Cancelacion en curso (dialogo abierto): que cita, que pasara con el pago y el motivo que escribe el cliente. */
data class CancelacionCita(
    val citaId: String,
    /** "Ana Soto - 14/10/2026 10:00", para identificar la cita en el dialogo. */
    val resumen: String,
    val politica: PoliticaReembolso,
    val motivo: String = "",
    val cancelando: Boolean = false,
    /** Error reintentable (sin conexion, fallo del servidor) mostrado dentro del dialogo. */
    val error: String? = null,
)

/** Largo maximo del motivo; el backend rechaza mas (`MOTIVO_INVALIDO`). */
const val MAX_MOTIVO_CANCELACION = 300

/** Sesion completada, mostrada en "Historial Reciente & Reembolsos". */
data class CitaHistorial(
    val id: String,
    val profesionalId: String,
    val fechaTexto: String,
    val modalidadTexto: String,
    val tituloSesion: String,
    val profesionalNombre: String,
    /** Estrellas de la resena que el cliente ya dejo; 0 si aun no reseno (o no se pudo comprobar). */
    val calificacion: Int,
    /**
     * `true` si se puede ofrecer "Dejar reseña": la reserva esta completada y
     * no tiene resena. Si la comprobacion fallo (sin red), se ofrece igual: la
     * pantalla de resena vuelve a comprobar y muestra "Ya reseñaste esta atencion".
     */
    val puedeResenar: Boolean = false,
    /** Ver [CitaProxima.estadoReporte]. */
    val estadoReporte: EstadoReporte? = null,
    /** Ver [CitaProxima.estadoPago]: el historial conserva el indicador "Pagada"/"Reembolsada". */
    val estadoPago: EstadoPago? = null,
)

/** Cita cancelada por el cliente o el profesional. */
data class CitaCancelada(
    val id: String,
    val profesionalId: String,
    val fechaHoraTexto: String,
    val profesionalNombre: String,
    val motivo: String?,
    /** Ver [CitaProxima.estadoReporte]. */
    val estadoReporte: EstadoReporte? = null,
    /** Ver [CitaProxima.estadoPago]: una cita pagada y luego cancelada sigue mostrando "Pagada"/"Reembolsada". */
    val estadoPago: EstadoPago? = null,
)

data class MisCitasState(
    val cargando: Boolean = false,
    /** `true` si la ultima carga fallo; la vista ofrece "Reintentar" en vez del estado vacio. */
    val errorCarga: Boolean = false,
    val tabSeleccionado: TabMisCitas = TabMisCitas.PROXIMAS,
    val citaEnCurso: CitaEnCurso? = null,
    val proximasCitas: List<CitaProxima> = emptyList(),
    val historial: List<CitaHistorial> = emptyList(),
    val canceladas: List<CitaCancelada> = emptyList(),
    /** No nulo mientras el dialogo "Cancelar cita" esta abierto. */
    val cancelacion: CancelacionCita? = null,
) {
    /** `true` si hay algo que pintar (aunque sea de una carga anterior). */
    val tieneCitas: Boolean
        get() = citaEnCurso != null || proximasCitas.isNotEmpty() || historial.isNotEmpty() || canceladas.isNotEmpty()
}

sealed interface MisCitasAction {
    data class SeleccionarTab(val tab: TabMisCitas) : MisCitasAction
    data object SeguirEnVivo : MisCitasAction
    data object Reprogramar : MisCitasAction
    data object VerPautaDigital : MisCitasAction
    data class AgregarAGoogleCalendar(val citaId: String) : MisCitasAction
    data class VerPreparacion(val citaId: String) : MisCitasAction
    data class DescargarBoleta(val citaId: String) : MisCitasAction
    data object ChatearConSoporte : MisCitasAction

    /** Navegacion a `:feature:reviews` (formulario de resena): la resuelve el `Root` via callback. */
    data class DejarResena(val reservaId: String, val profesionalId: String) : MisCitasAction

    /**
     * Navegacion a `:feature:payment` (pagar una reserva `CONFIRMADA`): la
     * resuelve el `Root` via callback. [titulo] y [montoClp] son solo
     * informativos para esa pantalla; el monto real lo fija el backend.
     */
    data class Pagar(val reservaId: String, val titulo: String, val montoClp: Long) : MisCitasAction

    /** Navegacion al formulario de reporte (`ReportarProblemaRoute`): la resuelve el `Root` via callback. */
    data class ReportarProblema(val reservaId: String, val profesionalId: String) : MisCitasAction

    /** Abre el dialogo de confirmacion para cancelar una cita proxima. */
    data class CancelarCita(val citaId: String) : MisCitasAction
    data class CambiarMotivoCancelacion(val motivo: String) : MisCitasAction
    data object ConfirmarCancelacion : MisCitasAction
    data object DescartarCancelacion : MisCitasAction

    /**
     * Vuelve a cargar las reservas (p. ej. al volver de dejar una resena, para que el boton desaparezca).
     * Se ignora si ya hay una carga en curso.
     */
    data object Recargar : MisCitasAction

    /** "Reintentar" tras un error de carga (misma recarga que [Recargar], pero iniciada por el usuario). */
    data object Reintentar : MisCitasAction
}

/** Eventos de una sola vez (no son estado): el `Root` los muestra en un Snackbar. */
sealed interface MisCitasEvent {
    data class MostrarMensaje(val texto: String) : MisCitasEvent
}

private fun CancelarReservaError.aMensaje(): String = when (this) {
    CancelarReservaError.SIN_INTERNET -> "Sin conexión. Revisa tu internet e inténtalo de nuevo."
    CancelarReservaError.SIN_SESION -> "Tu sesión expiró. Vuelve a iniciar sesión."
    CancelarReservaError.NO_AUTORIZADO -> "No puedes cancelar esta cita."
    CancelarReservaError.RESERVA_NO_ENCONTRADA -> "La cita ya no existe."
    CancelarReservaError.ESTADO_INVALIDO -> "Esta cita ya no se puede cancelar: cambió de estado."
    CancelarReservaError.CITA_YA_INICIADA -> "La hora de la cita ya llegó; no se puede cancelar."
    CancelarReservaError.MOTIVO_INVALIDO -> "El motivo es demasiado largo. Acórtalo e inténtalo de nuevo."
    CancelarReservaError.DATOS_INVALIDOS,
    CancelarReservaError.DESCONOCIDO,
    -> "No pudimos cancelar la cita. Inténtalo de nuevo."
}

/** Tras estos errores la cita ya cambio por otro lado: se cierra el dialogo y se recarga. */
private val ERRORES_CANCELAR_QUE_RECARGAN = setOf(
    CancelarReservaError.NO_AUTORIZADO,
    CancelarReservaError.RESERVA_NO_ENCONTRADA,
    CancelarReservaError.ESTADO_INVALIDO,
    CancelarReservaError.CITA_YA_INICIADA,
)

/** Anticipacion minima para que cancelar el cliente devuelva el total del pago. */
internal val ANTICIPACION_REEMBOLSO_TOTAL = 24.hours

private fun ModalidadServicio.aTexto(): String = when (this) {
    ModalidadServicio.DOMICILIO -> "A domicilio"
    ModalidadServicio.CONSULTA -> "En consulta"
    ModalidadServicio.ONLINE -> "Online"
}

private fun Instant.aHoraTexto(): String {
    val local = toLocalDateTime(TimeZone.currentSystemDefault())
    return "%02d:%02d".format(local.hour, local.minute)
}

private fun Instant.aFechaTexto(): String {
    val local = toLocalDateTime(TimeZone.currentSystemDefault())
    return "%02d/%02d/%04d".format(local.dayOfMonth, local.monthNumber, local.year)
}

private fun Instant.aFechaHoraTexto(): String = "${aFechaTexto()} ${aHoraTexto()}"

/** Verificacion general aprobada (no solo un RNPI escrito a mano). */
private fun Profesional.estaVerificado(): Boolean = estadoVerificacionGeneral == EstadoVerificacion.APROBADO

/** El RNPI solo cuenta como verificado con la insignia `CREDENCIALES` aprobada. */
private fun Profesional.rnpiEstaVerificado(): Boolean =
    insignias.any { it.tipo == TipoInsignia.CREDENCIALES && it.estado == EstadoVerificacion.APROBADO }

/** Estado del pago a mostrar: `null` mientras la reserva sigue `SOLICITADA` (aun no corresponde pagar). */
private fun Reserva.estadoPagoVisible(): EstadoPago? =
    if (estado == EstadoReserva.SOLICITADA) null else pago.estado

/**
 * Mapea la `Reserva` real a la tarjeta hero de la cita en curso. Sin
 * seguimiento en tiempo real conectado todavia (`profesionalEnCaminoMinutos`)
 * ni conteo de sesiones de un tratamiento (Reserva es 1:1 con una sesion, el
 * dominio no agrupa "tratamientos" de varias reservas, ver docs/DOMAIN.md):
 * se deja `1 de 1` en vez de inventar ese conteo.
 */
private fun Reserva.aCitaEnCurso(
    profesionales: Map<String, Profesional>,
    reportes: Map<String, EstadoReporte>,
): CitaEnCurso {
    val profesional = profesionales[profesionalId]
    val servicio = profesional?.servicios?.firstOrNull { it.id == servicioId }
    return CitaEnCurso(
        id = id,
        profesionalId = profesionalId,
        horaTexto = fechaHora.aHoraTexto(),
        modalidadTexto = modalidad.aTexto(),
        profesionalEnCaminoMinutos = null,
        profesionalNombre = profesional?.perfil?.nombre ?: "Profesional",
        especialidad = profesional?.especialidades?.firstOrNull().orEmpty(),
        numeroRegistroSis = profesional?.rnpi.orEmpty(),
        tratamientoTitulo = servicio?.nombre ?: "Sesión",
        tratamientoDescripcion = servicio?.descripcion.orEmpty(),
        sesionActual = 1,
        sesionesTotales = 1,
        ubicacionTexto = direccion?.let { d ->
            listOf("${d.calle} ${d.numero}".trim(), d.comuna).filter { it.isNotBlank() }.joinToString(", ")
        } ?: modalidad.aTexto(),
        estadoReporte = reportes[id],
        estadoPago = estadoPagoVisible(),
        verificado = profesional?.estaVerificado() == true,
        rnpiVerificado = profesional?.rnpiEstaVerificado() == true,
    )
}

private fun Reserva.aCitaProxima(
    profesionales: Map<String, Profesional>,
    reportes: Map<String, EstadoReporte>,
    ahora: Instant,
): CitaProxima {
    val profesional = profesionales[profesionalId]
    val servicio = profesional?.servicios?.firstOrNull { it.id == servicioId }
    return CitaProxima(
        id = id,
        profesionalId = profesionalId,
        modalidad = modalidad,
        lugar = direccion?.let { d -> listOf(d.comuna, d.ciudad).filter { it.isNotBlank() }.joinToString(", ") }
            ?: modalidad.aTexto(),
        fechaHoraTexto = fechaHora.aFechaHoraTexto(),
        profesionalNombre = profesional?.perfil?.nombre ?: "Profesional",
        tipoSesion = servicio?.nombre ?: "Sesión",
        precioTotal = pago.monto,
        porConfirmar = estado == EstadoReserva.SOLICITADA,
        estadoPago = estadoPagoVisible(),
        yaComenzo = fechaHora <= ahora,
        estadoReporte = reportes[id],
        inicioEpochMillis = fechaHora.toEpochMilliseconds(),
        duracionMinutos = servicio?.duracionMinutos,
        direccionTexto = direccion?.let { d ->
            listOf("${d.calle} ${d.numero}".trim(), d.comuna, d.ciudad).filter { it.isNotBlank() }.joinToString(", ")
        }?.takeIf { it.isNotBlank() },
    )
}

/**
 * [resena] es el resultado de `ResenaRepository.obtenerPorReserva`: la resena
 * existente, `null` si la reserva aun no tiene, o ausente del mapa si la
 * comprobacion fallo (ver [CitaHistorial.puedeResenar]).
 */
internal fun Reserva.aCitaHistorial(
    profesionales: Map<String, Profesional>,
    resena: Resena?,
    estadoReporte: EstadoReporte? = null,
): CitaHistorial {
    val profesional = profesionales[profesionalId]
    val servicio = profesional?.servicios?.firstOrNull { it.id == servicioId }
    return CitaHistorial(
        id = id,
        profesionalId = profesionalId,
        fechaTexto = fechaHora.aFechaTexto(),
        modalidadTexto = modalidad.aTexto(),
        tituloSesion = servicio?.nombre ?: "Sesión",
        profesionalNombre = profesional?.perfil?.nombre ?: "Profesional",
        calificacion = resena?.calificacion ?: 0,
        puedeResenar = resena == null,
        estadoReporte = estadoReporte,
        estadoPago = estadoPagoVisible(),
    )
}

private fun Reserva.aCitaCancelada(
    profesionales: Map<String, Profesional>,
    reportes: Map<String, EstadoReporte>,
): CitaCancelada {
    val profesional = profesionales[profesionalId]
    return CitaCancelada(
        id = id,
        profesionalId = profesionalId,
        fechaHoraTexto = fechaHora.aFechaHoraTexto(),
        profesionalNombre = profesional?.perfil?.nombre ?: "Profesional",
        motivo = when (estado) {
            EstadoReserva.CANCELADA_CLIENTE -> "Cancelada por ti"
            EstadoReserva.CANCELADA_PROFESIONAL -> "Cancelada por el profesional"
            EstadoReserva.RECHAZADA -> if (expirada) "Solicitud vencida sin respuesta" else "Solicitud rechazada"
            else -> null
        },
        estadoReporte = reportes[id],
        estadoPago = estadoPagoVisible(),
    )
}

@HiltViewModel
class MisCitasViewModel @Inject constructor(
    private val reservaRepository: ReservaRepository,
    private val profesionalRepository: ProfesionalRepository,
    private val resenaRepository: ResenaRepository,
    private val reporteProblemaRepository: ReporteProblemaRepository,
    private val firebaseAuth: FirebaseAuth,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(MisCitasState())
    val state: StateFlow<MisCitasState> = _state.asStateFlow()

    private val _eventos = Channel<MisCitasEvent>(Channel.BUFFERED)
    val eventos: Flow<MisCitasEvent> = _eventos.receiveAsFlow()

    private var cargaJob: Job? = null

    init {
        cargarReservas()
    }

    fun onAction(action: MisCitasAction) {
        when (action) {
            is MisCitasAction.SeleccionarTab ->
                _state.update { it.copy(tabSeleccionado = action.tab) }
            MisCitasAction.Recargar,
            MisCitasAction.Reintentar,
            -> if (cargaJob?.isActive != true) cargarReservas()
            is MisCitasAction.CancelarCita -> pedirCancelacion(action.citaId)
            is MisCitasAction.CambiarMotivoCancelacion -> _state.update { estado ->
                estado.cancelacion?.takeIf { !it.cancelando }?.let {
                    estado.copy(cancelacion = it.copy(motivo = action.motivo, error = null))
                } ?: estado
            }
            MisCitasAction.ConfirmarCancelacion -> confirmarCancelacion()
            MisCitasAction.DescartarCancelacion ->
                _state.update { estado ->
                    if (estado.cancelacion?.cancelando == true) estado else estado.copy(cancelacion = null)
                }
            // Dejar resena, reportar problema, pagar y agregar a Google Calendar
            // son navegacion/Intents: el Root las resuelve (NavGraph o Activity).
            is MisCitasAction.DejarResena,
            is MisCitasAction.ReportarProblema,
            is MisCitasAction.Pagar,
            is MisCitasAction.AgregarAGoogleCalendar,
            -> Unit
            // Seguir en vivo/chat, reprogramar, ver pauta digital, ver
            // preparacion, descargar boleta y chatear con soporte: no existe
            // backend ni pantalla todavia, y la vista ya no renderiza sus
            // botones; se conectan cuando la feature salga de esta fase
            // (docs/TASKS.md).
            MisCitasAction.SeguirEnVivo,
            MisCitasAction.Reprogramar,
            MisCitasAction.VerPautaDigital,
            is MisCitasAction.VerPreparacion,
            is MisCitasAction.DescargarBoleta,
            MisCitasAction.ChatearConSoporte,
            -> Unit
        }
    }

    private fun pedirCancelacion(citaId: String) {
        val cita = _state.value.proximasCitas.firstOrNull { it.id == citaId && it.puedeCancelar } ?: return
        val politica = when (cita.estadoPago) {
            EstadoPago.AUTORIZADO -> {
                val anticipacion = (cita.inicioEpochMillis - clock.now().toEpochMilliseconds()).milliseconds
                if (anticipacion >= ANTICIPACION_REEMBOLSO_TOTAL) PoliticaReembolso.TOTAL
                else PoliticaReembolso.SIN_REEMBOLSO_AUTOMATICO
            }
            else -> PoliticaReembolso.SIN_COBRO
        }
        _state.update {
            it.copy(
                cancelacion = CancelacionCita(
                    citaId = cita.id,
                    resumen = "${cita.profesionalNombre} • ${cita.fechaHoraTexto}",
                    politica = politica,
                ),
            )
        }
    }

    private fun confirmarCancelacion() {
        val cancelacion = _state.value.cancelacion ?: return
        if (cancelacion.cancelando) return
        _state.update { it.copy(cancelacion = cancelacion.copy(cancelando = true, error = null)) }
        viewModelScope.launch {
            when (val resultado = reservaRepository.cancelar(cancelacion.citaId, cancelacion.motivo.trim().ifBlank { null })) {
                is Result.Success -> {
                    _state.update { it.copy(cancelacion = null) }
                    _eventos.send(MisCitasEvent.MostrarMensaje(mensajeCancelada(resultado.data.reembolso, cancelacion.politica)))
                    recargarAhora()
                }
                is Result.Error -> {
                    val mensaje = resultado.error.aMensaje()
                    if (resultado.error in ERRORES_CANCELAR_QUE_RECARGAN) {
                        _state.update { it.copy(cancelacion = null) }
                        _eventos.send(MisCitasEvent.MostrarMensaje(mensaje))
                        recargarAhora()
                    } else {
                        // Reintentable: el dialogo sigue abierto con el error a la vista.
                        _state.update { it.copy(cancelacion = cancelacion.copy(cancelando = false, error = mensaje)) }
                    }
                }
            }
        }
    }

    /** Lo que informa el backend manda; si no lo trae, se usa la politica calculada al abrir el dialogo. */
    private fun mensajeCancelada(reembolso: ReembolsoCancelacion?, politica: PoliticaReembolso): String {
        val efectivo = when (reembolso) {
            ReembolsoCancelacion.TOTAL -> PoliticaReembolso.TOTAL
            ReembolsoCancelacion.NINGUNO -> PoliticaReembolso.SIN_REEMBOLSO_AUTOMATICO
            ReembolsoCancelacion.NO_APLICA -> PoliticaReembolso.SIN_COBRO
            null -> politica
        }
        return when (efectivo) {
            PoliticaReembolso.TOTAL -> "Cita cancelada. Se reembolsará el total de tu pago."
            PoliticaReembolso.SIN_REEMBOLSO_AUTOMATICO -> "Cita cancelada. Por la anticipación, no hay reembolso automático."
            PoliticaReembolso.SIN_COBRO -> "Cita cancelada."
        }
    }

    /** Recarga la lista tras cambiar una reserva, aunque haya una carga en curso (esa quedaria vieja). */
    private fun recargarAhora() {
        cargaJob?.cancel()
        cargarReservas()
    }

    private fun cargarReservas() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        cargaJob = viewModelScope.launch {
            _state.update { it.copy(cargando = true, errorCarga = false) }
            when (val resultado = reservaRepository.obtenerPorCliente(uid)) {
                is Result.Success -> aplicarReservas(uid, resultado.data)
                is Result.Error -> _state.update { it.copy(cargando = false, errorCarga = true) }
            }
        }
    }

    private suspend fun aplicarReservas(uid: String, reservas: List<Reserva>) {
        val profesionales = mutableMapOf<String, Profesional>()
        for (reserva in reservas) {
            if (reserva.profesionalId in profesionales) continue
            val resultado = profesionalRepository.obtenerPorId(reserva.profesionalId)
            if (resultado is Result.Success) profesionales[reserva.profesionalId] = resultado.data
        }

        // Una resena por reserva completada (id del documento = reservaId). Si
        // la lectura falla la reserva queda fuera del mapa y se ofrece "Dejar
        // resena" igual (ver `CitaHistorial.puedeResenar`).
        val resenas = mutableMapOf<String, Resena?>()
        for (reserva in reservas) {
            if (reserva.estado != EstadoReserva.COMPLETADA) continue
            val resultado = resenaRepository.obtenerPorReserva(reserva.id)
            if (resultado is Result.Success) resenas[reserva.id] = resultado.data
        }

        // Una sola consulta por `clienteId` para todos los reportes. Si falla,
        // ninguna cita queda marcada y se ofrece "Reportar un problema" en
        // todas (ver `CitaProxima.estadoReporte`).
        val reportes: Map<String, EstadoReporte> = if (reservas.isEmpty()) {
            emptyMap()
        } else {
            when (val resultado = reporteProblemaRepository.obtenerPorCliente(uid)) {
                is Result.Success -> resultado.data.associate { it.reservaId to it.estado }
                is Result.Error -> emptyMap()
            }
        }

        val ahora = clock.now()
        val citaEnCurso = reservas.firstOrNull { it.estado == EstadoReserva.EN_CURSO }
            ?.aCitaEnCurso(profesionales, reportes)
        val proximasCitas = reservas
            .filter { it.estado == EstadoReserva.SOLICITADA || it.estado == EstadoReserva.CONFIRMADA }
            .map { it.aCitaProxima(profesionales, reportes, ahora) }
        val historial = reservas
            .filter { it.estado == EstadoReserva.COMPLETADA }
            .map { it.aCitaHistorial(profesionales, resenas[it.id], reportes[it.id]) }
        val canceladas = reservas
            .filter {
                it.estado == EstadoReserva.CANCELADA_CLIENTE ||
                    it.estado == EstadoReserva.CANCELADA_PROFESIONAL ||
                    it.estado == EstadoReserva.RECHAZADA
            }
            .map { it.aCitaCancelada(profesionales, reportes) }

        _state.update {
            it.copy(
                cargando = false,
                errorCarga = false,
                citaEnCurso = citaEnCurso,
                proximasCitas = proximasCitas,
                historial = historial,
                canceladas = canceladas,
            )
        }
    }
}
