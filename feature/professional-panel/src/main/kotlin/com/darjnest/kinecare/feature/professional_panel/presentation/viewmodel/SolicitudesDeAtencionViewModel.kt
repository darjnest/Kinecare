@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darjnest.kinecare.core.common.data.error.ResponderReservaError
import com.darjnest.kinecare.core.common.data.repository.ReservaRepository
import com.darjnest.kinecare.core.common.data.repository.ServicioRepository
import com.darjnest.kinecare.core.common.data.repository.UsuarioRepository
import com.darjnest.kinecare.core.common.domain.model.EstadoPago
import com.darjnest.kinecare.core.common.domain.model.EstadoReserva
import com.darjnest.kinecare.core.common.domain.model.ModalidadServicio
import com.darjnest.kinecare.core.common.domain.model.Reserva
import com.darjnest.kinecare.core.common.domain.model.RespuestaReserva
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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/** Pestaña activa en la pantalla de Solicitudes de Atencion. */
enum class PestanaSolicitudes { PENDIENTES, HISTORIAL }

/** Nivel de urgencia con el que se destaca la solicitud pendiente. */
enum class NivelUrgenciaSolicitud { URGENTE, NORMAL }

/** Modalidad en la que se atenderia la solicitud. */
enum class ModalidadSolicitud { A_DOMICILIO, EN_CONSULTA, ONLINE }

/**
 * Solicitud de atencion pendiente de respuesta del profesional. Modelo de
 * presentacion mapeado desde una `Reserva` en `SOLICITADA` (ver
 * [aSolicitudAtencion]). Los campos que el dominio todavia no tiene
 * (calificacion y verificacion del paciente, motivo de consulta) son nulos y
 * la vista oculta esa parte de la tarjeta en vez de mostrar un dato falso.
 */
data class SolicitudAtencion(
    val id: String,
    val urgencia: NivelUrgenciaSolicitud,
    val tiempoRestanteTexto: String,
    val modalidad: ModalidadSolicitud,
    val modalidadTexto: String,
    val pacienteNombre: String,
    val calificacion: Double? = null,
    val pacienteVerificadoTexto: String? = null,
    val servicioNombre: String,
    val fechaHoraTexto: String,
    val tiempoRelativoTexto: String? = null,
    val direccion: String,
    val motivoConsulta: String? = null,
    /** Monto del servicio menos la comision de KineCare. */
    val honorarioClp: Long,
    /** Solo con el pago ya autorizado (Fase 5); `null` mientras el pago este pendiente. */
    val custodiaTexto: String? = null,
    val avisoPagoTexto: String? = null,
)

/** Como termino (o en que va) una solicitud que ya no espera respuesta. */
enum class EstadoSolicitudResuelta { CONFIRMADA, EN_CURSO, COMPLETADA, RECHAZADA, CANCELADA, VENCIDA }

/** Fila de la pestaña "Historial / Resueltas". */
data class SolicitudResuelta(
    val id: String,
    val pacienteNombre: String,
    val servicioNombre: String,
    val fechaHoraTexto: String,
    val estado: EstadoSolicitudResuelta,
    val estadoTexto: String,
)

data class SolicitudesDeAtencionState(
    val cargando: Boolean = false,
    /** La carga de reservas fallo: la vista ofrece reintentar en vez de decir "sin solicitudes". */
    val errorCarga: Boolean = false,
    val pestanaSeleccionada: PestanaSolicitudes = PestanaSolicitudes.PENDIENTES,
    /** Sin regla de plazo de respuesta en el dominio todavia: vacio oculta el banner. */
    val plazoRespuestaTexto: String = "",
    val solicitudesPendientes: List<SolicitudAtencion> = emptyList(),
    val historial: List<SolicitudResuelta> = emptyList(),
    val completadasEstaSemana: Int = 0,
    /** Sin dato de tiempos de respuesta todavia; `null` oculta el porcentaje. */
    val porcentajeRespuestaATiempo: Int? = null,
    /** Id de la solicitud cuya respuesta esta en curso (botones deshabilitados con progreso). */
    val respondiendoId: String? = null,
    /** Solicitud que el profesional pidio rechazar; la vista muestra un dialogo de confirmacion. */
    val solicitudPorRechazar: SolicitudAtencion? = null,
    /** Aviso puntual para un Snackbar; la vista lo consume con [SolicitudesDeAtencionAction.MensajeMostrado]. */
    val mensaje: String? = null,
)

