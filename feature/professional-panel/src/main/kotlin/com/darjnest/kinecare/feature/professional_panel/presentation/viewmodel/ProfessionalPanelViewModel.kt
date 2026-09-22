package com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Estado de verificacion de identidad/credenciales que se muestra en el
 * banner de la parte superior del panel. Modelo de presentacion reducido
 * (no reemplaza `Insignia`/`EstadoVerificacion` de dominio; cuando esta
 * pantalla se conecte a Firestore, se mapea desde el `Profesional` real).
 */
data class VerificacionPanel(
    val numeroSis: String,
    val entidad: String,
    val credencialesAlDia: Boolean,
)

/** Resumen de KPIs del dia mostrado en las 3 tarjetas superiores. */
data class ResumenHoy(
    val proximasCitas: Int,
    val citasHoyEnAgenda: Int,
    val porLiquidar: Long,
    val calificacion: Double,
    val totalResenas: Int,
    val actualizadoHaceTexto: String,
)

/** Siguiente cita agendada, destacada en su propia tarjeta con urgencia. */
data class ProximaCita(
    val pacienteNombre: String,
    val servicio: String,
    val minutosRestantes: Int,
    val modalidad: String,
    val horaTexto: String,
    val distanciaKm: Double,
    val comuna: String,
    val direccion: String,
    val piso: String,
)

/** Tono de color del icono/circulo de cada [AccesoGestion]. */
enum class ColorAcceso { AZUL, VERDE, GRIS, ROJO }

/**
 * Acceso rapido de la grilla "Gestion Profesional" (Mi Perfil, Servicios,
 * Disponibilidad, Solicitudes, Liquidaciones, Documentos).
 */
data class AccesoGestion(
    val id: String,
    val titulo: String,
    val subtitulo: String,
    val icono: ImageVector,
    val color: ColorAcceso,
    val badgeTexto: String? = null,
    val badgeNumero: Int? = null,
    val badgeCheck: Boolean = false,
    val subtituloEnAlerta: Boolean = false,
    val mostrarChevron: Boolean = false,
)

data class ProfessionalPanelState(
    val cargando: Boolean = false,
    val nombreProfesional: String = "",
    val disponible: Boolean = true,
    val verificacion: VerificacionPanel? = null,
    val resumenHoy: ResumenHoy? = null,
    val proximaCita: ProximaCita? = null,
    val accesosGestion: List<AccesoGestion> = emptyList(),
)

sealed interface ProfessionalPanelAction {
    data class CambiarDisponibilidad(val disponible: Boolean) : ProfessionalPanelAction
    data object VerFichaPaciente : ProfessionalPanelAction
    data object AbrirRuta : ProfessionalPanelAction
    data class SeleccionarAccesoGestion(val accesoId: String) : ProfessionalPanelAction
    data object AbrirConfiguracionCuenta : ProfessionalPanelAction
}

@HiltViewModel
class ProfessionalPanelViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(ProfessionalPanelState())
    val state: StateFlow<ProfessionalPanelState> = _state.asStateFlow()

    fun onAction(action: ProfessionalPanelAction) {
        when (action) {
            is ProfessionalPanelAction.CambiarDisponibilidad ->
                _state.update { it.copy(disponible = action.disponible) }
            // Ver ficha, ruta, accesos de gestion y configuracion: sin backend ni
            // navegacion todavia (no hay Firestore de reservas/servicios/ingresos
            // conectado a esta pantalla) — se conectan cuando la feature salga de
            // esta fase (docs/TASKS.md, Fase 7).
            ProfessionalPanelAction.VerFichaPaciente,
            ProfessionalPanelAction.AbrirRuta,
            is ProfessionalPanelAction.SeleccionarAccesoGestion,
            ProfessionalPanelAction.AbrirConfiguracionCuenta,
            -> Unit
        }
    }
}
