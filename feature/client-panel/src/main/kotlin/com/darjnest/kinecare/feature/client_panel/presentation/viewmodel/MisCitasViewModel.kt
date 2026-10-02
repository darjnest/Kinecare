@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.client_panel.presentation.viewmodel

import com.darjnest.kinecare.core.common.data.repository.ProfesionalRepository
import com.darjnest.kinecare.core.common.data.repository.ResenaRepository
import com.darjnest.kinecare.core.common.data.repository.ReservaRepository
import com.darjnest.kinecare.core.common.domain.model.EstadoReserva
import com.darjnest.kinecare.core.common.domain.model.ModalidadServicio
import com.darjnest.kinecare.core.common.domain.model.Profesional
import com.darjnest.kinecare.core.common.domain.model.Resena
import com.darjnest.kinecare.core.common.domain.model.Reserva
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.feature.client_panel.data.repository.ReporteProblemaRepository
import com.darjnest.kinecare.feature.client_panel.domain.EstadoReporte
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

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
     * Estado del reporte de problema de esta reserva; `null` si no tiene o si
     * no se pudo comprobar (sin red): se ofrece "Reportar un problema" igual y
     * la pantalla del reporte vuelve a comprobar.
     */
    val estadoReporte: EstadoReporte? = null,
)

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
)

data class MisCitasState(
    val cargando: Boolean = false,
    val tabSeleccionado: TabMisCitas = TabMisCitas.PROXIMAS,
    val citaEnCurso: CitaEnCurso? = null,
    val proximasCitas: List<CitaProxima> = emptyList(),
    val historial: List<CitaHistorial> = emptyList(),
    val canceladas: List<CitaCancelada> = emptyList(),
)

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

    /** Navegacion al formulario de reporte (`ReportarProblemaRoute`): la resuelve el `Root` via callback. */
    data class ReportarProblema(val reservaId: String, val profesionalId: String) : MisCitasAction

    /** Vuelve a cargar las reservas (p. ej. al volver de dejar una resena, para que el boton desaparezca). */
    data object Recargar : MisCitasAction
}

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
        profesionalNombre = profesional?.usuario?.nombre ?: "Profesional",
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
    )
}

private fun Reserva.aCitaProxima(
    profesionales: Map<String, Profesional>,
    reportes: Map<String, EstadoReporte>,
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
        profesionalNombre = profesional?.usuario?.nombre ?: "Profesional",
        tipoSesion = servicio?.nombre ?: "Sesión",
        precioTotal = pago.monto,
        porConfirmar = estado == EstadoReserva.SOLICITADA,
        estadoReporte = reportes[id],
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
        profesionalNombre = profesional?.usuario?.nombre ?: "Profesional",
        calificacion = resena?.calificacion ?: 0,
        puedeResenar = resena == null,
        estadoReporte = estadoReporte,
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
        profesionalNombre = profesional?.usuario?.nombre ?: "Profesional",
        motivo = when (estado) {
            EstadoReserva.CANCELADA_CLIENTE -> "Cancelada por ti"
            EstadoReserva.CANCELADA_PROFESIONAL -> "Cancelada por el profesional"
            EstadoReserva.RECHAZADA -> "Solicitud rechazada"
            else -> null
        },
        estadoReporte = reportes[id],
    )
}

@HiltViewModel
class MisCitasViewModel @Inject constructor(
    private val reservaRepository: ReservaRepository,
    private val profesionalRepository: ProfesionalRepository,
    private val resenaRepository: ResenaRepository,
    private val reporteProblemaRepository: ReporteProblemaRepository,
    private val firebaseAuth: FirebaseAuth,
) : ViewModel() {

    private val _state = MutableStateFlow(MisCitasState())
    val state: StateFlow<MisCitasState> = _state.asStateFlow()

    private var cargaJob: Job? = null

    init {
        cargarReservas()
    }

    fun onAction(action: MisCitasAction) {
        when (action) {
            is MisCitasAction.SeleccionarTab ->
                _state.update { it.copy(tabSeleccionado = action.tab) }
            MisCitasAction.Recargar -> if (cargaJob?.isActive != true) cargarReservas()
            // Dejar resena y reportar problema son navegacion: el Root las resuelve contra el NavGraph.
            is MisCitasAction.DejarResena,
            is MisCitasAction.ReportarProblema,
            -> Unit
            // Seguir en vivo/chat, reprogramar, ver pauta digital, agregar a
            // Google Calendar, ver preparacion, descargar boleta y chatear
            // con soporte: requieren backend de Pagos/Boletas y navegacion
            // hacia otras features, ninguno conectado todavia — se conectan
            // cuando la feature salga de esta fase (docs/TASKS.md).
            MisCitasAction.SeguirEnVivo,
            MisCitasAction.Reprogramar,
            MisCitasAction.VerPautaDigital,
            is MisCitasAction.AgregarAGoogleCalendar,
            is MisCitasAction.VerPreparacion,
            is MisCitasAction.DescargarBoleta,
            MisCitasAction.ChatearConSoporte,
            -> Unit
        }
    }

    private fun cargarReservas() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        cargaJob = viewModelScope.launch {
            _state.update { it.copy(cargando = true) }
            when (val resultado = reservaRepository.obtenerPorCliente(uid)) {
                is Result.Success -> aplicarReservas(uid, resultado.data)
                is Result.Error -> _state.update { it.copy(cargando = false) }
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

        val citaEnCurso = reservas.firstOrNull { it.estado == EstadoReserva.EN_CURSO }
            ?.aCitaEnCurso(profesionales, reportes)
        val proximasCitas = reservas
            .filter { it.estado == EstadoReserva.SOLICITADA || it.estado == EstadoReserva.CONFIRMADA }
            .map { it.aCitaProxima(profesionales, reportes) }
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
                citaEnCurso = citaEnCurso,
                proximasCitas = proximasCitas,
                historial = historial,
                canceladas = canceladas,
            )
        }
    }
}