sealed interface SolicitudesDeAtencionAction {
    data object VolverAtras : SolicitudesDeAtencionAction
    data class CambiarPestana(val pestana: PestanaSolicitudes) : SolicitudesDeAtencionAction
    data class AceptarSolicitud(val solicitudId: String) : SolicitudesDeAtencionAction

    /** Pide confirmacion antes de rechazar (rechazar no se puede deshacer). */
    data class RechazarSolicitud(val solicitudId: String) : SolicitudesDeAtencionAction
    data object ConfirmarRechazo : SolicitudesDeAtencionAction
    data object CancelarRechazo : SolicitudesDeAtencionAction
    data object Reintentar : SolicitudesDeAtencionAction
    data object MensajeMostrado : SolicitudesDeAtencionAction
}

/** Una solicitud se marca urgente si la cita empieza dentro de este plazo. */
internal val UMBRAL_URGENCIA: Duration = 24.hours

private val DIAS_ABREVIADOS = mapOf(
    DayOfWeek.MONDAY to "lun",
    DayOfWeek.TUESDAY to "mar",
    DayOfWeek.WEDNESDAY to "mié",
    DayOfWeek.THURSDAY to "jue",
    DayOfWeek.FRIDAY to "vie",
    DayOfWeek.SATURDAY to "sáb",
    DayOfWeek.SUNDAY to "dom",
)

/**
 * "Hoy, 17:30 hrs" / "Mañana, 09:00 hrs" / "lun 05/10, 09:00 hrs", en hora de
 * Chile: el horario del profesional se declara en esa zona
 * ([ZonaHorariaChile]), no en la del dispositivo.
 */
internal fun Instant.aFechaHoraTexto(ahora: Instant): String {
    val local = toLocalDateTime(ZonaHorariaChile)
    val hoy = ahora.toLocalDateTime(ZonaHorariaChile).date
    val hora = "%02d:%02d hrs".format(local.hour, local.minute)
    val dia = when (local.date) {
        hoy -> "Hoy"
        hoy.plus(1, DateTimeUnit.DAY) -> "Mañana"
        else -> "${DIAS_ABREVIADOS.getValue(local.dayOfWeek)} %02d/%02d".format(local.day, local.month.ordinal + 1)
    }
    return "$dia, $hora"
}

/** "Cita en 45 min" / "Cita en 3 h" / "Cita en 2 días" (redondeado hacia abajo). */
internal fun textoTiempoRestante(restante: Duration): String {
    val minutos = restante.inWholeMinutes.coerceAtLeast(0)
    return when {
        minutos < 60 -> "Cita en $minutos min"
        minutos < 24 * 60 -> "Cita en ${minutos / 60} h"
        else -> (minutos / (24 * 60)).let { dias -> if (dias == 1L) "Cita en 1 día" else "Cita en $dias días" }
    }
}

/** Monto que recibe el profesional: precio menos la comision fijada por el backend. */
internal fun Reserva.honorarioNeto(): Long = pago.monto - (pago.monto * comisionPorcentaje).roundToLong()

private fun ModalidadServicio.aModalidadSolicitud(): ModalidadSolicitud = when (this) {
    ModalidadServicio.DOMICILIO -> ModalidadSolicitud.A_DOMICILIO
    ModalidadServicio.CONSULTA -> ModalidadSolicitud.EN_CONSULTA
    ModalidadServicio.ONLINE -> ModalidadSolicitud.ONLINE
}

