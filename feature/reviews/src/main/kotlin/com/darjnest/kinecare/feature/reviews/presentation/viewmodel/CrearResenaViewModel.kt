@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.reviews.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darjnest.kinecare.core.common.data.error.ResenaError
import com.darjnest.kinecare.core.common.data.repository.ResenaRepository
import com.darjnest.kinecare.core.common.domain.model.Resena
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.feature.reviews.presentation.navigation.ARG_PROFESIONAL_ID
import com.darjnest.kinecare.feature.reviews.presentation.navigation.ARG_RESERVA_ID
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Clock

/** Largo maximo del comentario (lo aplica el ViewModel y `firestore.rules`). */
const val LARGO_MAXIMO_COMENTARIO = 500

const val CALIFICACION_MINIMA = 1
const val CALIFICACION_MAXIMA = 5

/** Por que fallo el envio; cada valor tiene su propio mensaje en la vista. */
enum class ErrorEnvioResena {
    /** No hay usuario autenticado. */
    SIN_SESION,
    SIN_INTERNET,

    /** Reserva no completada, ajena o ya resenada (PERMISSION_DENIED). */
    SIN_PERMISO,
    DESCONOCIDO,
}

/** Resena que el cliente ya escribio para esta reserva (se muestra de solo lectura). */
data class ResenaPropia(
    val calificacion: Int,
    val comentario: String?,
)

data class CrearResenaState(
    /** Comprobando con `obtenerPorReserva` si ya existe una resena. */
    val verificando: Boolean = true,
    /** Fallo la comprobacion inicial: no se muestra el formulario hasta poder reintentar. */
    val errorVerificacion: ResenaError? = null,
    /** No nulo si la reserva ya tiene resena: la vista muestra "Ya reseñaste esta atención" en vez del formulario. */
    val resenaExistente: ResenaPropia? = null,
    val calificacion: Int = 0,
    val comentario: String = "",
    val enviando: Boolean = false,
    val error: ErrorEnvioResena? = null,
    /** La resena se guardo: la vista navega hacia atras (mismo patron que `guardadoExitoso`). */
    val enviada: Boolean = false,
) {
    val puedeEnviar: Boolean
        get() = calificacion >= CALIFICACION_MINIMA && !enviando && !verificando &&
            errorVerificacion == null && resenaExistente == null && !enviada
}

sealed interface CrearResenaAction {
    data class SeleccionarCalificacion(val calificacion: Int) : CrearResenaAction
    data class CambiarComentario(val texto: String) : CrearResenaAction
    data object Enviar : CrearResenaAction
    data object Reintentar : CrearResenaAction

    /** Navegacion: la resuelve el `Root` contra el NavGraph, no el ViewModel. */
    data object VolverAtras : CrearResenaAction
}

@HiltViewModel
class CrearResenaViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val resenaRepository: ResenaRepository,
    private val firebaseAuth: FirebaseAuth,
) : ViewModel() {

    private val reservaId: String? = savedStateHandle[ARG_RESERVA_ID]
    private val profesionalId: String? = savedStateHandle[ARG_PROFESIONAL_ID]

    private val _state = MutableStateFlow(CrearResenaState())
    val state: StateFlow<CrearResenaState> = _state.asStateFlow()

    init {
        verificarResenaExistente()
    }

    fun onAction(action: CrearResenaAction) {
        when (action) {
            is CrearResenaAction.SeleccionarCalificacion -> {
                if (action.calificacion !in CALIFICACION_MINIMA..CALIFICACION_MAXIMA) return
                _state.update { it.copy(calificacion = action.calificacion, error = null) }
            }
            is CrearResenaAction.CambiarComentario ->
                _state.update { it.copy(comentario = action.texto.take(LARGO_MAXIMO_COMENTARIO), error = null) }
            CrearResenaAction.Enviar -> enviar()
            CrearResenaAction.Reintentar -> verificarResenaExistente()
            CrearResenaAction.VolverAtras -> Unit
        }
    }

    private fun verificarResenaExistente() {
        val id = reservaId
        if (id.isNullOrBlank()) {
            _state.update { it.copy(verificando = false, errorVerificacion = ResenaError.DESCONOCIDO) }
            return
        }
        _state.update { it.copy(verificando = true, errorVerificacion = null) }
        viewModelScope.launch {
            when (val resultado = resenaRepository.obtenerPorReserva(id)) {
                is Result.Success -> _state.update {
                    it.copy(
                        verificando = false,
                        resenaExistente = resultado.data?.let { resena ->
                            ResenaPropia(
                                calificacion = resena.calificacion,
                                comentario = resena.comentario?.trim()?.takeIf { c -> c.isNotEmpty() },
                            )
                        },
                    )
                }
                is Result.Error -> _state.update {
                    it.copy(verificando = false, errorVerificacion = resultado.error)
                }
            }
        }
    }

    private fun enviar() {
        val estado = _state.value
        if (!estado.puedeEnviar) return
        val reserva = reservaId
        val profesional = profesionalId
        if (reserva.isNullOrBlank() || profesional.isNullOrBlank()) {
            _state.update { it.copy(error = ErrorEnvioResena.DESCONOCIDO) }
            return
        }
        val clienteId = firebaseAuth.currentUser?.uid
        if (clienteId == null) {
            _state.update { it.copy(error = ErrorEnvioResena.SIN_SESION) }
            return
        }
        val resena = Resena(
            id = reserva,
            reservaId = reserva,
            clienteId = clienteId,
            profesionalId = profesional,
            calificacion = estado.calificacion,
            comentario = estado.comentario.trim().takeIf { it.isNotEmpty() },
            // Marcador: `ResenaRepository.crear` ignora `fecha` y escribe server timestamp.
            fecha = Clock.System.now(),
            respuestaProfesional = null,
        )
        _state.update { it.copy(enviando = true, error = null) }
        viewModelScope.launch {
            when (val resultado = resenaRepository.crear(resena)) {
                is Result.Success -> _state.update { it.copy(enviando = false, enviada = true) }
                is Result.Error -> _state.update {
                    it.copy(enviando = false, error = resultado.error.aErrorEnvio())
                }
            }
        }
    }
}

private fun ResenaError.aErrorEnvio(): ErrorEnvioResena = when (this) {
    ResenaError.SIN_INTERNET -> ErrorEnvioResena.SIN_INTERNET
    ResenaError.SIN_PERMISO -> ErrorEnvioResena.SIN_PERMISO
    ResenaError.DESCONOCIDO -> ErrorEnvioResena.DESCONOCIDO
}
