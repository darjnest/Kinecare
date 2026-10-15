package com.darjnest.kinecare.feature.payment.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.feature.payment.data.repository.PagoRepository
import com.darjnest.kinecare.feature.payment.domain.PagarComisionError
import com.darjnest.kinecare.feature.payment.presentation.util.esUrlDeMercadoPago
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

data class PagarComisionState(
    /** `pagarSaldoComision` en curso: unico momento en que el boton esta deshabilitado. */
    val cargando: Boolean = false,
    val error: PagarComisionError? = null,
    /** El dispositivo no tiene navegador para abrir el Custom Tab. */
    val navegadorNoDisponible: Boolean = false,
)

sealed interface PagarComisionAction {
    data object Pagar : PagarComisionAction

    /** El `Root` no pudo abrir el Custom Tab (sin navegador instalado). */
    data object NavegadorNoDisponible : PagarComisionAction

    /** Navegacion: las resuelve el `Root` contra el NavGraph, no el ViewModel. */
    data object VolverAtras : PagarComisionAction
    data object IniciarSesion : PagarComisionAction
}

/** Eventos de una sola vez; no viajan en el estado para no repetirse al rotar la pantalla. */
sealed interface PagarComisionEvent {
    /** Abrir [url] (Checkout Pro de KineCare) en un Custom Tab. No es `data class`: un `toString` no filtra la URL. */
    class AbrirPago(val url: String) : PagarComisionEvent
}

@HiltViewModel
class PagarComisionViewModel @Inject constructor(
    private val pagoRepository: PagoRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PagarComisionState())
    val state: StateFlow<PagarComisionState> = _state.asStateFlow()

    private val _events = Channel<PagarComisionEvent>(Channel.BUFFERED)
    val events: Flow<PagarComisionEvent> = _events.receiveAsFlow()

    fun onAction(action: PagarComisionAction) {
        when (action) {
            PagarComisionAction.Pagar -> pagar()
            PagarComisionAction.NavegadorNoDisponible -> _state.update { it.copy(navegadorNoDisponible = true) }
            PagarComisionAction.VolverAtras,
            PagarComisionAction.IniciarSesion,
            -> Unit
        }
    }

    private fun pagar() {
        if (_state.value.cargando) return
        _state.update { it.copy(cargando = true, error = null, navegadorNoDisponible = false) }
        viewModelScope.launch {
            when (val resultado = pagoRepository.pagarComisiones()) {
                is Result.Success -> {
                    val url = resultado.data.urlPago
                    if (esUrlDeMercadoPago(url)) {
                        _state.update { it.copy(cargando = false) }
                        _events.send(PagarComisionEvent.AbrirPago(url))
                    } else {
                        _state.update { it.copy(cargando = false, error = PagarComisionError.DESCONOCIDO) }
                    }
                }
                is Result.Error -> _state.update { it.copy(cargando = false, error = resultado.error) }
            }
        }
    }
}