private fun ModalidadServicio.aTexto(): String = when (this) {
    ModalidadServicio.DOMICILIO -> "A Domicilio"
    ModalidadServicio.CONSULTA -> "En Consulta"
    ModalidadServicio.ONLINE -> "Online"
}

internal fun Reserva.ubicacionTexto(): String = when (modalidad) {
    ModalidadServicio.DOMICILIO -> direccion
        ?.let { d -> listOf("${d.calle} ${d.numero}".trim(), d.comuna).filter { it.isNotBlank() }.joinToString(", ") }
        ?.takeIf { it.isNotBlank() }
        ?: "Dirección no informada"
    ModalidadServicio.CONSULTA -> "En tu consulta"
    ModalidadServicio.ONLINE -> "Atención online"
}

internal fun Reserva.aSolicitudAtencion(
    ahora: Instant,
    pacientes: Map<String, String>,
    servicios: Map<String, String>,
): SolicitudAtencion {
    val restante = fechaHora - ahora
    val urgente = restante < UMBRAL_URGENCIA
    val pagoAutorizado = pago.estado == EstadoPago.AUTORIZADO
    return SolicitudAtencion(
        id = id,
        urgencia = if (urgente) NivelUrgenciaSolicitud.URGENTE else NivelUrgenciaSolicitud.NORMAL,
        tiempoRestanteTexto = textoTiempoRestante(restante).let { if (urgente) "Urgente • $it" else it },
        modalidad = modalidad.aModalidadSolicitud(),
        modalidadTexto = modalidad.aTexto(),
        pacienteNombre = pacientes[clienteId] ?: "Paciente",
        servicioNombre = servicios[servicioId] ?: "Sesión",
        fechaHoraTexto = fechaHora.aFechaHoraTexto(ahora),
        direccion = ubicacionTexto(),
        honorarioClp = honorarioNeto(),
        custodiaTexto = if (pagoAutorizado) "Pago en custodia KineCare" else null,
        avisoPagoTexto = if (pagoAutorizado) "El paciente ya pagó; el monto se libera al completar la atención." else null,
    )
}

/** `null` si la reserva sigue esperando respuesta (va a "Pendientes"). */
internal fun Reserva.estadoResuelto(ahora: Instant): EstadoSolicitudResuelta? = when (estado) {
    EstadoReserva.SOLICITADA -> if (fechaHora <= ahora) EstadoSolicitudResuelta.VENCIDA else null
    EstadoReserva.CONFIRMADA -> EstadoSolicitudResuelta.CONFIRMADA
    EstadoReserva.EN_CURSO -> EstadoSolicitudResuelta.EN_CURSO
    EstadoReserva.COMPLETADA -> EstadoSolicitudResuelta.COMPLETADA
    EstadoReserva.RECHAZADA -> EstadoSolicitudResuelta.RECHAZADA
    EstadoReserva.CANCELADA_CLIENTE, EstadoReserva.CANCELADA_PROFESIONAL -> EstadoSolicitudResuelta.CANCELADA
}

private fun Reserva.textoEstadoResuelto(estado: EstadoSolicitudResuelta): String = when (estado) {
    EstadoSolicitudResuelta.CONFIRMADA -> "Confirmada"
    EstadoSolicitudResuelta.EN_CURSO -> "En curso"
    EstadoSolicitudResuelta.COMPLETADA -> "Completada"
    EstadoSolicitudResuelta.RECHAZADA -> "Rechazada"
    EstadoSolicitudResuelta.CANCELADA ->
        if (this.estado == EstadoReserva.CANCELADA_CLIENTE) "Cancelada por el paciente" else "Cancelada por ti"
    EstadoSolicitudResuelta.VENCIDA -> "Vencida sin respuesta"
}

/** Lunes de la semana (lunes a domingo) de [fecha]. */
private fun lunesDe(fecha: LocalDate): LocalDate = fecha.minus(fecha.dayOfWeek.ordinal, DateTimeUnit.DAY)

