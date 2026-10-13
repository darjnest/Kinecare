package com.darjnest.kinecare.feature.verification.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darjnest.kinecare.core.common.domain.model.EstadoVerificacion
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.feature.verification.data.repository.VerificacionRepository
import com.darjnest.kinecare.feature.verification.domain.EstadoSolicitudVerificacion
import com.darjnest.kinecare.feature.verification.domain.EstadoVerificacionError
import com.darjnest.kinecare.feature.verification.domain.MotivoRechazoVerificacion
import com.darjnest.kinecare.feature.verification.domain.SolicitarVerificacionError
import com.darjnest.kinecare.feature.verification.presentation.util.esUrlDeDidit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FaseVerificacion {
    /** Consultando el estado por primera vez (o tras un error). */
    CARGANDO,
    NO_SOLICITADO,
    PENDIENTE,
    APROBADO,
    RECHAZADO,

    /** No se pudo consultar el estado: pantalla de error con "Reintentar". */
    ERROR,
}

data class VerificationState(
    val fase: FaseVerificacion = FaseVerificacion.CARGANDO,
    /** Solo con [FaseVerificacion.RECHAZADO]; `null` si el backend no informo uno conocido. */
    val motivoRechazo: MotivoRechazoVerificacion? = null,
    /**
     * Error de la consulta: con [FaseVerificacion.ERROR] es la pantalla de
     * error; con cualquier otra fase, un "Actualizar estado" que fallo (se
     * muestra en linea y conserva los botones).
     */
    val errorEstado: EstadoVerificacionError? = null,
    /** `solicitarVerificacion` en curso: botones deshabilitados. */
    val solicitando: Boolean = false,
    /** "Actualizar estado" en curso: botones deshabilitados. */
    val actualizando: Boolean = false,
    val errorSolicitud: SolicitarVerificacionError? = null,
    /** El dispositivo no tiene navegador para abrir el Custom Tab. */
    val navegadorNoDisponible: Boolean = false,
)

sealed interface VerificationAction {
    /** "Verificar mi identidad", "Continuar verificacion" e "Intentar de nuevo": abre (o continua) la verificacion. */
    data object Verificar : VerificationAction

    /** "Actualizar estado" desde una verificacion en curso. */
    data object Actualizar : VerificationAction

    /** "Reintentar" tras no poder consultar el estado. */
    data object Reintentar : VerificationAction

    /**
     * La pantalla volvio a primer plano (p. ej. al cerrar el Custom Tab o
     * volver del resultado): si la verificacion estaba en curso, se refresca
     * el estado sin tapar el contenido.
     */
    data object Reanudar : VerificationAction

    /** El `Root` no pudo abrir el Custom Tab (sin navegador instalado). */
    data object NavegadorNoDisponible : VerificationAction

    /** Navegacion: las resuelve el `Root` contra el NavGraph, no el ViewModel. */
    data object VolverAtras : VerificationAction
    data object IniciarSesion : VerificationAction
}

/** Eventos de una sola vez; no viajan en el estado para no repetirse al rotar la pantalla. */
sealed interface VerificationEvent {
    /**
     * Abrir [url] (verificacion hospedada por Didit) en un Custom Tab. No es
     * `data class` a proposito: asi un `toString` accidental no filtra la URL.
     */
    class AbrirVerificacion(val url: String) : VerificationEvent
}

