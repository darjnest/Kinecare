package com.darjnest.kinecare.feature.payment.presentation.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class PaymentState(
    val cargando: Boolean = false,
)

sealed interface PaymentAction

@HiltViewModel
class PaymentViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(PaymentState())
    val state: StateFlow<PaymentState> = _state.asStateFlow()

    fun onAction(action: PaymentAction) {
        // TODO: implementar cuando la feature "Pago" salga de Fase 1 (docs/TASKS.md)
    }
}
