package com.darjnest.kinecare.feature.verification.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darjnest.kinecare.core.common.domain.model.EstadoVerificacion
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.feature.verification.data.repository.VerificacionRepository
import com.darjnest.kinecare.feature.verification.domain.EstadoVerificacionError
import com.darjnest.kinecare.feature.verification.domain.MotivoRechazoVerificacion
import com.darjnest.kinecare.feature.verification.presentation.navigation.ARG_SOLICITUD_ID
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Cuantas veces se consulta el estado mientras siga `PENDIENTE`: el proveedor
 * puede tardar unos segundos en resolver la verificacion al volver del
 * navegador. `estadoVerificacion` ademas relee al proveedor, asi que cada
 * consulta puede resolverla.
 */
internal const val MAX_CONSULTAS_PENDIENTE = 5

/** Espera entre dos consultas mientras la verificacion sigue `PENDIENTE`. */
internal val ESPERA_ENTRE_CONSULTAS: Duration = 3.seconds

enum class FaseVerificacionResultado {
    /** Consultando al backend (incluye las esperas de la verificacion `PENDIENTE`). */
    CONSULTANDO,
    APROBADO,

    /** Sigue `PENDIENTE` tras agotar las consultas: se resolvera sola y se avisara al profesional. */
    EN_REVISION,
    RECHAZADO,

    /** El backend no tiene una verificacion para quien llega (deep link sin `solicitudId` y sin solicitudes). */
    NO_SOLICITADO,
    ERROR,
}

data class VerificacionResultadoState(
    val fase: FaseVerificacionResultado = FaseVerificacionResultado.CONSULTANDO,
    /** Solo con [FaseVerificacionResultado.RECHAZADO]; `null` si el backend no informo uno conocido. */
    val motivo: MotivoRechazoVerificacion? = null,
    /** Solo con [FaseVerificacionResultado.ERROR]. */
    val error: EstadoVerificacionError? = null,
)

sealed interface VerificacionResultadoAction {
    /** Vuelve a consultar el estado (error de red o verificacion aun en revision). */
    data object Reintentar : VerificacionResultadoAction

    /** Navegacion: las resuelve el `Root` contra el NavGraph, no el ViewModel. */
    data object Listo : VerificacionResultadoAction
    data object IniciarSesion : VerificacionResultadoAction
}

@HiltViewModel
class VerificacionResultadoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val verificacionRepository: VerificacionRepository,
) : ViewModel() {

    /**
     * Solo identifica la solicitud (viene de un deep link y puede faltar): el
     * estado se consulta siempre al backend. Sin id, el backend responde por la
     * verificacion mas reciente de quien llama.
     */
    private val solicitudId: String? = savedStateHandle.get<String>(ARG_SOLICITUD_ID)?.takeIf { it.isNotBlank() }

    private val _state = MutableStateFlow(VerificacionResultadoState())
    val state: StateFlow<VerificacionResultadoState> = _state.asStateFlow()

    private var consultaJob: Job? = null

    init {
        consultar()
    }

    fun onAction(action: VerificacionResultadoAction) {
        when (action) {
            VerificacionResultadoAction.Reintentar -> consultar()
            VerificacionResultadoAction.Listo,
            VerificacionResultadoAction.IniciarSesion,
            -> Unit
        }
    }

    private fun consultar() {
        if (consultaJob?.isActive == true) return
        _state.value = VerificacionResultadoState(FaseVerificacionResultado.CONSULTANDO)
        consultaJob = viewModelScope.launch {
            var consultas = 0
            while (true) {
                consultas++
                when (val resultado = verificacionRepository.consultarEstado(solicitudId)) {
                    is Result.Error -> {
                        _state.value = VerificacionResultadoState(FaseVerificacionResultado.ERROR, error = resultado.error)
                        return@launch
                    }
                    is Result.Success -> {
                        val estado = when (resultado.data.estado) {
                            EstadoVerificacion.APROBADO -> VerificacionResultadoState(FaseVerificacionResultado.APROBADO)
                            EstadoVerificacion.RECHAZADO -> VerificacionResultadoState(
                                FaseVerificacionResultado.RECHAZADO,
                                motivo = resultado.data.motivo,
                            )
                            EstadoVerificacion.NO_SOLICITADO ->
                                VerificacionResultadoState(FaseVerificacionResultado.NO_SOLICITADO)
                            EstadoVerificacion.PENDIENTE ->
                                if (consultas >= MAX_CONSULTAS_PENDIENTE) {
                                    VerificacionResultadoState(FaseVerificacionResultado.EN_REVISION)
                                } else {
                                    null
                                }
                        }
                        if (estado != null) {
                            _state.value = estado
                            return@launch
                        }
                    }
                }
                delay(ESPERA_ENTRE_CONSULTAS)
            }
        }
    }
}
