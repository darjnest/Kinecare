package com.darjnest.kinecare.feature.client_panel.presentation.viewmodel

import com.darjnest.kinecare.core.common.domain.model.ModalidadServicio
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** Pestana segmentada seleccionada en "Mis Citas". */
enum class TabMisCitas { PROXIMAS, HISTORIAL, CANCELADAS }

/**
 * Cita en curso u hoy, mostrada en la tarjeta hero. Modelo de presentacion
 * reducido (no reemplaza `Reserva` de dominio; cuando esta pantalla se
 * conecte a Firestore, se mapea desde la `Reserva` real en estado
 * `CONFIRMADA`/`EN_CURSO`).
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

@HiltViewModel
class MisCitasViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(MisCitasState())
    val state: StateFlow<MisCitasState> = _state.asStateFlow()

    fun onAction(action: MisCitasAction) {
        when (action) {
            is MisCitasAction.SeleccionarTab ->
                _state.update { it.copy(tabSeleccionado = action.tab) }
            // Seguir en vivo/chat, reprogramar, ver pauta digital, agregar a
            // Google Calendar, ver preparacion, descargar boleta y chatear
            // con soporte: requieren backend de Reservas/Pagos y
            // navegacion hacia otras features, ninguno conectado todavia —
            // se conectan cuando la feature salga de esta fase
            // (docs/TASKS.md).
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
}
