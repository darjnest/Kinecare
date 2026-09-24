package com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** Pestaña activa en la pantalla de Solicitudes de Atencion. */
enum class PestanaSolicitudes { PENDIENTES, HISTORIAL }

/** Nivel de urgencia con el que se destaca la solicitud pendiente. */
enum class NivelUrgenciaSolicitud { URGENTE, NORMAL }

/** Modalidad en la que se atenderia la solicitud. */
enum class ModalidadSolicitud { A_DOMICILIO, EN_CONSULTA }

/**
 * Solicitud de atencion pendiente de respuesta del profesional. Modelo de
 * presentacion (no reemplaza `Reserva`/`EstadoReserva` de dominio; cuando
 * esta pantalla se conecte a Firestore, se mapea desde la `Reserva` real).
 */
data class SolicitudAtencion(
    val id: String,
    val urgencia: NivelUrgenciaSolicitud,
    val tiempoRestanteTexto: String,
    val modalidad: ModalidadSolicitud,
    val modalidadTexto: String,
    val pacienteNombre: String,
    val calificacion: Double,
    val pacienteVerificadoTexto: String,
    val especialidad: String,
    val fechaHoraTexto: String,
    val tiempoRelativoTexto: String? = null,
    val direccion: String,
    val motivoConsulta: String,
    val honorarioClp: Long,
    val custodiaTexto: String,
    val avisoPagoTexto: String,
)

data class SolicitudesDeAtencionState(
    val cargando: Boolean = false,
    val pestanaSeleccionada: PestanaSolicitudes = PestanaSolicitudes.PENDIENTES,
    val plazoRespuestaTexto: String = "",
    val solicitudesPendientes: List<SolicitudAtencion> = emptyList(),
    val completadasEstaSemana: Int = 0,
    val porcentajeRespuestaATiempo: Int = 0,
)

sealed interface SolicitudesDeAtencionAction {
    data object VolverAtras : SolicitudesDeAtencionAction
    data class CambiarPestana(val pestana: PestanaSolicitudes) : SolicitudesDeAtencionAction
    data class AceptarSolicitud(val solicitudId: String) : SolicitudesDeAtencionAction
    data class RechazarSolicitud(val solicitudId: String) : SolicitudesDeAtencionAction
}

@HiltViewModel
class SolicitudesDeAtencionViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(SolicitudesDeAtencionState())
    val state: StateFlow<SolicitudesDeAtencionState> = _state.asStateFlow()

    fun onAction(action: SolicitudesDeAtencionAction) {
        when (action) {
            is SolicitudesDeAtencionAction.CambiarPestana ->
                _state.update { it.copy(pestanaSeleccionada = action.pestana) }

            // Volver atras es navegacion, no estado del ViewModel: el Root la
            // resuelve directo contra el NavGraph (ver
            // ProfessionalPanelNavGraph). Aceptar y rechazar una solicitud
            // requieren el repositorio de reservas del profesional en
            // Firestore (y su Cloud Function de liberacion de custodia de
            // pago), que todavia no esta conectado a esta pantalla
            // (docs/TASKS.md, Fase 7).
            SolicitudesDeAtencionAction.VolverAtras,
            is SolicitudesDeAtencionAction.AceptarSolicitud,
            is SolicitudesDeAtencionAction.RechazarSolicitud,
            -> Unit
        }
    }
}
