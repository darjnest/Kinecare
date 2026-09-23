package com.darjnest.kinecare.feature.client_panel.presentation.viewmodel

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Identidad basica del paciente mostrada en la cabecera de "Mi Perfil".
 * Modelo de presentacion reducido (no reemplaza `Cliente`/`Usuario` de
 * dominio; cuando esta pantalla se conecte a Firestore, se mapea desde el
 * `Cliente` real).
 */
data class PacienteResumen(
    val nombreCompleto: String,
    val iniciales: String,
    val rut: String,
    val ciudad: String,
    val verificadoClaveUnica: Boolean,
)

/** Metrica numerica mostrada en la fila de 3 columnas del perfil. */
data class MetricaPaciente(
    val valor: String,
    val etiqueta: String,
    val icono: ImageVector,
)

/** Prevision de salud y seguro complementario del paciente. */
data class PrevisionSalud(
    val nombrePrevision: String,
    val sincronizada: Boolean,
    val integracionConectadaTexto: String? = null,
    val nombreSeguroComplementario: String? = null,
    val numeroPoliza: String? = null,
)

/** Tratamiento clinico activo derivado por orden medica. */
data class TratamientoClinico(
    val ordenMedicaVigente: Boolean,
    val diasParaVencer: Int?,
    val titulo: String,
    val doctorDerivante: String,
    val institucion: String,
    val sesionesCompletadas: Int,
    val sesionesTotales: Int,
)

/** Direccion registrada para atenciones a domicilio. */
data class DireccionCliente(
    val id: String,
    val etiqueta: String,
    val predeterminada: Boolean,
    val calle: String,
    val comunaRegion: String,
    val esOficina: Boolean = false,
)

/** Medio de pago guardado en la pasarela (Transbank Oneclick u otro). */
data class MetodoPagoGuardado(
    val ultimosDigitos: String,
    val descripcion: String,
    val verificado: Boolean,
)

/** Datos de facturacion electronica del paciente. */
data class FacturacionCliente(
    val rut: String,
    val email: String,
)

data class AjustesSeguridad(
    val avisosWhatsApp: Boolean = false,
    val ingresoBiometrico: Boolean = false,
)

data class MiPerfilClienteState(
    val cargando: Boolean = false,
    val resumen: PacienteResumen? = null,
    val metricas: List<MetricaPaciente> = emptyList(),
    val prevision: PrevisionSalud? = null,
    val tratamiento: TratamientoClinico? = null,
    val direcciones: List<DireccionCliente> = emptyList(),
    val metodoPago: MetodoPagoGuardado? = null,
    val facturacion: FacturacionCliente? = null,
    val ajustes: AjustesSeguridad = AjustesSeguridad(),
)

sealed interface MiPerfilClienteAction {
    data object EditarInformacionPersonal : MiPerfilClienteAction
    data object GestionarPrevisionYBoletas : MiPerfilClienteAction
    data object VerPautaActivaEnCasa : MiPerfilClienteAction
    data object VerHistorialDeEvoluciones : MiPerfilClienteAction
    data object AgregarNuevaDireccion : MiPerfilClienteAction
    data object EditarFacturacion : MiPerfilClienteAction
    data class CambiarAvisosWhatsApp(val activo: Boolean) : MiPerfilClienteAction
    data class CambiarIngresoBiometrico(val activo: Boolean) : MiPerfilClienteAction
    data object VerDerechosDelPaciente : MiPerfilClienteAction
}

@HiltViewModel
class MiPerfilClienteViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(MiPerfilClienteState())
    val state: StateFlow<MiPerfilClienteState> = _state.asStateFlow()

    fun onAction(action: MiPerfilClienteAction) {
        when (action) {
            is MiPerfilClienteAction.CambiarAvisosWhatsApp ->
                _state.update { it.copy(ajustes = it.ajustes.copy(avisosWhatsApp = action.activo)) }
            is MiPerfilClienteAction.CambiarIngresoBiometrico ->
                _state.update { it.copy(ajustes = it.ajustes.copy(ingresoBiometrico = action.activo)) }
            // Editar informacion personal/facturacion, gestionar prevision y
            // boletas SII, ver pauta activa/historial de evoluciones,
            // agregar una direccion y ver los derechos del paciente:
            // requieren escritura sobre el `Cliente` real y navegacion hacia
            // otras features, ninguno conectado todavia (no hay Firestore de
            // perfil cliente conectado a esta pantalla) — se conectan
            // cuando la feature salga de esta fase (docs/TASKS.md). Cerrar
            // sesion no es una accion de este ViewModel: `MiPerfilClienteRoot`
            // invoca directo el `onCerrarSesion` real recibido de `:app`.
            MiPerfilClienteAction.EditarInformacionPersonal,
            MiPerfilClienteAction.GestionarPrevisionYBoletas,
            MiPerfilClienteAction.VerPautaActivaEnCasa,
            MiPerfilClienteAction.VerHistorialDeEvoluciones,
            MiPerfilClienteAction.AgregarNuevaDireccion,
            MiPerfilClienteAction.EditarFacturacion,
            MiPerfilClienteAction.VerDerechosDelPaciente,
            -> Unit
        }
    }
}