@HiltViewModel
class VerificationViewModel @Inject constructor(
    private val verificacionRepository: VerificacionRepository,
) : ViewModel() {

    private enum class ModoConsulta {
        /** Primera carga o "Reintentar": reemplaza el contenido por el spinner. */
        CARGA,

        /** "Actualizar estado": conserva el contenido y muestra el error en linea. */
        ACTUALIZAR,

        /** Al volver a primer plano: conserva el contenido y ignora los errores. */
        REANUDAR,
    }

    private val _state = MutableStateFlow(VerificationState())
    val state: StateFlow<VerificationState> = _state.asStateFlow()

    private val _events = Channel<VerificationEvent>(Channel.BUFFERED)
    val events: Flow<VerificationEvent> = _events.receiveAsFlow()

    private var consultaJob: Job? = null

    init {
        consultar(ModoConsulta.CARGA)
    }

    fun onAction(action: VerificationAction) {
        when (action) {
            VerificationAction.Verificar -> verificar()
            VerificationAction.Actualizar -> consultar(ModoConsulta.ACTUALIZAR)
            VerificationAction.Reintentar -> consultar(ModoConsulta.CARGA)
            VerificationAction.Reanudar ->
                // Solo una verificacion en curso puede cambiar fuera de la app.
                if (_state.value.fase == FaseVerificacion.PENDIENTE) consultar(ModoConsulta.REANUDAR)
            VerificationAction.NavegadorNoDisponible -> _state.update { it.copy(navegadorNoDisponible = true) }
            VerificationAction.VolverAtras,
            VerificationAction.IniciarSesion,
            -> Unit
        }
    }

    private fun consultar(modo: ModoConsulta) {
        if (consultaJob?.isActive == true || _state.value.solicitando) return
        _state.update {
            when (modo) {
                ModoConsulta.CARGA -> it.copy(
                    fase = FaseVerificacion.CARGANDO,
                    errorEstado = null,
                    errorSolicitud = null,
                    navegadorNoDisponible = false,
                )
                ModoConsulta.ACTUALIZAR -> it.copy(actualizando = true, errorEstado = null)
                ModoConsulta.REANUDAR -> it
            }
        }
        consultaJob = viewModelScope.launch {
            when (val resultado = verificacionRepository.consultarEstado(null)) {
                is Result.Success -> _state.update { aplicarEstado(it, resultado.data).copy(actualizando = false) }
                is Result.Error -> _state.update {
                    when (modo) {
                        ModoConsulta.CARGA ->
                            it.copy(fase = FaseVerificacion.ERROR, errorEstado = resultado.error)
                        ModoConsulta.ACTUALIZAR ->
                            it.copy(actualizando = false, errorEstado = resultado.error)
                        ModoConsulta.REANUDAR -> it
                    }
                }
            }
        }
    }

    private fun aplicarEstado(estado: VerificationState, resultado: EstadoSolicitudVerificacion): VerificationState {
        val fase = when (resultado.estado) {
            EstadoVerificacion.NO_SOLICITADO -> FaseVerificacion.NO_SOLICITADO
            EstadoVerificacion.PENDIENTE -> FaseVerificacion.PENDIENTE
            EstadoVerificacion.APROBADO -> FaseVerificacion.APROBADO
            EstadoVerificacion.RECHAZADO -> FaseVerificacion.RECHAZADO
        }
        return estado.copy(
            fase = fase,
            motivoRechazo = if (fase == FaseVerificacion.RECHAZADO) resultado.motivo else null,
            errorEstado = null,
        )
    }

    private fun verificar() {
        val actual = _state.value
        if (actual.solicitando || actual.actualizando || actual.fase == FaseVerificacion.CARGANDO) return
        _state.update { it.copy(solicitando = true, errorSolicitud = null, navegadorNoDisponible = false) }
        viewModelScope.launch {
            when (val resultado = verificacionRepository.solicitar()) {
                is Result.Success -> {
                    val url = resultado.data.url
                    if (esUrlDeDidit(url)) {
                        // La verificacion queda abierta: si el profesional vuelve sin terminar, ve "en curso".
                        _state.update {
                            it.copy(
                                solicitando = false,
                                fase = FaseVerificacion.PENDIENTE,
                                motivoRechazo = null,
                                errorEstado = null,
                            )
                        }
                        _events.send(VerificationEvent.AbrirVerificacion(url))
                    } else {
                        _state.update {
                            it.copy(solicitando = false, errorSolicitud = SolicitarVerificacionError.DESCONOCIDO)
                        }
                    }
                }
                is Result.Error -> if (resultado.error == SolicitarVerificacionError.YA_VERIFICADO) {
                    // No es un fallo: la identidad ya esta verificada, se muestra el estado real.
                    _state.update { it.copy(solicitando = false) }
                    consultar(ModoConsulta.CARGA)
                } else {
                    _state.update { it.copy(solicitando = false, errorSolicitud = resultado.error) }
                }
            }
        }
    }
}