private fun ResponderReservaError.aMensaje(): String = when (this) {
    ResponderReservaError.SIN_INTERNET -> "Sin conexión. Revisa tu internet e inténtalo de nuevo."
    ResponderReservaError.SIN_SESION -> "Tu sesión expiró. Vuelve a iniciar sesión."
    ResponderReservaError.ROL_INVALIDO -> "Solo un profesional puede responder solicitudes."
    ResponderReservaError.RESERVA_NO_ENCONTRADA -> "La solicitud ya no existe."
    ResponderReservaError.RESERVA_YA_RESPONDIDA -> "Esta solicitud ya fue respondida o cancelada."
    ResponderReservaError.RESERVA_VENCIDA -> "La hora de esta solicitud ya pasó; no se puede aceptar."
    ResponderReservaError.DATOS_INVALIDOS,
    ResponderReservaError.DESCONOCIDO,
    -> "No pudimos enviar tu respuesta. Inténtalo de nuevo."
}

/**
 * Errores tras los cuales la lista local quedo desactualizada (otra
 * respuesta, una cancelacion o el paso del tiempo cambiaron la reserva): se
 * recarga para mostrar el estado real.
 */
private val ERRORES_QUE_RECARGAN = setOf(
    ResponderReservaError.RESERVA_NO_ENCONTRADA,
    ResponderReservaError.RESERVA_YA_RESPONDIDA,
    ResponderReservaError.RESERVA_VENCIDA,
)

