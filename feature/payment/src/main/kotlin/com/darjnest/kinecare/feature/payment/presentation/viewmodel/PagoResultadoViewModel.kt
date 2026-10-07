package com.darjnest.kinecare.feature.payment.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darjnest.kinecare.feature.payment.domain.EstadoPagoError
import com.darjnest.kinecare.feature.payment.data.repository.PagoRepository
import com.darjnest.kinecare.core.common.domain.model.EstadoPago
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.feature.payment.presentation.navigation.ARG_PAGO_ID
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
 * Cuantas veces se consulta el estado mientras siga `PENDIENTE`: el webhook de
 * Mercado Pago puede tardar unos segundos en llegar. `estadoPago` ademas relee
 * el pago a Mercado Pago, asi que cada consulta puede resolverlo.
 */
internal const val MAX_CONSULTAS_PENDIENTE = 5

/** Espera entre dos consultas mientras el pago sigue `PENDIENTE`. */
internal val ESPERA_ENTRE_CONSULTAS: Duration = 3.seconds

enum class FasePagoResultado {
    /** Consultando al backend (incluye las esperas del pago `PENDIENTE`). */
    CONSULTANDO,
    APROBADO,

    /** Sigue `PENDIENTE` tras agotar las consultas: se confirmara solo cuando llegue la notificacion. */
    EN_PROCESO,
    RECHAZADO,
    REEMBOLSADO,
    ERROR,
}

data class PagoResultadoState(
    val fase: FasePagoResultado = FasePagoResultado.CONSULTANDO,
    /** Solo con [FasePagoResultado.ERROR]. */
    val error: EstadoPagoError? = null,
)

sealed interface PagoResultadoAction {
    /** Vuelve a consultar el estado (error de red o pago aun en proceso). */
    data object Reintentar : PagoResultadoAction

    /** Navegacion: las resuelve el `Root` contra el NavGraph, no el ViewModel. */
    data object IrAMisCitas : PagoResultadoAction
    data object ReintentarPago : PagoResultadoAction
    data object IniciarSesion : PagoResultadoAction
}

@HiltViewModel
class PagoResultadoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pagoRepository: PagoRepository,
) : ViewModel() {

    /** Solo identifica el pago (viene de un deep link): el estado se consulta siempre al backend. */
    private val pagoId: String? = savedStateHandle[ARG_PAGO_ID]

    private val _state = MutableStateFlow(PagoResultadoState())
    val state: StateFlow<PagoResultadoState> = _state.asStateFlow()

    private var consultaJob: Job? = null

    init {
        consultar()
    }

    fun onAction(action: PagoResultadoAction) {
        when (action) {
            PagoResultadoAction.Reintentar -> consultar()
            PagoResultadoAction.IrAMisCitas,
            PagoResultadoAction.ReintentarPago,
            PagoResultadoAction.IniciarSesion,
            -> Unit
        }
    }

    private fun consultar() {
        if (consultaJob?.isActive == true) return
        if (pagoId.isNullOrBlank()) {
            _state.value = PagoResultadoState(FasePagoResultado.ERROR, EstadoPagoError.DATOS_INVALIDOS)
            return
        }
        _state.value = PagoResultadoState(FasePagoResultado.CONSULTANDO)
        consultaJob = viewModelScope.launch {
            var consultas = 0
            while (true) {
                consultas++
                when (val resultado = pagoRepository.consultarEstado(pagoId)) {
                    is Result.Error -> {
                        _state.value = PagoResultadoState(FasePagoResultado.ERROR, resultado.error)
                        return@launch
                    }
                    is Result.Success -> {
                        val fase = when (resultado.data) {
                            EstadoPago.AUTORIZADO -> FasePagoResultado.APROBADO
                            EstadoPago.RECHAZADO -> FasePagoResultado.RECHAZADO
                            EstadoPago.REEMBOLSADO -> FasePagoResultado.REEMBOLSADO
                            EstadoPago.PENDIENTE ->
                                if (consultas >= MAX_CONSULTAS_PENDIENTE) FasePagoResultado.EN_PROCESO else null
                        }
                        if (fase != null) {
                            _state.value = PagoResultadoState(fase)
                            return@launch
                        }
                    }
                }
                delay(ESPERA_ENTRE_CONSULTAS)
            }
        }
    }
}
