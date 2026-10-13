package com.darjnest.kinecare.feature.client_panel.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darjnest.kinecare.core.common.data.error.FotoPerfilError
import com.darjnest.kinecare.core.common.data.repository.FotoPerfilRepository
import com.darjnest.kinecare.core.common.data.repository.UsuarioRepository
import com.darjnest.kinecare.core.common.domain.model.RolUsuario
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
    /** Subiendo la foto elegida (se muestra progreso sobre el avatar). */
    val subiendoFoto: Boolean = false,
    /** Mensaje del ultimo intento fallido de cambiar la foto; `null` si no hay. */
    val errorFoto: String? = null,
    val nombre: String = "",
    val rut: String = "",
    val telefono: String = "",
    val correoContacto: String = "",
    /** Mensaje de la ultima carga o guardado fallido; `null` si no hay error que mostrar. */
    val error: String? = null,
    /** `true` si no se pudieron cargar los datos: no se debe guardar (se pisarian con campos vacios). */
    val cargaFallida: Boolean = false,
) {
    /** "Guardar Cambios" solo con los datos cargados, sin operacion en curso y con nombre. */
    val puedeGuardar: Boolean
        get() = !cargando && !guardando && !cargaFallida && nombre.isNotBlank()
}

sealed interface InformacionPersonalClienteAction {
    data object VolverAtras : InformacionPersonalClienteAction

    /** Abrir el selector de fotos: lo resuelve el `Root` (necesita el contexto de la Activity). */
    data object CambiarFoto : InformacionPersonalClienteAction

    /** El usuario eligio una imagen en el selector ([uri] es un `content://`). */
    data class FotoElegida(val uri: String) : InformacionPersonalClienteAction
    data class CambiarNombre(val valor: String) : InformacionPersonalClienteAction
    data class CambiarTelefono(val valor: String) : InformacionPersonalClienteAction
    data class CambiarCorreoContacto(val valor: String) : InformacionPersonalClienteAction
    data object GuardarCambios : InformacionPersonalClienteAction

    /** "Reintentar" cuando fallo la carga de los datos. */
    data object Reintentar : InformacionPersonalClienteAction
}

@HiltViewModel
class InformacionPersonalClienteViewModel @Inject constructor(
    private val usuarioRepository: UsuarioRepository,
    private val fotoPerfilRepository: FotoPerfilRepository,
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
            InformacionPersonalClienteAction.Reintentar ->
                if (!_state.value.cargando && _state.value.cargaFallida) cargarUsuario()
            is InformacionPersonalClienteAction.FotoElegida -> subirFoto(action.uri)
            // Volver atras es navegacion pura que el Root resuelve directo
            // contra el NavGraph (ver ClientPanelNavGraph) y abrir el
            // selector de fotos tambien es del Root (necesita la Activity):
            // ambos se mantienen como no-op aca solo para que el `when`
            // siga exhaustivo.
            InformacionPersonalClienteAction.VolverAtras,
            InformacionPersonalClienteAction.CambiarFoto,
            -> Unit
        }
    }

    private fun cargarUsuario() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        viewModelScope.launch {
            _state.update { it.copy(cargando = true, error = null, cargaFallida = false) }
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
                is Result.Error -> _state.update {
                    it.copy(cargando = false, cargaFallida = true, error = "No pudimos cargar tus datos")
                }
            }
        }
    }

    private fun subirFoto(uri: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        if (_state.value.subiendoFoto) return
        viewModelScope.launch {
            _state.update { it.copy(subiendoFoto = true, errorFoto = null) }
            when (val subida = fotoPerfilRepository.subir(uid, RolUsuario.CLIENTE, uri)) {
                is Result.Error -> _state.update {
                    it.copy(subiendoFoto = false, errorFoto = subida.error.aMensaje())
                }
                is Result.Success -> when (usuarioRepository.actualizarFotoUrl(uid, subida.data)) {
                    is Result.Success -> _state.update { it.copy(subiendoFoto = false, fotoUrl = subida.data) }
                    is Result.Error -> _state.update {
                        it.copy(subiendoFoto = false, errorFoto = "No pudimos guardar la foto en tu perfil. Intenta de nuevo.")
                    }
                }
            }
        }
    }

    private fun guardarCambios() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        val actual = _state.value
        if (!actual.puedeGuardar) return

        viewModelScope.launch {
            _state.update { it.copy(guardando = true, error = null) }
            val resultado = usuarioRepository.actualizarDatosPersonales(
                id = uid,
                nombre = actual.nombre,
                telefono = actual.telefono.ifBlank { null },
                correoContacto = actual.correoContacto.ifBlank { null },
            )
            when (resultado) {
                is Result.Success -> _state.update { it.copy(guardando = false, guardadoExitoso = true) }
                is Result.Error -> _state.update {
                    it.copy(guardando = false, error = "No pudimos guardar los cambios")
                }
            }
        }
    }
}

private fun FotoPerfilError.aMensaje(): String = when (this) {
    FotoPerfilError.SIN_INTERNET -> "Sin conexión. Revisa tu internet e intenta de nuevo."
    FotoPerfilError.SIN_PERMISO -> "No pudimos autorizar el cambio de foto. Cierra sesión, vuelve a entrar e intenta de nuevo."
    FotoPerfilError.IMAGEN_INVALIDA -> "No pudimos leer esa imagen. Elige otra foto."
    FotoPerfilError.IMAGEN_MUY_GRANDE -> "La foto es demasiado pesada. Elige otra más liviana."
    FotoPerfilError.ALMACENAMIENTO_NO_DISPONIBLE -> "Cambiar la foto de perfil aún no está disponible. Estamos trabajando en ello."
    FotoPerfilError.DESCONOCIDO -> "No pudimos subir la foto. Intenta de nuevo."
}