@HiltViewModel
class SolicitudesDeAtencionViewModel @Inject constructor(
    private val reservaRepository: ReservaRepository,
    private val usuarioRepository: UsuarioRepository,
    private val servicioRepository: ServicioRepository,
    private val firebaseAuth: FirebaseAuth,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(SolicitudesDeAtencionState())
    val state: StateFlow<SolicitudesDeAtencionState> = _state.asStateFlow()

    /** Fuente de verdad: las reservas del profesional; las dos pestañas se derivan de aca. */
    private var reservas: List<Reserva> = emptyList()

    /** Nombre por `clienteId` y por `servicioId`. Un fallo de lectura deja un texto generico. */
    private val pacientes = mutableMapOf<String, String>()
    private var servicios: Map<String, String> = emptyMap()

    private var cargaJob: Job? = null

    init {
        cargar()
    }

    fun onAction(action: SolicitudesDeAtencionAction) {
        when (action) {
            is SolicitudesDeAtencionAction.CambiarPestana ->
                _state.update { it.copy(pestanaSeleccionada = action.pestana) }

            is SolicitudesDeAtencionAction.AceptarSolicitud ->
                responder(action.solicitudId, RespuestaReserva.ACEPTAR)

            is SolicitudesDeAtencionAction.RechazarSolicitud -> {
                val solicitud = _state.value.solicitudesPendientes.firstOrNull { it.id == action.solicitudId }
                if (solicitud != null && _state.value.respondiendoId == null) {
                    _state.update { it.copy(solicitudPorRechazar = solicitud) }
                }
            }

            SolicitudesDeAtencionAction.ConfirmarRechazo -> {
                val solicitud = _state.value.solicitudPorRechazar ?: return
                _state.update { it.copy(solicitudPorRechazar = null) }
                responder(solicitud.id, RespuestaReserva.RECHAZAR)
            }

            SolicitudesDeAtencionAction.CancelarRechazo ->
                _state.update { it.copy(solicitudPorRechazar = null) }

            SolicitudesDeAtencionAction.Reintentar -> cargar()

            SolicitudesDeAtencionAction.MensajeMostrado ->
                _state.update { it.copy(mensaje = null) }

            // Volver atras es navegacion, no estado del ViewModel: el Root la
            // resuelve directo contra el NavGraph (ver ProfessionalPanelNavGraph).
            SolicitudesDeAtencionAction.VolverAtras -> Unit
        }
    }

    private fun cargar() {
        if (cargaJob?.isActive == true) return
        val uid = firebaseAuth.currentUser?.uid ?: return
        cargaJob = viewModelScope.launch {
            _state.update { it.copy(cargando = true, errorCarga = false) }
            when (val resultado = reservaRepository.obtenerPorProfesional(uid)) {
                is Result.Success -> {
                    reservas = resultado.data
                    resolverNombres(uid)
                    _state.update { derivar(it).copy(cargando = false) }
                }
                is Result.Error -> _state.update { it.copy(cargando = false, errorCarga = true) }
            }
        }
    }

    /**
     * Nombres de pacientes (`usuarios/{clienteId}`, legible por cualquier
     * usuario autenticado) y de servicios (catalogo propio, incluidos los
     * pausados: una reserva puede ser de un servicio pausado despues).
     */
    private suspend fun resolverNombres(uid: String) {
        if (servicios.isEmpty()) {
            val resultado = servicioRepository.obtenerPorProfesional(uid)
            if (resultado is Result.Success) servicios = resultado.data.associate { it.id to it.nombre }
        }
        for (clienteId in reservas.map { it.clienteId }.distinct()) {
            if (clienteId in pacientes) continue
            val resultado = usuarioRepository.obtenerPorId(clienteId)
            if (resultado is Result.Success) pacientes[clienteId] = resultado.data.nombre
        }
    }

    private fun derivar(estado: SolicitudesDeAtencionState): SolicitudesDeAtencionState {
        val ahora = clock.now()
        val semanaActual = lunesDe(ahora.toLocalDateTime(ZonaHorariaChile).date)
        val pendientes = reservas
            .filter { it.estadoResuelto(ahora) == null }
            .sortedBy { it.fechaHora }
            .map { it.aSolicitudAtencion(ahora, pacientes, servicios) }
        val historial = reservas
            .mapNotNull { reserva ->
                reserva.estadoResuelto(ahora)?.let { resuelto ->
                    SolicitudResuelta(
                        id = reserva.id,
                        pacienteNombre = pacientes[reserva.clienteId] ?: "Paciente",
                        servicioNombre = servicios[reserva.servicioId] ?: "Sesión",
                        fechaHoraTexto = reserva.fechaHora.aFechaHoraTexto(ahora),
                        estado = resuelto,
                        estadoTexto = reserva.textoEstadoResuelto(resuelto),
                    ) to reserva.fechaHora
                }
            }
            .sortedByDescending { (_, fechaHora) -> fechaHora }
            .map { (fila, _) -> fila }
        val completadasEstaSemana = reservas.count {
            it.estado == EstadoReserva.COMPLETADA &&
                lunesDe(it.fechaHora.toLocalDateTime(ZonaHorariaChile).date) == semanaActual
        }
        return estado.copy(
            solicitudesPendientes = pendientes,
            historial = historial,
            completadasEstaSemana = completadasEstaSemana,
        )
    }

    private fun responder(reservaId: String, respuesta: RespuestaReserva) {
        if (_state.value.respondiendoId != null) return
        val paciente = _state.value.solicitudesPendientes.firstOrNull { it.id == reservaId }?.pacienteNombre ?: return
        _state.update { it.copy(respondiendoId = reservaId) }
        viewModelScope.launch {
            when (val resultado = reservaRepository.responder(reservaId, respuesta)) {
                is Result.Success -> {
                    reservas = reservas.map { if (it.id == reservaId) it.copy(estado = resultado.data) else it }
                    val mensaje = when (resultado.data) {
                        EstadoReserva.CONFIRMADA -> "Cita con $paciente confirmada."
                        else -> "Solicitud de $paciente rechazada."
                    }
                    _state.update { derivar(it).copy(respondiendoId = null, mensaje = mensaje) }
                }
                is Result.Error -> {
                    _state.update { it.copy(respondiendoId = null, mensaje = resultado.error.aMensaje()) }
                    if (resultado.error in ERRORES_QUE_RECARGAN) cargar()
                }
            }
        }
    }
}
