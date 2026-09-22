package com.darjnest.kinecare.feature.auth.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darjnest.kinecare.core.common.domain.model.RolUsuario
import com.darjnest.kinecare.core.common.domain.model.Usuario
import com.darjnest.kinecare.core.common.domain.util.RutUtils
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.feature.auth.data.local.LoginPreferences
import com.darjnest.kinecare.feature.auth.data.repository.AuthRepository
import com.darjnest.kinecare.feature.auth.domain.AuthError
import com.darjnest.kinecare.feature.auth.domain.ResultadoGoogle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ModoAuth { LOGIN, REGISTRO }

/** Datos de Google pendientes de RUT/teléfono/rol antes de crear `usuarios/{uid}`. */
data class PerfilGooglePendiente(
    val uid: String,
    val correoGoogle: String,
    val fotoUrl: String?,
)

data class AuthState(
    val modo: ModoAuth = ModoAuth.LOGIN,
    val nombre: String = "",
    val rut: String = "",
    val telefono: String = "",
    val correo: String = "",
    val password: String = "",
    val aceptaTerminos: Boolean = false,
    val rolSeleccionado: RolUsuario = RolUsuario.CLIENTE,
    val recordarCuenta: Boolean = true,
    val cargando: Boolean = false,
    val mensajeError: String? = null,
    val usuarioAutenticado: Usuario? = null,
    val perfilGooglePendiente: PerfilGooglePendiente? = null,
) {
    val rutInvalido: Boolean
        get() = rut.isNotBlank() && !RutUtils.esValido(rut)

    val passwordDebil: Boolean
        get() = modo == ModoAuth.REGISTRO && password.isNotEmpty() && password.length < LARGO_MINIMO_PASSWORD

    val puedeEnviar: Boolean
        get() = RutUtils.esValido(rut) && password.isNotBlank() && (
            modo == ModoAuth.LOGIN ||
                (
                    nombre.isNotBlank() &&
                        telefono.isNotBlank() &&
                        correo.isNotBlank() &&
                        aceptaTerminos &&
                        password.length >= LARGO_MINIMO_PASSWORD
                    )
            )

    val puedeConfirmarPerfilGoogle: Boolean
        get() = RutUtils.esValido(rut) && nombre.isNotBlank() && telefono.isNotBlank() && aceptaTerminos

    private companion object {
        const val LARGO_MINIMO_PASSWORD = 6
    }
}

sealed interface AuthAction {
    data class CambiarModo(val modo: ModoAuth) : AuthAction
    data class CambiarNombre(val valor: String) : AuthAction
    data class CambiarRut(val valor: String) : AuthAction
    data class CambiarTelefono(val valor: String) : AuthAction
    data class CambiarCorreo(val valor: String) : AuthAction
    data class CambiarPassword(val valor: String) : AuthAction
    data class CambiarAceptaTerminos(val valor: Boolean) : AuthAction
    data class SeleccionarRol(val rol: RolUsuario) : AuthAction
    data class CambiarRecordarCuenta(val valor: Boolean) : AuthAction
    data object Enviar : AuthAction
    data object CerrarSesion : AuthAction
    data object IniciandoGoogle : AuthAction
    data class IniciarSesionConGoogle(val idToken: String) : AuthAction
    data object ConfirmarPerfilGoogle : AuthAction
    data object CancelarPerfilGoogle : AuthAction
    data class ErrorGenerico(val mensaje: String?) : AuthAction
}

