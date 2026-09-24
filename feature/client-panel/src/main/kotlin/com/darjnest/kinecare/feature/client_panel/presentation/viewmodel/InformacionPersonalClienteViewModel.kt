package com.darjnest.kinecare.feature.client_panel.presentation.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Datos personales editables del paciente en "Información Personal".
 * Modelo de presentacion reducido (no reemplaza `Usuario` de dominio;
 * cuando esta pantalla se conecte a Firestore, se precarga desde y se
 * escribe hacia el `Usuario` real). El RUT no es editable: es el
 * identificador de login (docs/DOMAIN.md).
 */
data class InformacionPersonalClienteState(
    val cargando: Boolean = false,
    val guardando: Boolean = false,
    val fotoUrl: String? = null,
    val nombre: String = "",
    val rut: String = "",
    val telefono: String = "",
    val correoContacto: String = "",
)

sealed interface InformacionPersonalClienteAction {
    data object VolverAtras : InformacionPersonalClienteAction
    data object CambiarFoto : InformacionPersonalClienteAction
    data class CambiarNombre(val valor: String) : InformacionPersonalClienteAction
    data class CambiarTelefono(val valor: String) : InformacionPersonalClienteAction
    data class CambiarCorreoContacto(val valor: String) : InformacionPersonalClienteAction
    data object GuardarCambios : InformacionPersonalClienteAction
}

@HiltViewModel
class InformacionPersonalClienteViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(InformacionPersonalClienteState())
    val state: StateFlow<InformacionPersonalClienteState> = _state.asStateFlow()

    fun onAction(action: InformacionPersonalClienteAction) {
        when (action) {
            is InformacionPersonalClienteAction.CambiarNombre ->
                _state.update { it.copy(nombre = action.valor) }
            is InformacionPersonalClienteAction.CambiarTelefono ->
                _state.update { it.copy(telefono = action.valor) }
            is InformacionPersonalClienteAction.CambiarCorreoContacto ->
                _state.update { it.copy(correoContacto = action.valor) }
            // Volver atras, cambiar la foto de perfil y guardar los cambios:
            // requieren navegacion y escritura sobre el `Usuario` real, sin
            // backend de perfil cliente conectado todavia — se conectan
            // cuando la feature salga de esta fase (docs/TASKS.md, Fase 2).
            // Volver atras y guardar cambios normalmente no llegan hasta
            // aca: `InformacionPersonalClienteRoot` las intercepta como
            // navegacion (ver ClientPanelNavGraph); se mantienen como no-op
            // aca solo para que el `when` siga exhaustivo.
            InformacionPersonalClienteAction.VolverAtras,
            InformacionPersonalClienteAction.CambiarFoto,
            InformacionPersonalClienteAction.GuardarCambios,
            -> Unit
        }
    }
}
