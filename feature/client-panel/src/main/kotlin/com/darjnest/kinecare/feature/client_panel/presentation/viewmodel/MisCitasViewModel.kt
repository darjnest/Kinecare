@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.client_panel.presentation.viewmodel

import com.darjnest.kinecare.core.common.data.repository.ProfesionalRepository
import com.darjnest.kinecare.core.common.data.repository.ReservaRepository
import com.darjnest.kinecare.core.common.domain.model.EstadoReserva
import com.darjnest.kinecare.core.common.domain.model.ModalidadServicio
import com.darjnest.kinecare.core.common.domain.model.Profesional
import com.darjnest.kinecare.core.common.domain.model.Reserva
import com.darjnest.kinecare.core.common.result.Result
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
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
)

/** Cita agendada en dias proximos, distinta de la cita de hoy. */
data class CitaProxima(
    val id: String,
    val modalidad: ModalidadServicio,
    val lugar: String,
    val fechaHoraTexto: String,
    val profesionalNombre: String,
    val tipoSesion: String,
    val precioTotal: Long,
)

/** Sesion completada, mostrada en "Historial Reciente & Reembolsos". */
data class CitaHistorial(
    val id: String,
    val fechaTexto: String,
    val modalidadTexto: String,
    val tituloSesion: String,
    val profesionalNombre: String,
    val calificacion: Int,
)

/** Cita cancelada por el cliente o el profesional. */
data class CitaCancelada(
    val id: String,
    val fechaHoraTexto: String,
    val profesionalNombre: String,
    val motivo: String?,
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
private fun Reserva.aCitaEnCurso(profesionales: Map<String, Profesional>): CitaEnCurso {
    val profesional = profesionales[profesionalId]
    val servicio = profesional?.servicios?.firstOrNull { it.id == servicioId }
    return CitaEnCurso(
        id = id,
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
    )
}

private fun Reserva.aCitaProxima(profesionales: Map<String, Profesional>): CitaProxima {
    val profesional = profesionales[profesionalId]
    val servicio = profesional?.servicios?.firstOrNull { it.id == servicioId }
    return CitaProxima(
        id = id,
        modalidad = modalidad,
        lugar = direccion?.let { d -> listOf(d.comuna, d.ciudad).filter { it.isNotBlank() }.joinToString(", ") }
            ?: modalidad.aTexto(),
        fechaHoraTexto = fechaHora.aFechaHoraTexto(),
        profesionalNombre = profesional?.usuario?.nombre ?: "Profesional",
        tipoSesion = servicio?.nombre ?: "Sesión",
        precioTotal = pago.monto,
    )
}

private fun Reserva.aCitaHistorial(profesionales: Map<String, Profesional>): CitaHistorial {
    val profesional = profesionales[profesionalId]
    val servicio = profesional?.servicios?.firstOrNull { it.id == servicioId }
    return CitaHistorial(
        id = id,
        fechaTexto = fechaHora.aFechaTexto(),
        modalidadTexto = modalidad.aTexto(),
        tituloSesion = servicio?.nombre ?: "Sesión",
        profesionalNombre = profesional?.usuario?.nombre ?: "Profesional",
        // Sin `Resena` conectada a esta pantalla todavia (`:feature:reviews`
        // sigue fuera de alcance, ver docs/TASKS.md): sin calificacion.
        calificacion = 0,
    )
}

private fun Reserva.aCitaCancelada(profesionales: Map<String, Profesional>): CitaCancelada {
    val profesional = profesionales[profesionalId]
    return CitaCancelada(
        id = id,
        fechaHoraTexto = fechaHora.aFechaHoraTexto(),
        profesionalNombre = profesional?.usuario?.nombre ?: "Profesional",
        motivo = when (estado) {
            EstadoReserva.CANCELADA_CLIENTE -> "Cancelada por ti"
            EstadoReserva.CANCELADA_PROFESIONAL -> "Cancelada por el profesional"
            EstadoReserva.RECHAZADA -> "Solicitud rechazada"
            else -> null
        },
    )
}

@HiltViewModel
class MisCitasViewModel @Inject constructor(
    private val reservaRepository: ReservaRepository,
    private val profesionalRepository: ProfesionalRepository,
    private val firebaseAuth: FirebaseAuth,
) : ViewModel() {

    private val _state = MutableStateFlow(MisCitasState())
    val state: StateFlow<MisCitasState> = _state.asStateFlow()

    init {
        cargarReservas()
    }

    fun onAction(action: MisCitasAction) {
        when (action) {
            is MisCitasAction.SeleccionarTab ->
                _state.update { it.copy(tabSeleccionado = action.tab) }
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
        viewModelScope.launch {
            _state.update { it.copy(cargando = true) }
            when (val resultado = reservaRepository.obtenerPorCliente(uid)) {
                is Result.Success -> aplicarReservas(resultado.data)
                is Result.Error -> _state.update { it.copy(cargando = false) }
            }
        }
    }

    private suspend fun aplicarReservas(reservas: List<Reserva>) {
        val profesionales = mutableMapOf<String, Profesional>()
        for (reserva in reservas) {
            if (reserva.profesionalId in profesionales) continue
            val resultado = profesionalRepository.obtenerPorId(reserva.profesionalId)
            if (resultado is Result.Success) profesionales[reserva.profesionalId] = resultado.data
        }

        val citaEnCurso = reservas.firstOrNull { it.estado == EstadoReserva.EN_CURSO }?.aCitaEnCurso(profesionales)
        val proximasCitas = reservas
            .filter { it.estado == EstadoReserva.SOLICITADA || it.estado == EstadoReserva.CONFIRMADA }
            .map { it.aCitaProxima(profesionales) }
        val historial = reservas
            .filter { it.estado == EstadoReserva.COMPLETADA }
            .map { it.aCitaHistorial(profesionales) }
        val canceladas = reservas
            .filter {
                it.estado == EstadoReserva.CANCELADA_CLIENTE ||
                    it.estado == EstadoReserva.CANCELADA_PROFESIONAL ||
                    it.estado == EstadoReserva.RECHAZADA
            }
            .map { it.aCitaCancelada(profesionales) }

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