private fun AuthError.aMensaje(): String = when (this) {
    AuthError.CREDENCIALES_INVALIDAS -> "RUT o contraseña incorrectos."
    AuthError.USUARIO_NO_ENCONTRADO -> "No encontramos una cuenta con ese RUT."
    AuthError.RUT_YA_REGISTRADO -> "Ya existe una cuenta con ese RUT."
    AuthError.PASSWORD_DEBIL -> "La contraseña debe tener al menos 6 caracteres."
    AuthError.SIN_INTERNET -> "Sin conexión a internet. Intenta de nuevo."
    AuthError.DESCONOCIDO -> "Algo salió mal. Intenta de nuevo."
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val loginPreferences: LoginPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val recordarCuenta = loginPreferences.recordarCuenta()
            val rutRecordado = if (recordarCuenta) loginPreferences.rutRecordado() else ""
            _state.value = _state.value.copy(recordarCuenta = recordarCuenta, rut = rutRecordado)
        }
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
            is AuthAction.CambiarRut -> _state.value = _state.value.copy(rut = action.valor)
            is AuthAction.CambiarTelefono -> _state.value = _state.value.copy(telefono = action.valor)
            is AuthAction.CambiarCorreo -> _state.value = _state.value.copy(correo = action.valor)
            is AuthAction.CambiarPassword -> _state.value = _state.value.copy(password = action.valor)
            is AuthAction.CambiarAceptaTerminos -> _state.value = _state.value.copy(aceptaTerminos = action.valor)
            is AuthAction.SeleccionarRol -> _state.value = _state.value.copy(rolSeleccionado = action.rol)
            is AuthAction.CambiarRecordarCuenta -> _state.value = _state.value.copy(recordarCuenta = action.valor)
            AuthAction.Enviar -> enviar()
            AuthAction.CerrarSesion -> viewModelScope.launch { authRepository.cerrarSesion() }
            AuthAction.IniciandoGoogle -> _state.value = _state.value.copy(cargando = true, mensajeError = null)
            is AuthAction.IniciarSesionConGoogle -> iniciarSesionConGoogle(action.idToken)
            AuthAction.ConfirmarPerfilGoogle -> confirmarPerfilGoogle()
            AuthAction.CancelarPerfilGoogle -> cancelarPerfilGoogle()
            is AuthAction.ErrorGenerico -> _state.value = _state.value.copy(cargando = false, mensajeError = action.mensaje)
        }
    }

    private fun iniciarSesionConGoogle(idToken: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(cargando = true, mensajeError = null)
            when (val resultado = authRepository.iniciarSesionConGoogle(idToken)) {
                is Result.Success -> when (val datos = resultado.data) {
                    is ResultadoGoogle.SesionIniciada -> {
                        _state.value = _state.value.copy(cargando = false, usuarioAutenticado = datos.usuario)
                    }
                    is ResultadoGoogle.RequiereCompletarPerfil -> {
                        _state.value = AuthState(
                            modo = ModoAuth.REGISTRO,
                            nombre = datos.nombreSugerido,
                            perfilGooglePendiente = PerfilGooglePendiente(
                                uid = datos.uid,
                                correoGoogle = datos.correoGoogle,
                                fotoUrl = datos.fotoUrl,
                            ),
                        )
                    }
                }
                is Result.Error -> _state.value = _state.value.copy(cargando = false, mensajeError = resultado.error.aMensaje())
            }
        }
    }

    private fun confirmarPerfilGoogle() {
        val actual = _state.value
        val pendiente = actual.perfilGooglePendiente ?: return
        if (!actual.puedeConfirmarPerfilGoogle || actual.cargando) return

        viewModelScope.launch {
            _state.value = actual.copy(cargando = true, mensajeError = null)
            val resultado = authRepository.completarRegistroGoogle(
                uid = pendiente.uid,
                nombre = actual.nombre,
                rut = actual.rut,
                telefono = actual.telefono,
                correoContacto = pendiente.correoGoogle,
                rol = actual.rolSeleccionado,
                fotoUrl = pendiente.fotoUrl,
            )
            when (resultado) {
                is Result.Success -> _state.value = _state.value.copy(
                    cargando = false,
                    usuarioAutenticado = resultado.data,
                    perfilGooglePendiente = null,
                )
                is Result.Error -> _state.value = _state.value.copy(
                    cargando = false,
                    mensajeError = resultado.error.aMensaje(),
                )
            }
        }
    }

    private fun cancelarPerfilGoogle() {
        viewModelScope.launch {
            authRepository.cerrarSesion()
            _state.value = AuthState()
        }
    }

    private fun enviar() {
        val actual = _state.value
        if (!actual.puedeEnviar || actual.cargando) return

        viewModelScope.launch {
            _state.value = actual.copy(cargando = true, mensajeError = null)

            val resultado = if (actual.modo == ModoAuth.LOGIN) {
                authRepository.iniciarSesion(actual.rut, actual.password)
            } else {
                authRepository.registrar(
                    actual.nombre,
                    actual.rut,
                    actual.password,
                    actual.rolSeleccionado,
                    actual.telefono,
                    actual.correo,
                )
            }

            when (resultado) {
                is Result.Success -> {
                    if (actual.modo == ModoAuth.LOGIN) {
                        loginPreferences.guardar(actual.recordarCuenta, actual.rut)
                        _state.value = _state.value.copy(
                            cargando = false,
                            usuarioAutenticado = resultado.data,
                        )
                    } else {
                        // El registro no deja la sesión iniciada: vuelve al login con el RUT
                        // ya cargado para que el usuario inicie sesión explícitamente.
                        _state.value = AuthState(
                            modo = ModoAuth.LOGIN,
                            rut = actual.rut,
                            recordarCuenta = actual.recordarCuenta,
                        )
                    }
                }
                is Result.Error -> _state.value = _state.value.copy(
                    cargando = false,
                    mensajeError = resultado.error.aMensaje(),
                )
            }
        }
    }
}
