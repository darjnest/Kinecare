package com.darjnest.kinecare.feature.payment.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darjnest.kinecare.core.common.data.error.ConectarMercadoPagoError
import com.darjnest.kinecare.core.common.data.repository.PagoRepository
import com.darjnest.kinecare.core.common.result.Result
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

data class ConectarMercadoPagoState(
    /** `conectarMercadoPago` en curso: unico momento en que el boton esta deshabilitado. */
    val cargando: Boolean = false,
    val error: ConectarMercadoPagoError? = null,
    /** El dispositivo no tiene navegador para abrir el Custom Tab. */
    val navegadorNoDisponible: Boolean = false,
)

sealed interface ConectarMercadoPagoAction {
    data object Conectar : ConectarMercadoPagoAction

    /** El `Root` no pudo abrir el Custom Tab (sin navegador instalado). */
    data object NavegadorNoDisponible : ConectarMercadoPagoAction

    /** Navegacion: las resuelve el `Root` contra el NavGraph, no el ViewModel. */
    data object VolverAtras : ConectarMercadoPagoAction
    data object IniciarSesion : ConectarMercadoPagoAction
}

/** Eventos de una sola vez; no viajan en el estado para no repetirse al rotar la pantalla. */
sealed interface ConectarMercadoPagoEvent {
    /**
     * Abrir [url] (autorizacion OAuth de Mercado Pago) en un Custom Tab. No es
     * `data class` a proposito: asi un `toString` accidental no filtra la URL.
     */
    class AbrirAutorizacion(val url: String) : ConectarMercadoPagoEvent
}

@HiltViewModel
class ConectarMercadoPagoViewModel @Inject constructor(
    private val pagoRepository: PagoRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ConectarMercadoPagoState())
    val state: StateFlow<ConectarMercadoPagoState> = _state.asStateFlow()

    private val _events = Channel<ConectarMercadoPagoEvent>(Channel.BUFFERED)
    val events: Flow<ConectarMercadoPagoEvent> = _events.receiveAsFlow()

    fun onAction(action: ConectarMercadoPagoAction) {
        when (action) {
            ConectarMercadoPagoAction.Conectar -> conectar()
            ConectarMercadoPagoAction.NavegadorNoDisponible ->
                _state.update { it.copy(navegadorNoDisponible = true) }
            ConectarMercadoPagoAction.VolverAtras,
            ConectarMercadoPagoAction.IniciarSesion,
            -> Unit
        }
    }

    private fun conectar() {
        if (_state.value.cargando) return
        _state.update { it.copy(cargando = true, error = null, navegadorNoDisponible = false) }
        viewModelScope.launch {
            when (val resultado = pagoRepository.obtenerUrlConexion()) {
                is Result.Success -> {
                    val url = resultado.data
                    if (esUrlHttps(url)) {
                        _state.update { it.copy(cargando = false) }
                        _events.send(ConectarMercadoPagoEvent.AbrirAutorizacion(url))
                    } else {
                        _state.update { it.copy(cargando = false, error = ConectarMercadoPagoError.DESCONOCIDO) }
                    }
                }
                is Result.Error -> _state.update { it.copy(cargando = false, error = resultado.error) }
            }
        }
    }
}
