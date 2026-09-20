package com.darjnest.kinecare.feature.verification.presentation.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class VerificationState(
    val cargando: Boolean = false,
)

sealed interface VerificationAction

@HiltViewModel
class VerificationViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(VerificationState())
    val state: StateFlow<VerificationState> = _state.asStateFlow()

    fun onAction(action: VerificationAction) {
        // TODO: implementar cuando la feature "Verificación" salga de Fase 1 (docs/TASKS.md)
    }
}
