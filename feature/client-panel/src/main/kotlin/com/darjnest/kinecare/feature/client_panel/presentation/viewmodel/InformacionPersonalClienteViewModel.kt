package com.darjnest.kinecare.feature.client_panel.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darjnest.kinecare.core.common.data.repository.UsuarioRepository
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
 * Datos personales editables del paciente en "Información Personal".
 * Modelo de presentacion reducido (no reemplaza `Usuario` de dominio;
 * se precarga desde y se escribe hacia el `Usuario` real via
 * [UsuarioRepository]). El RUT no es editable: es el identificador de
 * login (docs/DOMAIN.md).
 */
data class InformacionPersonalClienteState(
    val cargando: Boolean = false,
    val guardando: Boolean = false,
    /**
     * Se pone en `true` cuando `GuardarCambios` termina de escribir en
     * `usuarios/{uid}` con exito: `InformacionPersonalClienteRoot` observa
     * este campo con `LaunchedEffect` para volver atras (mismo patron que
     * usa `AuthViewModel.usuarioAutenticado` con `AuthScreen`, no hay
     * `Flow<Event>` separado todavia en el proyecto).
     */
    val guardadoExitoso: Boolean = false,
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
class InformacionPersonalClienteViewModel @Inject constructor(
    private val usuarioRepository: UsuarioRepository,
    private val firebaseAuth: FirebaseAuth,
) : ViewModel() {

    private val _state = MutableStateFlow(InformacionPersonalClienteState())
    val state: StateFlow<InformacionPersonalClienteState> = _state.asStateFlow()

    init {
        cargarUsuario()
    }

    fun onAction(action: InformacionPersonalClienteAction) {
        when (action) {
            is InformacionPersonalClienteAction.CambiarNombre ->
                _state.update { it.copy(nombre = action.valor) }
            is InformacionPersonalClienteAction.CambiarTelefono ->
                _state.update { it.copy(telefono = action.valor) }
            is InformacionPersonalClienteAction.CambiarCorreoContacto ->
                _state.update { it.copy(correoContacto = action.valor) }
            InformacionPersonalClienteAction.GuardarCambios -> guardarCambios()
            // Volver atras y cambiar la foto de perfil: volver atras es
            // navegacion pura que el Root resuelve directo contra el
            // NavGraph (ver ClientPanelNavGraph); cambiar la foto sigue
            // bloqueado porque Storage no esta disponible (plan Spark, ver
            // docs/TASKS.md Fase 2) — ambos se mantienen como no-op aca
            // solo para que el `when` siga exhaustivo.
            InformacionPersonalClienteAction.VolverAtras,
            InformacionPersonalClienteAction.CambiarFoto,
            -> Unit
        }
    }

    private fun cargarUsuario() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        viewModelScope.launch {
            _state.update { it.copy(cargando = true) }
            when (val resultado = usuarioRepository.obtenerPorId(uid)) {
                is Result.Success -> _state.update {
                    val usuario = resultado.data
                    it.copy(
                        cargando = false,
                        fotoUrl = usuario.fotoUrl,
                        nombre = usuario.nombre,
                        rut = usuario.rut,
                        telefono = usuario.telefono.orEmpty(),
                        correoContacto = usuario.correoContacto.orEmpty(),
                    )
                }
                is Result.Error -> _state.update { it.copy(cargando = false) }
            }
        }
    }

    private fun guardarCambios() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        val actual = _state.value
        if (actual.guardando) return

        viewModelScope.launch {
            _state.update { it.copy(guardando = true) }
            val resultado = usuarioRepository.actualizarDatosPersonales(
                id = uid,
                nombre = actual.nombre,
                telefono = actual.telefono.ifBlank { null },
                correoContacto = actual.correoContacto.ifBlank { null },
            )
            when (resultado) {
                is Result.Success -> _state.update { it.copy(guardando = false, guardadoExitoso = true) }
                is Result.Error -> _state.update { it.copy(guardando = false) }
            }
        }
    }
}
