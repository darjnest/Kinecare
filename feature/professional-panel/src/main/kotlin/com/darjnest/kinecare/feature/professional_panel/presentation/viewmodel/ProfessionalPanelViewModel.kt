package com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Work
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

private val accesosDeEjemplo = listOf(
    AccesoGestion(
        id = "mi-perfil",
        titulo = "Mi Perfil",
        subtitulo = "Foto, bio y títulos",
        icono = Icons.Filled.Work,
        color = ColorAcceso.AZUL,
        mostrarChevron = true,
    ),
    AccesoGestion(
        id = "servicios-tarifas",
        titulo = "Servicios y Tarifas",
        subtitulo = "4 terapias activas",
        icono = Icons.Filled.Sell,
        color = ColorAcceso.VERDE,
        badgeTexto = "CLP $25k",
    ),
    AccesoGestion(
        id = "disponibilidad",
        titulo = "Disponibilidad",
        subtitulo = "Zonas y bloques",
        icono = Icons.Filled.CalendarMonth,
        color = ColorAcceso.GRIS,
        mostrarChevron = true,
    ),
    AccesoGestion(
        id = "solicitudes",
        titulo = "Solicitudes",
        subtitulo = "2 pendientes",
        icono = Icons.Filled.NotificationsActive,
        color = ColorAcceso.ROJO,
        badgeNumero = 2,
        subtituloEnAlerta = true,
    ),
    AccesoGestion(
        id = "liquidaciones",
        titulo = "Liquidaciones",
        subtitulo = "Cuenta bancaria",
        icono = Icons.Filled.AccountBalanceWallet,
        color = ColorAcceso.VERDE,
    ),
    AccesoGestion(
        id = "documentos",
        titulo = "Documentos",
        subtitulo = "SIS y Biometría",
        icono = Icons.Filled.Folder,
        color = ColorAcceso.AZUL,
        badgeCheck = true,
    ),
)

data class ProfessionalPanelState(
    val cargando: Boolean = false,
    val nombreProfesional: String = "Camila",
    val disponible: Boolean = true,
    val verificacion: VerificacionPanel = VerificacionPanel(
        numeroSis = "481923",
        entidad = "Superintendencia de Salud",
        credencialesAlDia = true,
    ),
    val resumenHoy: ResumenHoy = ResumenHoy(
        proximasCitas = 3,
        citasHoyEnAgenda = 2,
        porLiquidar = 320_000,
        calificacion = 4.9,
        totalResenas = 28,
        actualizadoHaceTexto = "Actualizado hace 5m",
    ),
    val proximaCita: ProximaCita = ProximaCita(
        pacienteNombre = "Matías Morales",
        servicio = "Kinesiología Deportiva y Reintegro",
        minutosRestantes = 45,
        modalidad = "A domicilio",
        horaTexto = "Hoy, 15:30 hrs",
        distanciaKm = 3.2,
        comuna = "Providencia",
        direccion = "Av. Providencia 1200, Depto 301",
        piso = "PISO 3",
    ),
    val accesosGestion: List<AccesoGestion> = accesosDeEjemplo,
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
