package com.darjnest.kinecare.feature.client_panel.presentation.viewmodel

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darjnest.kinecare.core.common.data.repository.ClienteRepository
import com.darjnest.kinecare.core.common.data.repository.UsuarioRepository
import com.darjnest.kinecare.core.common.domain.model.Direccion
import com.darjnest.kinecare.core.common.result.Result
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Identidad basica del paciente mostrada en la cabecera de "Mi Perfil".
 * Modelo de presentacion reducido (no reemplaza `Cliente`/`Usuario` de
 * dominio; se mapea desde el `Cliente`/`Usuario` real).
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

/** Iniciales (hasta 2) a partir del nombre completo, para el avatar circular. */
private fun inicialesDeNombre(nombre: String): String =
    nombre.trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")

/**
 * Mapea `Cliente.direcciones` (sin id/etiqueta/flag de predeterminada en el
 * dominio, ver docs/DOMAIN.md) al modelo de presentacion que la tarjeta de
 * direcciones necesita: se trata la primera direccion de la lista como la
 * predeterminada (misma convencion que usa `ClienteRepositoryImpl` al
 * anteponer la direccion principal en el array de Firestore) y se deriva un
 * id/etiqueta estables por indice, sin agregar campos nuevos al dominio.
 */
private fun List<Direccion>.aDireccionesCliente(): List<DireccionCliente> =
    mapIndexed { index, direccion ->
        DireccionCliente(
            id = "direccion-$index",
            etiqueta = if (index == 0) "Dirección principal" else "Dirección ${index + 1}",
            predeterminada = index == 0,
            calle = listOf(direccion.calle, direccion.numero).filter { it.isNotBlank() }.joinToString(" "),
            comunaRegion = listOf(direccion.comuna, direccion.ciudad).filter { it.isNotBlank() }.joinToString(", "),
            esOficina = false,
        )
    }

@HiltViewModel
class MiPerfilClienteViewModel @Inject constructor(
    private val usuarioRepository: UsuarioRepository,
    private val clienteRepository: ClienteRepository,
    private val firebaseAuth: FirebaseAuth,
) : ViewModel() {

    private val _state = MutableStateFlow(MiPerfilClienteState())
    val state: StateFlow<MiPerfilClienteState> = _state.asStateFlow()

    init {
        cargarPerfil()
    }

    fun onAction(action: MiPerfilClienteAction) {
        when (action) {
            is MiPerfilClienteAction.CambiarAvisosWhatsApp ->
                _state.update { it.copy(ajustes = it.ajustes.copy(avisosWhatsApp = action.activo)) }
            is MiPerfilClienteAction.CambiarIngresoBiometrico ->
                _state.update { it.copy(ajustes = it.ajustes.copy(ingresoBiometrico = action.activo)) }
            // Editar facturacion, gestionar prevision y boletas SII, ver
            // pauta activa/historial de evoluciones, agregar una direccion
            // y ver los derechos del paciente: requieren escritura sobre el
            // `Cliente` real y navegacion hacia otras features, ninguno
            // conectado todavia (sin modelo de producto/tratamiento clinico
            // en Firestore) — se conectan cuando la feature salga de esta
            // fase (docs/TASKS.md). Cerrar sesion no es una accion de este
            // ViewModel: `MiPerfilClienteRoot` invoca directo el
            // `onCerrarSesion` real recibido de `:app`. Editar informacion
            // personal tampoco llega normalmente hasta aca:
            // `MiPerfilClienteRoot` la intercepta como navegacion hacia
            // `InformacionPersonalClienteRoute`; se mantiene como no-op aca
            // solo para que el `when` siga exhaustivo.
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

    private fun cargarPerfil() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        viewModelScope.launch {
            _state.update { it.copy(cargando = true) }

            val usuario = when (val resultado = usuarioRepository.obtenerPorId(uid)) {
                is Result.Success -> resultado.data
                is Result.Error -> null
            }
            if (usuario == null) {
                _state.update { it.copy(cargando = false) }
                return@launch
            }

            val direcciones = when (val resultado = clienteRepository.obtenerPorId(uid)) {
                is Result.Success -> resultado.data.direcciones
                is Result.Error -> emptyList()
            }

            _state.update {
                it.copy(
                    cargando = false,
                    resumen = PacienteResumen(
                        nombreCompleto = usuario.nombre,
                        iniciales = inicialesDeNombre(usuario.nombre),
                        rut = usuario.rut,
                        ciudad = direcciones.firstOrNull()?.ciudad.orEmpty(),
                        // No existe todavia un dato de verificacion ClaveUnica
                        // en Firestore (fuera de alcance de esta fase, ver
                        // docs/TASKS.md) — se fija en `false` hasta que el
                        // backend de identidad lo resuelva.
                        verificadoClaveUnica = false,
                    ),
                    direcciones = direcciones.aDireccionesCliente(),
                )
            }
        }
    }
}
