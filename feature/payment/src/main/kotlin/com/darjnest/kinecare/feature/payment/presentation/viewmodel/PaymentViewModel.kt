package com.darjnest.kinecare.feature.payment.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darjnest.kinecare.core.common.data.error.IniciarPagoError
import com.darjnest.kinecare.core.common.data.repository.PagoRepository
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.feature.payment.presentation.navigation.ARG_MONTO_CLP
import com.darjnest.kinecare.feature.payment.presentation.navigation.ARG_RESERVA_ID
import com.darjnest.kinecare.feature.payment.presentation.navigation.ARG_TITULO
import com.darjnest.kinecare.feature.payment.presentation.util.esUrlHttps
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PaymentState(
    /** Nombre del servicio a pagar; solo informativo. */
    val titulo: String = "",
    /** Total en CLP que mostro la reserva; solo informativo: el cobro real lo fija el backend. */
    val montoClp: Long = 0,
    /** `iniciarPago` en curso: unico momento en que el boton "Pagar" esta deshabilitado. */
    val cargando: Boolean = false,
    val error: IniciarPagoError? = null,
    /** El dispositivo no tiene navegador para abrir el Custom Tab. */
    val navegadorNoDisponible: Boolean = false,
)

sealed interface PaymentAction {
    data object Pagar : PaymentAction

    /** El `Root` no pudo abrir el Custom Tab (sin navegador instalado). */
    data object NavegadorNoDisponible : PaymentAction

    /** Navegacion: la resuelve el `Root` contra el NavGraph, no el ViewModel. */
    data object VolverAtras : PaymentAction
}

/** Eventos de una sola vez; no viajan en el estado para no repetirse al rotar la pantalla. */
sealed interface PaymentEvent {
    /**
     * Abrir [url] (pagina de pago de Mercado Pago) en un Custom Tab. No es
     * `data class` a proposito: asi un `toString` accidental no filtra la URL.
     */
    class AbrirPago(val url: String) : PaymentEvent
}

@HiltViewModel
class PaymentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pagoRepository: PagoRepository,
) : ViewModel() {

    private val reservaId: String = savedStateHandle[ARG_RESERVA_ID] ?: ""

    private val _state = MutableStateFlow(
        PaymentState(
            titulo = savedStateHandle[ARG_TITULO] ?: "",
            montoClp = savedStateHandle[ARG_MONTO_CLP] ?: 0L,
        ),
    )
    val state: StateFlow<PaymentState> = _state.asStateFlow()

    private val _events = Channel<PaymentEvent>(Channel.BUFFERED)
    val events: Flow<PaymentEvent> = _events.receiveAsFlow()

    fun onAction(action: PaymentAction) {
        when (action) {
            PaymentAction.Pagar -> pagar()
            PaymentAction.NavegadorNoDisponible -> _state.update { it.copy(navegadorNoDisponible = true) }
            // Volver atras es navegacion: el Root la resuelve contra el NavGraph.
            PaymentAction.VolverAtras -> Unit
        }
    }

    private fun pagar() {
        if (_state.value.cargando) return
        if (reservaId.isBlank()) {
            _state.update { it.copy(error = IniciarPagoError.DATOS_INVALIDOS) }
            return
        }
        _state.update { it.copy(cargando = true, error = null, navegadorNoDisponible = false) }
        viewModelScope.launch {
            when (val resultado = pagoRepository.iniciar(reservaId)) {
                is Result.Success -> {
                    val url = resultado.data.urlPago
                    if (esUrlHttps(url)) {
                        _state.update { it.copy(cargando = false) }
                        _events.send(PaymentEvent.AbrirPago(url))
                    } else {
                        _state.update { it.copy(cargando = false, error = IniciarPagoError.DESCONOCIDO) }
                    }
                }
                is Result.Error -> _state.update { it.copy(cargando = false, error = resultado.error) }
            }
        }
    }
}
