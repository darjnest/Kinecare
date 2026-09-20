package com.darjnest.kinecare.feature.auth.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darjnest.kinecare.core.common.domain.model.RolUsuario
import com.darjnest.kinecare.core.common.domain.model.Usuario
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.feature.auth.data.repository.AuthRepository
import com.darjnest.kinecare.feature.auth.domain.AuthError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ModoAuth { LOGIN, REGISTRO }

data class AuthState(
    val modo: ModoAuth = ModoAuth.LOGIN,
    val nombre: String = "",
    val email: String = "",
    val password: String = "",
    val rolSeleccionado: RolUsuario = RolUsuario.CLIENTE,
    val cargando: Boolean = false,
    val mensajeError: String? = null,
    val usuarioAutenticado: Usuario? = null,
) {
    val puedeEnviar: Boolean
        get() = email.isNotBlank() && password.isNotBlank() && (modo == ModoAuth.LOGIN || nombre.isNotBlank())
}

sealed interface AuthAction {
    data class CambiarModo(val modo: ModoAuth) : AuthAction
    data class CambiarNombre(val valor: String) : AuthAction
    data class CambiarEmail(val valor: String) : AuthAction
    data class CambiarPassword(val valor: String) : AuthAction
    data class SeleccionarRol(val rol: RolUsuario) : AuthAction
    data object Enviar : AuthAction
    data object CerrarSesion : AuthAction
}

private fun AuthError.aMensaje(): String = when (this) {
    AuthError.CREDENCIALES_INVALIDAS -> "Email o contraseña incorrectos."
    AuthError.USUARIO_NO_ENCONTRADO -> "No encontramos una cuenta con ese email."
    AuthError.EMAIL_YA_REGISTRADO -> "Ya existe una cuenta con ese email."
    AuthError.SIN_INTERNET -> "Sin conexión a internet. Intenta de nuevo."
    AuthError.DESCONOCIDO -> "Algo salió mal. Intenta de nuevo."
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.observarUsuarioActual().collect { usuario ->
                _state.value = _state.value.copy(usuarioAutenticado = usuario)
            }
        }
    }

    fun onAction(action: AuthAction) {
        when (action) {
            is AuthAction.CambiarModo -> _state.value = _state.value.copy(modo = action.modo, mensajeError = null)
            is AuthAction.CambiarNombre -> _state.value = _state.value.copy(nombre = action.valor)
            is AuthAction.CambiarEmail -> _state.value = _state.value.copy(email = action.valor)
            is AuthAction.CambiarPassword -> _state.value = _state.value.copy(password = action.valor)
            is AuthAction.SeleccionarRol -> _state.value = _state.value.copy(rolSeleccionado = action.rol)
            AuthAction.Enviar -> enviar()
            AuthAction.CerrarSesion -> viewModelScope.launch { authRepository.cerrarSesion() }
        }
    }

    private fun enviar() {
        val actual = _state.value
        if (!actual.puedeEnviar || actual.cargando) return

        viewModelScope.launch {
            _state.value = actual.copy(cargando = true, mensajeError = null)

            val resultado = if (actual.modo == ModoAuth.LOGIN) {
                authRepository.iniciarSesion(actual.email, actual.password)
            } else {
                authRepository.registrar(actual.nombre, actual.email, actual.password, actual.rolSeleccionado)
            }

            when (resultado) {
                is Result.Success -> _state.value = _state.value.copy(
                    cargando = false,
                    usuarioAutenticado = resultado.data,
                )
                is Result.Error -> _state.value = _state.value.copy(
                    cargando = false,
                    mensajeError = resultado.error.aMensaje(),
                )
            }
        }
    }
}
