package com.darjnest.kinecare.feature.booking.presentation.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class BookingState(
    val cargando: Boolean = false,
)

sealed interface BookingAction

@HiltViewModel
class BookingViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(BookingState())
    val state: StateFlow<BookingState> = _state.asStateFlow()

    fun onAction(action: BookingAction) {
        // TODO: implementar cuando la feature "Reserva" salga de Fase 1 (docs/TASKS.md)
    }
}
