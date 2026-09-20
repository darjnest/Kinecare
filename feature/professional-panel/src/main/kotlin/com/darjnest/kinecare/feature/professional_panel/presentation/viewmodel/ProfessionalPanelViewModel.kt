package com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class ProfessionalPanelState(
    val cargando: Boolean = false,
)

sealed interface ProfessionalPanelAction

@HiltViewModel
class ProfessionalPanelViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(ProfessionalPanelState())
    val state: StateFlow<ProfessionalPanelState> = _state.asStateFlow()

    fun onAction(action: ProfessionalPanelAction) {
        // TODO: implementar cuando la feature "Panel profesional" salga de Fase 1 (docs/TASKS.md)
    }
}
